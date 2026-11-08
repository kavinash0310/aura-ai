package ai.aura.api.api;

import ai.aura.api.application.ParsedSbom;

import java.time.Instant;
import java.util.List;

public record SbomLookupResponse(
        Instant requestedAt,
        String format,
        String specVersion,
        int componentCount,
        int cveCount,
        List<DeclaredVulnerability> vulnerabilities,
        VulnerabilityLookupResponse intelligence,
        String coverageNote
) {
    public SbomLookupResponse {
        vulnerabilities = List.copyOf(vulnerabilities);
    }

    public record Component(String bomRef, String name, String version, String purl) {
    }

    public record DeclaredVulnerability(
            String cve,
            List<String> affectedBomRefs,
            List<Component> affectedComponents
    ) {
        public DeclaredVulnerability {
            affectedBomRefs = List.copyOf(affectedBomRefs);
            affectedComponents = List.copyOf(affectedComponents);
        }

        public static DeclaredVulnerability from(ParsedSbom.DeclaredVulnerability vulnerability) {
            return new DeclaredVulnerability(
                    vulnerability.cve(),
                    vulnerability.affectedReferences(),
                    vulnerability.affectedComponents().stream()
                            .map(component -> new Component(
                                    component.reference(), component.name(), component.version(), component.purl()))
                            .toList());
        }
    }
}
