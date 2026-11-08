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

import java.io.IOException;
import java.math.BigDecimal;
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
public class GitHubAdvisoryProvider {
    public static final String SOURCE_URL = "https://github.com/advisories/";
    private static final String ENDPOINT = "https://api.github.com/advisories";
    private static final String API_VERSION = "2026-03-10";
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_ADVISORIES = 10;
    private static final int MAX_DESCRIPTION_LENGTH = 3_000;
    private static final int MAX_CWES = 20;
    private static final int MAX_AFFECTED_PACKAGES = 20;
    private static final int MAX_REFERENCES = 10;
    private static final int MAX_CACHE_ENTRIES = 500;

    private final RestClient client;
    private final Duration cacheDuration;
    private final JsonMapper jsonMapper;
    private final Map<String, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);

    @Autowired
    public GitHubAdvisoryProvider(
            @Value("${aura.cyber.github-advisories.timeout:8s}") Duration timeout,
            @Value("${aura.cyber.github-advisories.cache-duration:6h}") Duration cacheDuration,
            JsonMapper jsonMapper
    ) {
        this(createClient(timeout), cacheDuration, jsonMapper);
    }

    public GitHubAdvisoryProvider(RestClient client, Duration cacheDuration, JsonMapper jsonMapper) {
        this.client = client;
        this.cacheDuration = cacheDuration;
        this.jsonMapper = jsonMapper;
    }

    public synchronized GitHubAdvisoryData lookup(String suppliedCve) {
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
                    .queryParam("cve_id", cve)
                    .queryParam("type", "reviewed")
                    .queryParam("per_page", MAX_ADVISORIES)
                    .build()
                    .encode()
                    .toUri();
            GitHubAdvisoryData result = client.get()
                    .uri(uri)
                    .accept(MediaType.parseMediaType("application/vnd.github+json"))
                    .header("X-GitHub-Api-Version", API_VERSION)
                    .exchange((request, response) -> readResponse(cve, response));
            if (!result.status().equals("UNAVAILABLE") && !result.status().equals("RATE_LIMITED")) {
                cache.put(cve, new CacheEntry(result, result.retrievedAt().plus(cacheDuration)));
                evictOldestEntries();
            }
            return result;
        } catch (RestClientException | IllegalArgumentException | ResponseTooLargeException exception) {
            return unavailable(cve, "UNAVAILABLE",
                    "GitHub Advisory Database is temporarily unavailable or returned a response above AURA's 256 KB limit. No retry was attempted.");
        }
    }

    private GitHubAdvisoryData readResponse(String cve, ClientHttpResponse response) throws IOException {
        int statusCode = response.getStatusCode().value();
        if (statusCode == 429 || (statusCode == 403
                && "0".equals(response.getHeaders().getFirst("X-RateLimit-Remaining")))) {
            return unavailable(cve, "RATE_LIMITED",
                    "GitHub rate-limited the request. No automatic retry was attempted.");
        }
        if (statusCode < 200 || statusCode >= 300) {
            return unavailable(cve, "UNAVAILABLE",
                    "GitHub Advisory Database returned an unavailable response. No retry was attempted.");
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
            return unavailable(cve, "UNAVAILABLE", "GitHub returned unusable advisory data.");
        }
        if (root == null || !root.isArray()) {
            return unavailable(cve, "UNAVAILABLE", "GitHub returned an unusable advisory response.");
        }

        List<GitHubAdvisoryData.Advisory> advisories = new ArrayList<>();
        boolean truncated = hasNextPage(response);
        for (JsonNode item : root) {
            if (!item.isObject() || !matchesCve(item, cve)) {
                continue;
            }
            if (advisories.size() == MAX_ADVISORIES) {
                truncated = true;
                break;
            }
            advisories.add(readAdvisory(item, cve));
        }
        if (advisories.isEmpty()) {
            return new GitHubAdvisoryData(cve, "NOT_FOUND", retrievedAt, List.of(), false,
                    "GitHub returned no reviewed advisory explicitly associated with this CVE ID.");
        }
        return new GitHubAdvisoryData(cve, "AVAILABLE", retrievedAt, advisories, truncated,
                "GitHub-reviewed advisory evidence is shown as published. Package version ranges are not matched against the local inventory.");
    }

    private static boolean matchesCve(JsonNode item, String cve) {
        if (cve.equalsIgnoreCase(text(item, "cve_id"))) {
            return true;
        }
        JsonNode identifiers = item.path("identifiers");
        if (!identifiers.isArray()) {
            return false;
        }
        for (JsonNode identifier : identifiers) {
            if (identifier.path("type").asText("").equalsIgnoreCase("CVE")
                    && cve.equalsIgnoreCase(text(identifier, "value"))) {
                return true;
            }
        }
        return false;
    }

    private static GitHubAdvisoryData.Advisory readAdvisory(JsonNode item, String cve) {
        String ghsaId = text(item, "ghsa_id");
        String sourceUrl = ghsaId.matches("(?i)GHSA-[23456789CFGHJMPQRVWX]{4}-[23456789CFGHJMPQRVWX]{4}-[23456789CFGHJMPQRVWX]{4}")
                ? SOURCE_URL + ghsaId.toUpperCase(Locale.ROOT)
                : SOURCE_URL + "?query=" + cve;
        GitHubAdvisoryData.Cvss cvssV3 = readCvss(item.path("cvss_severities").path("cvss_v3"));
        GitHubAdvisoryData.Cvss cvssV4 = readCvss(item.path("cvss_severities").path("cvss_v4"));
        if (cvssV3 == null) {
            cvssV3 = readCvss(item.path("cvss"));
        }
        CweResults cwes = readCwes(item.path("cwes"));
        PackageResults packages = readAffectedPackages(item.path("vulnerabilities"));
        ReferenceResults references = readReferences(item.path("references"));
        return new GitHubAdvisoryData.Advisory(
                clip(ghsaId, 50),
                cve,
                sourceUrl,
                clip(text(item, "type"), 40),
                clip(text(item, "severity"), 30).toUpperCase(Locale.ROOT),
                clip(text(item, "summary"), 500),
                clip(text(item, "description"), MAX_DESCRIPTION_LENGTH),
                clip(text(item, "published_at"), 64),
                clip(text(item, "updated_at"), 64),
                clip(text(item, "github_reviewed_at"), 64),
                clip(text(item, "withdrawn_at"), 64),
                cvssV3,
                cvssV4,
                cwes.values(),
                cwes.truncated(),
                packages.values(),
                packages.truncated(),
                references.values(),
                references.truncated()
        );
    }

    private static GitHubAdvisoryData.Cvss readCvss(JsonNode node) {
        if (!node.isObject()) {
            return null;
        }
        String vector = text(node, "vector_string");
        BigDecimal score = decimal(node.path("score").asText(""));
        return vector.isBlank() && score == null ? null : new GitHubAdvisoryData.Cvss(clip(vector, 200), score);
    }

    private static CweResults readCwes(JsonNode nodes) {
        if (!nodes.isArray()) {
            return new CweResults(List.of(), false);
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode node = nodes.get(index);
            String id = text(node, "cwe_id");
            if (id.matches("CWE-[0-9]+")) {
                values.add(id);
                if (values.size() == MAX_CWES) {
                    boolean truncated = false;
                    for (int nextIndex = index + 1; nextIndex < nodes.size(); nextIndex++) {
                        String nextId = text(nodes.get(nextIndex), "cwe_id");
                        if (nextId.matches("CWE-[0-9]+") && !values.contains(nextId)) {
                            truncated = true;
                            break;
                        }
                    }
                    return new CweResults(List.copyOf(values), truncated);
                }
            }
        }
        return new CweResults(List.copyOf(values), false);
    }

    private static PackageResults readAffectedPackages(JsonNode nodes) {
        if (!nodes.isArray()) {
            return new PackageResults(List.of(), false);
        }
        List<GitHubAdvisoryData.AffectedPackage> packages = new ArrayList<>();
        boolean truncated = false;
        for (JsonNode node : nodes) {
            JsonNode packageNode = node.path("package");
            if (!packageNode.isObject()) {
                continue;
            }
            if (packages.size() == MAX_AFFECTED_PACKAGES) {
                truncated = true;
                break;
            }
            packages.add(new GitHubAdvisoryData.AffectedPackage(
                    clip(text(packageNode, "ecosystem"), 100),
                    clip(text(packageNode, "name"), 300),
                    clip(text(node, "vulnerable_version_range"), 500),
                    clip(text(node, "first_patched_version"), 200)
            ));
        }
        return new PackageResults(List.copyOf(packages), truncated);
    }

    private static ReferenceResults readReferences(JsonNode nodes) {
        if (!nodes.isArray()) {
            return new ReferenceResults(List.of(), false);
        }
        List<GitHubAdvisoryData.Reference> references = new ArrayList<>();
        boolean truncated = false;
        for (JsonNode node : nodes) {
            String value = node.isTextual() ? node.asText().strip() : "";
            if (!value.startsWith("https://") || value.length() > 2_048) {
                continue;
            }
            if (references.size() == MAX_REFERENCES) {
                truncated = true;
                break;
            }
            references.add(new GitHubAdvisoryData.Reference(value));
        }
        return new ReferenceResults(List.copyOf(references), truncated);
    }

    private static boolean hasNextPage(ClientHttpResponse response) {
        String link = response.getHeaders().getFirst("Link");
        return link != null && link.matches("(?s).*<[^>]+>;\\s*rel=\\\"next\\\".*");
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

    private static BigDecimal decimal(String value) {
        if (value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
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

    private static GitHubAdvisoryData unavailable(String cve, String status, String note) {
        return new GitHubAdvisoryData(cve, status, Instant.now(), List.of(), false, note);
    }

    private record CacheEntry(GitHubAdvisoryData data, Instant expiresAt) {
    }

    private record CweResults(List<String> values, boolean truncated) {
    }

    private record PackageResults(List<GitHubAdvisoryData.AffectedPackage> values, boolean truncated) {
    }

    private record ReferenceResults(List<GitHubAdvisoryData.Reference> values, boolean truncated) {
    }

    private static final class ResponseTooLargeException extends RuntimeException {
    }
}
