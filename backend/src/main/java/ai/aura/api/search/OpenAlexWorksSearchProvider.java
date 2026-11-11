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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Component
@Order(4)
public class OpenAlexWorksSearchProvider implements SearchProvider {
    private static final String BASE_URL = "https://api.openalex.org";
    private static final String ENDPOINT = "/works";
    private static final int MAX_ABSTRACT_LENGTH = 4000;
    private static final int MAX_ABSTRACT_POSITIONS = 4000;
    private static final int MAX_INDEX_TERMS = 10_000;
    private static final Pattern WORK_PATH = Pattern.compile("^/W[0-9]+$");

    private final RestClient client;
    private final String apiKey;

    @Autowired
    public OpenAlexWorksSearchProvider(
            @Value("${aura.search.openalex.api-key:}") String apiKey,
            @Value("${aura.search.openalex.timeout:8s}") Duration timeout
    ) {
        this(createClient(timeout), apiKey);
    }

    OpenAlexWorksSearchProvider(RestClient client, String apiKey) {
        this.client = client;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
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
        return "openalex";
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    @Override
    public boolean isAutomaticDefault() {
        return false;
    }

    @Override
    public List<ProviderSource> search(String query, ResearchFilters filters) {
        if (!isConfigured()) {
            throw new SearchProviderNotConfiguredException();
        }

        URI uri = UriComponentsBuilder.fromUriString(BASE_URL + ENDPOINT)
                .queryParam("search.title_abstract_keywords", query)
                .queryParam("per_page", filters.resultLimit())
                .queryParam("select", "id,title,publication_date,authorships,abstract_inverted_index")
                .queryParamIfPresent("filter", Optional.ofNullable(publicationDateFilter(filters.freshness())))
                .build()
                .encode()
                .toUri();

        JsonNode response;
        try {
            response = client.get()
                    .uri(uri)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
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

        if (response == null || !response.path("results").isArray()) {
            throw new SearchProviderException("SEARCH_PROVIDER_INVALID_RESPONSE", false);
        }

        List<ProviderSource> results = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();
        for (JsonNode work : response.path("results")) {
            if (results.size() >= filters.resultLimit()) {
                break;
            }

            String title = cleanText(text(work, "title"));
            String url = workUrl(text(work, "id"));
            if (title.isBlank() || url.isBlank() || !seenUrls.add(url)) {
                continue;
            }

            List<String> authors = authorNames(work.path("authorships"));
            String publisher = authors.isEmpty()
                    ? "OpenAlex scholarly work"
                    : "OpenAlex · " + String.join(", ", authors);
            results.add(new ProviderSource(
                    SourceType.WEB,
                    limitText(title, 1000),
                    url,
                    "openalex.org",
                    reconstructAbstract(work.path("abstract_inverted_index")),
                    limitText(publisher, 300),
                    parsePublicationDate(text(work, "publication_date"))
            ));
        }
        return List.copyOf(results);
    }

    private static String publicationDateFilter(String freshness) {
        if (freshness == null) {
            return null;
        }
        long days = switch (freshness) {
            case "pd" -> 1;
            case "pw" -> 7;
            case "pm" -> 30;
            case "py" -> 365;
            default -> 0;
        };
        return days == 0 ? null : "from_publication_date:"
                + LocalDate.now(ZoneOffset.UTC).minusDays(days).toString();
    }

    private static String workUrl(String value) {
        try {
            URI uri = URI.create(value);
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || !"openalex.org".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null
                    || uri.getPort() != -1
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || path == null
                    || !WORK_PATH.matcher(path).matches()) {
                return "";
            }
            return "https://openalex.org" + path;
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private static List<String> authorNames(JsonNode authorships) {
        if (!authorships.isArray()) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode authorship : authorships) {
            if (names.size() >= 3) {
                break;
            }
            String name = cleanText(text(authorship.path("author"), "display_name"));
            if (!name.isBlank()) {
                names.add(limitText(name, 100));
            }
        }
        return List.copyOf(names);
    }

    private static String reconstructAbstract(JsonNode invertedIndex) {
        if (!invertedIndex.isObject()) {
            return null;
        }

        TreeMap<Integer, String> wordsByPosition = new TreeMap<>();
        int termCount = 0;
        for (var term : invertedIndex.properties()) {
            if (termCount++ >= MAX_INDEX_TERMS || wordsByPosition.size() >= MAX_ABSTRACT_POSITIONS) {
                break;
            }
            String word = cleanText(term.getKey());
            JsonNode positions = term.getValue();
            if (word.isBlank() || word.length() > 100 || !positions.isArray()) {
                continue;
            }
            for (JsonNode position : positions) {
                if (position.canConvertToInt()) {
                    int index = position.intValue();
                    if (index >= 0 && index < MAX_ABSTRACT_POSITIONS) {
                        wordsByPosition.putIfAbsent(index, word);
                    }
                }
                if (wordsByPosition.size() >= MAX_ABSTRACT_POSITIONS) {
                    break;
                }
            }
        }

        StringBuilder abstractText = new StringBuilder();
        for (String word : wordsByPosition.values()) {
            if (abstractText.length() > 0) {
                abstractText.append(' ');
            }
            abstractText.append(word);
            if (abstractText.length() >= MAX_ABSTRACT_LENGTH) {
                abstractText.setLength(MAX_ABSTRACT_LENGTH);
                break;
            }
        }
        String normalized = cleanText(abstractText.toString());
        return normalized.isBlank() ? null : normalized;
    }

    private static Instant parsePublicationDate(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static String cleanText(String value) {
        return value.replaceAll("[\\p{Cc}&&[^\\t\\n\\r]]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String limitText(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
