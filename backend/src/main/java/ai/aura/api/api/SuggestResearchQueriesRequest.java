package ai.aura.api.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuggestResearchQueriesRequest(
        @NotBlank @Size(max = 300) String topic
) {
}
