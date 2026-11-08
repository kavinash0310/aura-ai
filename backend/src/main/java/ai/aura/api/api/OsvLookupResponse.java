package ai.aura.api.api;

import ai.aura.api.cyber.OsvVulnerabilityData;

import java.time.Instant;
import java.util.List;

public record OsvLookupResponse(
        Instant requestedAt,
        String cve,
        String status,
        Instant retrievedAt,
        String sourceUrl,
        String osvId,
        String summary,
        String details,
        String publishedAt,
        String modifiedAt,
        String withdrawnAt,
        List<String> aliases,
        List<OsvVulnerabilityData.AffectedPackage> affectedPackages,
        boolean affectedPackagesTruncated,
        List<OsvVulnerabilityData.Reference> references,
        boolean referencesTruncated,
        String note
) {
    public OsvLookupResponse {
        aliases = List.copyOf(aliases);
        affectedPackages = List.copyOf(affectedPackages);
        references = List.copyOf(references);
    }
}
