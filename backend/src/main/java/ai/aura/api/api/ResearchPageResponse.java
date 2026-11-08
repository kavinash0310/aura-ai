package ai.aura.api.api;

import ai.aura.api.application.PublicPageFetcher;

import java.time.Instant;
import java.util.UUID;

public record ResearchPageResponse(
        UUID sourceId,
        String sourceUrl,
        String host,
        String title,
        String contentHash,
        int characterCount,
        boolean truncated,
        Instant fetchedAt,
        String text,
        String coverageNote
) {
    public static ResearchPageResponse from(UUID sourceId, PublicPageFetcher.FetchedPage page, String contentHash) {
        return new ResearchPageResponse(sourceId, page.url(), page.host(), page.title(), contentHash,
                page.text().length(), page.truncated(), page.fetchedAt(), page.text(),
                "Fetched only after an explicit request. This page snapshot is held in browser memory, is not saved by AURA, and is not sent to a model or another provider.");
    }
}
