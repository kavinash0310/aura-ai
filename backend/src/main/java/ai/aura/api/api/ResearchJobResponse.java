package ai.aura.api.api;

import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchMode;
import ai.aura.api.domain.ResearchProviderRun;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ResearchJobResponse(
        UUID id,
        String subject,
        ResearchMode mode,
        ResearchJobStatus status,
        Instant createdAt,
        String searchProvider,
        String searchCountry,
        String searchLanguage,
        String searchFreshness,
        Integer searchLimit,
        Instant searchStartedAt,
        Instant searchCompletedAt,
        int searchResultCount,
        String failureCode,
        List<ProviderRunResponse> providerRuns
) {
    public static ResearchJobResponse from(ResearchJob job, List<ResearchProviderRun> providerRuns) {
        return new ResearchJobResponse(
                job.getId(),
                job.getSubject(),
                job.getMode(),
                job.getStatus(),
                job.getCreatedAt(),
                job.getSearchProvider(),
                job.getSearchCountry(),
                job.getSearchLanguage(),
                job.getSearchFreshness(),
                job.getSearchLimit(),
                job.getSearchStartedAt(),
                job.getSearchCompletedAt(),
                job.getSearchResultCount(),
                job.getFailureCode(),
                providerRuns.stream().map(ProviderRunResponse::from).toList()
        );
    }
}

