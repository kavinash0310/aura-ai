package ai.aura.api.api;

import ai.aura.api.domain.SourceType;

import java.time.Instant;
import java.util.List;

public record ResearchClaimComparisonResponse(
        Instant generatedAt,
        String provider,
        String model,
        String claim,
        String assessment,
        String explanation,
        List<EvidenceItem> evidence,
        List<String> limitations,
        List<Citation> citations,
        String coverageNote
) {
    public record EvidenceItem(String stance, String text, List<String> sourceIds) {
    }

    public record Citation(String id, String title, String url, String domain, SourceType kind) {
    }
}
