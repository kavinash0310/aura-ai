package ai.aura.api.application;

import ai.aura.api.api.ProviderRunResponse;
import ai.aura.api.api.ResearchSearchResponse;
import ai.aura.api.api.ResearchSourceResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public final class ResearchReportPdfRenderer {
    private static final float MARGIN = 48;
    private static final float BODY_SIZE = 9;
    private static final float BODY_LEADING = 13;
    private static final int MAX_PARAGRAPH_LENGTH = 8_000;

    public byte[] render(ResearchSearchResponse response) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.getDocumentInformation().setTitle("AURA AI research results");
            document.getDocumentInformation().setSubject(clean(response.query()));
            PdfWriter writer = new PdfWriter(document);

            writer.heading("AURA AI - RESEARCH RESULTS", 17);
            writer.field("Topic", response.query());
            writer.field("Provider", response.provider());
            writer.field("Status", response.status() == null ? null : response.status().name());
            writer.field("Searched", response.searchedAt() == null ? null : response.searchedAt().toString());
            writer.section("Coverage and limits");
            writer.paragraph(response.coverageNote());

            writer.section("Provider outcomes");
            if (response.providerRuns().isEmpty()) {
                writer.paragraph("No provider-run details were recorded.");
            } else {
                for (ProviderRunResponse run : response.providerRuns()) {
                    writer.paragraph("Query: " + value(run.query())
                            + " | Provider: " + value(run.provider())
                            + " | Attempt: " + run.attempt()
                            + " | Status: " + value(run.status() == null ? null : run.status().name())
                            + " | Results: " + run.resultCount()
                            + (run.failureCode() == null ? "" : " | Failure: " + run.failureCode()));
                }
            }

            writer.section("Sources (" + response.results().size() + ")");
            if (response.results().isEmpty()) {
                writer.paragraph("No sources match the current filters.");
            } else {
                for (ResearchSourceResponse source : response.results()) {
                    writer.heading(source.rank() + ". " + value(source.title()), 11);
                    writer.field("Provider", source.provider());
                    writer.field("Search query", source.searchQuery());
                    writer.field("Type", source.kind() == null ? null : source.kind().name());
                    writer.field("Publisher", source.publisher());
                    writer.field("Published", source.publishedAt() == null ? "Unknown" : source.publishedAt().toString());
                    writer.field("Retrieved", source.retrievedAt() == null ? "Unknown" : source.retrievedAt().toString());
                    writer.field("Source URL", source.url());
                    if (source.description() != null && !source.description().isBlank()) {
                        writer.field("Description", source.description());
                    }
                    writer.spacer();
                }
            }

            writer.finish();
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create the research PDF export.", exception);
        }
    }

    private static String value(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private static String clean(String value) {
        if (value == null) return "";
        String cleaned = value.replaceAll("[\\p{Cc}&&[^\\t\\r\\n]]", " ")
                .replaceAll("\\s+", " ")
                .strip();
        if (cleaned.length() <= MAX_PARAGRAPH_LENGTH) return cleaned;
        return cleaned.substring(0, MAX_PARAGRAPH_LENGTH) + " [text truncated]";
    }

    private static final class PdfWriter {
        private static final float PAGE_WIDTH = PDRectangle.LETTER.getWidth();
        private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();

        private final PDDocument document;
        private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        private PdfWriter(PDDocument document) throws IOException {
            this.document = document;
            newPage();
        }

        private void heading(String text, float size) throws IOException {
            paragraph(text, bold, size, 0, size + 6);
            y -= 3;
        }

        private void section(String text) throws IOException {
            y -= 4;
            paragraph(text, bold, 11, 0, 15);
        }

        private void field(String label, String text) throws IOException {
            if (text == null || text.isBlank()) return;
            paragraph(label + ": " + clean(text), regular, BODY_SIZE, 0, BODY_LEADING);
        }

        private void paragraph(String text) throws IOException {
            paragraph(text, regular, BODY_SIZE, 0, BODY_LEADING);
        }

        private void paragraph(String text, PDFont font, float size, float indent, float leading) throws IOException {
            String safe = safeForFont(clean(text), font);
            if (safe.isBlank()) return;
            for (String line : wrap(safe, font, size, PAGE_WIDTH - (2 * MARGIN) - indent)) {
                ensureSpace(leading);
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(MARGIN + indent, y);
                stream.showText(line);
                stream.endText();
                y -= leading;
            }
        }

        private void spacer() {
            y -= 4;
        }

        private void ensureSpace(float leading) throws IOException {
            if (y < MARGIN + leading) newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) stream.close();
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        private void finish() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private static String safeForFont(String text, PDFont font) throws IOException {
            try {
                font.encode(text);
                return text;
            } catch (IOException | IllegalArgumentException ignored) {
                StringBuilder safe = new StringBuilder(text.length());
                for (int offset = 0; offset < text.length();) {
                    int codePoint = text.codePointAt(offset);
                    String glyph = new String(Character.toChars(codePoint));
                    try {
                        font.encode(glyph);
                        safe.append(glyph);
                    } catch (IOException | IllegalArgumentException unsupportedGlyph) {
                        safe.append('?');
                    }
                    offset += Character.charCount(codePoint);
                }
                return safe.toString();
            }
        }

        private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            for (String word : text.split("\\s+")) {
                if (word.isEmpty()) continue;
                if (line.isEmpty()) {
                    appendLongWord(lines, line, word, font, size, maxWidth);
                    continue;
                }
                String candidate = line + " " + word;
                if (width(candidate, font, size) <= maxWidth) {
                    line.append(' ').append(word);
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    appendLongWord(lines, line, word, font, size, maxWidth);
                }
            }
            if (!line.isEmpty()) lines.add(line.toString());
            return lines;
        }

        private static void appendLongWord(
                List<String> lines,
                StringBuilder line,
                String word,
                PDFont font,
                float size,
                float maxWidth
        ) throws IOException {
            for (int offset = 0; offset < word.length();) {
                int codePoint = word.codePointAt(offset);
                String glyph = new String(Character.toChars(codePoint));
                if (!line.isEmpty() && width(line + glyph, font, size) > maxWidth) {
                    lines.add(line.toString());
                    line.setLength(0);
                }
                line.append(glyph);
                offset += Character.charCount(codePoint);
            }
        }

        private static float width(String text, PDFont font, float size) throws IOException {
            return font.getStringWidth(text) * size / 1000;
        }
    }
}
