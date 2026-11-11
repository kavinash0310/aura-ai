package ai.aura.api.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredDocumentTextExtractorTest {
    private final StructuredDocumentTextExtractor extractor =
            new StructuredDocumentTextExtractor(JsonMapper.builder().build());

    @Test
    void flattensCsvRowsAndPreservesQuotedCommasAndNewlines() {
        String text = extractor.extractCsv("\uFEFFname,notes\r\n\"AURA, AI\",\"line one\nline two\"\r\n");

        assertThat(text).contains("Row 1: [\"name\", \"notes\"]\n");
        assertThat(text).contains("Row 2: [\"AURA, AI\", \"line one\\nline two\"]\n");
    }

    @Test
    void rejectsMalformedAndOversizedCsv() {
        assertThatThrownBy(() -> extractor.extractCsv("\"unterminated"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("CSV file is malformed");
        assertThatThrownBy(() -> extractor.extractCsv("x,".repeat(200) + "x"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("200 columns");

        StringBuilder rows = new StringBuilder();
        for (int index = 0; index <= StructuredDocumentTextExtractor.MAX_CSV_ROWS; index++) rows.append("x\n");
        assertThatThrownBy(() -> extractor.extractCsv(rows.toString()))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("10,000 rows");

        assertThatThrownBy(() -> extractor.extractCsv("x".repeat(StructuredDocumentTextExtractor.MAX_EXTRACTED_CHARS)))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("250,000 characters");
    }

    @Test
    void flattensJsonWithPathsAndEscapedStringValues() {
        String text = extractor.extractJson("{\"items\":[{\"name\":\"AURA\\nAI\"}],\"active\":true,\"nil\":null}");

        assertThat(text).contains("$[\"items\"][0][\"name\"]: \"AURA\\nAI\"");
        assertThat(text).contains("$[\"active\"]: true");
        assertThat(text).contains("$[\"nil\"]: null");
    }

    @Test
    void rejectsMalformedTrailingDeepLargeAndOversizedJson() {
        assertThatThrownBy(() -> extractor.extractJson("not json"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("malformed");
        assertThatThrownBy(() -> extractor.extractJson("{} {}"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("trailing data");
        assertThatThrownBy(() -> extractor.extractJson("{\"key\":1,\"key\":2}"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("malformed");

        String deep = "[".repeat(StructuredDocumentTextExtractor.MAX_JSON_DEPTH + 1)
                + "0" + "]".repeat(StructuredDocumentTextExtractor.MAX_JSON_DEPTH + 1);
        assertThatThrownBy(() -> extractor.extractJson(deep))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("64 levels");

        StringBuilder manyValues = new StringBuilder("[");
        for (int index = 0; index <= StructuredDocumentTextExtractor.MAX_JSON_NODES; index++) {
            if (index > 0) manyValues.append(',');
            manyValues.append("[]");
        }
        manyValues.append(']');
        assertThatThrownBy(() -> extractor.extractJson(manyValues.toString()))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("50,000 nodes");

        String longValue = "{\"value\":\"" + "x".repeat(StructuredDocumentTextExtractor.MAX_EXTRACTED_CHARS) + "\"}";
        assertThatThrownBy(() -> extractor.extractJson(longValue))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("250,000 characters");
    }
}
