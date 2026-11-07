package ai.aura.api.domain;

import ai.aura.api.search.ProviderSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_sources")
public class ResearchSource {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "research_job_id", nullable = false, updatable = false)
    private UUID researchJobId;

    @Column(nullable = false, length = 32, updatable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32, updatable = false)
    private SourceType type;

    @Column(name = "result_rank", nullable = false, updatable = false)
    private int rank;

    @Column(nullable = false, length = 1000, updatable = false)
    private String title;

    @Column(nullable = false, length = 2048, updatable = false)
    private String url;

    @Column(nullable = false, length = 253, updatable = false)
    private String domain;

    @Column(length = 4000, updatable = false)
    private String description;

    @Column(length = 300, updatable = false)
    private String publisher;

    @Column(name = "published_at", updatable = false)
    private Instant publishedAt;

    @Column(name = "retrieved_at", nullable = false, updatable = false)
    private Instant retrievedAt;

    protected ResearchSource() {
    }

    private ResearchSource(
            UUID id,
            UUID researchJobId,
            String provider,
            SourceType type,
            int rank,
            String title,
            String url,
            String domain,
            String description,
            String publisher,
            Instant publishedAt,
            Instant retrievedAt
    ) {
        this.id = id;
        this.researchJobId = researchJobId;
        this.provider = provider;
        this.type = type;
        this.rank = rank;
        this.title = title;
        this.url = url;
        this.domain = domain;
        this.description = description;
        this.publisher = publisher;
        this.publishedAt = publishedAt;
        this.retrievedAt = retrievedAt;
    }

    public static ResearchSource from(
            UUID researchJobId,
            String provider,
            int rank,
            ProviderSource source,
            Instant retrievedAt
    ) {
        return new ResearchSource(
                UUID.randomUUID(),
                researchJobId,
                provider,
                source.type(),
                rank,
                source.title(),
                source.url(),
                source.domain(),
                source.description(),
                source.publisher(),
                source.publishedAt(),
                retrievedAt
        );
    }

    public UUID getId() { return id; }
    public UUID getResearchJobId() { return researchJobId; }
    public String getProvider() { return provider; }
    public SourceType getType() { return type; }
    public int getRank() { return rank; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getDomain() { return domain; }
    public String getDescription() { return description; }
    public String getPublisher() { return publisher; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getRetrievedAt() { return retrievedAt; }
}


