package ai.aura.api.application;

import ai.aura.api.api.ResearchReportResponse;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchSource;
import ai.aura.api.persistence.ResearchSourceRepository;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
public class ResearchReportService {

    private final ResearchJobService jobs;
    private final ResearchSourceRepository sources;
    private final AiReportClient aiReportClient;

    public ResearchReportService(
            ResearchJobService jobs,
            ResearchSourceRepository sources,
            AiReportClient aiReportClient
    ) {
        this.jobs = jobs;
        this.sources = sources;
        this.aiReportClient = aiReportClient;
    }

    public ResearchReportResponse generate(UUID jobId, List<UUID> sourceIds) {
        ResearchJob job = jobs.get(jobId);
        if (job.getStatus() != ResearchJobStatus.COMPLETED
                && job.getStatus() != ResearchJobStatus.PARTIAL) {
            throw new ResearchJobConflictException();
        }
        if (sourceIds == null || sourceIds.isEmpty() || sourceIds.size() > 20
                || new HashSet<>(sourceIds).size() != sourceIds.size()) {
            throw new InvalidResearchFilterException("Choose one to 20 distinct sources for the report.");
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
        List<AiResearchReportRequest.Source> reportSources = sourceIds.stream()
                .map(selectedById::get)
                .map(source -> new AiResearchReportRequest.Source(
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
        ResearchReportResponse generated = aiReportClient.generate(
                new AiResearchReportRequest(job.getSubject(), reportSources)
        );
        if (job.getStatus() != ResearchJobStatus.PARTIAL) {
            return generated;
        }

        String failedProviders = jobs.listProviderRuns(jobId).stream()
                .filter(run -> run.getStatus() == ai.aura.api.domain.ResearchProviderRunStatus.FAILED)
                .map(run -> run.getProvider() + " (" + run.getFailureCode() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
        String coverageNote = "Partial search: " + failedProviders
                + " did not return results. This report summarizes only the selected saved sources and may omit evidence from failed providers. "
                + generated.coverageNote();
        return new ResearchReportResponse(
                generated.generatedAt(),
                generated.provider(),
                generated.model(),
                generated.title(),
                generated.summary(),
                generated.summarySourceIds(),
                generated.findings(),
                generated.limitations(),
                generated.citations(),
                coverageNote
        );
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
