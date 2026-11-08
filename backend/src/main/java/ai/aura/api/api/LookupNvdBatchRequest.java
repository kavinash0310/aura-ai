package ai.aura.api.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record LookupNvdBatchRequest(
        @NotEmpty
        @Size(min = 1, max = 25)
        List<@NotBlank @Pattern(regexp = "(?i)CVE-[0-9]{4}-[0-9]{4,}", message = "Each item must be a CVE ID.") String> cves
) {
}
