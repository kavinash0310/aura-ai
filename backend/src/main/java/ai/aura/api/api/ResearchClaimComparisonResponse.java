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
        String coverageNote,
        ModelRunTelemetry modelRun
) {
    public ResearchClaimComparisonResponse(
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
        this(generatedAt, provider, model, claim, assessment, explanation, evidence,
                limitations, citations, coverageNote, null);
    }

    public record EvidenceItem(
            String stance,
            String text,
            List<String> sourceIds,
            List<EvidenceQuote> evidenceQuotes
    ) {
        public EvidenceItem(String stance, String text, List<String> sourceIds) {
            this(stance, text, sourceIds, List.of());
        }
    }

    public record EvidenceQuote(String sourceId, String quote) {
    }

    public record Citation(String id, String title, String url, String domain, SourceType kind) {
    }

    public record ModelRunTelemetry(
            String promptVersion,
            Long durationMs,
            Long promptTokenCount,
            Long outputTokenCount
    ) {
    }
}
