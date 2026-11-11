package ai.aura.api.application;

import java.util.List;

public record AiDocumentQuestionRequest(String question, List<Source> sources) {
    public record Source(
            String id,
            String title,
            String originalFilename,
            int versionNumber,
            Integer page,
            int startOffset,
            int endOffset,
            int excerptStartOffset,
            int excerptEndOffset,
            String excerpt
    ) {
    }
}
