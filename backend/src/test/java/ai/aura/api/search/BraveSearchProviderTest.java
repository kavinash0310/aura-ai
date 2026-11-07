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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BraveSearchProviderTest {

    @Test
    void searchesFixedPublicEndpointAndKeepsOnlySafeDeduplicatedHttpSources() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BraveSearchProvider provider = new BraveSearchProvider(builder.build(), "test-token");

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.search.brave.com/res/v1/web/search?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Subscription-Token", "test-token"))
                .andExpect(queryParam("q", "public%20research%20%26%20safety"))
                .andExpect(queryParam("count", "5"))
                .andExpect(queryParam("safesearch", "strict"))
                .andExpect(queryParam("result_filter", "web,news,discussions"))
                .andRespond(withSuccess("""
                        {
                          "web": {"results": [
                            {"title":"Web source","url":"https://example.org/a%2Fb#section","description":"Web details","source":"Example","page_age":"2026-10-25T12:00:00Z"},
                            {"title":"Unsafe scheme","url":"file:///private/data"}
                          ]},
                          "news": {"results": [
                            {"title":"Duplicate","url":"https://example.org/a%2Fb#other"}
                          ]},
                          "discussions": {"results": [
                            {"title":"Discussion","url":"https://forum.example.net/thread","description":"Public discussion"}
                          ]}
                        }
                        """, MediaType.APPLICATION_JSON));

        List<ProviderSource> results = provider.search(
                "public research & safety",
                new ResearchFilters("US", "en", "pw", 5)
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).type()).isEqualTo(SourceType.WEB);
        assertThat(results.get(0).url()).isEqualTo("https://example.org/a%2Fb");
        assertThat(results.get(0).publishedAt()).isNotNull();
        assertThat(results.get(1).type()).isEqualTo(SourceType.DISCUSSION);
        assertThat(results).noneMatch(result -> result.url().startsWith("file:"));
        server.verify();
    }

    @Test
    void missingKeyPreventsAnyProviderRequest() {
        BraveSearchProvider provider = new BraveSearchProvider(RestClient.builder().build(), " ");

        assertThat(provider.isConfigured()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        provider.search("topic", ResearchFilters.defaults()))
                .isInstanceOf(SearchProviderNotConfiguredException.class);
    }
}

