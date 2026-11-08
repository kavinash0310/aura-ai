package ai.aura.api.cyber;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OsvProviderTest {
    private static final String CVE = "CVE-2026-12345";

    @Test
    void preservesBoundedStructuredVersionEvidenceForLocalAssessment() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsvProvider provider = new OsvProvider(builder.build(), Duration.ofHours(6), JsonMapper.builder().build());
        server.expect(requestTo("https://api.osv.dev/v1/vulns/" + CVE))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {
                          "id":"GHSA-7fhm-mqm4-2wp7",
                          "aliases":["CVE-2026-12345"],
                          "affected":[{
                            "package":{"name":"demo","ecosystem":"npm","purl":"pkg:npm/demo"},
                            "versions":["1.0.0-beta.1"],
                            "ranges":[
                              {"type":"SEMVER","events":[{"introduced":"1.0.0"},{"fixed":"2.0.0"}]},
                              {"type":"SEMVER","events":[{"introduced":"3.0.0"},{"last_affected":"3.5.0"}]}
                            ]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        OsvVulnerabilityData data = provider.lookup(CVE);

        assertThat(data.status()).isEqualTo("AVAILABLE");
        assertThat(data.affectedPackages()).hasSize(1);
        var affected = data.affectedPackages().getFirst();
        assertThat(affected.affectedVersions()).containsExactly("1.0.0-beta.1");
        assertThat(affected.affectedVersionsTruncated()).isFalse();
        assertThat(affected.versionRangeDetails()).containsExactly(
                new OsvVulnerabilityData.VersionRange("SEMVER", java.util.List.of(
                        new OsvVulnerabilityData.VersionEvent("1.0.0", null, null, null, true),
                        new OsvVulnerabilityData.VersionEvent(null, "2.0.0", null, null, true))),
                new OsvVulnerabilityData.VersionRange("SEMVER", java.util.List.of(
                        new OsvVulnerabilityData.VersionEvent("3.0.0", null, null, null, true),
                        new OsvVulnerabilityData.VersionEvent(null, null, "3.5.0", null, true))));
        assertThat(affected.versionRanges()).containsExactly(
                "SEMVER · introduced 1.0.0 · fixed 2.0.0",
                "SEMVER · introduced 3.0.0 · last affected 3.5.0");
        assertThat(affected.versionRangesTruncated()).isFalse();
        server.verify();
    }

    @Test
    void marksAffectedVersionAndRangeEvidenceTruncatedAtIndependentCaps() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsvProvider provider = new OsvProvider(builder.build(), Duration.ofHours(6), JsonMapper.builder().build());
        String versions = IntStream.range(0, 101).mapToObj(index -> "\"1.0." + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        String events = IntStream.range(0, 51).mapToObj(index -> "{\"introduced\":\"1.0." + index + "\"}")
                .collect(java.util.stream.Collectors.joining(","));
        String body = """
                {"id":"GHSA-7fhm-mqm4-2wp7","aliases":["CVE-2026-12345"],"affected":[{
                  "package":{"name":"demo","ecosystem":"npm","purl":"pkg:npm/demo"},
                  "versions":[%s],"ranges":[{"type":"SEMVER","events":[%s]}]
                }]}
                """.formatted(versions, events);
        server.expect(requestTo("https://api.osv.dev/v1/vulns/" + CVE))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        OsvVulnerabilityData data = provider.lookup(CVE);
        var affected = data.affectedPackages().getFirst();

        assertThat(affected.affectedVersions()).hasSize(100);
        assertThat(affected.affectedVersionsTruncated()).isTrue();
        assertThat(affected.versionRangeDetails().getFirst().events()).hasSize(50);
        assertThat(affected.versionRangesTruncated()).isTrue();
        server.verify();
    }

    @Test
    void marksMalformedVersionEvidenceAsIncomplete() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OsvProvider provider = new OsvProvider(builder.build(), Duration.ofHours(6), JsonMapper.builder().build());
        server.expect(requestTo("https://api.osv.dev/v1/vulns/" + CVE))
                .andRespond(withSuccess("""
                        {"id":"GHSA-7fhm-mqm4-2wp7","aliases":["CVE-2026-12345"],"affected":[{
                          "package":{"name":"demo","ecosystem":"npm","purl":"pkg:npm/demo"},
                          "versions":"not-an-array","ranges":[false]
                        }]}
                        """, MediaType.APPLICATION_JSON));

        OsvVulnerabilityData data = provider.lookup(CVE);
        var affected = data.affectedPackages().getFirst();

        assertThat(affected.affectedVersions()).isEmpty();
        assertThat(affected.affectedVersionsTruncated()).isTrue();
        assertThat(affected.versionRanges()).isEmpty();
        assertThat(affected.versionRangesTruncated()).isTrue();
        server.verify();
    }
}
