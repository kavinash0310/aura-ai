package ai.aura.api.cyber;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GitHubAdvisoryProviderTest {
    private static final String CVE = "CVE-2026-12345";

    @Test
    void fetchesOneExactCveParsesBoundedReviewedEvidenceAndCachesIt() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubAdvisoryProvider provider = new GitHubAdvisoryProvider(
                builder.build(), Duration.ofHours(6), JsonMapper.builder().build());

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.github.com/advisories?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("cve_id", CVE))
                .andExpect(queryParam("type", "reviewed"))
                .andExpect(queryParam("per_page", "10"))
                .andExpect(header("X-GitHub-Api-Version", "2026-03-10"))
                .andRespond(withSuccess("""
                        [{"ghsa_id":"GHSA-7fhm-mqm4-2wp7","cve_id":"CVE-2026-12345","type":"reviewed","severity":"high",
                          "summary":"Example advisory","description":"Bounded description","published_at":"2026-01-01T00:00:00Z",
                          "updated_at":"2026-01-02T00:00:00Z","github_reviewed_at":"2026-01-02T00:00:00Z","withdrawn_at":null,
                          "cvss":{"vector_string":"CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H","score":9.8},
                          "cvss_severities":{"cvss_v3":{"vector_string":"CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H","score":9.8},"cvss_v4":{"vector_string":"CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N","score":9.3}},
                          "cwes":[{"cwe_id":"CWE-79","name":"Cross-site Scripting"},{"cwe_id":"not-a-cwe","name":"Ignored"}],
                          "vulnerabilities":[{"package":{"ecosystem":"npm","name":"example"},"vulnerable_version_range":"< 2.0.0","first_patched_version":"2.0.0"}],
                          "references":["https://github.com/example/project/security/advisories/GHSA-7fhm-mqm4-2wp7","javascript:alert(1)"]},
                         {"ghsa_id":"GHSA-abcd-1234-efgh","cve_id":"CVE-2026-99999","summary":"Wrong CVE must not leak"}]
                        """, MediaType.APPLICATION_JSON));

        GitHubAdvisoryData data = provider.lookup(CVE.toLowerCase());
        GitHubAdvisoryData cached = provider.lookup(CVE);

        assertThat(data.status()).isEqualTo("AVAILABLE");
        assertThat(data.advisories()).hasSize(1);
        var advisory = data.advisories().getFirst();
        assertThat(advisory.ghsaId()).isEqualTo("GHSA-7fhm-mqm4-2wp7");
        assertThat(advisory.sourceUrl()).isEqualTo("https://github.com/advisories/GHSA-7FHM-MQM4-2WP7");
        assertThat(advisory.severity()).isEqualTo("HIGH");
        assertThat(advisory.cvssV4().score()).isEqualByComparingTo("9.3");
        assertThat(advisory.cwes()).containsExactly("CWE-79");
        assertThat(advisory.cwesTruncated()).isFalse();
        assertThat(advisory.affectedPackages()).containsExactly(new GitHubAdvisoryData.AffectedPackage(
                "npm", "example", "< 2.0.0", "2.0.0"));
        assertThat(advisory.references()).hasSize(1);
        assertThat(advisory.references().getFirst().url()).startsWith("https://");
        assertThat(cached.retrievedAt()).isEqualTo(data.retrievedAt());
        server.verify();
    }

    @Test
    void refusesToReturnAdvisoryThatOnlyMentionsAnotherCve() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubAdvisoryProvider provider = new GitHubAdvisoryProvider(
                builder.build(), Duration.ofHours(6), JsonMapper.builder().build());
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.github.com/advisories?")))
                .andRespond(withSuccess("""
                        [{"ghsa_id":"GHSA-7fhm-mqm4-2wp7","cve_id":"CVE-2026-99999","summary":"Not the requested CVE"}]
                        """, MediaType.APPLICATION_JSON));

        GitHubAdvisoryData result = provider.lookup(CVE);

        assertThat(result.status()).isEqualTo("NOT_FOUND");
        assertThat(result.advisories()).isEmpty();
        server.verify();
    }

    @Test
    void returnsRateLimitedWithoutRetrying() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GitHubAdvisoryProvider provider = new GitHubAdvisoryProvider(
                builder.build(), Duration.ofHours(6), JsonMapper.builder().build());
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://api.github.com/advisories?")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        GitHubAdvisoryData result = provider.lookup(CVE);

        assertThat(result.status()).isEqualTo("RATE_LIMITED");
        assertThat(result.note()).contains("No automatic retry");
        server.verify();
    }
}
