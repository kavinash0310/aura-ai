package ai.aura.api.application;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchSource;
import ai.aura.api.persistence.ResearchJobRepository;
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
import java.util.List;
import java.util.UUID;

@Service
public class ResearchJobService {

    private final ResearchJobRepository repository;
    private final ResearchSourceRepository sources;

    public ResearchJobService(ResearchJobRepository repository, ResearchSourceRepository sources) {
        this.repository = repository;
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

    @Transactional
    public void delete(UUID id) {
        ResearchJob job = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResearchJobNotFoundException(id));
        repository.delete(job);
    }

    @Transactional
    public ResearchJob beginSearch(UUID id, ResearchFilters filters, String provider) {
        ResearchJob job = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResearchJobNotFoundException(id));
        if (job.getStatus() != ResearchJobStatus.REQUESTED) {
            throw new ResearchJobConflictException();
        }
        job.beginSearch(provider, filters, Instant.now());
        return job;
    }

    @Transactional
    public ResearchJob completeSearch(UUID id, List<ProviderSearchResult> providerResults) {
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
        job.completeSearch(entities.size(), retrievedAt);
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
