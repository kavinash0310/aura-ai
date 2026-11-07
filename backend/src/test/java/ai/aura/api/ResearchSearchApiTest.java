package ai.aura.api;

import ai.aura.api.application.AiReportClient;
import ai.aura.api.application.AiResearchReportRequest;
import ai.aura.api.api.ResearchReportResponse;
import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.search.ProviderSource;
import ai.aura.api.search.SearchProvider;
import ai.aura.api.search.SearchProviderException;
import ai.aura.api.persistence.ResearchSourceRepository;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ResearchSearchApiTest.TestSearchProviderConfiguration.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:aura-search-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "aura.auth.username=test-owner",
        "aura.auth.password=this-is-a-test-password-with-enough-length"
})
class ResearchSearchApiTest {

    private static final String USERNAME = "test-owner";
    private static final String PASSWORD = "this-is-a-test-password-with-enough-length";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ai.aura.api.application.ResearchJobService jobs;

    @Autowired
    private ResearchSourceRepository sources;

    @MockitoBean
    private AiReportClient aiReportClient;

    @Test
    void listsRecentHistoryAndDeletesItsSavedSources() throws Exception {
        UUID id = createJob("persisted brief");
        runSearch(id);
        org.assertj.core.api.Assertions.assertThat(sources.countByResearchJobId(id)).isEqualTo(3);

        mockMvc.perform(get("/api/v1/research-jobs").with(httpBasic(USERNAME, PASSWORD)).param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..subject", org.hamcrest.Matchers.hasItem("persisted brief")));

        mockMvc.perform(get("/api/v1/research-jobs").param("limit", "25"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/research-jobs").with(httpBasic(USERNAME, PASSWORD)).param("limit", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESEARCH_FILTER"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/research-jobs/" + id).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(sources.countByResearchJobId(id)).isZero();
        mockMvc.perform(get("/api/v1/research-jobs/" + id).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void searchPersistsSourcesAndFiltersByKindHostTextAndDate() throws Exception {
        UUID id = createJob("civic research");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"country\":\"US\",\"language\":\"en\",\"freshness\":\"pm\",\"resultLimit\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.providerResultCount").value(3))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.coverageNote").value(org.hamcrest.Matchers.containsString("do not represent the entire internet")));

        mockMvc.perform(get("/api/v1/research-jobs/" + id + "/sources")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("kind", "NEWS")
                        .param("q", "CIVIC")
                        .param("host", "example.org")
                        .param("publishedFrom", "2026-10-01")
                        .param("publishedTo", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].kind").value("NEWS"))
                .andExpect(jsonPath("$.results[0].domain").value("news.example.org"));

        mockMvc.perform(get("/api/v1/research-jobs/" + id + "/sources")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("host", "notexample.org"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(0));
    }

    @Test
    void csvExportIsDownloadableAndNeutralizesFormulaCells() throws Exception {
        UUID id = createJob("export test");
        runSearch(id);

        mockMvc.perform(get("/api/v1/research-jobs/" + id + "/export")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"aura-research-" + id + ".csv\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("'=SUM(1,1)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(",\"\",\"\",\"")));
    }

    @Test
    void markdownExportEscapesUntrustedHtmlAndLineBreaks() throws Exception {
        UUID id = createJob("markdown test");
        runSearch(id);

        mockMvc.perform(get("/api/v1/research-jobs/" + id + "/export")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("format", "md"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/markdown")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;img src=x onerror=alert(1)&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<img"))));
    }

    @Test
    void reportRequiresAuthenticationAndSendsOnlySelectedSourcesToAiService() throws Exception {
        UUID id = createJob("passkey adoption");
        runSearch(id);
        var selectedSource = sources.findByResearchJobIdOrderByRankAsc(id).getFirst();
        var response = new ResearchReportResponse(
                Instant.parse("2026-11-07T12:00:00Z"),
                "ollama",
                "test-model",
                "Passkey research",
                "The selected source describes passkey adoption.",
                List.of(selectedSource.getId().toString()),
                List.of(new ResearchReportResponse.Finding(
                        "A public source discusses passkeys.",
                        List.of(selectedSource.getId().toString())
                )),
                List.of("Pages were not fetched."),
                List.of(new ResearchReportResponse.Citation(
                        selectedSource.getId().toString(),
                        selectedSource.getTitle(),
                        selectedSource.getUrl(),
                        selectedSource.getDomain(),
                        selectedSource.getType()
                )),
                "Generated from selected provider snippets only."
        );
        when(aiReportClient.generate(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceIds\":[\"" + selectedSource.getId() + "\"]}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/report")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceIds\":[\"" + selectedSource.getId() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.citations[0].id").value(selectedSource.getId().toString()));

        ArgumentCaptor<AiResearchReportRequest> request = ArgumentCaptor.forClass(AiResearchReportRequest.class);
        verify(aiReportClient).generate(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().sources())
                .extracting(AiResearchReportRequest.Source::id)
                .containsExactly(selectedSource.getId().toString());
    }

    @Test
    void searchRequiresAuthenticationAndCannotBeRepeated() throws Exception {
        UUID id = createJob("repeat test");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        runSearch(id);
        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void providerFailureLeavesAnAuditableFailedJob() throws Exception {
        UUID id = createJob("provider-fails");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEARCH_PROVIDER_RATE_LIMITED"));

        mockMvc.perform(get("/api/v1/research-jobs/" + id).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("SEARCH_PROVIDER_RATE_LIMITED"));
    }

    private UUID createJob(String subject) {
        return jobs.create(subject, ai.aura.api.domain.ResearchMode.GENERAL).getId();
    }

    private void runSearch(UUID id) throws Exception {
        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class TestSearchProviderConfiguration {
        @Bean
        @Primary
        SearchProvider testSearchProvider() {
            return new SearchProvider() {
                @Override
                public String name() {
                    return "test";
                }

                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public List<ProviderSource> search(String query, ResearchFilters filters) {
                    if (query.equals("provider-fails")) {
                        throw new SearchProviderException("SEARCH_PROVIDER_RATE_LIMITED", true);
                    }
                    return List.of(
                            new ProviderSource(
                                    ai.aura.api.domain.SourceType.WEB,
                                    "=SUM(1,1)",
                                    "https://web.example.com/report",
                                    "web.example.com",
                                    "Public web result",
                                    "Example Web",
                                    Instant.parse("2026-10-25T12:00:00Z")
                            ),
                            new ProviderSource(
                                    ai.aura.api.domain.SourceType.NEWS,
                                    "Civic update",
                                    "https://news.example.org/story",
                                    "news.example.org",
                                    "A civic news item",
                                    "Example News",
                                    Instant.parse("2026-10-20T10:00:00Z")
                            ),
                            new ProviderSource(
                                    ai.aura.api.domain.SourceType.DISCUSSION,
                                    "Community discussion",
                                    "https://talk.example.net/thread",
                                    "talk.example.net",
                                    "<img src=x onerror=alert(1)>",
                                    null,
                                    null
                            )
                    );
                }
            };
        }
    }
}
