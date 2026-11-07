package ai.aura.api.application;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.search.ProviderSource;
import ai.aura.api.search.SearchProvider;
import ai.aura.api.search.SearchProviderException;
import ai.aura.api.search.SearchProviderNotConfiguredException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;

@Service
public class ResearchSearchService {

    private final List<SearchProvider> providers;
    private final Map<String, SearchProvider> providersByName;
    private final ResearchJobService jobs;

    public ResearchSearchService(List<SearchProvider> providers, ResearchJobService jobs) {
        this.providers = List.copyOf(providers);
        this.providersByName = providers.stream()
                .collect(Collectors.toUnmodifiableMap(SearchProvider::name, Function.identity()));
        this.jobs = jobs;
    }

    public ResearchJob run(UUID jobId, ResearchFilters filters, String requestedProvider) {
        SearchProvider provider = resolveProvider(requestedProvider);
        if (!provider.isConfigured()) {
            throw new SearchProviderNotConfiguredException();
        }

        ResearchJob job = jobs.beginSearch(jobId, filters, provider.name());
        List<ProviderSource> results;
        try {
            results = provider.search(job.getSubject(), filters);
        } catch (SearchProviderException exception) {
            jobs.failSearch(jobId, exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            jobs.failSearch(jobId, "SEARCH_PROVIDER_FAILURE");
            throw new SearchProviderException("SEARCH_PROVIDER_FAILURE", false);
        }
        return jobs.completeSearch(jobId, provider.name(), results);
    }

    private SearchProvider resolveProvider(String requestedProvider) {
        if (requestedProvider != null && !requestedProvider.isBlank()) {
            SearchProvider selected = providersByName.get(requestedProvider);
            if (selected == null) {
                throw new InvalidResearchFilterException("Search provider must be brave or gdelt.");
            }
            return selected;
        }
        return providers.stream()
                .filter(SearchProvider::isConfigured)
                .findFirst()
                .orElseThrow(SearchProviderNotConfiguredException::new);
    }
}


