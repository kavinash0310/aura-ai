package ai.aura.api.api;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record GenerateResearchReportRequest(
        @NotEmpty @Size(max = 20) List<@NotNull UUID> sourceIds
) {
}
