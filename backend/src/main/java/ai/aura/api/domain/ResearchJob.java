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
@Table(name = "research_jobs")
public class ResearchJob {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 300)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "research_mode", nullable = false, length = 32)
    private ResearchMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResearchJobStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ResearchJob() {
    }

    private ResearchJob(UUID id, String subject, ResearchMode mode, ResearchJobStatus status, Instant createdAt) {
        this.id = id;
        this.subject = subject;
        this.mode = mode;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ResearchJob requested(String subject, ResearchMode mode) {
        return new ResearchJob(
                UUID.randomUUID(),
                subject.strip(),
                mode,
                ResearchJobStatus.REQUESTED,
                Instant.now()
        );
    }

    public UUID getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public ResearchMode getMode() {
        return mode;
    }

    public ResearchJobStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
