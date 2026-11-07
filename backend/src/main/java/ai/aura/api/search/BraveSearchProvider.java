package ai.aura.api.search;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.SourceType;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
public class BraveSearchProvider implements SearchProvider {
    private static final String BASE_URL = "https://api.search.brave.com";
    private static final String ENDPOINT = "/res/v1/web/search";
    private static final int MAX_TEXT_LENGTH = 4000;

    private final RestClient client;
    private final String apiKey;

    @Autowired
    public BraveSearchProvider(
            @Value("${aura.search.brave.api-key:}") String apiKey,
            @Value("${aura.search.brave.timeout:8s}") Duration timeout
    ) {
        this(createClient(timeout), apiKey);
    }

    BraveSearchProvider(RestClient client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    private static RestClient createClient(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder()
                .baseUrl(BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public String name() {
        return "brave";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    @Override
    public List<ProviderSource> search(String query, ResearchFilters filters) {
        if (!isConfigured()) {
            throw new SearchProviderNotConfiguredException();
        }

        URI uri = UriComponentsBuilder.fromUriString(BASE_URL + ENDPOINT)
                .queryParam("q", query)
                .queryParam("count", filters.resultLimit())
                .queryParam("safesearch", "strict")
                .queryParam("result_filter", "web,news,discussions")
                .queryParamIfPresent("country", Optional.ofNullable(filters.country()))
                .queryParamIfPresent("search_lang", Optional.ofNullable(filters.language()))
                .queryParamIfPresent("freshness", Optional.ofNullable(filters.freshness()))
                .build()
                .encode()
                .toUri();

        JsonNode response;
        try {
            response = client.get()
                    .uri(uri)
                    .header("Accept", "application/json")
                    .header("X-Subscription-Token", apiKey)
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

        if (response == null) {
            throw new SearchProviderException("SEARCH_PROVIDER_INVALID_RESPONSE", false);
        }

        List<ProviderSource> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        appendResults(response.path("web"), SourceType.WEB, filters.resultLimit(), results, seenUrls);
        appendResults(response.path("news"), SourceType.NEWS, filters.resultLimit(), results, seenUrls);
        appendResults(response.path("discussions"), SourceType.DISCUSSION, filters.resultLimit(), results, seenUrls);
        return List.copyOf(results);
    }

    private static void appendResults(
            JsonNode container,
            SourceType type,
            int limit,
            List<ProviderSource> results,
            Set<String> seenUrls
    ) {
        JsonNode items = container.path("results");
        if (!items.isArray()) {
            return;
        }

        int acceptedForType = 0;
        for (JsonNode item : items) {
            if (acceptedForType >= limit || results.size() >= limit * 3) {
                break;
            }

            String title = text(item, "title");
            String rawUrl = text(item, "url");
            if (title.isBlank() || rawUrl.isBlank()) {
                continue;
            }

            URI uri;
            try {
                uri = URI.create(rawUrl);
            } catch (IllegalArgumentException exception) {
                continue;
            }

            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || scheme == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                continue;
            }

            String url = removeFragment(uri);
            if (!seenUrls.add(url)) {
                continue;
            }

            String domain = host.toLowerCase(Locale.ROOT);
            String description = limitText(text(item, "description"), MAX_TEXT_LENGTH);
            String publisher = text(item, "source");
            if (publisher.isBlank()) {
                publisher = domain;
            }
            Instant publishedAt = parseInstant(text(item, "page_age"));

            results.add(new ProviderSource(
                    type,
                    limitText(title, 1000),
                    limitText(url, 2048),
                    limitText(domain, 253),
                    description,
                    limitText(publisher, 300),
                    publishedAt
            ));
            acceptedForType++;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String limitText(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static Instant parseInstant(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private static String removeFragment(URI uri) {
        String normalized = uri.toASCIIString();
        int fragmentStart = normalized.indexOf('#');
        return fragmentStart < 0 ? normalized : normalized.substring(0, fragmentStart);
    }
}


