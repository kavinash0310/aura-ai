package ai.aura.api;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:aura-doc-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "aura.auth.username=test-owner",
        "aura.auth.password=this-is-a-test-password-with-enough-length"
})
class ResearchDocumentApiTest {
    private static final String USERNAME = "test-owner";
    private static final String PASSWORD = "this-is-a-test-password-with-enough-length";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void importsSearchesRetrievesAndDeletesAnAuthorizedTextDocument() throws Exception {
        mockMvc.perform(get("/api/v1/documents"))
                .andExpect(status().isUnauthorized());

        MockMultipartFile file = new MockMultipartFile("file", "research-notes.md", "text/markdown",
                "Private research notes about AURA intelligence.\r\nSecond line with source detail.".getBytes(StandardCharsets.UTF_8));
        String location = mockMvc.perform(multipart("/api/v1/documents").file(file)
                        .param("title", "Research notes")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/api/v1/documents/")))
                .andExpect(jsonPath("$.title").value("Research notes"))
                .andExpect(jsonPath("$.originalFilename").value("research-notes.md"))
                .andExpect(jsonPath("$.mediaType").value("text/markdown; charset=utf-8"))
                .andExpect(jsonPath("$.contentHash").value(org.hamcrest.Matchers.matchesRegex("[a-f0-9]{64}")))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get("/api/v1/documents/search").param("q", "AURA")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Research notes"))
                .andExpect(jsonPath("$[0].startOffset").value(29))
                .andExpect(jsonPath("$[0].endOffset").value(33))
                .andExpect(jsonPath("$[0].citation").value(org.hamcrest.Matchers.matchesRegex(
                        "aura-doc://[0-9a-f-]+#chars=29-33")));

        mockMvc.perform(get(location).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Private research notes about AURA intelligence.\nSecond line with source detail."));

        mockMvc.perform(delete(location).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(location).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESEARCH_DOCUMENT_NOT_FOUND"));
    }

    @Test
    void rejectsUnsupportedAndMalformedDocumentFiles() throws Exception {
        MockMultipartFile unsupported = new MockMultipartFile("file", "payload.pdf", "application/pdf", "text".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/documents").file(unsupported)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));

        MockMultipartFile malformed = new MockMultipartFile("file", "payload.txt", "text/plain", new byte[]{(byte) 0xc3, (byte) 0x28});
        mockMvc.perform(multipart("/api/v1/documents").file(malformed)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Documents must use valid UTF-8 encoding."));
    }

    @Test
    void importsCsvAndJsonAsSearchableExtractedText() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "evidence.csv", "text/csv",
                "indicator,notes\nCVE-2026-12345,\"AURA, local research\"\n".getBytes(StandardCharsets.UTF_8));
        String csvLocation = mockMvc.perform(multipart("/api/v1/documents").file(csv)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("text/csv; charset=utf-8"))
                .andReturn().getResponse().getHeader("Location");
        mockMvc.perform(get(csvLocation).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString(
                        "Row 2: [\"CVE-2026-12345\", \"AURA, local research\"]")));

        MockMultipartFile json = new MockMultipartFile("file", "evidence.json", "application/json",
                "{\"finding\":{\"id\":\"CVE-2026-54321\"}}".getBytes(StandardCharsets.UTF_8));
        String jsonLocation = mockMvc.perform(multipart("/api/v1/documents").file(json)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("application/json; charset=utf-8"))
                .andReturn().getResponse().getHeader("Location");
        mockMvc.perform(get(jsonLocation).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("$[\"finding\"][\"id\"]: \"CVE-2026-54321\"\n"));
    }

    @Test
    void rejectsJsonWithTrailingContent() throws Exception {
        MockMultipartFile malformed = new MockMultipartFile("file", "evidence.json", "application/json",
                "{} {}".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/documents").file(malformed)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The JSON file is malformed or contains trailing data."));
    }

    @Test
    void importsHtmlAsPlainTextWithoutSavingMarkupOrScriptContent() throws Exception {
        MockMultipartFile html = new MockMultipartFile("file", "brief.html", "text/html",
                "<html><body><h1>AURA evidence</h1><p>Local HTML notes</p><script>secret-script</script></body></html>"
                        .getBytes(StandardCharsets.UTF_8));
        String location = mockMvc.perform(multipart("/api/v1/documents").file(html)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("text/html; charset=utf-8"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("AURA evidence Local HTML notes"))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret-script"))));
    }

    @Test
    void versionsDocumentsWithoutChangingPriorContentOrCitations() throws Exception {
        MockMultipartFile first = new MockMultipartFile("file", "notes-v1.txt", "text/plain",
                "ARCHIVEWORD belongs to version one.".getBytes(StandardCharsets.UTF_8));
        String firstLocation = mockMvc.perform(multipart("/api/v1/documents").file(first)
                        .param("title", "Versioned notes")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(1))
                .andReturn().getResponse().getHeader("Location");
        String firstId = firstLocation.substring(firstLocation.lastIndexOf('/') + 1);

        MockMultipartFile second = new MockMultipartFile("file", "notes-v2.txt", "text/plain",
                "CURRENTWORD belongs to version two.".getBytes(StandardCharsets.UTF_8));
        String secondLocation = mockMvc.perform(multipart("/api/v1/documents/" + firstId + "/versions").file(second)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.logicalDocumentId").value(firstId))
                .andExpect(jsonPath("$.versionNumber").value(2))
                .andExpect(jsonPath("$.title").value("Versioned notes"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get("/api/v1/documents/" + firstId + "/versions")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].versionNumber").value(2))
                .andExpect(jsonPath("$[1].versionNumber").value(1));

        mockMvc.perform(get("/api/v1/documents").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].versionNumber").value(2));

        mockMvc.perform(get("/api/v1/documents/search").param("q", "ARCHIVEWORD")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/documents/search").param("q", "CURRENTWORD")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(secondLocation.substring(secondLocation.lastIndexOf('/') + 1)));

        mockMvc.perform(get(firstLocation).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("ARCHIVEWORD belongs to version one."));

        mockMvc.perform(delete(firstLocation).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(secondLocation).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void importsPdfTextAndReturnsPageCitations() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "research.pdf", "application/pdf",
                pdfWithPages("AURA source on the first page.", "Evidence about AURA on the second page."));

        String location = mockMvc.perform(multipart("/api/v1/documents").file(file)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaType").value("application/pdf"))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get("/api/v1/documents/search").param("q", "second page")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].page").value(2))
                .andExpect(jsonPath("$[0].citation").value(org.hamcrest.Matchers.matchesRegex(
                        "aura-doc://[0-9a-f-]+#page=2&chars=\\d+-\\d+")));

        mockMvc.perform(get(location).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("\f")))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("second page")));
    }

    @Test
    void rejectsScannedStyleAndOutOfBoundsPdfs() throws Exception {
        MockMultipartFile scannedStyle = new MockMultipartFile("file", "scan.pdf", "application/pdf",
                pdfWithPages((String) null));
        mockMvc.perform(multipart("/api/v1/documents").file(scannedStyle)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "The PDF has no extractable text. Scanned PDFs need OCR, which is not enabled."));

        String[] tooManyPages = new String[101];
        MockMultipartFile tooManyPagesPdf = new MockMultipartFile("file", "many-pages.pdf", "application/pdf",
                pdfWithPages(tooManyPages));
        mockMvc.perform(multipart("/api/v1/documents").file(tooManyPagesPdf)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("PDF files may contain up to 100 pages."));

        MockMultipartFile tooMuchText = new MockMultipartFile("file", "large-text.pdf", "application/pdf",
                pdfWithPages("A".repeat(250_001)));
        mockMvc.perform(multipart("/api/v1/documents").file(tooMuchText)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("PDF extracted text must be 250,000 characters or less."));
    }

    private static byte[] pdfWithPages(String... pageTexts) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (pageText != null && !pageText.isEmpty()) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.newLineAtOffset(50, 700);
                        content.showText(pageText);
                        content.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
