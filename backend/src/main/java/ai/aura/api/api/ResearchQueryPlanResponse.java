package ai.aura.api.api;

import java.time.Instant;
import java.util.List;

public record ResearchQueryPlanResponse(
        Instant generatedAt,
        String provider,
        String model,
        List<String> suggestions,
        ModelRun modelRun,
        String note
) {
    public record ModelRun(
            String promptVersion,
            Long durationMs,
            Long promptTokenCount,
            Long outputTokenCount
    ) {
    }
}
