package ai.aura.api.cyber;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NvdCveData(
        String cve,
        String status,
        Instant retrievedAt,
        String publishedAt,
        String lastModifiedAt,
        String vulnerabilityStatus,
        String description,
        String cvssVersion,
        BigDecimal cvssScore,
        String cvssSeverity,
        String cvssVector,
        List<String> cwes,
        List<CpeCriterion> applicability,
        boolean applicabilityTruncated,
        List<Reference> references,
        boolean referencesTruncated,
        String note
) {
    public NvdCveData {
        cwes = List.copyOf(cwes);
        applicability = List.copyOf(applicability);
        references = List.copyOf(references);
    }

    public record CpeCriterion(String criteria, boolean vulnerable,
                               String versionStartIncluding, String versionStartExcluding,
                               String versionEndIncluding, String versionEndExcluding) {
    }

    public record Reference(String url, List<String> tags) {
        public Reference {
            tags = List.copyOf(tags);
        }
    }
}
