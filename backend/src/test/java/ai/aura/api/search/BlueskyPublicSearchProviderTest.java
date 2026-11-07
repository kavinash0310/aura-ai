package ai.aura.api.search;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class BlueskyPublicSearchProviderTest {

    @Test
    void searchesFixedPublicEndpointAndKeepsOnlyValidDeduplicatedPostLinks() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        BlueskyPublicSearchProvider provider = new BlueskyPublicSearchProvider(builder.build(), true);

        server.expect(requestTo(org.hamcrest.Matchers.startsWith(
                        "https://public.api.bsky.app/xrpc/app.bsky.feed.searchPostsV2?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("query", "public%20climate%20research"))
                .andExpect(queryParam("sort", "recent"))
                .andExpect(queryParam("limit", "5"))
                .andExpect(queryParam("languages", "en"))
                .andExpect(request -> {
                    assertThat(request.getURI().getRawQuery()).contains("since=");
                    assertThat(request.getURI().getRawQuery()).doesNotContain("country=");
                })
                .andRespond(withSuccess("""
                        {"posts":[
                          {"uri":"at://did:plc:abc123/app.bsky.feed.post/post001","record":{"text":"Public climate post","createdAt":"2026-11-07T12:00:00Z"},"author":{"handle":"ignored.example"}},
                          {"uri":"at://did:plc:abc123/app.bsky.feed.post/post001","record":{"text":"Duplicate","createdAt":"2026-11-07T12:00:00Z"}},
                          {"uri":"at://did:plc:abc123/app.bsky.feed.post/post002","record":{"text":"   ","createdAt":"2026-11-07T12:00:00Z"}},
                          {"uri":"javascript:alert(1)","record":{"text":"Unsafe link","createdAt":"2026-11-07T12:00:00Z"}},
                          {"uri":"at://did:plc:abc123/app.bsky.feed.post/post003","record":{"text":"Second public post"},"indexedAt":"2026-11-06T12:00:00Z"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<ProviderSource> results = provider.search(
                "public climate research",
                new ResearchFilters("US", "en", "pw", 5)
        );

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ProviderSource::type).containsOnly(SourceType.DISCUSSION);
        assertThat(results.getFirst().url())
                .isEqualTo("https://bsky.app/profile/did:plc:abc123/post/post001");
        assertThat(results.getFirst().publisher()).isEqualTo("Bluesky public post");
        assertThat(results.getFirst().description()).isEqualTo("Public climate post");
        assertThat(results.getFirst().publishedAt()).isEqualTo(Instant.parse("2026-11-07T12:00:00Z"));
        assertThat(results.get(1).publishedAt()).isEqualTo(Instant.parse("2026-11-06T12:00:00Z"));
        assertThat(results).noneMatch(result -> result.title().contains("ignored.example"));
        server.verify();
    }

    @Test
    void freshnessWindowIsBoundedAndDisabledProviderMakesNoRequest() {
        Instant start = Instant.parse(BlueskyPublicSearchProvider.freshnessStart("py"));
        assertThat(start).isAfter(Instant.now().minusSeconds(366L * 24 * 60 * 60));
        assertThat(start).isBefore(Instant.now().minusSeconds(364L * 24 * 60 * 60));
        assertThat(BlueskyPublicSearchProvider.freshnessStart("unknown")).isNull();

        BlueskyPublicSearchProvider provider = new BlueskyPublicSearchProvider(RestClient.builder().build(), false);
        assertThat(provider.isConfigured()).isFalse();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        provider.search("topic", ResearchFilters.defaults()))
                .isInstanceOf(SearchProviderNotConfiguredException.class);
    }
}
