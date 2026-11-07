package ai.aura.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ResearchDocumentPostgresSearchTest {
    private static final String USERNAME = "test-owner";
    private static final String PASSWORD = "this-is-a-test-password-with-enough-length";

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("aura_test")
            .withUsername("aura_test")
            .withPassword("test-only-password");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("aura.auth.username", () -> USERNAME);
        registry.add("aura.auth.password", () -> PASSWORD);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesPostgresSearchIndexAndKeepsExactPhraseCitations() throws Exception {
        upload("exact.txt", "The report records a zero day issue in the service.");
        upload("split.txt", "The word zero appears here. Much later, the word day appears here.");
        upload("punctuation.txt", "A section titled .... is retained.");

        mockMvc.perform(get("/api/v1/documents/search").param("q", "zero day")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originalFilename").value("exact.txt"))
                .andExpect(jsonPath("$[0].startOffset").value(21))
                .andExpect(jsonPath("$[0].endOffset").value(29));

        mockMvc.perform(get("/api/v1/documents/search").param("q", "....")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originalFilename").value("punctuation.txt"));

        Integer indexCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_research_documents_search_vector'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, indexCount);
    }

    private void upload(String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/plain",
                content.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/documents").file(file)
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isCreated());
    }
}
