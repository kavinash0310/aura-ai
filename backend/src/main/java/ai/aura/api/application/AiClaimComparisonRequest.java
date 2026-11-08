package ai.aura.api.application;

import ai.aura.api.domain.SourceType;

import java.time.Instant;
import java.util.List;

public record AiClaimComparisonRequest(String claim, List<Source> sources) {
    public record Source(
            String id,
            String title,
            String url,
            String domain,
            SourceType kind,
            String description,
            String publisher,
            Instant publishedAt
    ) {
    }
}
