package ai.aura.api.api;

import ai.aura.api.domain.ResearchSource;
import ai.aura.api.domain.SourceType;

import java.time.Instant;
import java.util.UUID;

public record ResearchSourceResponse(
        UUID id,
        String provider,
        SourceType kind,
        int rank,
        String title,
        String url,
        String domain,
        String description,
        String publisher,
        Instant publishedAt,
        Instant retrievedAt
) {
    public static ResearchSourceResponse from(ResearchSource source) {
        return new ResearchSourceResponse(
                source.getId(),
                source.getProvider(),
                source.getType(),
                source.getRank(),
                source.getTitle(),
                source.getUrl(),
                source.getDomain(),
                source.getDescription(),
                source.getPublisher(),
                source.getPublishedAt(),
                source.getRetrievedAt()
        );
    }
}


