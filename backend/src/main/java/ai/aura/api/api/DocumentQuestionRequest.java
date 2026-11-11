package ai.aura.api.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record DocumentQuestionRequest(
        @NotBlank @Size(max = 500) String question,
        @NotBlank @Size(min = 2, max = 100) String searchQuery,
        @NotNull @Size(min = 1, max = 5) List<@Valid Selection> selections
) {
    public record Selection(
            @NotNull UUID documentId,
            @Min(0) int startOffset,
            @Min(1) int endOffset
    ) {
    }
}
