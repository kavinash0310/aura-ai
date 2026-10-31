package ai.aura.api.api;

import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchMode;

import java.time.Instant;
import java.util.UUID;

public record ResearchJobResponse(
        UUID id,
        String subject,
        ResearchMode mode,
        ResearchJobStatus status,
        Instant createdAt
) {
    public static ResearchJobResponse from(ResearchJob job) {
        return new ResearchJobResponse(
                job.getId(),
                job.getSubject(),
                job.getMode(),
                job.getStatus(),
                job.getCreatedAt()
        );
    }
}
