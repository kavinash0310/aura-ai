package ai.aura.api.search;

import ai.aura.api.domain.SourceType;

import java.time.Instant;

public record ProviderSource(
        SourceType type,
        String title,
        String url,
        String domain,
        String description,
        String publisher,
        Instant publishedAt
) {
}


