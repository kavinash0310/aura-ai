package ai.aura.api.cyber;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record KevCatalogSnapshot(
        String catalogVersion,
        LocalDate dateReleased,
        Instant retrievedAt,
        Map<String, KevEntry> entries,
        boolean stale
) {
    public KevCatalogSnapshot {
        entries = Map.copyOf(entries);
    }
}
