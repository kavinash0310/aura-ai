package ai.aura.api.api;

import ai.aura.api.domain.ResearchDocument;

import java.time.Instant;
import java.util.UUID;

public record ResearchDocumentSearchHit(
        UUID documentId,
        String title,
        String originalFilename,
        String contentHash,
        Instant uploadedAt,
        String snippet,
        int startOffset,
        int endOffset,
        String citation
) {
    public static ResearchDocumentSearchHit from(ResearchDocument document, String query) {
        String content = document.getContent();
        int match = indexOfIgnoreCase(content, query);
        int start = Math.max(0, match - 120);
        int end = Math.min(content.length(), Math.max(match + query.length(), match) + 240);
        String snippet = (start > 0 ? "…" : "") + content.substring(start, end) + (end < content.length() ? "…" : "");
        int citationStart = Math.max(0, match);
        int citationEnd = Math.min(content.length(), citationStart + query.length());
        return new ResearchDocumentSearchHit(document.getId(), document.getTitle(), document.getOriginalFilename(),
                document.getContentHash(), document.getUploadedAt(), snippet, citationStart, citationEnd,
                "aura-doc://" + document.getId() + "#chars=" + citationStart + "-" + citationEnd);
    }

    private static int indexOfIgnoreCase(String content, String query) {
        for (int index = 0; index <= content.length() - query.length(); index++) {
            if (content.regionMatches(true, index, query, 0, query.length())) return index;
        }
        return 0;
    }
}
