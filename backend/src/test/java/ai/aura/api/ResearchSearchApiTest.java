package ai.aura.api;

import ai.aura.api.application.AiReportClient;
import ai.aura.api.application.AiResearchQueryPlanRequest;
import ai.aura.api.application.AiResearchReportRequest;
import ai.aura.api.api.ResearchReportResponse;
import ai.aura.api.api.ResearchQueryPlanResponse;
import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchProviderRunStatus;
import ai.aura.api.search.ProviderSource;
import ai.aura.api.search.SearchProvider;
import ai.aura.api.search.SearchProviderException;
import ai.aura.api.persistence.ResearchProviderRunRepository;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
        "aura.auth.password=this-is-a-test-password-with-enough-length",
        "aura.search.brave.api-key=",
        "aura.search.gdelt.enabled=false",
        "aura.search.bluesky.enabled=false"
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

    @Autowired
    private ResearchProviderRunRepository providerRuns;

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
                        .content("{\"country\":\"US\",\"language\":\"en\",\"freshness\":\"pm\",\"resultLimit\":5,\"provider\":\"test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.provider").value("test"))
                .andExpect(jsonPath("$.providerResultCount").value(3))
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].provider").value("test"))
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
    void allModeSearchesEveryConfiguredProviderAndKeepsPerSourceProvenance() throws Exception {
        UUID id = createJob("multi-source research");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultLimit\":5,\"provider\":\"all\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("all"))
                .andExpect(jsonPath("$.providerResultCount").value(4))
                .andExpect(jsonPath("$.results.length()").value(4))
                .andExpect(jsonPath("$.providerRuns.length()").value(2))
                .andExpect(jsonPath("$.providerRuns[0].provider").value("test"))
                .andExpect(jsonPath("$.providerRuns[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerRuns[0].query").value("multi-source research"))
                .andExpect(jsonPath("$.results[0].searchQuery").value("multi-source research"))
                .andExpect(jsonPath("$.coverageNote").value(org.hamcrest.Matchers.containsString("every currently configured public search provider")));

        org.assertj.core.api.Assertions.assertThat(sources.findByResearchJobIdOrderByRankAsc(id))
                .extracting(source -> source.getProvider())
                .containsExactlyInAnyOrder("test", "test", "test", "secondary");

        mockMvc.perform(get("/api/v1/research-jobs/" + id + "/sources")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("provider", "SECONDARY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].provider").value("secondary"));

        mockMvc.perform(get("/api/v1/research-jobs/" + id + "/export")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("format", "json")
                        .param("provider", "secondary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].provider").value("secondary"));
    }

    @Test
    void runsBoundedQueryPlanAndRecordsWhichQueryReturnedEachSource() throws Exception {
        UUID id = createJob("passkeys adoption");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"test\",\"queries\":[\"passkeys adoption\",\"passkey deployment guidance\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerRuns.length()").value(2))
                .andExpect(jsonPath("$.providerRuns[0].queryOrder").value(1))
                .andExpect(jsonPath("$.providerRuns[0].query").value("passkeys adoption"))
                .andExpect(jsonPath("$.providerRuns[1].queryOrder").value(2))
                .andExpect(jsonPath("$.providerRuns[1].query").value("passkey deployment guidance"))
                .andExpect(jsonPath("$.results.length()").value(6))
                .andExpect(jsonPath("$.results[0].searchQuery").value("passkeys adoption"))
                .andExpect(jsonPath("$.results[3].searchQuery").value("passkey deployment guidance"));

        mockMvc.perform(get("/api/v1/research-jobs/" + id + "/export")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("provider,searchQuery,rank")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("passkey deployment guidance")));

        mockMvc.perform(get("/api/v1/research-jobs/" + id + "/sources")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("q", "deployment guidance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.results[0].searchQuery").value("passkey deployment guidance"));
    }

    @Test
    void rejectsQueryPlansThatDoNotStartWithTheResearchTopic() throws Exception {
        UUID id = createJob("approved topic");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"test\",\"queries\":[\"unrelated query\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESEARCH_FILTER"));

        org.assertj.core.api.Assertions.assertThat(jobs.get(id).getStatus()).isEqualTo(ResearchJobStatus.REQUESTED);
    }

    @Test
    void allModeKeepsSuccessfulResultsAndAuditsTheFailedProvider() throws Exception {
        UUID id = createJob("multi-source-fails");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"all\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.providerResultCount").value(3))
                .andExpect(jsonPath("$.providerRuns.length()").value(2))
                .andExpect(jsonPath("$.providerRuns[0].provider").value("test"))
                .andExpect(jsonPath("$.providerRuns[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.providerRuns[1].provider").value("secondary"))
                .andExpect(jsonPath("$.providerRuns[1].status").value("FAILED"))
                .andExpect(jsonPath("$.providerRuns[1].failureCode").value("SEARCH_PROVIDER_RATE_LIMITED"))
                .andExpect(jsonPath("$.coverageNote").value(org.hamcrest.Matchers.containsString("Partial coverage")));

        mockMvc.perform(get("/api/v1/research-jobs/" + id).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.failureCode").value("SEARCH_PROVIDER_RATE_LIMITED"));
        org.assertj.core.api.Assertions.assertThat(sources.countByResearchJobId(id)).isEqualTo(3);

        mockMvc.perform(get("/api/v1/research-jobs/" + id + "/sources")
                        .with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(3))
                .andExpect(jsonPath("$.providerRuns[1].status").value("FAILED"));
    }

    @Test
    void ownerCanExplicitlyRetryOnlyTheLatestFailedProviderAttempt() throws Exception {
        UUID id = createJob("retryable-query");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"all\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.providerRuns[1].attempt").value(1))
                .andExpect(jsonPath("$.providerRuns[1].status").value("FAILED"));

        mockMvc.perform(post("/api/v1/research-jobs/" + id
                        + "/providers/secondary/queries/1/retry").param("attempt", "1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/research-jobs/" + id
                        + "/providers/secondary/queries/1/retry")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("attempt", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.providerResultCount").value(4))
                .andExpect(jsonPath("$.providerRuns.length()").value(3))
                .andExpect(jsonPath("$.providerRuns[1].status").value("FAILED"))
                .andExpect(jsonPath("$.providerRuns[2].attempt").value(2))
                .andExpect(jsonPath("$.providerRuns[2].status").value("SUCCEEDED"));

        mockMvc.perform(post("/api/v1/research-jobs/" + id
                        + "/providers/secondary/queries/1/retry")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("attempt", "1"))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(sources.countByResearchJobId(id)).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(providerRuns.findByResearchJobIdOrderByRunOrderAsc(id))
                .extracting(run -> run.getAttempt())
                .containsExactly(1, 1, 2);
    }

    @Test
    void explicitProviderRetryStopsAfterThreeTotalAttempts() throws Exception {
        UUID id = createJob("multi-source-fails");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"all\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"));

        mockMvc.perform(post("/api/v1/research-jobs/" + id
                        + "/providers/secondary/queries/1/retry")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("attempt", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerRuns[2].attempt").value(2))
                .andExpect(jsonPath("$.providerRuns[2].status").value("FAILED"));

        mockMvc.perform(post("/api/v1/research-jobs/" + id
                        + "/providers/secondary/queries/1/retry")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("attempt", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerRuns[3].attempt").value(3))
                .andExpect(jsonPath("$.providerRuns[3].status").value("FAILED"));

        mockMvc.perform(post("/api/v1/research-jobs/" + id
                        + "/providers/secondary/queries/1/retry")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .param("attempt", "3"))
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(providerRuns.findByResearchJobIdOrderByRunOrderAsc(id))
                .extracting(run -> run.getAttempt())
                .containsExactly(1, 1, 2, 3);
    }

    @Test
    void allModeRetainsAllFailedProviderOutcomesWhenNoProviderSucceeds() throws Exception {
        UUID id = createJob("all-providers-fail");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"all\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEARCH_PROVIDER_RATE_LIMITED"));

        mockMvc.perform(get("/api/v1/research-jobs/" + id).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.providerRuns.length()").value(2))
                .andExpect(jsonPath("$.providerRuns[0].status").value("FAILED"))
                .andExpect(jsonPath("$.providerRuns[1].status").value("FAILED"));
        org.assertj.core.api.Assertions.assertThat(sources.countByResearchJobId(id)).isZero();
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("provider,searchQuery,rank,kind")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"test\",\"export test\",1,\"WEB\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PROVIDER_RUN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SUCCEEDED")))
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
                .andExpect(content().string(org.hamcrest.Matchers.containsString("## Provider outcomes")))
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
    void queryPlanRequiresAuthenticationAndReturnsLocalDraftSuggestions() throws Exception {
        var response = new ResearchQueryPlanResponse(
                Instant.parse("2026-11-08T12:00:00Z"),
                "ollama",
                "test-model",
                List.of("official public response", "independent timeline"),
                new ResearchQueryPlanResponse.ModelRun("research-query-plan-v1", 120L, 10L, 8L),
                "Draft only; no search providers contacted."
        );
        when(aiReportClient.suggestQueries(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/research/query-plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"public policy\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/research/query-plan")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/research/query-plan")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"public policy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions.length()").value(2))
                .andExpect(jsonPath("$.suggestions[0]").value("official public response"))
                .andExpect(jsonPath("$.modelRun.promptVersion").value("research-query-plan-v1"));

        ArgumentCaptor<AiResearchQueryPlanRequest> request =
                ArgumentCaptor.forClass(AiResearchQueryPlanRequest.class);
        verify(aiReportClient).suggestQueries(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().topic()).isEqualTo("public policy");
    }

    @Test
    void searchRequiresAuthenticationAndCannotBeRepeated() throws Exception {
        UUID id = createJob("repeat test");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"test\"}"))
                .andExpect(status().isUnauthorized());

        runSearch(id);
        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"test\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void providerFailureLeavesAnAuditableFailedJob() throws Exception {
        UUID id = createJob("provider-fails");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"test\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SEARCH_PROVIDER_RATE_LIMITED"));

        mockMvc.perform(get("/api/v1/research-jobs/" + id).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureCode").value("SEARCH_PROVIDER_RATE_LIMITED"))
                .andExpect(jsonPath("$.providerRuns[0].provider").value("test"))
                .andExpect(jsonPath("$.providerRuns[0].status").value("FAILED"));
    }

    @Test
    void recoveryKeepsCommittedProviderEvidenceAndClosesInterruptedRuns() {
        UUID id = createJob("recover interrupted search");
        jobs.beginSearch(
                id,
                new ResearchFilters(null, null, null, 20),
                "all",
                List.of("first", "second", "third"),
                List.of("recover interrupted search")
        );
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jobs.delete(id))
                .isInstanceOf(ai.aura.api.application.ResearchJobConflictException.class);
        jobs.startProviderRun(id, "first", 1);
        jobs.completeProviderRun(id, "first", 1, List.of(new ProviderSource(
                ai.aura.api.domain.SourceType.WEB,
                "Durable result",
                "https://example.com/durable-result",
                "example.com",
                "Saved when the first provider completed.",
                "Example",
                Instant.parse("2026-11-07T12:00:00Z")
        )));
        jobs.startProviderRun(id, "second", 1);

        jobs.recoverInterruptedSearches();

        org.assertj.core.api.Assertions.assertThat(jobs.get(id).getStatus()).isEqualTo(ResearchJobStatus.PARTIAL);
        org.assertj.core.api.Assertions.assertThat(sources.countByResearchJobId(id)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(providerRuns.findByResearchJobIdOrderByRunOrderAsc(id))
                .extracting(run -> run.getStatus())
                .containsExactly(
                        ResearchProviderRunStatus.SUCCEEDED,
                        ResearchProviderRunStatus.FAILED,
                        ResearchProviderRunStatus.SKIPPED
                );
        org.assertj.core.api.Assertions.assertThat(providerRuns.findByResearchJobIdOrderByRunOrderAsc(id))
                .extracting(run -> run.getFailureCode())
                .containsExactly((String) null, "SEARCH_INTERRUPTED", "SEARCH_INTERRUPTED_BEFORE_PROVIDER");
    }

    @Test
    void recoveryCompletesAJobWhenEveryProviderAlreadyCommittedSuccessfully() {
        UUID id = createJob("finish after restart");
        jobs.beginSearch(id, ResearchFilters.defaults(), "test", List.of("test"), List.of("finish after restart"));
        jobs.startProviderRun(id, "test", 1);
        jobs.completeProviderRun(id, "test", 1, List.of());

        jobs.recoverInterruptedSearches();

        org.assertj.core.api.Assertions.assertThat(jobs.get(id).getStatus()).isEqualTo(ResearchJobStatus.COMPLETED);
        org.assertj.core.api.Assertions.assertThat(providerRuns.findByResearchJobIdOrderByRunOrderAsc(id))
                .extracting(run -> run.getStatus())
                .containsExactly(ResearchProviderRunStatus.SUCCEEDED);
    }

    @Test
    void recoveryFailsAJobWhenNoProviderCompletedBeforeInterruption() {
        UUID id = createJob("fail after restart");
        jobs.beginSearch(id, ResearchFilters.defaults(), "all", List.of("first", "second"), List.of("fail after restart"));
        jobs.startProviderRun(id, "first", 1);

        jobs.recoverInterruptedSearches();

        org.assertj.core.api.Assertions.assertThat(jobs.get(id).getStatus()).isEqualTo(ResearchJobStatus.FAILED);
        org.assertj.core.api.Assertions.assertThat(jobs.get(id).getFailureCode()).isEqualTo("SEARCH_INTERRUPTED");
        org.assertj.core.api.Assertions.assertThat(sources.countByResearchJobId(id)).isZero();
    }

    @Test
    void recoveryFailsClosedWhenLegacyProviderCountsHaveNoSavedEvidence() {
        UUID id = createJob("legacy interrupted search");
        jobs.beginSearch(id, ResearchFilters.defaults(), "test", List.of("test"), List.of("legacy interrupted search"));
        jobs.startProviderRun(id, "test", 1);
        var legacyRun = providerRuns.findByResearchJobIdAndProviderAndQueryOrderAndAttempt(id, "test", 1, 1)
                .orElseThrow();
        legacyRun.succeed(1, Instant.now());
        providerRuns.save(legacyRun);

        jobs.recoverInterruptedSearches();

        org.assertj.core.api.Assertions.assertThat(jobs.get(id).getStatus()).isEqualTo(ResearchJobStatus.FAILED);
        org.assertj.core.api.Assertions.assertThat(jobs.get(id).getFailureCode())
                .isEqualTo("SEARCH_RECOVERY_SOURCE_COUNT_MISMATCH");
        org.assertj.core.api.Assertions.assertThat(sources.countByResearchJobId(id)).isZero();
    }

    @Test
    void unknownProviderIsRejectedBeforeTheJobStarts() throws Exception {
        UUID id = createJob("unsupported provider");

        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"unknown\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RESEARCH_FILTER"));

        mockMvc.perform(get("/api/v1/research-jobs/" + id).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    private UUID createJob(String subject) {
        return jobs.create(subject, ai.aura.api.domain.ResearchMode.GENERAL).getId();
    }

    private void runSearch(UUID id) throws Exception {
        mockMvc.perform(post("/api/v1/research-jobs/" + id + "/search")
                        .with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"test\"}"))
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
                    if (query.equals("provider-fails") || query.equals("all-providers-fail")) {
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

        @Bean
        SearchProvider secondaryTestSearchProvider() {
            ConcurrentHashMap<String, AtomicInteger> callsByQuery = new ConcurrentHashMap<>();
            return new SearchProvider() {
                @Override
                public String name() {
                    return "secondary";
                }

                @Override
                public boolean isConfigured() {
                    return true;
                }

                @Override
                public List<ProviderSource> search(String query, ResearchFilters filters) {
                    if (query.equals("multi-source-fails") || query.equals("all-providers-fail")) {
                        throw new SearchProviderException("SEARCH_PROVIDER_RATE_LIMITED", true);
                    }
                    if (query.equals("retryable-query")
                            && callsByQuery.computeIfAbsent(query, ignored -> new AtomicInteger()).incrementAndGet() == 1) {
                        throw new SearchProviderException("SEARCH_PROVIDER_RATE_LIMITED", true);
                    }
                    return List.of(new ProviderSource(
                            ai.aura.api.domain.SourceType.NEWS,
                            "Secondary provider result",
                            "https://secondary.example.net/story",
                            "secondary.example.net",
                            "A second provider result",
                            "Secondary",
                            Instant.parse("2026-10-21T10:00:00Z")
                    ));
                }
            };
        }
    }
}
