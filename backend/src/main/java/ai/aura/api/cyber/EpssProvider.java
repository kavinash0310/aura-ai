package ai.aura.api.cyber;

import tools.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component
public class EpssProvider {
    public static final String SOURCE_URL = "https://api.first.org/epss/";
    private static final String ENDPOINT = "https://api.first.org/data/v1/epss";
    private final RestClient client;

    @Autowired
    public EpssProvider(@Value("${aura.cyber.epss.timeout:8s}") Duration timeout) {
        this(createClient(timeout));
    }

    public EpssProvider(RestClient client) {
        this.client = client;
    }

    private static RestClient createClient(Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }

    public Map<String, EpssScore> lookup(Set<String> cves) {
        String joinedCves = String.join(",", new TreeSet<>(cves));
        var uri = UriComponentsBuilder.fromUriString(ENDPOINT)
                .queryParam("cve", joinedCves)
                .build()
                .encode()
                .toUri();
        JsonNode root;
        try {
            root = client.get().uri(uri).retrieve().body(JsonNode.class);
        } catch (RestClientException exception) {
            throw new CyberDataProviderException("EPSS lookup could not be completed.");
        }
        if (root == null || !root.path("data").isArray()) {
            throw new CyberDataProviderException("EPSS returned an unusable response.");
        }
        Map<String, EpssScore> scores = new HashMap<>();
        try {
            for (JsonNode node : root.path("data")) {
                String cve = node.path("cve").asText("").toUpperCase();
                if (!cves.contains(cve)) {
                    continue;
                }
                BigDecimal score = decimal(node.path("epss").asText(""));
                BigDecimal percentile = decimal(node.path("percentile").asText(""));
                String rawDate = node.hasNonNull("date") ? node.path("date").asText()
                        : node.path("created").asText("");
                LocalDate date = rawDate.isBlank() ? null : LocalDate.parse(rawDate);
                if (score != null && percentile != null && score.signum() >= 0 && score.compareTo(BigDecimal.ONE) <= 0
                        && percentile.signum() >= 0 && percentile.compareTo(BigDecimal.ONE) <= 0) {
                    scores.put(cve, new EpssScore(cve, score, percentile, date));
                }
            }
        } catch (IllegalArgumentException exception) {
            throw new CyberDataProviderException("EPSS returned an unusable response.");
        }
        return Map.copyOf(scores);
    }

    private static BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
