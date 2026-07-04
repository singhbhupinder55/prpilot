package dev.prpilot.review;

import dev.prpilot.review.claude.ClaudeReviewService;
import dev.prpilot.review.embedding.VoyageEmbeddingService;
import dev.prpilot.review.github.GitHubDiffService;
import dev.prpilot.review.model.Review;
import dev.prpilot.review.repository.ReviewRepository;
import dev.prpilot.review.retrieval.RagRetrievalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class ReviewServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withDatabaseName("prpilot")
            .withUsername("prpilot")
            .withPassword("prpilot_dev");

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.7.0"));

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("prpilot.anthropic.api-key", () -> "test-key");
        registry.add("prpilot.anthropic.api-url",
                () -> "https://api.anthropic.com/v1/messages");
        registry.add("prpilot.anthropic.model", () -> "claude-haiku-4-5");
        registry.add("prpilot.anthropic.max-tokens", () -> "1024");
        registry.add("prpilot.voyage.api-key", () -> "test-key");
        registry.add("prpilot.voyage.api-url",
                () -> "https://api.voyageai.com/v1/embeddings");
        registry.add("prpilot.voyage.model", () -> "voyage-code-2");
        registry.add("prpilot.github.token", () -> "test-token");
        registry.add("prpilot.github.api-url", () -> "https://api.github.com");
    }

    // Mock all external API dependencies — no real HTTP calls in tests
    @MockitoBean
    VoyageEmbeddingService voyageEmbeddingService;

    @MockitoBean
    ClaudeReviewService claudeReviewService;

    @MockitoBean
    RagRetrievalService ragRetrievalService;

    @MockitoBean
    GitHubDiffService gitHubDiffService;

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    ReviewRepository reviewRepository;

    @Test
    @DisplayName("PR event triggers review creation with COMPLETED status")
    void prEventCreatesCompletedReview() throws Exception {
        // Arrange — mock all external calls
        when(gitHubDiffService.fetchPrDiff(anyString(), anyLong()))
                .thenReturn("+ added exponential backoff logic\n- removed fixed delay");
        when(voyageEmbeddingService.embedQuery(anyString()))
                .thenReturn("[0.1,0.2,0.3]");
        when(ragRetrievalService.retrieveRelevantChunks(anyString(), anyString()))
                .thenReturn(List.of("public class WebSocketHandler {}",
                        "// reconnect logic"));
        when(claudeReviewService.generateReview(
                anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn("## Code Review\n\nThis looks good overall.");

        // Act — publish a PR event to Kafka
        kafkaTemplate.send("pr.events", "test/test-repo", buildPayload(
                "integration-test-001", "Add exponential backoff", "sha-001", 1));

        // Assert — wait for consumer to process
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Review> review = reviewRepository
                    .findByDeliveryId("integration-test-001");

            assertThat(review).isPresent();
            assertThat(review.get().getStatus()).isEqualTo("COMPLETED");
            assertThat(review.get().getModelUsed()).isEqualTo("claude-haiku-4-5");
            assertThat(review.get().getReviewBody()).contains("Code Review");
            assertThat(review.get().getChunksUsed()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("duplicate delivery ID is skipped without creating a second review")
    void duplicateDeliveryIdIsSkipped() throws Exception {
        when(gitHubDiffService.fetchPrDiff(anyString(), anyLong()))
                .thenReturn("+ some changed code");
        when(voyageEmbeddingService.embedQuery(anyString()))
                .thenReturn("[0.1,0.2,0.3]");
        when(ragRetrievalService.retrieveRelevantChunks(anyString(), anyString()))
                .thenReturn(List.of("some code"));
        when(claudeReviewService.generateReview(
                anyString(), anyString(), anyString(), anyString(), anyList()))
                .thenReturn("Review content");

        String payload = buildPayload(
                "integration-test-002", "Dup PR", "sha-002", 2);

        // Send twice — second should be skipped
        kafkaTemplate.send("pr.events", "test/test-repo", payload);
        kafkaTemplate.send("pr.events", "test/test-repo", payload);

        // Wait for processing
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<Review> review = reviewRepository
                    .findByDeliveryId("integration-test-002");
            assertThat(review).isPresent();
            assertThat(review.get().getStatus()).isEqualTo("COMPLETED");
        });

        // Only one review should exist for this deliveryId
        long count = reviewRepository.findAll().stream()
                .filter(r -> "integration-test-002".equals(r.getDeliveryId()))
                .count();
        assertThat(count).isEqualTo(1);
    }

    private String buildPayload(String deliveryId, String title,
                                String sha, int prNumber) {
        return """
                {
                  "deliveryId": "%s",
                  "action": "opened",
                  "repoFullName": "test/test-repo",
                  "prNumber": %d,
                  "prTitle": "%s",
                  "prAuthor": "test-user",
                  "headSha": "%s",
                  "baseBranch": "main",
                  "headBranch": "feature/test",
                  "htmlUrl": "https://github.com/test/test-repo/pull/%d",
                  "receivedAt": "2026-06-30T00:00:00Z"
                }
                """.formatted(deliveryId, prNumber, title, sha, prNumber);
    }
}