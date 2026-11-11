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

    public ResearchJob run(UUID jobId, ResearchFilters filters, String requestedProvider, List<String> requestedQueries) {
        List<SearchProvider> selectedProviders = resolveProviders(requestedProvider);
        if (selectedProviders.isEmpty()) {
            throw new SearchProviderNotConfiguredException();
        }

        String selectedProvider = isAllProviders(requestedProvider)
                ? "all"
                : selectedProviders.getFirst().name();
        List<String> queries = normalizeQueries(jobs.get(jobId).getSubject(), requestedQueries);
        ResearchJob job = jobs.beginSearch(
                jobId,
                filters,
                selectedProvider,
                selectedProviders.stream().map(SearchProvider::name).toList(),
                queries
        );
        SearchProviderException firstFailure = null;
        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            String query = queries.get(queryIndex);
            int queryOrder = queryIndex + 1;
            for (SearchProvider provider : selectedProviders) {
                jobs.startProviderRun(jobId, provider.name(), queryOrder);
                List<ProviderSource> providerResults;
                try {
                    providerResults = provider.search(query, filters);
                } catch (SearchProviderException exception) {
                    jobs.failProviderRun(jobId, provider.name(), queryOrder, exception.getCode());
                    if (firstFailure == null) {
                        firstFailure = exception;
                    }
                    continue;
                } catch (RuntimeException exception) {
                    SearchProviderException failure = new SearchProviderException("SEARCH_PROVIDER_FAILURE", false);
                    jobs.failProviderRun(jobId, provider.name(), queryOrder, failure.getCode());
                    if (firstFailure == null) {
                        firstFailure = failure;
                    }
                    continue;
                }
                jobs.completeProviderRun(jobId, provider.name(), queryOrder, providerResults);
            }
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

    public ResearchJob retryFailedProvider(UUID jobId, String requestedProvider, int queryOrder, int attempt) {
        SearchProvider provider = providersByName.get(requestedProvider);
        if (provider == null) {
            throw new InvalidResearchFilterException("The requested provider is not supported.");
        }
        if (!provider.isConfigured()) {
            throw new SearchProviderNotConfiguredException();
        }
        ResearchJob existingJob = jobs.get(jobId);
        var retryRun = jobs.beginProviderRetry(jobId, requestedProvider, queryOrder, attempt);
        jobs.startProviderRun(jobId, requestedProvider, queryOrder, retryRun.getAttempt());

        SearchProviderException failure = null;
        List<ProviderSource> providerResults = null;
        ResearchFilters retryFilters = new ResearchFilters(
                existingJob.getSearchCountry(),
                existingJob.getSearchLanguage(),
                existingJob.getSearchFreshness(),
                existingJob.getSearchLimit() == null ? 20 : existingJob.getSearchLimit()
        );
        try {
            providerResults = provider.search(retryRun.getQueryText(), retryFilters);
        } catch (SearchProviderException exception) {
            failure = exception;
            jobs.failProviderRun(jobId, requestedProvider, queryOrder, retryRun.getAttempt(), exception.getCode());
        } catch (RuntimeException exception) {
            failure = new SearchProviderException("SEARCH_PROVIDER_FAILURE", false);
            jobs.failProviderRun(jobId, requestedProvider, queryOrder, retryRun.getAttempt(), failure.getCode());
        }

        if (failure == null) {
            jobs.completeProviderRun(jobId, requestedProvider, queryOrder, retryRun.getAttempt(), providerResults);
        }

        ResearchJob completed = jobs.completeSearch(jobId, failure == null ? null : failure.getCode());
        if (completed.getStatus() == ai.aura.api.domain.ResearchJobStatus.FAILED) {
            if (failure != null && failure.getCode().equals(completed.getFailureCode())) {
                throw failure;
            }
            throw new SearchProviderException(
                    completed.getFailureCode() == null ? "SEARCH_PROVIDER_FAILURE" : completed.getFailureCode(),
                    false
            );
        }
        return completed;
    }

    private static List<String> normalizeQueries(String subject, List<String> requestedQueries) {
        String rootQuery = subject.strip();
        if (requestedQueries == null || requestedQueries.isEmpty()) {
            return List.of(rootQuery);
        }
        if (requestedQueries.size() > 3) {
            throw new InvalidResearchFilterException("A research plan can contain at most three queries.");
        }
        List<String> normalized = requestedQueries.stream()
                .map(String::strip)
                .toList();
        if (normalized.getFirst().isBlank() || !normalized.getFirst().equalsIgnoreCase(rootQuery)) {
            throw new InvalidResearchFilterException("The first planned query must match the research topic.");
        }
        java.util.LinkedHashMap<String, String> unique = new java.util.LinkedHashMap<>();
        for (String query : normalized) {
            if (query.isBlank()) {
                throw new InvalidResearchFilterException("Planned queries cannot be blank.");
            }
            unique.putIfAbsent(query.toLowerCase(java.util.Locale.ROOT), query);
        }
        unique.put(rootQuery.toLowerCase(java.util.Locale.ROOT), rootQuery);
        return List.copyOf(unique.values());
    }

    private List<SearchProvider> resolveProviders(String requestedProvider) {
        if (isAllProviders(requestedProvider)) {
            return providers.stream().filter(SearchProvider::isConfigured).toList();
        }
        if (requestedProvider != null && !requestedProvider.isBlank()) {
            SearchProvider selected = providersByName.get(requestedProvider);
            if (selected == null) {
                throw new InvalidResearchFilterException("Search provider must be brave, gdelt, bluesky, openalex, or all.");
            }
            return selected.isConfigured() ? List.of(selected) : List.of();
        }
        return providers.stream()
                .filter(provider -> provider.isConfigured() && provider.isAutomaticDefault())
                .findFirst()
                .map(List::of)
                .orElseGet(List::of);
    }

    private static boolean isAllProviders(String requestedProvider) {
        return "all".equalsIgnoreCase(requestedProvider);
    }
}


