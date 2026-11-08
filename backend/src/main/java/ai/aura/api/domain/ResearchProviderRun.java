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
@Table(name = "research_provider_runs")
public class ResearchProviderRun {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "research_job_id", nullable = false, updatable = false)
    private UUID researchJobId;

    @Column(nullable = false, length = 32, updatable = false)
    private String provider;

    @Column(name = "run_order", nullable = false, updatable = false)
    private int runOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ResearchProviderRunStatus status;

    @Column(name = "result_count", nullable = false)
    private int resultCount;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ResearchProviderRun() {
    }

    private ResearchProviderRun(UUID id, UUID researchJobId, String provider, int runOrder) {
        this.id = id;
        this.researchJobId = researchJobId;
        this.provider = provider;
        this.runOrder = runOrder;
        this.status = ResearchProviderRunStatus.PENDING;
    }

    public static ResearchProviderRun pending(UUID jobId, String provider, int runOrder) {
        return new ResearchProviderRun(UUID.randomUUID(), jobId, provider, runOrder);
    }

    public void start(Instant at) {
        if (status != ResearchProviderRunStatus.PENDING) {
            throw new IllegalStateException("Only a pending provider run can start.");
        }
        status = ResearchProviderRunStatus.RUNNING;
        startedAt = at;
    }

    public void succeed(int count, Instant at) {
        if (status != ResearchProviderRunStatus.RUNNING || count < 0) {
            throw new IllegalStateException("A running provider run and a nonnegative result count are required.");
        }
        status = ResearchProviderRunStatus.SUCCEEDED;
        resultCount = count;
        completedAt = at;
        failureCode = null;
    }

    public void fail(String code, Instant at) {
        if (status != ResearchProviderRunStatus.RUNNING) {
            throw new IllegalStateException("Only a running provider run can fail.");
        }
        status = ResearchProviderRunStatus.FAILED;
        failureCode = code;
        completedAt = at;
    }

    public void skip(String code, Instant at) {
        if (status != ResearchProviderRunStatus.PENDING) {
            throw new IllegalStateException("Only a pending provider run can be skipped.");
        }
        status = ResearchProviderRunStatus.SKIPPED;
        failureCode = code;
        completedAt = at;
    }

    public UUID getId() { return id; }
    public UUID getResearchJobId() { return researchJobId; }
    public String getProvider() { return provider; }
    public int getRunOrder() { return runOrder; }
    public ResearchProviderRunStatus getStatus() { return status; }
    public int getResultCount() { return resultCount; }
    public String getFailureCode() { return failureCode; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
