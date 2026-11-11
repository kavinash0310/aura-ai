package ai.aura.api.api;

import java.time.Instant;
import java.util.List;

public record DocumentQuestionResponse(
        Instant generatedAt,
        String provider,
        String model,
        String question,
        String answer,
        List<EvidenceItem> evidence,
        List<Citation> citations,
        List<String> limitations,
        String coverageNote,
        ResearchReportResponse.ModelRunTelemetry modelRun
) {
    public record EvidenceItem(String text, List<String> sourceIds, List<EvidenceQuote> evidenceQuotes) {
    }

    public record EvidenceQuote(String sourceId, String quote) {
    }

    public record Citation(
            String id,
            String title,
            String originalFilename,
            int versionNumber,
            Integer page,
            int startOffset,
            int endOffset,
            String citation
    ) {
    }
}
