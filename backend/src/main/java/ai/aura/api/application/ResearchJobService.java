package ai.aura.api.application;

import ai.aura.api.domain.ResearchFilters;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchProviderRun;
import ai.aura.api.domain.ResearchProviderRunStatus;
import ai.aura.api.domain.ResearchSource;
import ai.aura.api.persistence.ResearchJobRepository;
import ai.aura.api.persistence.ResearchProviderRunRepository;
import ai.aura.api.persistence.ResearchSourceRepository;
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
import java.util.Objects;
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
        if (job.getStatus() == ResearchJobStatus.SEARCHING) {
            throw new ResearchJobConflictException();
        }
        repository.delete(job);
    }

    @Transactional
    public ResearchJob beginSearch(
            UUID id,
            ResearchFilters filters,
            String provider,
            List<String> selectedProviders,
            List<String> queries
    ) {
        ResearchJob job = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResearchJobNotFoundException(id));
        if (job.getStatus() != ResearchJobStatus.REQUESTED) {
            throw new ResearchJobConflictException();
        }
        job.beginSearch(provider, filters, Instant.now());
        List<ResearchProviderRun> pendingRuns = new ArrayList<>();
        int runOrder = 1;
        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            for (String selectedProvider : selectedProviders) {
                pendingRuns.add(ResearchProviderRun.pending(
                        id,
                        selectedProvider,
                        runOrder++,
                        queryIndex + 1,
                        queries.get(queryIndex)
                ));
            }
        }
        providerRuns.saveAll(pendingRuns);
        return job;
    }

    @Transactional
    public void startProviderRun(UUID jobId, String provider, int queryOrder) {
        ResearchJob job = repository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResearchJobNotFoundException(jobId));
        if (job.getStatus() != ResearchJobStatus.SEARCHING) {
            throw new ResearchJobConflictException();
        }
        ResearchProviderRun run = providerRuns.findByResearchJobIdAndProviderAndQueryOrder(jobId, provider, queryOrder)
                .orElseThrow(ResearchJobConflictException::new);
        run.start(Instant.now());
    }

    @Transactional
    public void completeProviderRun(
            UUID jobId,
            String provider,
            int queryOrder,
            List<ProviderSource> providerResults
    ) {
        ResearchJob job = repository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResearchJobNotFoundException(jobId));
        if (job.getStatus() != ResearchJobStatus.SEARCHING) {
            throw new ResearchJobConflictException();
        }
        ResearchProviderRun run = providerRuns.findByResearchJobIdAndProviderAndQueryOrder(jobId, provider, queryOrder)
                .orElseThrow(ResearchJobConflictException::new);
        int precedingResultCount = providerRuns.findByResearchJobIdOrderByRunOrderAsc(jobId).stream()
                .filter(previous -> previous.getRunOrder() < run.getRunOrder())
                .filter(previous -> previous.getStatus() == ResearchProviderRunStatus.SUCCEEDED)
                .mapToInt(ResearchProviderRun::getResultCount)
                .sum();
        Instant retrievedAt = Instant.now();
        List<ResearchSource> entities = new ArrayList<>();
        for (int index = 0; index < providerResults.size(); index++) {
            entities.add(ResearchSource.from(
                    jobId,
                    provider,
                    run.getQueryText(),
                    precedingResultCount + index + 1,
                    providerResults.get(index),
                    retrievedAt
            ));
        }
        sources.saveAll(entities);
        run.succeed(providerResults.size(), retrievedAt);
    }

    @Transactional
    public void failProviderRun(UUID jobId, String provider, int queryOrder, String code) {
        ResearchJob job = repository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new ResearchJobNotFoundException(jobId));
        if (job.getStatus() != ResearchJobStatus.SEARCHING) {
            throw new ResearchJobConflictException();
        }
        ResearchProviderRun run = providerRuns.findByResearchJobIdAndProviderAndQueryOrder(jobId, provider, queryOrder)
                .orElseThrow(ResearchJobConflictException::new);
        run.fail(code, Instant.now());
    }

    @Transactional
    public ResearchJob completeSearch(UUID id, String firstFailureCode) {
        ResearchJob job = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResearchJobNotFoundException(id));
        if (job.getStatus() != ResearchJobStatus.SEARCHING) {
            throw new ResearchJobConflictException();
        }

        List<ResearchProviderRun> runs = providerRuns.findByResearchJobIdOrderByRunOrderAsc(id);
        boolean stillRunning = runs.stream().anyMatch(run ->
                run.getStatus() == ResearchProviderRunStatus.PENDING
                        || run.getStatus() == ResearchProviderRunStatus.RUNNING
        );
        if (stillRunning) {
            throw new ResearchJobConflictException();
        }

        Instant completedAt = Instant.now();
        boolean anySucceeded = runs.stream().anyMatch(run -> run.getStatus() == ResearchProviderRunStatus.SUCCEEDED);
        boolean anyIncomplete = runs.stream().anyMatch(run ->
                run.getStatus() == ResearchProviderRunStatus.FAILED
                        || run.getStatus() == ResearchProviderRunStatus.SKIPPED
        );
        String failureCode = firstFailureCode != null ? firstFailureCode : runs.stream()
                .filter(run -> run.getStatus() == ResearchProviderRunStatus.FAILED
                        || run.getStatus() == ResearchProviderRunStatus.SKIPPED)
                .map(ResearchProviderRun::getFailureCode)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        int resultCount = Math.toIntExact(sources.countByResearchJobId(id));
        long reportedSuccessCount = runs.stream()
                .filter(run -> run.getStatus() == ResearchProviderRunStatus.SUCCEEDED)
                .mapToLong(ResearchProviderRun::getResultCount)
                .sum();
        if (reportedSuccessCount != resultCount) {
            job.failSearch("SEARCH_RESULT_COUNT_MISMATCH", completedAt);
            return job;
        }
        if (anySucceeded && anyIncomplete) {
            job.completePartialSearch(resultCount, failureCode, completedAt);
        } else if (anySucceeded) {
            job.completeSearch(resultCount, completedAt);
        } else {
            job.failSearch(failureCode == null ? "SEARCH_PROVIDER_FAILURE" : failureCode, completedAt);
        }
        return job;
    }

    @Transactional
    public void recoverInterruptedSearches() {
        List<ResearchJob> interruptedJobs = repository.findByStatusOrderBySearchStartedAtAsc(
                ResearchJobStatus.SEARCHING
        );
        for (ResearchJob candidate : interruptedJobs) {
            ResearchJob job = repository.findByIdForUpdate(candidate.getId())
                    .orElseThrow(() -> new ResearchJobNotFoundException(candidate.getId()));
            if (job.getStatus() != ResearchJobStatus.SEARCHING) {
                continue;
            }

            Instant recoveredAt = Instant.now();
            List<ResearchProviderRun> runs = providerRuns.findByResearchJobIdOrderByRunOrderAsc(job.getId());
            for (ResearchProviderRun run : runs) {
                if (run.getStatus() == ResearchProviderRunStatus.RUNNING) {
                    run.fail("SEARCH_INTERRUPTED", recoveredAt);
                } else if (run.getStatus() == ResearchProviderRunStatus.PENDING) {
                    run.skip("SEARCH_INTERRUPTED_BEFORE_PROVIDER", recoveredAt);
                }
            }
            boolean anySucceeded = runs.stream().anyMatch(run -> run.getStatus() == ResearchProviderRunStatus.SUCCEEDED);
            boolean anyIncomplete = runs.stream().anyMatch(run ->
                    run.getStatus() == ResearchProviderRunStatus.FAILED
                            || run.getStatus() == ResearchProviderRunStatus.SKIPPED
            );
            String failureCode = runs.stream()
                    .filter(run -> run.getStatus() == ResearchProviderRunStatus.FAILED
                            || run.getStatus() == ResearchProviderRunStatus.SKIPPED)
                    .map(ResearchProviderRun::getFailureCode)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse("SEARCH_INTERRUPTED");
            int resultCount = Math.toIntExact(sources.countByResearchJobId(job.getId()));
            long reportedSuccessCount = runs.stream()
                    .filter(run -> run.getStatus() == ResearchProviderRunStatus.SUCCEEDED)
                    .mapToLong(ResearchProviderRun::getResultCount)
                    .sum();
            if (reportedSuccessCount != resultCount) {
                job.failSearch("SEARCH_RECOVERY_SOURCE_COUNT_MISMATCH", recoveredAt);
                continue;
            }
            if (anySucceeded && anyIncomplete) {
                job.completePartialSearch(resultCount, failureCode, recoveredAt);
            } else if (anySucceeded) {
                job.completeSearch(resultCount, recoveredAt);
            } else {
                job.failSearch(failureCode, recoveredAt);
            }
        }
    }
}
