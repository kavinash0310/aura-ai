package ai.aura.api.application;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchProviderRun;
import ai.aura.api.domain.ResearchSource;
import ai.aura.api.persistence.ResearchJobRepository;
import ai.aura.api.persistence.ResearchProviderRunRepository;
import ai.aura.api.persistence.ResearchSourceRepository;
import ai.aura.api.search.ProviderSearchResult;
import ai.aura.api.search.ProviderSource;
import ai.aura.api.domain.ResearchMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ResearchJobService {

    private final ResearchJobRepository repository;
    private final ResearchProviderRunRepository providerRuns;
    private final ResearchSourceRepository sources;

    public ResearchJobService(
            ResearchJobRepository repository,
            ResearchProviderRunRepository providerRuns,
            ResearchSourceRepository sources
    ) {
        this.repository = repository;
        this.providerRuns = providerRuns;
        this.sources = sources;
    }

    @Transactional
    public ResearchJob create(String subject, ResearchMode mode) {
        return repository.save(ResearchJob.requested(subject, mode));
    }

    @Transactional(readOnly = true)
    public ResearchJob get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResearchJobNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<ResearchJob> listRecent(int limit) {
        return repository.findAll(PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();
    }

    @Transactional(readOnly = true)
    public List<ResearchProviderRun> listProviderRuns(UUID jobId) {
        return providerRuns.findByResearchJobIdOrderByRunOrderAsc(jobId);
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<ResearchProviderRun>> listProviderRuns(Collection<UUID> jobIds) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        return providerRuns.findByResearchJobIdInOrderByResearchJobIdAscRunOrderAsc(jobIds).stream()
                .collect(Collectors.groupingBy(
                        ResearchProviderRun::getResearchJobId,
                        java.util.LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    @Transactional
    public void delete(UUID id) {
        ResearchJob job = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResearchJobNotFoundException(id));
        repository.delete(job);
    }

    @Transactional
    public ResearchJob beginSearch(UUID id, ResearchFilters filters, String provider, List<String> selectedProviders) {
        ResearchJob job = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResearchJobNotFoundException(id));
        if (job.getStatus() != ResearchJobStatus.REQUESTED) {
            throw new ResearchJobConflictException();
        }
        job.beginSearch(provider, filters, Instant.now());
        List<ResearchProviderRun> pendingRuns = new ArrayList<>();
        for (int index = 0; index < selectedProviders.size(); index++) {
            pendingRuns.add(ResearchProviderRun.pending(id, selectedProviders.get(index), index + 1));
        }
        providerRuns.saveAll(pendingRuns);
        return job;
    }

    @Transactional
    public void startProviderRun(UUID jobId, String provider) {
        ResearchProviderRun run = providerRuns.findByResearchJobIdAndProvider(jobId, provider)
                .orElseThrow(ResearchJobConflictException::new);
        run.start(Instant.now());
    }

    @Transactional
    public void completeProviderRun(UUID jobId, String provider, int resultCount) {
        ResearchProviderRun run = providerRuns.findByResearchJobIdAndProvider(jobId, provider)
                .orElseThrow(ResearchJobConflictException::new);
        run.succeed(resultCount, Instant.now());
    }

    @Transactional
    public void failProviderRun(UUID jobId, String provider, String code) {
        ResearchProviderRun run = providerRuns.findByResearchJobIdAndProvider(jobId, provider)
                .orElseThrow(ResearchJobConflictException::new);
        run.fail(code, Instant.now());
    }

    @Transactional
    public ResearchJob completeSearch(
            UUID id,
            List<ProviderSearchResult> providerResults,
            String firstFailureCode
    ) {
        ResearchJob job = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResearchJobNotFoundException(id));
        if (job.getStatus() != ResearchJobStatus.SEARCHING) {
            throw new ResearchJobConflictException();
        }

        Instant retrievedAt = Instant.now();
        List<ResearchSource> entities = new ArrayList<>();
        int rank = 1;
        for (ProviderSearchResult providerResult : providerResults) {
            for (ProviderSource result : providerResult.sources()) {
                entities.add(ResearchSource.from(id, providerResult.provider(), rank++, result, retrievedAt));
            }
        }
        sources.saveAll(entities);
        if (firstFailureCode != null && providerResults.isEmpty()) {
            job.failSearch(firstFailureCode, retrievedAt);
        } else if (firstFailureCode != null) {
            job.completePartialSearch(entities.size(), firstFailureCode, retrievedAt);
        } else {
            job.completeSearch(entities.size(), retrievedAt);
        }
        return job;
    }

    @Transactional
    public void failSearch(UUID id, String code) {
        ResearchJob job = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResearchJobNotFoundException(id));
        if (job.getStatus() == ResearchJobStatus.SEARCHING) {
            job.failSearch(code, Instant.now());
        }
    }
}
