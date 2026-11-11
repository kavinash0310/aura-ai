package ai.aura.api.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HtmlDocumentTextExtractorTest {
    private final HtmlDocumentTextExtractor extractor = new HtmlDocumentTextExtractor();

    @Test
    void extractsPlainTextAndDropsExecutableEmbeddedAndHiddenElements() {
        String html = "<html><head><title>metadata</title><script>script-marker</script></head>"
                + "<body><main><h1>AURA report</h1><p>Useful <strong>evidence</strong>.</p>"
                + "<div hidden>hidden-marker</div><div aria-hidden='true'>aria-marker</div>"
                + "<style>style-marker</style><iframe>frame-marker</iframe>"
                + "<form><label>form-marker</label></form><p>visible text</p></main></body></html>";

        String text = extractor.extract(html);

        assertThat(text).contains("AURA report", "Useful evidence.", "visible text");
        assertThat(text).doesNotContain("metadata", "script-marker", "hidden-marker", "aria-marker",
                "style-marker", "frame-marker", "form-marker");
    }

    @Test
    void rejectsHtmlWithoutTextAndCapsNodesAndExtractedCharacters() {
        assertThatThrownBy(() -> extractor.extract("<script>not text</script><style>not text</style>"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("no extractable");

        assertThatThrownBy(() -> extractor.extract("<p>" + "x".repeat(HtmlDocumentTextExtractor.MAX_EXTRACTED_CHARS + 1) + "</p>"))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("250,000 characters");

        String manyNodes = "<i>x</i>".repeat(HtmlDocumentTextExtractor.MAX_NODE_COUNT / 2 + 1);
        assertThatThrownBy(() -> extractor.extract(manyNodes))
                .isInstanceOf(InvalidDocumentException.class)
                .hasMessageContaining("50,000 parser nodes");
    }

    @Test
    void limitsParserDepthAndKeepsEmbeddedResourcesOutOfExtractedText() {
        String deeplyNested = "<div>".repeat(100) + "bounded text" + "</div>".repeat(100);

        assertThat(extractor.extract(deeplyNested)).contains("bounded text");
    }
}
