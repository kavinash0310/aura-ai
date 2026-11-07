package ai.aura.api.cyber;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class NvdProvider {
    public static final String SOURCE_URL = "https://nvd.nist.gov/vuln/detail/";
    private static final String ENDPOINT = "https://services.nvd.nist.gov/rest/json/cves/2.0";
    private static final int MAX_DESCRIPTION_LENGTH = 2_000;
    private static final int MAX_CPE_CRITERIA = 50;
    private static final int MAX_REFERENCES = 10;
    private static final int MAX_CWES = 20;
    private static final int MAX_CACHE_ENTRIES = 500;

    private final RestClient client;
    private final Duration cacheDuration;
    private final String apiKey;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public NvdProvider(
            @Value("${aura.cyber.nvd.timeout:8s}") Duration timeout,
            @Value("${aura.cyber.nvd.cache-duration:6h}") Duration cacheDuration,
            @Value("${aura.cyber.nvd.api-key:}") String apiKey
    ) {
        this(createClient(timeout), cacheDuration, apiKey);
    }

    public NvdProvider(RestClient client, Duration cacheDuration, String apiKey) {
        this.client = client;
        this.cacheDuration = cacheDuration;
        this.apiKey = apiKey == null ? "" : apiKey.strip();
    }

    public synchronized NvdCveData lookup(String cve) {
        String normalized = cve.strip().toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        CacheEntry cached = cache.get(normalized);
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return cached.data();
        }
        if (cached != null) {
            cache.remove(normalized, cached);
        }

        try {
            URI uri = UriComponentsBuilder.fromUriString(ENDPOINT)
                    .queryParam("cveId", normalized)
                    .build()
                    .encode()
                    .toUri();
            RestClient.RequestHeadersSpec<?> request = client.get().uri(uri);
            if (!apiKey.isBlank()) {
                request.header("apiKey", apiKey);
            }
            JsonNode root = request.retrieve().body(JsonNode.class);
            NvdCveData result = parse(normalized, root, Instant.now());
            if (!result.status().equals("UNAVAILABLE") && !result.status().equals("RATE_LIMITED")) {
                cache.put(normalized, new CacheEntry(result, result.retrievedAt().plus(cacheDuration)));
                evictOldestCacheEntries();
            }
            return result;
        } catch (RestClientResponseException exception) {
            String status = exception.getStatusCode().value() == 429 ? "RATE_LIMITED" : "UNAVAILABLE";
            return unavailable(normalized, status, "NVD is temporarily unavailable. No automatic retry was attempted.");
        } catch (RestClientException | IllegalArgumentException exception) {
            return unavailable(normalized, "UNAVAILABLE", "NVD is temporarily unavailable or returned unusable data.");
        }
    }

    private void evictOldestCacheEntries() {
        Iterator<String> keys = cache.keySet().iterator();
        while (cache.size() > MAX_CACHE_ENTRIES && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private static RestClient createClient(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    private static NvdCveData parse(String cve, JsonNode root, Instant retrievedAt) {
        JsonNode vulnerabilities = root == null ? null : root.get("vulnerabilities");
        if (vulnerabilities == null || !vulnerabilities.isArray()) {
            return unavailable(cve, "UNAVAILABLE", "NVD returned an unusable response.");
        }
        JsonNode record = null;
        for (JsonNode item : vulnerabilities) {
            JsonNode candidate = item.path("cve");
            if (candidate.path("id").asText("").equalsIgnoreCase(cve)) {
                record = candidate;
                break;
            }
        }
        if (record == null) {
            return new NvdCveData(cve, "NOT_FOUND", retrievedAt, null, null, null, null,
                    null, null, null, null, List.of(), List.of(), false, List.of(), false,
                    "NVD did not return a record for this CVE ID.");
        }

        Cvss cvss = readCvss(record.path("metrics"));
        List<String> cwes = readCwes(record.path("weaknesses"));
        CpeResults cpe = readCpeCriteria(record.path("configurations"));
        ReferenceResults references = readReferences(record.path("references"));
        return new NvdCveData(
                cve,
                "AVAILABLE",
                retrievedAt,
                text(record, "published"),
                text(record, "lastModified"),
                text(record, "vulnStatus"),
                description(record.path("descriptions")),
                cvss.version(),
                cvss.score(),
                cvss.severity(),
                cvss.vector(),
                cwes,
                cpe.criteria(),
                cpe.truncated(),
                references.references(),
                references.truncated(),
                "NVD describes severity and CPE applicability; these are not proof that a user's software is affected."
        );
    }

    private static Cvss readCvss(JsonNode metrics) {
        String[] keys = {"cvssMetricV40", "cvssMetricV31", "cvssMetricV30", "cvssMetricV2"};
        for (String key : keys) {
            JsonNode values = metrics.path(key);
            if (!values.isArray() || values.isEmpty()) {
                continue;
            }
            JsonNode selected = values.get(0);
            for (JsonNode candidate : values) {
                if (candidate.path("type").asText("").equalsIgnoreCase("Primary")) {
                    selected = candidate;
                    break;
                }
            }
            JsonNode data = selected.path("cvssData");
            BigDecimal score = decimal(data.path("baseScore").asText(""));
            if (score != null) {
                String severity = data.path("baseSeverity").asText(selected.path("baseSeverity").asText(""));
                return new Cvss(
                        data.path("version").asText(""), score, severity,
                        data.path("vectorString").asText("")
                );
            }
        }
        return new Cvss(null, null, null, null);
    }

    private static List<String> readCwes(JsonNode weaknesses) {
        LinkedHashSet<String> cwes = new LinkedHashSet<>();
        if (weaknesses.isArray()) {
            for (JsonNode weakness : weaknesses) {
                JsonNode descriptions = weakness.path("description");
                if (descriptions.isArray()) {
                    for (JsonNode description : descriptions) {
                        String value = description.path("value").asText("");
                        if (value.matches("CWE-[0-9]+")) {
                            cwes.add(value);
                            if (cwes.size() >= MAX_CWES) return List.copyOf(cwes);
                        }
                    }
                }
            }
        }
        return List.copyOf(cwes);
    }

    private static CpeResults readCpeCriteria(JsonNode configurations) {
        ArrayDeque<JsonNode> pending = new ArrayDeque<>();
        if (configurations.isArray()) configurations.forEach(pending::addLast);
        LinkedHashSet<NvdCveData.CpeCriterion> criteria = new LinkedHashSet<>();
        boolean truncated = false;
        while (!pending.isEmpty()) {
            JsonNode node = pending.removeFirst();
            JsonNode matches = node.path("cpeMatch");
            if (matches.isArray()) {
                for (JsonNode match : matches) {
                    String value = match.path("criteria").asText("");
                    if (value.isBlank()) continue;
                    NvdCveData.CpeCriterion criterion = new NvdCveData.CpeCriterion(
                            value,
                            match.path("vulnerable").asBoolean(false),
                            text(match, "versionStartIncluding"),
                            text(match, "versionStartExcluding"),
                            text(match, "versionEndIncluding"),
                            text(match, "versionEndExcluding")
                    );
                    if (!criteria.contains(criterion) && criteria.size() == MAX_CPE_CRITERIA) {
                        truncated = true;
                        break;
                    }
                    criteria.add(criterion);
                }
            }
            JsonNode children = node.path("nodes");
            if (children.isArray()) children.forEach(pending::addLast);
            if (criteria.size() >= MAX_CPE_CRITERIA) {
                truncated = truncated || !pending.isEmpty();
                break;
            }
        }
        return new CpeResults(List.copyOf(criteria), truncated);
    }

    private static ReferenceResults readReferences(JsonNode nodes) {
        List<NvdCveData.Reference> references = new ArrayList<>();
        if (!nodes.isArray()) return new ReferenceResults(List.of(), false);
        for (JsonNode node : nodes) {
            String value = text(node, "url");
            if (value == null || !value.startsWith("https://") || value.length() > 2_048) continue;
            List<String> tags = new ArrayList<>();
            JsonNode tagsNode = node.path("tags");
            if (tagsNode.isArray()) {
                for (JsonNode tag : tagsNode) {
                    if (tags.size() == 5) break;
                    String text = tag.asText("");
                    if (!text.isBlank()) tags.add(text);
                }
            }
            references.add(new NvdCveData.Reference(value, tags));
            if (references.size() == MAX_REFERENCES) {
                return new ReferenceResults(List.copyOf(references), true);
            }
        }
        return new ReferenceResults(List.copyOf(references), false);
    }

    private static String description(JsonNode descriptions) {
        if (!descriptions.isArray()) return null;
        for (JsonNode item : descriptions) {
            if (item.path("lang").asText("").equalsIgnoreCase("en")) {
                String value = text(item, "value");
                if (value == null || value.isBlank()) return null;
                if (value.length() > MAX_DESCRIPTION_LENGTH) {
                    return value.substring(0, MAX_DESCRIPTION_LENGTH);
                }
                return value;
            }
        }
        return null;
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText("");
    }

    private static NvdCveData unavailable(String cve, String status, String note) {
        return new NvdCveData(cve, status, Instant.now(), null, null, null, null,
                null, null, null, null, List.of(), List.of(), false, List.of(), false, note);
    }

    private record CacheEntry(NvdCveData data, Instant expiresAt) {
    }

    private record Cvss(String version, BigDecimal score, String severity, String vector) {
    }

    private record CpeResults(List<NvdCveData.CpeCriterion> criteria, boolean truncated) {
    }

    private record ReferenceResults(List<NvdCveData.Reference> references, boolean truncated) {
    }
}
