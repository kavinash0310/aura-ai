package ai.aura.api.application;

import java.util.List;

/** Format-neutral, in-memory view of the references declared by an uploaded SBOM. */
public record ParsedSbom(
        String format,
        String specVersion,
        int componentCount,
        List<DeclaredVulnerability> vulnerabilities
) {
    public ParsedSbom {
        vulnerabilities = List.copyOf(vulnerabilities);
    }

    public record Component(String reference, String name, String version, String purl) {
    }

    public record DeclaredVulnerability(
            String cve,
            List<String> affectedReferences,
            List<Component> affectedComponents
    ) {
        public DeclaredVulnerability {
            affectedReferences = List.copyOf(affectedReferences);
            affectedComponents = List.copyOf(affectedComponents);
        }
    }
}
