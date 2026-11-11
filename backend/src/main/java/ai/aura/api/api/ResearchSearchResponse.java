package ai.aura.api.api;

import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchProviderRun;

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
        List<ProviderRunResponse> providerRuns,
        String coverageNote
) {
    public static ResearchSearchResponse from(
            ResearchJob job,
            List<ResearchSourceResponse> results,
            List<ResearchProviderRun> providerRuns
    ) {
        List<ProviderRunResponse> runResponses = providerRuns.stream()
                .map(ProviderRunResponse::from)
                .toList();
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
                runResponses,
                coverageNote(job.getSearchProvider(), job.getStatus(), runResponses)
        );
    }

    private static String coverageNote(
            String provider,
            ResearchJobStatus status,
            List<ProviderRunResponse> providerRuns
    ) {
        if (status == ResearchJobStatus.PARTIAL) {
            java.util.Map<String, ProviderRunResponse> latestAttempts = new java.util.LinkedHashMap<>();
            for (ProviderRunResponse run : providerRuns) {
                String key = run.provider() + ":" + run.queryOrder();
                latestAttempts.merge(key, run, (current, candidate) ->
                        candidate.attempt() > current.attempt() ? candidate : current);
            }
            String failedProviders = latestAttempts.values().stream()
                    .filter(run -> run.status() == ai.aura.api.domain.ResearchProviderRunStatus.FAILED
                            || run.status() == ai.aura.api.domain.ResearchProviderRunStatus.SKIPPED)
                    .map(ProviderRunResponse::provider)
                    .collect(java.util.stream.Collectors.joining(", "));
            return "Partial coverage: successful providers' results are saved; " + failedProviders
                    + " failed or were skipped and did not return results. Provider outcome and failure codes are listed below. "
                    + "Coverage remains partial, not a crawl of the internet.";
        }
        if ("all".equals(provider)) {
            return "AURA queried every currently configured public search provider. Sources show which provider returned each result; duplicate links may appear under more than one provider. Provider outcomes are listed below. Coverage remains partial, not a crawl of the internet. GDELT covers indexed news from at most the past three months. Bluesky covers bounded public-post search and does not apply country filters; it does not connect to an account or private feed. OpenAlex returns scholarly works matched against titles, abstracts, and keywords; country and language filters are not applied.";
        }
        if ("gdelt".equals(provider)) {
            return "GDELT indexes a subset of public global news and searches at most the past three months. It does not search social posts or the entire internet.";
        }
        if ("bluesky".equals(provider)) {
            return "Results are bounded public Bluesky post search through the official public API. Country filters are not applied. AURA does not connect to accounts, read private feeds, or search the entire internet.";
        }
        if ("openalex".equals(provider)) {
            return "OpenAlex returns bounded scholarly work records matched against titles, abstracts, and keywords. Abstracts may be missing or incomplete. Freshness uses publication dates at calendar-day precision; country and language filters are not applied. This is not general web search or a complete author profile.";
        }
        return "Results cover only the selected provider and enabled result types; they do not represent the entire internet. Bluesky public-post search does not connect to an account or private feed.";
    }
}


