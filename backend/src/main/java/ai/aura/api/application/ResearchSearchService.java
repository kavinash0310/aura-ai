package ai.aura.api.application;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.search.ProviderSource;
import ai.aura.api.search.SearchProvider;
import ai.aura.api.search.SearchProviderException;
import ai.aura.api.search.SearchProviderNotConfiguredException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ResearchSearchService {

    private final SearchProvider provider;
    private final ResearchJobService jobs;

    public ResearchSearchService(SearchProvider provider, ResearchJobService jobs) {
        this.provider = provider;
        this.jobs = jobs;
    }

    public ResearchJob run(UUID jobId, ResearchFilters filters) {
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
}


