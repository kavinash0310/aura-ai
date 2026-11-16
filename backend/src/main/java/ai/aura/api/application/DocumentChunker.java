package ai.aura.api.application;

/**
 * Selects a deterministic, page-local text window around a selected phrase match.
 * Offsets remain Java string offsets into the immutable stored document.
 */
public final class DocumentChunker {
    public static final int MAX_CHUNK_CHARACTERS = 700;
    public static final int CHUNK_OVERLAP_CHARACTERS = 140;
    private static final int CHUNK_STRIDE = MAX_CHUNK_CHARACTERS - CHUNK_OVERLAP_CHARACTERS;

    private DocumentChunker() {
    }

    public static Chunk containing(int matchStart, int matchEnd, int pageStart, int pageEnd) {
        if (pageStart < 0 || pageEnd < pageStart || matchStart < pageStart
                || matchEnd <= matchStart || matchEnd > pageEnd) {
            throw new IllegalArgumentException("The selected match must fit inside its page bounds.");
        }

        int relativeStart = matchStart - pageStart;
        int chunkStart = pageStart + (relativeStart / CHUNK_STRIDE) * CHUNK_STRIDE;
        int latestStart = Math.max(pageStart, pageEnd - MAX_CHUNK_CHARACTERS);
        chunkStart = Math.min(chunkStart, latestStart);
        if (matchEnd > chunkStart + MAX_CHUNK_CHARACTERS) {
            chunkStart = matchEnd - MAX_CHUNK_CHARACTERS;
        }
        chunkStart = Math.max(pageStart, chunkStart);

        int chunkEnd = Math.min(pageEnd, chunkStart + MAX_CHUNK_CHARACTERS);
        if (matchStart < chunkStart || matchEnd > chunkEnd) {
            throw new IllegalArgumentException("The selected match does not fit inside the bounded chunk.");
        }
        return new Chunk(chunkStart, chunkEnd);
    }

    public record Chunk(int startOffset, int endOffset) {
    }
}
