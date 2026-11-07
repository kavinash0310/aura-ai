package ai.aura.api.search;

import ai.aura.api.domain.ResearchFilters;

import java.util.List;

public interface SearchProvider {
    String name();

    boolean isConfigured();

    List<ProviderSource> search(String query, ResearchFilters filters);
}


