package ai.aura.api.search;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GdeltNewsSearchProviderTest {

    @Test
    void requestsOnlyBoundedNewsMetadataAndDropsUnsafeOrDuplicateLinks() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GdeltNewsSearchProvider provider = new GdeltNewsSearchProvider(builder.build(), true);

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.gdeltproject.org/api/v2/doc/doc?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("query", "climate%20change%20sourcecountry:unitedstates%20sourcelang:eng"))
                .andExpect(queryParam("mode", "artlist"))
                .andExpect(queryParam("format", "json"))
                .andExpect(queryParam("maxrecords", "5"))
                .andExpect(queryParam("timespan", "1week"))
                .andRespond(withSuccess("""
                        {"articles":[
                          {"title":"News story","url":"https://news.example.org/story#headline","domain":"news.example.org","seendate":"20261107T143000Z"},
                          {"title":"Duplicate","url":"https://news.example.org/story#other","domain":"news.example.org"},
                          {"title":"Unsafe scheme","url":"file:///private/data"},
                          {"title":"Credential URL","url":"https://user:secret@news.example.org/story"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<ProviderSource> results = provider.search(
                "climate change",
                new ResearchFilters("US", "en", "pw", 5)
        );

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().type()).isEqualTo(SourceType.NEWS);
        assertThat(results.getFirst().url()).isEqualTo("https://news.example.org/story");
        assertThat(results.getFirst().description()).isNull();
        assertThat(results.getFirst().publishedAt()).isEqualTo(java.time.Instant.parse("2026-11-07T14:30:00Z"));
        server.verify();
    }

    @Test
    void mapsYearWindowToProviderMaximumAndPreventsCallsWhenDisabled() {
        assertThat(GdeltNewsSearchProvider.timespan("py")).isEqualTo("3months");

        GdeltNewsSearchProvider provider = new GdeltNewsSearchProvider(RestClient.builder().build(), false);
        assertThat(provider.isConfigured()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        provider.search("topic", ResearchFilters.defaults()))
                .isInstanceOf(SearchProviderNotConfiguredException.class);
    }
}
