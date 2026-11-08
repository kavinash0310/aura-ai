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

    @Column(name = "search_provider", length = 32)
    private String searchProvider;

    @Column(name = "search_country", length = 2)
    private String searchCountry;

    @Column(name = "search_language", length = 2)
    private String searchLanguage;

    @Column(name = "search_freshness", length = 2)
    private String searchFreshness;

    @Column(name = "search_limit")
    private Integer searchLimit;

    @Column(name = "search_started_at")
    private Instant searchStartedAt;

    @Column(name = "search_completed_at")
    private Instant searchCompletedAt;

    @Column(name = "search_result_count", nullable = false)
    private int searchResultCount;

    @Column(name = "failure_code", length = 64)
    private String failureCode;

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

    public void beginSearch(String provider, ResearchFilters filters, Instant startedAt) {
        this.status = ResearchJobStatus.SEARCHING;
        this.searchProvider = provider;
        this.searchCountry = filters.country();
        this.searchLanguage = filters.language();
        this.searchFreshness = filters.freshness();
        this.searchLimit = filters.resultLimit();
        this.searchStartedAt = startedAt;
        this.searchCompletedAt = null;
        this.searchResultCount = 0;
        this.failureCode = null;
    }

    public void completeSearch(int resultCount, Instant completedAt) {
        this.status = ResearchJobStatus.COMPLETED;
        this.searchResultCount = resultCount;
        this.searchCompletedAt = completedAt;
        this.failureCode = null;
    }

    public void completePartialSearch(int resultCount, String code, Instant completedAt) {
        this.status = ResearchJobStatus.PARTIAL;
        this.searchResultCount = resultCount;
        this.searchCompletedAt = completedAt;
        this.failureCode = code;
    }

    public void failSearch(String code, Instant completedAt) {
        this.status = ResearchJobStatus.FAILED;
        this.searchCompletedAt = completedAt;
        this.failureCode = code;
    }

    public UUID getId() { return id; }
    public String getSubject() { return subject; }
    public ResearchMode getMode() { return mode; }
    public ResearchJobStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public String getSearchProvider() { return searchProvider; }
    public String getSearchCountry() { return searchCountry; }
    public String getSearchLanguage() { return searchLanguage; }
    public String getSearchFreshness() { return searchFreshness; }
    public Integer getSearchLimit() { return searchLimit; }
    public Instant getSearchStartedAt() { return searchStartedAt; }
    public Instant getSearchCompletedAt() { return searchCompletedAt; }
    public int getSearchResultCount() { return searchResultCount; }
    public String getFailureCode() { return failureCode; }
}

