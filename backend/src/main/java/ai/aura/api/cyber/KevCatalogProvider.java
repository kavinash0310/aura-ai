package ai.aura.api.cyber;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class KevCatalogProvider {
    public static final String SOURCE_URL = "https://github.com/CISAGov/kev-data/blob/develop/known_exploited_vulnerabilities.json";
    private static final String FEED_URL = "https://raw.githubusercontent.com/CISAGov/kev-data/develop/known_exploited_vulnerabilities.json";

    private final RestClient client;
    private final Duration cacheDuration;
    private volatile KevCatalogSnapshot cached;

    @Autowired
    public KevCatalogProvider(
            @Value("${aura.cyber.kev.timeout:8s}") Duration timeout,
            @Value("${aura.cyber.kev.cache-duration:6h}") Duration cacheDuration
    ) {
        this(createClient(timeout), cacheDuration);
    }

    public KevCatalogProvider(RestClient client, Duration cacheDuration) {
        this.client = client;
        this.cacheDuration = cacheDuration;
    }

    private static RestClient createClient(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    public KevCatalogSnapshot lookup(Set<String> cves) {
        KevCatalogSnapshot current = cached;
        Instant now = Instant.now();
        if (current != null && current.retrievedAt().plus(cacheDuration).isAfter(now)) {
            return subset(current, cves, false);
        }

        synchronized (this) {
            current = cached;
            now = Instant.now();
            if (current != null && current.retrievedAt().plus(cacheDuration).isAfter(now)) {
                return subset(current, cves, false);
            }
            try {
                KevCatalogSnapshot refreshed = fetch();
                cached = refreshed;
                return subset(refreshed, cves, false);
            } catch (RestClientException | IllegalArgumentException exception) {
                if (current != null) {
                    return subset(current, cves, true);
                }
                throw new CyberDataProviderException("KEV catalog could not be loaded.");
            }
        }
    }

    private KevCatalogSnapshot fetch() {
        JsonNode root = client.get().uri(FEED_URL).retrieve().body(JsonNode.class);
        if (root == null || !root.path("vulnerabilities").isArray()) {
            throw new IllegalArgumentException("Invalid KEV catalog response.");
        }

        Map<String, KevEntry> entries = new HashMap<>();
        for (JsonNode node : root.path("vulnerabilities")) {
            String cve = text(node, "cveID").toUpperCase(Locale.ROOT);
            if (!cve.matches("CVE-[0-9]{4}-[0-9]{4,}")) {
                continue;
            }
            entries.put(cve, new KevEntry(
                    cve,
                    text(node, "vendorProject"),
                    text(node, "product"),
                    text(node, "vulnerabilityName"),
                    date(node, "dateAdded"),
                    date(node, "dueDate"),
                    text(node, "requiredAction"),
                    text(node, "knownRansomwareCampaignUse")
            ));
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("KEV catalog did not contain valid entries.");
        }
        int expectedCount = root.path("count").asInt(-1);
        if (expectedCount > 0 && expectedCount != entries.size()) {
            throw new IllegalArgumentException("KEV catalog count does not match its records.");
        }
        LocalDate releaseDate = date(root, "dateReleased");
        String version = text(root, "catalogVersion");
        if (releaseDate == null || version.isBlank()) {
            throw new IllegalArgumentException("KEV catalog metadata is incomplete.");
        }
        return new KevCatalogSnapshot(version, releaseDate, Instant.now(), entries, false);
    }

    private static KevCatalogSnapshot subset(KevCatalogSnapshot snapshot, Set<String> cves, boolean stale) {
        Map<String, KevEntry> matches = new HashMap<>();
        cves.forEach(cve -> {
            KevEntry entry = snapshot.entries().get(cve);
            if (entry != null) {
                matches.put(cve, entry);
            }
        });
        return new KevCatalogSnapshot(snapshot.catalogVersion(), snapshot.dateReleased(), snapshot.retrievedAt(), matches, stale);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static LocalDate date(JsonNode node, String field) {
        String value = text(node, field);
        return value.isBlank() ? null : LocalDate.parse(value);
    }
}
