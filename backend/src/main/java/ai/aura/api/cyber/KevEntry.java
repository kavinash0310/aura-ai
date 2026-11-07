package ai.aura.api.cyber;

import java.time.LocalDate;

public record KevEntry(
        String cve,
        String vendorProject,
        String product,
        String vulnerabilityName,
        LocalDate dateAdded,
        LocalDate dueDate,
        String requiredAction,
        String knownRansomwareCampaignUse
) {
}
