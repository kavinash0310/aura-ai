package ai.aura.api;

import ai.aura.api.cyber.EpssProvider;
import ai.aura.api.cyber.EpssScore;
import ai.aura.api.cyber.KevCatalogProvider;
import ai.aura.api.cyber.KevCatalogSnapshot;
import ai.aura.api.cyber.KevEntry;
import ai.aura.api.application.CycloneDxSbomParser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SbomLookupApiTest.ProviderTestConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:aura-sbom-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "aura.auth.username=test-owner",
        "aura.auth.password=this-is-a-test-password-with-enough-length"
})
class SbomLookupApiTest {

    private static final String USER = "test-owner";
    private static final String PASSWORD = "this-is-a-test-password-with-enough-length";
    private static final String BOM = """
            {"bomFormat":"CycloneDX","specVersion":"1.7",
             "components":[{"type":"library","bom-ref":"pkg:maven/org.example/demo@1.0","name":"demo","version":"1.0","purl":"pkg:maven/org.example/demo@1.0"}],
             "vulnerabilities":[{"id":"CVE-2026-12345","affects":[{"ref":"pkg:maven/org.example/demo@1.0"}]}]}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void requiresAuthenticationAndEnrichesOnlyTheCveDeclaredByTheSbom() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "inventory.json", "application/json", BOM.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/cyber/sbom/lookup").file(file))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(multipart("/api/v1/cyber/sbom/lookup")
                        .file(file)
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").value("CycloneDX JSON"))
                .andExpect(jsonPath("$.specVersion").value("1.7"))
                .andExpect(jsonPath("$.componentCount").value(1))
                .andExpect(jsonPath("$.cveCount").value(1))
                .andExpect(jsonPath("$.vulnerabilities[0].cve").value("CVE-2026-12345"))
                .andExpect(jsonPath("$.vulnerabilities[0].affectedComponents[0].name").value("demo"))
                .andExpect(jsonPath("$.intelligence.results[0].cve").value("CVE-2026-12345"))
                .andExpect(jsonPath("$.coverageNote").value(org.hamcrest.Matchers.containsString("does not match package versions")));
    }

    @Test
    void rejectsAnUploadAboveTheParserLimitBeforeReadingItsContents() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.json", "application/json", new byte[CycloneDxSbomParser.MAX_FILE_BYTES + 1]);

        mockMvc.perform(multipart("/api/v1/cyber/sbom/lookup")
                        .file(file)
                        .with(httpBasic(USER, PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SBOM"));
    }

    @TestConfiguration
    static class ProviderTestConfiguration {
        @Bean
        @Primary
        KevCatalogProvider kevCatalogProvider() {
            return new KevCatalogProvider((org.springframework.web.client.RestClient) null, Duration.ofHours(6)) {
                @Override
                public KevCatalogSnapshot lookup(Set<String> cves) {
                    return new KevCatalogSnapshot("test", LocalDate.parse("2026-11-07"),
                            Instant.parse("2026-11-07T00:00:00Z"), Map.of(), false);
                }
            };
        }

        @Bean
        @Primary
        EpssProvider epssProvider() {
            return new EpssProvider((org.springframework.web.client.RestClient) null) {
                @Override
                public Map<String, EpssScore> lookup(Set<String> cves) {
                    return Map.of("CVE-2026-12345", new EpssScore("CVE-2026-12345",
                            new BigDecimal("0.4"), new BigDecimal("0.8"), LocalDate.parse("2026-11-07")));
                }
            };
        }
    }
}
