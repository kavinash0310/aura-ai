package ai.aura.api.application;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/**
 * Extracts bounded, searchable text from user-selected PDFs. The one-at-a-time
 * guard limits concurrent parser memory use in this single-instance service.
 */
@Component
public class PdfTextExtractor {
    public static final int MAX_PAGE_COUNT = 100;
    public static final int MAX_EXTRACTED_CHARS = 250_000;
    private static final int SIGNATURE_SCAN_BYTES = 1024;
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    public synchronized String extract(byte[] bytes) {
        if (!hasPdfSignature(bytes)) {
            throw new InvalidDocumentException("The .pdf file does not have a valid PDF signature.");
        }

        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new InvalidDocumentException("Encrypted PDFs are not supported.");
            }
            int pageCount = document.getNumberOfPages();
            if (pageCount < 1) {
                throw new InvalidDocumentException("The PDF does not contain any pages.");
            }
            if (pageCount > MAX_PAGE_COUNT) {
                throw new InvalidDocumentException("PDF files may contain up to 100 pages.");
            }

            BoundedTextWriter text = new BoundedTextWriter(MAX_EXTRACTED_CHARS);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            stripper.setPageStart("");
            stripper.setPageEnd("");
            for (int page = 1; page <= pageCount; page++) {
                if (page > 1) text.appendPageBreak();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                stripper.writeText(document, text);
            }

            String extracted = text.toString();
            if (extracted.replace("\f", "").isBlank()) {
                throw new InvalidDocumentException("The PDF has no extractable text. Scanned PDFs need OCR, which is not enabled.");
            }
            return extracted;
        } catch (InvalidDocumentException exception) {
            throw exception;
        } catch (IOException exception) {
            if (exception instanceof ExtractedTextLimitException) {
                throw new InvalidDocumentException("PDF extracted text must be 250,000 characters or less.");
            }
            throw new InvalidDocumentException("The PDF could not be read. It may be corrupt, encrypted, or unsupported.");
        }
    }

    private static boolean hasPdfSignature(byte[] bytes) {
        if (bytes == null || bytes.length < PDF_SIGNATURE.length) return false;
        int scanLength = Math.min(bytes.length - PDF_SIGNATURE.length, SIGNATURE_SCAN_BYTES - PDF_SIGNATURE.length);
        for (int offset = 0; offset <= scanLength; offset++) {
            boolean matches = true;
            for (int index = 0; index < PDF_SIGNATURE.length; index++) {
                if (bytes[offset + index] != PDF_SIGNATURE[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) return true;
        }
        return false;
    }

    private static final class BoundedTextWriter extends Writer {
        private final int limit;
        private final StringBuilder content = new StringBuilder();

        private BoundedTextWriter(int limit) {
            this.limit = limit;
        }

        @Override
        public void write(char[] characters, int offset, int length) throws IOException {
            for (int index = offset; index < offset + length; index++) appendSanitized(characters[index]);
        }

        @Override
        public void write(String value, int offset, int length) throws IOException {
            for (int index = offset; index < offset + length; index++) appendSanitized(value.charAt(index));
        }

        private void appendSanitized(char character) throws IOException {
            char safe = character == '\r' ? '\n'
                    : Character.isISOControl(character) && character != '\n' && character != '\t' ? ' '
                    : character;
            ensureCapacityForOneCharacter();
            content.append(safe);
        }

        private void appendPageBreak() throws IOException {
            ensureCapacityForOneCharacter();
            content.append('\f');
        }

        private void ensureCapacityForOneCharacter() throws ExtractedTextLimitException {
            if (content.length() >= limit) throw new ExtractedTextLimitException();
        }

        @Override
        public void flush() {
            // The extractor writes directly into its bounded in-memory buffer.
        }

        @Override
        public void close() {
            // The buffer is owned by the enclosing extraction operation.
        }

        @Override
        public String toString() {
            return content.toString();
        }
    }

    private static final class ExtractedTextLimitException extends IOException {
    }
}
