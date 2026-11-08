package ai.aura.api.cyber;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OsvProvider {
    public static final String SOURCE_URL = "https://osv.dev/vulnerability/";
    private static final String ENDPOINT = "https://api.osv.dev/v1/vulns/";
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_TEXT_LENGTH = 5_000;
    private static final int MAX_ALIAS_COUNT = 20;
    private static final int MAX_AFFECTED_PACKAGES = 20;
    private static final int MAX_PACKAGE_RANGES = 10;
    private static final int MAX_REFERENCES = 10;
    private static final int MAX_CACHE_ENTRIES = 500;

    private final RestClient client;
    private final JsonMapper jsonMapper;
    private final Duration cacheDuration;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public OsvProvider(
            @Value("${aura.cyber.osv.timeout:8s}") Duration timeout,
            @Value("${aura.cyber.osv.cache-duration:6h}") Duration cacheDuration,
            JsonMapper jsonMapper
    ) {
        this(createClient(timeout), cacheDuration, jsonMapper);
    }

    public OsvProvider(RestClient client, Duration cacheDuration, JsonMapper jsonMapper) {
        this.client = client;
        this.cacheDuration = cacheDuration;
        this.jsonMapper = jsonMapper;
    }

    public synchronized OsvVulnerabilityData lookup(String suppliedCve) {
        String cve = suppliedCve.strip().toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        CacheEntry cached = cache.get(cve);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.data();
        }
        if (cached != null) {
            cache.remove(cve, cached);
        }

        try {
            URI uri = UriComponentsBuilder.fromUriString(ENDPOINT)
                    .pathSegment(cve)
                    .build()
                    .encode()
                    .toUri();
            OsvVulnerabilityData result = client.get()
                    .uri(uri)
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, response) -> readResponse(cve, response));
            if (!result.status().equals("UNAVAILABLE") && !result.status().equals("RATE_LIMITED")) {
                cache.put(cve, new CacheEntry(result, result.retrievedAt().plus(cacheDuration)));
                evictOldestEntries();
            }
            return result;
        } catch (RestClientException | IllegalArgumentException | ResponseTooLargeException exception) {
            return unavailable(cve, "UNAVAILABLE",
                    "OSV is temporarily unavailable or returned a response above AURA's 256 KB limit. No retry was attempted.");
        }
    }

    private OsvVulnerabilityData readResponse(String cve, ClientHttpResponse response) throws IOException {
        int statusCode = response.getStatusCode().value();
        if (statusCode == 404) {
            return notFound(cve, "OSV has no record for this exact CVE ID.");
        }
        if (statusCode == 429) {
            return unavailable(cve, "RATE_LIMITED", "OSV rate-limited the request. No automatic retry was attempted.");
        }
        if (statusCode < 200 || statusCode >= 300) {
            return unavailable(cve, "UNAVAILABLE", "OSV returned an unavailable response. No retry was attempted.");
        }
        long contentLength = response.getHeaders().getContentLength();
        if (contentLength > MAX_RESPONSE_BYTES) {
            throw new ResponseTooLargeException();
        }
        byte[] body = response.getBody().readNBytes(MAX_RESPONSE_BYTES + 1);
        if (body.length > MAX_RESPONSE_BYTES) {
            throw new ResponseTooLargeException();
        }
        Instant retrievedAt = Instant.now();
        JsonNode root;
        try {
            root = jsonMapper.readTree(body);
        } catch (JacksonException exception) {
            return unavailable(cve, "UNAVAILABLE", "OSV returned unusable advisory data.");
        }
        if (root == null || !root.isObject()) {
            return unavailable(cve, "UNAVAILABLE", "OSV returned unusable advisory data.");
        }

        String osvId = text(root, "id");
        List<String> aliases = boundedStrings(root.path("aliases"), MAX_ALIAS_COUNT);
        boolean matchingCve = cve.equalsIgnoreCase(osvId) || aliases.stream().anyMatch(cve::equalsIgnoreCase);
        if (!matchingCve) {
            return notFound(cve, "OSV returned no advisory explicitly associated with this CVE ID.");
        }

        PackageResults affected = readAffectedPackages(root.path("affected"));
        ReferenceResults references = readReferences(root.path("references"));
        return new OsvVulnerabilityData(
                cve,
                "AVAILABLE",
                retrievedAt,
                clip(osvId, 200),
                clip(text(root, "summary"), 500),
                clip(text(root, "details"), MAX_TEXT_LENGTH),
                clip(text(root, "published"), 64),
                clip(text(root, "modified"), 64),
                clip(text(root, "withdrawn"), 64),
                aliases,
                affected.packages(),
                affected.truncated(),
                references.references(),
                references.truncated(),
                "OSV aggregates public vulnerability records. Advisory package ranges are source evidence, not proof that a user's software is affected. AURA does not match packages automatically."
        );
    }

    private static PackageResults readAffectedPackages(JsonNode affectedNode) {
        if (!affectedNode.isArray()) {
            return new PackageResults(List.of(), false);
        }
        List<OsvVulnerabilityData.AffectedPackage> packages = new ArrayList<>();
        boolean truncated = false;
        for (JsonNode affected : affectedNode) {
            if (!affected.isObject()) {
                continue;
            }
            if (packages.size() == MAX_AFFECTED_PACKAGES) {
                truncated = true;
                break;
            }
            JsonNode packageNode = affected.path("package");
            if (!packageNode.isObject()) {
                continue;
            }
            packages.add(new OsvVulnerabilityData.AffectedPackage(
                    clip(text(packageNode, "name"), 300),
                    clip(text(packageNode, "ecosystem"), 100),
                    clip(text(packageNode, "purl"), 500),
                    readVersionRanges(affected.path("ranges"))
            ));
        }
        return new PackageResults(packages, truncated);
    }

    private static List<String> readVersionRanges(JsonNode rangesNode) {
        if (!rangesNode.isArray()) {
            return List.of();
        }
        List<String> ranges = new ArrayList<>();
        for (JsonNode range : rangesNode) {
            if (!range.isObject() || ranges.size() == MAX_PACKAGE_RANGES) {
                break;
            }
            StringBuilder description = new StringBuilder(clip(text(range, "type"), 40));
            JsonNode events = range.path("events");
            if (events.isArray()) {
                for (JsonNode event : events) {
                    if (!event.isObject()) {
                        continue;
                    }
                    for (String key : List.of("introduced", "fixed", "last_affected", "limit")) {
                        String value = text(event, key);
                        if (!value.isBlank()) {
                            if (!description.isEmpty()) description.append(" · ");
                            description.append(key.replace('_', ' ')).append(' ').append(clip(value, 120));
                        }
                    }
                }
            }
            ranges.add(clip(description.toString(), 500));
        }
        return List.copyOf(ranges);
    }

    private static ReferenceResults readReferences(JsonNode referencesNode) {
        if (!referencesNode.isArray()) {
            return new ReferenceResults(List.of(), false);
        }
        List<OsvVulnerabilityData.Reference> references = new ArrayList<>();
        boolean truncated = false;
        for (JsonNode reference : referencesNode) {
            String url = text(reference, "url");
            if (!url.startsWith("https://") || url.length() > 2_048) {
                continue;
            }
            if (references.size() == MAX_REFERENCES) {
                truncated = true;
                break;
            }
            references.add(new OsvVulnerabilityData.Reference(
                    clip(text(reference, "type"), 50), url));
        }
        return new ReferenceResults(references, truncated);
    }

    private static List<String> boundedStrings(JsonNode values, int maximum) {
        if (!values.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> results = new LinkedHashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) {
                continue;
            }
            results.add(clip(value.asText(), 200));
            if (results.size() == maximum) {
                break;
            }
        }
        return List.copyOf(results);
    }

    private void evictOldestEntries() {
        Iterator<String> keys = cache.keySet().iterator();
        while (cache.size() > MAX_CACHE_ENTRIES && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
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

    private static String text(JsonNode node, String property) {
        JsonNode value = node.path(property);
        return value.isTextual() ? value.asText().strip() : "";
    }

    private static String clip(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value == null ? "" : value;
        }
        return value.substring(0, maximum);
    }

    private static OsvVulnerabilityData notFound(String cve, String note) {
        return empty(cve, "NOT_FOUND", note);
    }

    private static OsvVulnerabilityData unavailable(String cve, String status, String note) {
        return empty(cve, status, note);
    }

    private static OsvVulnerabilityData empty(String cve, String status, String note) {
        return new OsvVulnerabilityData(cve, status, Instant.now(), null, null, null,
                null, null, null, List.of(), List.of(), false, List.of(), false, note);
    }

    private record CacheEntry(OsvVulnerabilityData data, Instant expiresAt) {
    }

    private record PackageResults(List<OsvVulnerabilityData.AffectedPackage> packages, boolean truncated) {
    }

    private record ReferenceResults(List<OsvVulnerabilityData.Reference> references, boolean truncated) {
    }

    private static final class ResponseTooLargeException extends RuntimeException {
    }
}
