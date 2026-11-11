package ai.aura.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "research_documents")
public class ResearchDocument {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "logical_document_id", nullable = false, updatable = false)
    private UUID logicalDocumentId;

    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(nullable = false, length = 300, updatable = false)
    private String title;

    @Column(name = "original_filename", nullable = false, length = 255, updatable = false)
    private String originalFilename;

    @Column(name = "media_type", nullable = false, length = 64, updatable = false)
    private String mediaType;

    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "character_count", nullable = false, updatable = false)
    private int characterCount;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    protected ResearchDocument() {
    }

    private ResearchDocument(UUID id, UUID logicalDocumentId, int versionNumber, String title, String originalFilename, String mediaType,
                             String contentHash, String content, Instant uploadedAt) {
        this.id = id;
        this.logicalDocumentId = logicalDocumentId;
        this.versionNumber = versionNumber;
        this.title = title;
        this.originalFilename = originalFilename;
        this.mediaType = mediaType;
        this.contentHash = contentHash;
        this.content = content;
        this.characterCount = content.length();
        this.uploadedAt = uploadedAt;
    }

    public static ResearchDocument create(String title, String originalFilename, String mediaType,
                                          String contentHash, String content, Instant uploadedAt) {
        UUID id = UUID.randomUUID();
        return new ResearchDocument(id, id, 1, title, originalFilename, mediaType,
                contentHash, content, uploadedAt);
    }

    public static ResearchDocument createVersion(UUID logicalDocumentId, int versionNumber, String title,
                                                 String originalFilename, String mediaType, String contentHash,
                                                 String content, Instant uploadedAt) {
        return new ResearchDocument(UUID.randomUUID(), logicalDocumentId, versionNumber, title, originalFilename,
                mediaType, contentHash, content, uploadedAt);
    }

    public UUID getId() { return id; }
    public UUID getLogicalDocumentId() { return logicalDocumentId; }
    public int getVersionNumber() { return versionNumber; }
    public String getTitle() { return title; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMediaType() { return mediaType; }
    public String getContentHash() { return contentHash; }
    public String getContent() { return content; }
    public int getCharacterCount() { return characterCount; }
    public Instant getUploadedAt() { return uploadedAt; }
}
