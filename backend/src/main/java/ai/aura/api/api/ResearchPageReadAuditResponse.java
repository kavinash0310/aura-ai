package ai.aura.api.api;

import ai.aura.api.domain.ResearchPageRead;

import java.time.Instant;
import java.util.UUID;

public record ResearchPageReadAuditResponse(
        UUID id,
        UUID sourceId,
        String status,
        Instant requestedAt,
        Instant completedAt,
        String contentHash,
        Integer characterCount,
        Boolean truncated,
        String failureCode
) {
    public static ResearchPageReadAuditResponse from(ResearchPageRead read) {
        return new ResearchPageReadAuditResponse(read.getId(), read.getSourceId(), read.getStatus().name(),
                read.getRequestedAt(), read.getCompletedAt(), read.getContentHash(), read.getCharacterCount(),
                read.getTruncated(), read.getFailureCode());
    }
}
