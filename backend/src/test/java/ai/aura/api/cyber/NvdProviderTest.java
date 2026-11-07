package ai.aura.api.cyber;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NvdProviderTest {

    @Test
    void looksUpOneExactCveParsesNvdEvidenceAndCachesTheResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NvdProvider provider = new NvdProvider(builder.build(), Duration.ofHours(6), "test-nvd-key");

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://services.nvd.nist.gov/rest/json/cves/2.0?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("cveId", "CVE-2021-44228"))
                .andExpect(header("apiKey", "test-nvd-key"))
                .andRespond(withSuccess("""
                        {"totalResults":1,"vulnerabilities":[{"cve":{
                          "id":"CVE-2021-44228","published":"2021-12-10T10:15:09.143","lastModified":"2026-08-11T19:33:44.513","vulnStatus":"Analyzed",
                          "descriptions":[{"lang":"en","value":"Example description."}],
                          "metrics":{"cvssMetricV31":[{"source":"nvd@nist.gov","type":"Primary","cvssData":{"version":"3.1","vectorString":"CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:C/C:H/I:H/A:H","baseScore":10.0,"baseSeverity":"CRITICAL"}}]},
                          "weaknesses":[{"description":[{"lang":"en","value":"CWE-917"}]}],
                          "configurations":[{"nodes":[{"cpeMatch":[{"criteria":"cpe:2.3:a:apache:log4j:2.0:*:*:*:*:*:*:*","vulnerable":true,"versionEndExcluding":"2.16.0"}]}]}],
                          "references":[{"url":"https://logging.apache.org/log4j/2.x/security.html","tags":["Vendor Advisory"]},{"url":"javascript:alert(1)","tags":[]}]
                        }}]}
                        """, MediaType.APPLICATION_JSON));

        NvdCveData data = provider.lookup("cve-2021-44228");
        NvdCveData cached = provider.lookup("CVE-2021-44228");

        assertThat(data.status()).isEqualTo("AVAILABLE");
        assertThat(data.cvssVersion()).isEqualTo("3.1");
        assertThat(data.cvssScore()).isEqualByComparingTo("10.0");
        assertThat(data.cvssSeverity()).isEqualTo("CRITICAL");
        assertThat(data.cwes()).containsExactly("CWE-917");
        assertThat(data.applicability()).containsExactly(new NvdCveData.CpeCriterion(
                "cpe:2.3:a:apache:log4j:2.0:*:*:*:*:*:*:*", true, null, null, null, "2.16.0"));
        assertThat(data.references()).hasSize(1);
        assertThat(data.references().getFirst().url()).startsWith("https://");
        assertThat(cached.retrievedAt()).isEqualTo(data.retrievedAt());
        server.verify();
    }

    @Test
    void surfacesRateLimitingWithoutRetrying() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        NvdProvider provider = new NvdProvider(builder.build(), Duration.ofHours(6), "");
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://services.nvd.nist.gov/rest/json/cves/2.0?")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        NvdCveData result = provider.lookup("CVE-2026-12345");

        assertThat(result.status()).isEqualTo("RATE_LIMITED");
        assertThat(result.note()).contains("No automatic retry");
        server.verify();
    }
}
