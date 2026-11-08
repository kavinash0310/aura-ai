package ai.aura.api.application;

import ai.aura.api.api.SbomLookupResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class SbomLookupService {
    private static final String COVERAGE_NOTE = "AURA reads CycloneDX component references and only CVE IDs already declared by the SBOM. It does not match package versions to vulnerabilities, prove that a component is installed, or scan systems. The uploaded file is not persisted.";

    private final CycloneDxSbomParser parser;
    private final VulnerabilityLookupService vulnerabilityLookup;

    public SbomLookupService(CycloneDxSbomParser parser, VulnerabilityLookupService vulnerabilityLookup) {
        this.parser = parser;
        this.vulnerabilityLookup = vulnerabilityLookup;
    }

    public SbomLookupResponse lookup(byte[] content) {
        ParsedCycloneDxSbom sbom = parser.parse(content);
        List<String> cves = sbom.vulnerabilities().stream()
                .map(ParsedCycloneDxSbom.DeclaredVulnerability::cve)
                .toList();
        var intelligence = cves.isEmpty() ? null : vulnerabilityLookup.lookup(cves);
        return new SbomLookupResponse(
                Instant.now(),
                "CycloneDX JSON",
                sbom.specVersion(),
                sbom.componentCount(),
                sbom.vulnerabilities().size(),
                sbom.vulnerabilities(),
                intelligence,
                COVERAGE_NOTE
        );
    }
}
