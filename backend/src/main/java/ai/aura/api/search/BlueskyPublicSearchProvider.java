package ai.aura.api.search;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.SourceType;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(3)
public class BlueskyPublicSearchProvider implements SearchProvider {
    private static final String BASE_URL = "https://public.api.bsky.app";
    private static final String ENDPOINT = "/xrpc/app.bsky.feed.searchPostsV2";
    private static final int MAX_TEXT_LENGTH = 4000;
    private static final Pattern POST_URI = Pattern.compile(
            "^at://(did:[a-z0-9]+:[A-Za-z0-9._:-]+|[A-Za-z0-9.-]+)/app\\.bsky\\.feed\\.post/([A-Za-z0-9]{1,64})$"
    );

    private final RestClient client;
    private final boolean enabled;

    @Autowired
    public BlueskyPublicSearchProvider(
            @Value("${aura.search.bluesky.enabled:true}") boolean enabled,
            @Value("${aura.search.bluesky.timeout:8s}") Duration timeout
    ) {
        this(createClient(timeout), enabled);
    }

    BlueskyPublicSearchProvider(RestClient client, boolean enabled) {
        this.client = client;
        this.enabled = enabled;
    }

    private static RestClient createClient(Duration timeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String name() {
        return "bluesky";
    }

    @Override
    public boolean isConfigured() {
        return enabled;
    }

    @Override
    public List<ProviderSource> search(String query, ResearchFilters filters) {
        if (!isConfigured()) {
            throw new SearchProviderNotConfiguredException();
        }

        UriComponentsBuilder request = UriComponentsBuilder.fromUriString(BASE_URL + ENDPOINT)
                .queryParam("query", query)
                .queryParam("sort", "recent")
                .queryParam("limit", filters.resultLimit());
        if (filters.language() != null) {
            request.queryParam("languages", filters.language());
        }
        if (filters.freshness() != null) {
            request.queryParamIfPresent("since", Optional.ofNullable(freshnessStart(filters.freshness())));
        }

        JsonNode response;
        try {
            response = client.get()
                    .uri(request.build().encode().toUri())
                    .header("Accept", "application/json")
                    .header("User-Agent", "AURA-AI-local-research/0.1")
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException exception) {
            String code = exception.getStatusCode().value() == 429
                    ? "SEARCH_PROVIDER_RATE_LIMITED"
                    : "SEARCH_PROVIDER_FAILURE";
            throw new SearchProviderException(code, exception.getStatusCode().is5xxServerError()
                    || exception.getStatusCode().value() == 429);
        } catch (RestClientException exception) {
            throw new SearchProviderException("SEARCH_PROVIDER_UNAVAILABLE", true);
        }

        if (response == null || !response.path("posts").isArray()) {
            throw new SearchProviderException("SEARCH_PROVIDER_INVALID_RESPONSE", false);
        }

        List<ProviderSource> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (JsonNode post : response.path("posts")) {
            if (results.size() >= filters.resultLimit()) {
                break;
            }

            String text = cleanText(text(post.path("record"), "text"));
            String postUrl = postUrl(text(post, "uri"));
            if (text.isBlank() || postUrl.isBlank() || !seenUrls.add(postUrl)) {
                continue;
            }

            results.add(new ProviderSource(
                    SourceType.DISCUSSION,
                    limitText(text, 1000),
                    postUrl,
                    "bsky.app",
                    limitText(text, MAX_TEXT_LENGTH),
                    "Bluesky public post",
                    parseInstant(text(post.path("record"), "createdAt"), text(post, "indexedAt"))
            ));
        }
        return List.copyOf(results);
    }

    static String freshnessStart(String freshness) {
        Duration age = switch (freshness) {
            case "pd" -> Duration.ofDays(1);
            case "pw" -> Duration.ofDays(7);
            case "pm" -> Duration.ofDays(30);
            case "py" -> Duration.ofDays(365);
            default -> null;
        };
        return age == null ? null : Instant.now().minus(age).toString();
    }

    private static String postUrl(String postUri) {
        Matcher matcher = POST_URI.matcher(postUri);
        if (!matcher.matches()) {
            return "";
        }
        return "https://bsky.app/profile/" + matcher.group(1) + "/post/" + matcher.group(2);
    }

    private static String cleanText(String value) {
        return value.replaceAll("[\\p{Cc}&&[^\\t\\n\\r]]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String limitText(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static Instant parseInstant(String createdAt, String indexedAt) {
        for (String value : List.of(createdAt, indexedAt)) {
            if (value.isBlank()) {
                continue;
            }
            try {
                return Instant.parse(value);
            } catch (Exception ignored) {
                try {
                    return OffsetDateTime.parse(value).toInstant();
                } catch (Exception ignoredAgain) {
                    // Try the indexed timestamp if the record timestamp is malformed.
                }
            }
        }
        return null;
    }
}
