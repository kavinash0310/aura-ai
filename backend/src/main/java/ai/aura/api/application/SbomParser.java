package ai.aura.api.application;

import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
public class SbomParser {
    private final CycloneDxSbomParser cycloneDxParser;
    private final SpdxSbomParser spdxParser;
    private final SarifSbomParser sarifParser;
    private final JsonMapper jsonMapper;

    public SbomParser(
            CycloneDxSbomParser cycloneDxParser,
            SpdxSbomParser spdxParser,
            SarifSbomParser sarifParser,
            JsonMapper jsonMapper
    ) {
        this.cycloneDxParser = cycloneDxParser;
        this.spdxParser = spdxParser;
        this.sarifParser = sarifParser;
        this.jsonMapper = jsonMapper;
    }

    public ParsedSbom parse(byte[] input) {
        if (input == null || input.length == 0) {
            throw new InvalidSbomException("Choose a non-empty CycloneDX or SPDX JSON SBOM.");
        }
        if (input.length > CycloneDxSbomParser.MAX_FILE_BYTES) {
            throw new InvalidSbomException("SBOM JSON files must be 2 MB or smaller.");
        }

        JsonNode root;
        try {
            root = jsonMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(input);
        } catch (JacksonException exception) {
            throw new InvalidSbomException("The file is not valid JSON.");
        }
        if (root != null && root.isObject()) {
            if ("CycloneDX".equals(root.path("bomFormat").asText())) {
                ParsedCycloneDxSbom cycloneDx = cycloneDxParser.parse(input);
                List<ParsedSbom.DeclaredVulnerability> vulnerabilities = cycloneDx.vulnerabilities().stream()
                        .map(vulnerability -> new ParsedSbom.DeclaredVulnerability(
                                vulnerability.cve(),
                                vulnerability.affectedBomRefs(),
                                vulnerability.affectedComponents().stream()
                                        .map(component -> new ParsedSbom.Component(
                                                component.bomRef(), component.name(), component.version(), component.purl()))
                                        .toList()))
                        .toList();
                return new ParsedSbom("CycloneDX JSON", cycloneDx.specVersion(), cycloneDx.componentCount(), vulnerabilities);
            }
            if ("SPDX-2.3".equals(root.path("spdxVersion").asText())) {
                return spdxParser.parse(input);
            }
            if ("2.1.0".equals(root.path("version").asText())) {
                return sarifParser.parse(input);
            }
        }
        throw new InvalidSbomException("The JSON file must be CycloneDX 1.3–1.7, SPDX 2.3, or SARIF 2.1.0.");
    }
}
