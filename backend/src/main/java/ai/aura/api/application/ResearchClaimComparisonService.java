package ai.aura.api.application;

import ai.aura.api.api.ResearchClaimComparisonResponse;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchSource;
import ai.aura.api.persistence.ResearchSourceRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ResearchClaimComparisonService {

    private final ResearchJobService jobs;
    private final ResearchSourceRepository sources;
    private final AiReportClient aiReportClient;

    public ResearchClaimComparisonService(
            ResearchJobService jobs,
            ResearchSourceRepository sources,
            AiReportClient aiReportClient
    ) {
        this.jobs = jobs;
        this.sources = sources;
        this.aiReportClient = aiReportClient;
    }

    public ResearchClaimComparisonResponse compare(UUID jobId, String claim, List<UUID> sourceIds) {
        ResearchJob job = jobs.get(jobId);
        if (job.getStatus() != ResearchJobStatus.COMPLETED
                && job.getStatus() != ResearchJobStatus.PARTIAL) {
            throw new ResearchJobConflictException();
        }
        if (claim == null || claim.isBlank() || claim.length() > 500) {
            throw new InvalidResearchFilterException("Enter a claim of up to 500 characters.");
        }
        if (sourceIds == null || sourceIds.isEmpty() || sourceIds.size() > 20
                || new HashSet<>(sourceIds).size() != sourceIds.size()) {
            throw new InvalidResearchFilterException("Choose one to 20 distinct sources to compare the claim.");
        }

        List<ResearchSource> selected = sources.findAllById(sourceIds);
        if (selected.size() != sourceIds.size()
                || selected.stream().anyMatch(source -> !source.getResearchJobId().equals(jobId))) {
            throw new InvalidResearchFilterException("Every selected source must belong to this research brief.");
        }

        var selectedById = selected.stream().collect(java.util.stream.Collectors.toMap(
                ResearchSource::getId,
                source -> source
        ));
        List<AiClaimComparisonRequest.Source> comparisonSources = sourceIds.stream()
                .map(selectedById::get)
                .map(source -> new AiClaimComparisonRequest.Source(
                        source.getId().toString(),
                        truncate(source.getTitle(), 500),
                        source.getUrl(),
                        source.getDomain(),
                        source.getType(),
                        truncate(source.getDescription(), 1200),
                        truncate(source.getPublisher(), 300),
                        source.getPublishedAt()
                ))
                .toList();

        ResearchClaimComparisonResponse generated = aiReportClient.compare(
                new AiClaimComparisonRequest(claim.trim(), comparisonSources)
        );
        if (job.getStatus() != ResearchJobStatus.PARTIAL) {
            return generated;
        }

        String failedProviders = jobs.listProviderRuns(jobId).stream()
                .filter(run -> run.getStatus() == ai.aura.api.domain.ResearchProviderRunStatus.FAILED
                        || run.getStatus() == ai.aura.api.domain.ResearchProviderRunStatus.SKIPPED)
                .map(run -> run.getProvider() + " (" + run.getFailureCode() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        return new ResearchClaimComparisonResponse(
                generated.generatedAt(),
                generated.provider(),
                generated.model(),
                generated.claim(),
                generated.assessment(),
                generated.explanation(),
                generated.evidence(),
                generated.limitations(),
                generated.citations(),
                "Partial search: " + failedProviders
                        + " failed or were skipped. The comparison covers only selected saved snippets. "
                        + generated.coverageNote()
        );
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
