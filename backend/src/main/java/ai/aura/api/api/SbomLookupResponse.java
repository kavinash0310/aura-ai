package ai.aura.api.api;

import ai.aura.api.application.ParsedCycloneDxSbom;

import java.time.Instant;
import java.util.List;

public record SbomLookupResponse(
        Instant requestedAt,
        String format,
        String specVersion,
        int componentCount,
        int cveCount,
        List<ParsedCycloneDxSbom.DeclaredVulnerability> vulnerabilities,
        VulnerabilityLookupResponse intelligence,
        String coverageNote
) {
    public SbomLookupResponse {
        vulnerabilities = List.copyOf(vulnerabilities);
    }
}
