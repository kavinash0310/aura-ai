package ai.aura.api.application;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CycloneDxSbomParserTest {

    private final CycloneDxSbomParser parser = new CycloneDxSbomParser(JsonMapper.builder().build());

    @Test
    void readsOnlyDeclaredCvesAndResolvesNestedComponentReferences() {
        String json = """
                {
                  "bomFormat":"CycloneDX","specVersion":"1.7",
                  "metadata":{"component":{"type":"application","bom-ref":"app","name":"sample","version":"1"}},
                  "components":[{"type":"library","bom-ref":"pkg:npm/example@2","name":"example","version":"2","purl":"pkg:npm/example@2","components":[{"type":"library","name":"unreferenced"}]}],
                  "vulnerabilities":[
                    {"id":"cve-2026-12345","affects":[{"ref":"pkg:npm/example@2"},{"ref":"missing-ref"}]},
                    {"id":"CVE-2026-12345","affects":[{"ref":"app"}]},
                    {"id":"GHSA-example","affects":[{"ref":"app"}]}
                  ]
                }
                """;

        ParsedCycloneDxSbom parsed = parser.parse(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(parsed.specVersion()).isEqualTo("1.7");
        assertThat(parsed.componentCount()).isEqualTo(3);
        assertThat(parsed.vulnerabilities()).hasSize(1);
        assertThat(parsed.vulnerabilities().getFirst().cve()).isEqualTo("CVE-2026-12345");
        assertThat(parsed.vulnerabilities().getFirst().affectedBomRefs())
                .containsExactly("pkg:npm/example@2", "missing-ref", "app");
        assertThat(parsed.vulnerabilities().getFirst().affectedComponents())
                .extracting(ParsedCycloneDxSbom.Component::name)
                .containsExactly("example", "sample");
    }

    @Test
    void rejectsInvalidFormatsVersionsAndTrailingJson() {
        assertInvalid("not json");
        assertInvalid("{\"bomFormat\":\"SPDX\",\"specVersion\":\"1.7\"}");
        assertInvalid("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"2.0\"}");
        assertInvalid("{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.7\"} {} ");
    }

    @Test
    void capsFileSizeComponentCountAndDistinctCves() {
        assertThatThrownBy(() -> parser.parse(new byte[CycloneDxSbomParser.MAX_FILE_BYTES + 1]))
                .isInstanceOf(InvalidSbomException.class)
                .hasMessageContaining("2 MB");

        StringBuilder tooManyComponents = new StringBuilder(
                "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.7\",\"components\":[");
        for (int index = 0; index < 10_001; index++) {
            if (index > 0) tooManyComponents.append(',');
            tooManyComponents.append("{\"name\":\"library\"}");
        }
        tooManyComponents.append("]}");
        assertThatThrownBy(() -> parser.parse(tooManyComponents.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidSbomException.class)
                .hasMessageContaining("10,000 components");

        StringBuilder tooManyCves = new StringBuilder(
                "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.7\",\"vulnerabilities\":[");
        for (int index = 0; index < 26; index++) {
            if (index > 0) tooManyCves.append(',');
            tooManyCves.append("{\"id\":\"CVE-2026-").append(1000 + index).append("\"}");
        }
        tooManyCves.append("]}");
        assertThatThrownBy(() -> parser.parse(tooManyCves.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidSbomException.class)
                .hasMessageContaining("25 distinct CVE IDs");

        StringBuilder tooManyReferences = new StringBuilder(
                "{\"bomFormat\":\"CycloneDX\",\"specVersion\":\"1.7\",\"vulnerabilities\":[{\"id\":\"CVE-2026-12345\",\"affects\":[");
        for (int index = 0; index < 10_001; index++) {
            if (index > 0) tooManyReferences.append(',');
            tooManyReferences.append("{\"ref\":\"ref-").append(index).append("\"}");
        }
        tooManyReferences.append("]}]}");
        assertThatThrownBy(() -> parser.parse(tooManyReferences.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidSbomException.class)
                .hasMessageContaining("10,000 affected component references");
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> parser.parse(json.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidSbomException.class);
    }
}
