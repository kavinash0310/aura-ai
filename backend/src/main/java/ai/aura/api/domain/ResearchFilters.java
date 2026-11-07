package ai.aura.api.domain;

public record ResearchFilters(
        String country,
        String language,
        String freshness,
        int resultLimit
) {
    public static ResearchFilters defaults() {
        return new ResearchFilters(null, null, null, 20);
    }
}


