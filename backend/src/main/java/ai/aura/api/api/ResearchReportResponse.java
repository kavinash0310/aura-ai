package ai.aura.api.api;

import ai.aura.api.domain.SourceType;

import java.time.Instant;
import java.util.List;

public record ResearchReportResponse(
        Instant generatedAt,
        String provider,
        String model,
        String title,
        String summary,
        List<String> summarySourceIds,
        List<Finding> findings,
        List<String> limitations,
        List<Citation> citations,
        String coverageNote,
        ModelRunTelemetry modelRun
) {
    public ResearchReportResponse(
            Instant generatedAt,
            String provider,
            String model,
            String title,
            String summary,
            List<String> summarySourceIds,
            List<Finding> findings,
            List<String> limitations,
            List<Citation> citations,
            String coverageNote
    ) {
        this(generatedAt, provider, model, title, summary, summarySourceIds, findings,
                limitations, citations, coverageNote, null);
    }

    public record Finding(String text, List<String> sourceIds, List<EvidenceQuote> evidenceQuotes) {
        public Finding(String text, List<String> sourceIds) {
            this(text, sourceIds, List.of());
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
