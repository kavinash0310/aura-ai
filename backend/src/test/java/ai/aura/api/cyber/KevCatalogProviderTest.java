package ai.aura.api.cyber;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class KevCatalogProviderTest {

    @Test
    void readsOnlyTheFixedCisaMirrorAndCachesTheValidatedCatalog() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KevCatalogProvider provider = new KevCatalogProvider(builder.build(), Duration.ofHours(6));

        server.expect(requestTo("https://raw.githubusercontent.com/CISAGov/kev-data/develop/known_exploited_vulnerabilities.json"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "catalogVersion": "2026.11.07",
                          "dateReleased": "2026-11-07",
                          "count": 1,
                          "vulnerabilities": [{
                            "cveID": "CVE-2026-12345",
                            "vendorProject": "Example",
                            "product": "Server",
                            "vulnerabilityName": "Example issue",
                            "dateAdded": "2026-11-06",
                            "dueDate": "2026-11-20",
                            "requiredAction": "Apply the vendor update.",
                            "knownRansomwareCampaignUse": "Unknown"
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        var first = provider.lookup(Set.of("CVE-2026-12345"));
        var cached = provider.lookup(Set.of("CVE-2026-54321"));

        assertThat(first.entries()).containsKey("CVE-2026-12345");
        assertThat(first.entries().get("CVE-2026-12345").vendorProject()).isEqualTo("Example");
        assertThat(first.dateReleased()).hasToString("2026-11-07");
        assertThat(cached.entries()).isEmpty();
        server.verify();
    }

    @Test
    void marksLastGoodCatalogStaleWhenRefreshFails() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KevCatalogProvider provider = new KevCatalogProvider(builder.build(), Duration.ZERO);
        String catalog = """
                {"catalogVersion":"2026.11.07","dateReleased":"2026-11-07","count":1,
                 "vulnerabilities":[{"cveID":"CVE-2026-12345","dateAdded":"2026-11-06","dueDate":"2026-11-20"}]}
                """;
        server.expect(requestTo("https://raw.githubusercontent.com/CISAGov/kev-data/develop/known_exploited_vulnerabilities.json"))
                .andRespond(withSuccess(catalog, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://raw.githubusercontent.com/CISAGov/kev-data/develop/known_exploited_vulnerabilities.json"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        provider.lookup(Set.of("CVE-2026-12345"));
        var stale = provider.lookup(Set.of("CVE-2026-12345"));

        assertThat(stale.stale()).isTrue();
        assertThat(stale.entries()).containsKey("CVE-2026-12345");
        server.verify();
    }
}
