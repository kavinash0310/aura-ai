package ai.aura.api.api;

import ai.aura.api.cyber.GitHubAdvisoryData;

import java.time.Instant;
import java.util.List;

public record GitHubAdvisoryLookupResponse(
        Instant requestedAt,
        String cve,
        String status,
        Instant retrievedAt,
        List<GitHubAdvisoryData.Advisory> advisories,
        boolean advisoriesTruncated,
        String note
) {
    public GitHubAdvisoryLookupResponse {
        advisories = List.copyOf(advisories);
    }
}
