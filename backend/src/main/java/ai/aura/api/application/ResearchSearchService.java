package ai.aura.api.application;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.search.ProviderSearchResult;
import ai.aura.api.search.ProviderSource;
import ai.aura.api.search.SearchProvider;
import ai.aura.api.search.SearchProviderException;
import ai.aura.api.search.SearchProviderNotConfiguredException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        List<SearchProvider> selectedProviders = resolveProviders(requestedProvider);
        if (selectedProviders.isEmpty()) {
            throw new SearchProviderNotConfiguredException();
        }

        String selectedProvider = isAllProviders(requestedProvider)
                ? "all"
                : selectedProviders.getFirst().name();
        ResearchJob job = jobs.beginSearch(jobId, filters, selectedProvider);
        List<ProviderSearchResult> results = new ArrayList<>();
        try {
            for (SearchProvider provider : selectedProviders) {
                List<ProviderSource> providerResults = provider.search(job.getSubject(), filters);
                results.add(new ProviderSearchResult(provider.name(), providerResults));
            }
        } catch (SearchProviderException exception) {
            jobs.failSearch(jobId, exception.getCode());
            throw exception;
        } catch (RuntimeException exception) {
            jobs.failSearch(jobId, "SEARCH_PROVIDER_FAILURE");
            throw new SearchProviderException("SEARCH_PROVIDER_FAILURE", false);
        }
        return jobs.completeSearch(jobId, results);
    }

    private List<SearchProvider> resolveProviders(String requestedProvider) {
        if (isAllProviders(requestedProvider)) {
            return providers.stream().filter(SearchProvider::isConfigured).toList();
        }
        if (requestedProvider != null && !requestedProvider.isBlank()) {
            SearchProvider selected = providersByName.get(requestedProvider);
            if (selected == null) {
                throw new InvalidResearchFilterException("Search provider must be brave, gdelt, bluesky, or all.");
            }
            return selected.isConfigured() ? List.of(selected) : List.of();
        }
        return providers.stream()
                .filter(SearchProvider::isConfigured)
                .findFirst()
                .map(List::of)
                .orElseGet(List::of);
    }

    private static boolean isAllProviders(String requestedProvider) {
        return "all".equalsIgnoreCase(requestedProvider);
    }
}


