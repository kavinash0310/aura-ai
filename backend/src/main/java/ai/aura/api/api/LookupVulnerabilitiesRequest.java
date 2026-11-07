package ai.aura.api.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LookupVulnerabilitiesRequest(
        @NotEmpty
        @Size(max = 25)
        List<@Pattern(regexp = "(?i)CVE-[0-9]{4}-[0-9]{4,}", message = "Each item must be a CVE ID.") String> cves
) {
}
