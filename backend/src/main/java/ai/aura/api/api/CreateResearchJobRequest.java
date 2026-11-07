package ai.aura.api.api;

import ai.aura.api.domain.ResearchMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateResearchJobRequest(
        @NotBlank @Size(max = 300) String subject,
        @NotNull ResearchMode mode
) {
}
