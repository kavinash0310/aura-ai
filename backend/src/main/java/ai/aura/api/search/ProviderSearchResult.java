package ai.aura.api.search;

import java.util.List;

public record ProviderSearchResult(String provider, List<ProviderSource> sources) {
    public ProviderSearchResult {
        sources = List.copyOf(sources);
    }
}
