package ai.aura.api.api;

import ai.aura.api.domain.ResearchProviderRun;
import ai.aura.api.domain.ResearchProviderRunStatus;

import java.time.Instant;

public record ProviderRunResponse(
        String provider,
        ResearchProviderRunStatus status,
        int resultCount,
        String failureCode,
        Instant startedAt,
        Instant completedAt
) {
    public static ProviderRunResponse from(ResearchProviderRun run) {
        return new ProviderRunResponse(
                run.getProvider(),
                run.getStatus(),
                run.getResultCount(),
                run.getFailureCode(),
                run.getStartedAt(),
                run.getCompletedAt()
        );
    }
}
