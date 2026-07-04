package dev.prpilot.review.kafka;

import dev.prpilot.review.claude.ClaudeReviewService;
import dev.prpilot.review.embedding.VoyageEmbeddingService;
import dev.prpilot.review.github.GitHubDiffService;
import dev.prpilot.review.model.PullRequestEvent;
import dev.prpilot.review.model.Review;
import dev.prpilot.review.repository.ReviewRepository;
import dev.prpilot.review.retrieval.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PullRequestEventConsumer {

    private final VoyageEmbeddingService embeddingService;
    private final RagRetrievalService ragRetrievalService;
    private final ClaudeReviewService claudeReviewService;
    private final ReviewRepository reviewRepository;
    private final ReviewCompletedProducer reviewCompletedProducer;
    private final GitHubDiffService gitHubDiffService;

    @Value("${prpilot.anthropic.model}")
    private String modelUsed;

    @KafkaListener(
            topics = "${prpilot.kafka.topics.pr-events}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onPullRequestEvent(PullRequestEvent event) {
        log.info("Consumed event: delivery={}, repo={}, pr=#{}",
                event.deliveryId(), event.repoFullName(), event.prNumber());

        // Idempotency check
        if (reviewRepository.findByDeliveryId(event.deliveryId()).isPresent()) {
            log.info("Skipping duplicate delivery={}", event.deliveryId());
            return;
        }

        // Create PENDING review immediately
        Review review = Review.builder()
                .repoFullName(event.repoFullName())
                .prNumber(event.prNumber())
                .headSha(event.headSha())
                .deliveryId(event.deliveryId())
                .status("PENDING")
                .build();
        reviewRepository.save(review);

        try {
            // 1. Embed the PR query
            String queryText = buildQueryText(event);
            String queryEmbedding = embeddingService.embedQuery(queryText);

            // 2. RAG retrieval
            List<String> relevantChunks = ragRetrievalService
                    .retrieveRelevantChunks(event.repoFullName(), queryEmbedding);

            // 3. Claude review
            String reviewBody = claudeReviewService.generateReview(
                    event.prTitle(),
                    event.prAuthor(),
                    event.repoFullName(),
                    event.headSha(),
                    relevantChunks);

            // 4. Persist completed review
            review.setStatus("COMPLETED");
            review.setReviewBody(reviewBody);
            review.setModelUsed(modelUsed);
            review.setChunksUsed(relevantChunks.size());
            review.setCompletedAt(Instant.now());
            reviewRepository.save(review);

            // 5. Publish to reviews.completed so notification-service can post to GitHub
            reviewCompletedProducer.publish(new ReviewCompletedEvent(
                    event.deliveryId(),
                    event.repoFullName(),
                    event.prNumber(),
                    event.htmlUrl(),
                    reviewBody));

            log.info("Review completed for delivery={}, chunks={}, model={}",
                    event.deliveryId(), relevantChunks.size(), modelUsed);

        } catch (Exception e) {
            review.setStatus("FAILED");
            reviewRepository.save(review);
            log.error("Review failed for delivery={}", event.deliveryId(), e);
        }
    }

    private String buildQueryText(PullRequestEvent event) {
        // Try to fetch the real diff first
        String diff = gitHubDiffService.fetchPrDiff(
                event.repoFullName(), event.prNumber());

        if (!diff.isBlank()) {
            log.debug("Using real PR diff as query ({} chars)", diff.length());
            return """
                    Pull request: %s
                    Repository: %s
                    Author: %s
                    Branch: %s -> %s

                    Changed files:
                    %s
                    """.formatted(
                    event.prTitle(),
                    event.repoFullName(),
                    event.prAuthor(),
                    event.headBranch(),
                    event.baseBranch(),
                    diff);
        }

        // Fallback to metadata only if diff fetch failed
        log.warn("Falling back to metadata-only query for delivery={}",
                event.deliveryId());
        return """
                Pull request: %s
                Repository: %s
                Author: %s
                Branch: %s -> %s
                """.formatted(
                event.prTitle(),
                event.repoFullName(),
                event.prAuthor(),
                event.headBranch(),
                event.baseBranch());
    }
}