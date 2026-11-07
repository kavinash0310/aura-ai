package ai.aura.api.api;

import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResearchSearchResponse(
        UUID jobId,
        String query,
        ResearchJobStatus status,
        String provider,
        String country,
        String language,
        String freshness,
        Integer requestedLimit,
        int providerResultCount,
        Instant searchedAt,
        List<ResearchSourceResponse> results,
        String coverageNote
) {
    public static ResearchSearchResponse from(ResearchJob job, List<ResearchSourceResponse> results) {
        return new ResearchSearchResponse(
                job.getId(),
                job.getSubject(),
                job.getStatus(),
                job.getSearchProvider(),
                job.getSearchCountry(),
                job.getSearchLanguage(),
                job.getSearchFreshness(),
                job.getSearchLimit(),
                job.getSearchResultCount(),
                job.getSearchCompletedAt(),
                List.copyOf(results),
                coverageNote(job.getSearchProvider())
        );
    }

    private static String coverageNote(String provider) {
        if ("gdelt".equals(provider)) {
            return "GDELT indexes a subset of public global news and searches at most the past three months. It does not search social posts or the entire internet.";
        }
        return "Results cover only the selected provider and enabled result types; they do not represent the entire internet. Brave discussion results are not a direct social-account connector.";
    }
}


