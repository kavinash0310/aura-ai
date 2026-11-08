package ai.aura.api.api;

import ai.aura.api.domain.ResearchFilters;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RunResearchRequest(
        @Pattern(regexp = "[A-Z]{2}") String country,
        @Pattern(regexp = "[a-z]{2}") String language,
        @Pattern(regexp = "pd|pw|pm|py") String freshness,
        @Min(1) @Max(20) Integer resultLimit,
        @Pattern(regexp = "[a-z][a-z0-9-]{0,31}") String provider,
        @Size(max = 3) List<@NotBlank @Size(max = 300) String> queries
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


