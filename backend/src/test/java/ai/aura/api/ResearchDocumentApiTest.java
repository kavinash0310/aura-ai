package ai.aura.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
}
