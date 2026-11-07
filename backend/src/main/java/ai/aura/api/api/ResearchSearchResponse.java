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
        if ("all".equals(provider)) {
            return "AURA queried every currently configured public search provider. Sources show which provider returned each result; duplicate links may appear under more than one provider. Coverage remains partial, and any provider failure marks the full search as failed. GDELT covers indexed news from at most the past three months. Bluesky covers bounded public-post search and does not apply country filters; it does not connect to an account or private feed.";
        }
        if ("gdelt".equals(provider)) {
            return "GDELT indexes a subset of public global news and searches at most the past three months. It does not search social posts or the entire internet.";
        }
        if ("bluesky".equals(provider)) {
            return "Results are bounded public Bluesky post search through the official public API. Country filters are not applied. AURA does not connect to accounts, read private feeds, or search the entire internet.";
        }
        return "Results cover only the selected provider and enabled result types; they do not represent the entire internet. Bluesky public-post search does not connect to an account or private feed.";
    }
}


