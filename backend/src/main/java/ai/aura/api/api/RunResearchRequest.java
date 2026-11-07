package ai.aura.api.api;

import ai.aura.api.domain.ResearchFilters;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record RunResearchRequest(
        @Pattern(regexp = "[A-Z]{2}") String country,
        @Pattern(regexp = "[a-z]{2}") String language,
        @Pattern(regexp = "pd|pw|pm|py") String freshness,
        @Min(1) @Max(20) Integer resultLimit
) {
    public ResearchFilters toFilters() {
        return new ResearchFilters(
                country,
                language,
                freshness,
                resultLimit == null ? 20 : resultLimit
        );
    }
}


