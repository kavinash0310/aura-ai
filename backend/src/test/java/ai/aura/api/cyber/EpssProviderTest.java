package ai.aura.api.cyber;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EpssProviderTest {

    @Test
    void queriesTheOfficialLookupForOnlyRequestedCvesAndValidatesScoreBounds() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        EpssProvider provider = new EpssProvider(builder.build());

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.first.org/data/v1/epss?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("cve", "CVE-2026-12345,CVE-2026-22222"))
                .andRespond(withSuccess("""
                        {"status":"OK","data":[
                          {"cve":"CVE-2026-12345","epss":"0.8","percentile":"0.95","date":"2026-11-07"},
                          {"cve":"CVE-2026-22222","epss":"4.0","percentile":"0.95","date":"2026-11-07"},
                          {"cve":"CVE-2026-99999","epss":"0.9","percentile":"0.99","date":"2026-11-07"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        var scores = provider.lookup(Set.of("CVE-2026-12345", "CVE-2026-22222"));

        assertThat(scores).containsOnlyKeys("CVE-2026-12345");
        assertThat(scores.get("CVE-2026-12345").score()).isEqualByComparingTo(new BigDecimal("0.8"));
        server.verify();
    }
}
