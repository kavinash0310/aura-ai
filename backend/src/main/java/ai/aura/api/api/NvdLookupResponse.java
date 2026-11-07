package ai.aura.api.api;

import ai.aura.api.cyber.NvdCveData;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NvdLookupResponse(
        Instant requestedAt,
        String cve,
        String status,
        Instant retrievedAt,
        String sourceUrl,
        String note,
        String publishedAt,
        String lastModifiedAt,
        String vulnerabilityStatus,
        String description,
        String cvssVersion,
        BigDecimal cvssScore,
        String cvssSeverity,
        String cvssVector,
        List<String> cwes,
        List<NvdCveData.CpeCriterion> applicability,
        boolean applicabilityTruncated,
        List<NvdCveData.Reference> references,
        boolean referencesTruncated
) {
    public NvdLookupResponse {
        cwes = List.copyOf(cwes);
        applicability = List.copyOf(applicability);
        references = List.copyOf(references);
    }
}
