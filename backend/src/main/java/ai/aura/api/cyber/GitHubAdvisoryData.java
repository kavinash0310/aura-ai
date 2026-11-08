package ai.aura.api.cyber;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record GitHubAdvisoryData(
        String cve,
        String status,
        Instant retrievedAt,
        List<Advisory> advisories,
        boolean advisoriesTruncated,
        String note
) {
    public GitHubAdvisoryData {
        advisories = List.copyOf(advisories);
    }

    public record Advisory(
            String ghsaId,
            String cveId,
            String sourceUrl,
            String type,
            String severity,
            String summary,
            String description,
            String publishedAt,
            String updatedAt,
            String reviewedAt,
            String withdrawnAt,
            Cvss cvssV3,
            Cvss cvssV4,
            List<String> cwes,
            boolean cwesTruncated,
            List<AffectedPackage> affectedPackages,
            boolean affectedPackagesTruncated,
            List<Reference> references,
            boolean referencesTruncated
    ) {
        public Advisory {
            cwes = List.copyOf(cwes);
            affectedPackages = List.copyOf(affectedPackages);
            references = List.copyOf(references);
        }
    }

    public record Cvss(String vector, BigDecimal score) {
    }

    public record AffectedPackage(String ecosystem, String name, String vulnerableVersionRange,
                                  String firstPatchedVersion) {
    }

    public record Reference(String url) {
    }
}
