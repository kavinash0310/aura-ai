package ai.aura.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_page_reads")
public class ResearchPageRead {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "research_job_id", nullable = false, updatable = false)
    private UUID researchJobId;

    @Column(name = "source_id", nullable = false, updatable = false)
    private UUID sourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResearchPageReadStatus status;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Column(name = "character_count")
    private Integer characterCount;

    private Boolean truncated;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    protected ResearchPageRead() {
    }

    private ResearchPageRead(UUID id, UUID researchJobId, UUID sourceId, Instant requestedAt) {
        this.id = id;
        this.researchJobId = researchJobId;
        this.sourceId = sourceId;
        this.requestedAt = requestedAt;
        this.status = ResearchPageReadStatus.RUNNING;
    }

    public static ResearchPageRead running(UUID researchJobId, UUID sourceId, Instant requestedAt) {
        return new ResearchPageRead(UUID.randomUUID(), researchJobId, sourceId, requestedAt);
    }

    public void succeed(String hash, int count, boolean wasTruncated, Instant at) {
        if (status != ResearchPageReadStatus.RUNNING || hash == null || !hash.matches("[a-f0-9]{64}") || count < 0) {
            throw new IllegalStateException("A running page read with a valid hash and count is required.");
        }
        status = ResearchPageReadStatus.SUCCEEDED;
        completedAt = at;
        contentHash = hash;
        characterCount = count;
        truncated = wasTruncated;
        failureCode = null;
    }

    public void fail(String code, Instant at) {
        if (status != ResearchPageReadStatus.RUNNING || code == null || code.isBlank() || code.length() > 64) {
            throw new IllegalStateException("A running page read and a stable failure code are required.");
        }
        status = ResearchPageReadStatus.FAILED;
        completedAt = at;
        failureCode = code;
    }

    public void abandon(Instant at) {
        if (status != ResearchPageReadStatus.RUNNING) {
            throw new IllegalStateException("Only a running page read can be abandoned.");
        }
        status = ResearchPageReadStatus.ABANDONED;
        completedAt = at;
        failureCode = "PUBLIC_PAGE_READ_INTERRUPTED";
    }

    public UUID getId() { return id; }
    public UUID getResearchJobId() { return researchJobId; }
    public UUID getSourceId() { return sourceId; }
    public ResearchPageReadStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getContentHash() { return contentHash; }
    public Integer getCharacterCount() { return characterCount; }
    public Boolean getTruncated() { return truncated; }
    public String getFailureCode() { return failureCode; }
}
