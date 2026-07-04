package dev.prpilot.review.github;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Fetches the actual changed files (diff) from a GitHub PR via the REST API.
 * This replaces the title-based query with real code changes,
 * making RAG retrieval and Claude's review dramatically more relevant.
 */
@Service
@Slf4j
public class GitHubDiffService {

    private final RestClient restClient;

    public GitHubDiffService(
            @Value("${prpilot.github.token}") String token,
            @Value("${prpilot.github.api-url}") String apiUrl) {

        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Returns a combined string of all changed file patches in the PR.
     * Falls back to empty string if the API call fails — review still proceeds
     * with title-based query rather than crashing entirely.
     */
    public String fetchPrDiff(String repoFullName, Long prNumber) {
        try {
            String url = "/repos/" + repoFullName + "/pulls/" + prNumber + "/files";
            log.debug("Fetching PR diff from {}", url);

            List<GitHubFile> files = restClient.get()
                    .uri(url)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<>() {});

            if (files == null || files.isEmpty()) {
                log.warn("No files found for PR #{} in {}", prNumber, repoFullName);
                return "";
            }

            String diff = files.stream()
                    .filter(f -> f.patch() != null && !f.patch().isBlank())
                    .map(f -> "File: " + f.filename() + "\n" + f.patch())
                    .collect(Collectors.joining("\n\n---\n\n"));

            log.info("Fetched diff for PR #{}: {} files, {} chars",
                    prNumber, files.size(), diff.length());

            // Truncate if too long for embedding (voyage-code-2 has token limits)
            if (diff.length() > 8000) {
                log.debug("Truncating diff from {} to 8000 chars", diff.length());
                diff = diff.substring(0, 8000);
            }

            return diff;

        } catch (Exception e) {
            log.error("Failed to fetch PR diff for {}/pull/{}: {}",
                    repoFullName, prNumber, e.getMessage());
            return ""; // graceful fallback
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GitHubFile(
            String filename,
            String status,   // "added", "modified", "removed"
            int additions,
            int deletions,
            String patch     // the actual diff text
    ) {}
}