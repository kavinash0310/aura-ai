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
        List<SearchProvider> selectedProviders = resolveProviders(requestedProvider);
        if (selectedProviders.isEmpty()) {
            throw new SearchProviderNotConfiguredException();
        }

        String selectedProvider = isAllProviders(requestedProvider)
                ? "all"
                : selectedProviders.getFirst().name();
        ResearchJob job = jobs.beginSearch(
                jobId,
                filters,
                selectedProvider,
                selectedProviders.stream().map(SearchProvider::name).toList()
        );
        SearchProviderException firstFailure = null;
        for (SearchProvider provider : selectedProviders) {
            jobs.startProviderRun(jobId, provider.name());
            List<ProviderSource> providerResults;
            try {
                providerResults = provider.search(job.getSubject(), filters);
            } catch (SearchProviderException exception) {
                jobs.failProviderRun(jobId, provider.name(), exception.getCode());
                if (firstFailure == null) {
                    firstFailure = exception;
                }
                continue;
            } catch (RuntimeException exception) {
                SearchProviderException failure = new SearchProviderException("SEARCH_PROVIDER_FAILURE", false);
                jobs.failProviderRun(jobId, provider.name(), failure.getCode());
                if (firstFailure == null) {
                    firstFailure = failure;
                }
                continue;
            }
            jobs.completeProviderRun(jobId, provider.name(), providerResults);
        }
        ResearchJob completed = jobs.completeSearch(jobId, firstFailure == null ? null : firstFailure.getCode());
        if (completed.getStatus() == ai.aura.api.domain.ResearchJobStatus.FAILED) {
            if (firstFailure != null && firstFailure.getCode().equals(completed.getFailureCode())) {
                throw firstFailure;
            }
            throw new SearchProviderException(
                    completed.getFailureCode() == null ? "SEARCH_PROVIDER_FAILURE" : completed.getFailureCode(),
                    false
            );
        }
        return completed;
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


