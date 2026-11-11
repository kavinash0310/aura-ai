package ai.aura.api.api;

import ai.aura.api.domain.ResearchDocument;

import java.time.Instant;
import java.util.UUID;

public record ResearchDocumentResponse(
        UUID id,
        UUID logicalDocumentId,
        int versionNumber,
        String title,
        String originalFilename,
        String mediaType,
        String contentHash,
        int characterCount,
        Instant uploadedAt,
        String content
) {
    public static ResearchDocumentResponse from(ResearchDocument document, boolean includeContent) {
        return new ResearchDocumentResponse(document.getId(), document.getLogicalDocumentId(), document.getVersionNumber(),
                document.getTitle(), document.getOriginalFilename(), document.getMediaType(), document.getContentHash(), document.getCharacterCount(),
                document.getUploadedAt(), includeContent ? document.getContent() : null);
    }
}
