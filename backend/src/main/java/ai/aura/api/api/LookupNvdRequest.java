package ai.aura.api.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LookupNvdRequest(
        @NotBlank
        @Pattern(regexp = "(?i)CVE-[0-9]{4}-[0-9]{4,}", message = "The item must be a CVE ID.")
        String cve
) {
}
