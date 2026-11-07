package ai.aura.api.application;

import java.util.List;

public record ParsedCycloneDxSbom(
        String specVersion,
        int componentCount,
        List<DeclaredVulnerability> vulnerabilities
) {
    public ParsedCycloneDxSbom {
        vulnerabilities = List.copyOf(vulnerabilities);
    }

    public record Component(String bomRef, String name, String version, String purl) {
    }

    public record DeclaredVulnerability(String cve, List<String> affectedBomRefs, List<Component> affectedComponents) {
        public DeclaredVulnerability {
            affectedBomRefs = List.copyOf(affectedBomRefs);
            affectedComponents = List.copyOf(affectedComponents);
        }
    }
}
