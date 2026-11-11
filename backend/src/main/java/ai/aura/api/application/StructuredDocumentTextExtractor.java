package ai.aura.api.application;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;

/** Converts small, user-selected CSV and JSON files into bounded searchable text. */
@Component
public class StructuredDocumentTextExtractor {
    public static final int MAX_EXTRACTED_CHARS = 250_000;
    public static final int MAX_CSV_ROWS = 10_000;
    public static final int MAX_CSV_COLUMNS = 200;
    public static final int MAX_JSON_NODES = 50_000;
    public static final int MAX_JSON_DEPTH = 64;

    private final JsonMapper jsonMapper;

    public StructuredDocumentTextExtractor(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String extractCsv(String csv) {
        String normalized = stripLeadingBom(csv);
        BoundedText output = new BoundedText();
        try (CSVParser parser = CSVFormat.RFC4180.parse(new StringReader(normalized))) {
            int rowNumber = 0;
            for (CSVRecord record : parser) {
                if (++rowNumber > MAX_CSV_ROWS) {
                    throw new InvalidDocumentException("CSV files may contain up to 10,000 rows.");
                }
                if (record.size() > MAX_CSV_COLUMNS) {
                    throw new InvalidDocumentException("CSV files may contain up to 200 columns per row.");
                }
                output.append("Row " + rowNumber + ": [");
                for (int column = 0; column < record.size(); column++) {
                    if (column > 0) output.append(", ");
                    output.appendQuoted(record.get(column));
                }
                output.append("]\n");
            }
        } catch (InvalidDocumentException exception) {
            throw exception;
        } catch (IOException | IllegalStateException | UncheckedIOException exception) {
            throw new InvalidDocumentException("The CSV file is malformed or could not be read.");
        }
        return output.toString();
    }

    public String extractJson(String json) {
        validateJsonDepth(json);
        JsonNode root;
        try {
            root = jsonMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .readTree(stripLeadingBom(json));
        } catch (JacksonException exception) {
            throw new InvalidDocumentException("The JSON file is malformed or contains trailing data.");
        }
        if (root == null) throw new InvalidDocumentException("The JSON file must contain a value.");

        BoundedText output = new BoundedText();
        ArrayDeque<JsonValue> pending = new ArrayDeque<>();
        pending.addLast(new JsonValue(root, "$", 1));
        int visitedNodes = 0;
        while (!pending.isEmpty()) {
            JsonValue current = pending.removeFirst();
            if (++visitedNodes > MAX_JSON_NODES) {
                throw new InvalidDocumentException("JSON files may contain up to 50,000 nodes.");
            }
            if (current.depth() > MAX_JSON_DEPTH) {
                throw new InvalidDocumentException("JSON nesting may be up to 64 levels deep.");
            }

            JsonNode node = current.node();
            if (node.isObject()) {
                for (var property : node.properties()) {
                    pending.addLast(new JsonValue(property.getValue(),
                            current.path() + "[" + quote(property.getKey()) + "]", current.depth() + 1));
                }
            } else if (node.isArray()) {
                for (int index = 0; index < node.size(); index++) {
                    pending.addLast(new JsonValue(node.get(index), current.path() + "[" + index + "]", current.depth() + 1));
                }
            } else {
                output.append(current.path());
                output.append(": ");
                if (node.isTextual()) output.appendQuoted(node.asString());
                else output.append(node.toString());
                output.append("\n");
            }
        }
        return output.toString();
    }

    private String quote(String value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new InvalidDocumentException("A JSON property name could not be read.");
        }
    }

    private static String stripLeadingBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }

    private static void validateJsonDepth(String json) {
        boolean inString = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = 0; index < json.length(); index++) {
            char character = json.charAt(index);
            if (inString) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') inString = false;
            } else if (character == '"') {
                inString = true;
            } else if (character == '{' || character == '[') {
                if (++depth > MAX_JSON_DEPTH) {
                    throw new InvalidDocumentException("JSON nesting may be up to 64 levels deep.");
                }
            } else if (character == '}' || character == ']') {
                depth--;
            }
        }
    }

    private record JsonValue(JsonNode node, String path, int depth) {
    }

    private static final class BoundedText {
        private final StringBuilder value = new StringBuilder();

        void append(String text) {
            ensureCapacity(text.length());
            value.append(text);
        }

        void appendQuoted(String text) {
            append("\"");
            for (int index = 0; index < text.length(); index++) {
                char character = text.charAt(index);
                switch (character) {
                    case '"' -> append("\\\"");
                    case '\\' -> append("\\\\");
                    case '\n' -> append("\\n");
                    case '\r' -> append("\\r");
                    case '\t' -> append("\\t");
                    default -> {
                        if (Character.isISOControl(character)) append(String.format("\\u%04x", (int) character));
                        else append(String.valueOf(character));
                    }
                }
            }
            append("\"");
        }

        private void ensureCapacity(int extraCharacters) {
            if ((long) value.length() + extraCharacters > MAX_EXTRACTED_CHARS) {
                throw new InvalidDocumentException("Structured document extracted text must be 250,000 characters or less.");
            }
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }
}
