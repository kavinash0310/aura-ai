package ai.aura.api.search;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.SourceType;
import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Component
@Order(2)
public class GdeltNewsSearchProvider implements SearchProvider {
    private static final String ENDPOINT = "https://api.gdeltproject.org/api/v2/doc/doc";
    private static final DateTimeFormatter SEEN_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");

    private final RestClient client;
    private final boolean enabled;

    @Autowired
    public GdeltNewsSearchProvider(
            @Value("${aura.search.gdelt.enabled:true}") boolean enabled,
            @Value("${aura.search.gdelt.timeout:8s}") Duration timeout
    ) {
        this(createClient(timeout), enabled);
    }

    GdeltNewsSearchProvider(RestClient client, boolean enabled) {
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
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    @Override
    public String name() {
        return "gdelt";
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

        URI uri = UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("query", providerQuery(query, filters))
                .queryParam("mode", "artlist")
                .queryParam("format", "json")
                .queryParam("maxrecords", filters.resultLimit())
                .queryParamIfPresent("timespan", Optional.ofNullable(timespan(filters.freshness())))
                .build()
                .encode()
                .toUri();

        JsonNode response;
        try {
            response = client.get()
                    .uri(uri)
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

        if (response == null || !response.path("articles").isArray()) {
            throw new SearchProviderException("SEARCH_PROVIDER_INVALID_RESPONSE", false);
        }

        List<ProviderSource> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (JsonNode item : response.path("articles")) {
            if (results.size() >= filters.resultLimit()) {
                break;
            }
            String title = text(item, "title");
            String rawUrl = text(item, "url");
            if (title.isBlank() || rawUrl.isBlank()) {
                continue;
            }

            URI articleUri;
            try {
                articleUri = URI.create(rawUrl);
            } catch (IllegalArgumentException exception) {
                continue;
            }
            String scheme = articleUri.getScheme();
            String host = articleUri.getHost();
            if (host == null || scheme == null
                    || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
                    || articleUri.getUserInfo() != null) {
                continue;
            }

            String url = removeFragment(articleUri);
            if (!seenUrls.add(url)) {
                continue;
            }
            String domain = host.toLowerCase(Locale.ROOT);
            results.add(new ProviderSource(
                    SourceType.NEWS,
                    limitText(title, 1000),
                    limitText(url, 2048),
                    limitText(domain, 253),
                    null,
                    limitText(domain, 300),
                    parseInstant(text(item, "seendate"))
            ));
        }
        return List.copyOf(results);
    }

    private static String providerQuery(String query, ResearchFilters filters) {
        StringBuilder value = new StringBuilder(query.strip());
        if (StringUtils.hasText(filters.country())) {
            String countryName = new Locale("", filters.country()).getDisplayCountry(Locale.ENGLISH)
                    .replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
            if (!countryName.isBlank()) {
                value.append(" sourcecountry:").append(countryName);
            }
        }
        if (StringUtils.hasText(filters.language())) {
            try {
                String language = new Locale(filters.language()).getISO3Language();
                if (!language.isBlank()) {
                    value.append(" sourcelang:").append(language);
                }
            } catch (java.util.MissingResourceException ignored) {
                // The API filter has already restricted this value to a two-letter code.
            }
        }
        return value.toString();
    }

    static String timespan(String freshness) {
        if (freshness == null) {
            return null;
        }
        return switch (freshness) {
            case "pd" -> "1day";
            case "pw" -> "1week";
            case "pm" -> "1month";
            case "py" -> "3months";
            default -> null;
        };
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
                try {
                    return LocalDateTime.parse(value, SEEN_DATE_FORMAT).toInstant(ZoneOffset.UTC);
                } catch (Exception ignoredLast) {
                    return null;
                }
            }
        }
    }

    private static String removeFragment(URI uri) {
        String normalized = uri.toASCIIString();
        int fragmentStart = normalized.indexOf('#');
        return fragmentStart < 0 ? normalized : normalized.substring(0, fragmentStart);
    }
}
