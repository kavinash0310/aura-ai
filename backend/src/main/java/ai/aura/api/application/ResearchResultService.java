package ai.aura.api.application;

import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchSource;
import ai.aura.api.domain.SourceType;
import ai.aura.api.persistence.ResearchSourceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResearchResultService {

    private final ResearchJobService jobs;
    private final ResearchSourceRepository sources;

    public ResearchResultService(ResearchJobService jobs, ResearchSourceRepository sources) {
        this.jobs = jobs;
        this.sources = sources;
    }

    public List<ResearchSource> list(
            UUID jobId,
            SourceType kind,
            String provider,
            String query,
            String host,
            LocalDate publishedFrom,
            LocalDate publishedTo
    ) {
        ResearchJob job = jobs.get(jobId);
        if (job.getStatus() != ResearchJobStatus.COMPLETED
                && job.getStatus() != ResearchJobStatus.PARTIAL) {
            throw new ResearchJobConflictException();
        }

        String normalizedQuery = query == null ? null : query.strip().toLowerCase(Locale.ROOT);
        String normalizedHost = host == null ? null : host.strip().toLowerCase(Locale.ROOT);
        String normalizedProvider = provider == null || provider.isBlank()
                ? null
                : provider.strip().toLowerCase(Locale.ROOT);
        if (normalizedQuery != null && normalizedQuery.length() > 200) {
            throw new InvalidResearchFilterException("The text filter is too long.");
        }
        if (normalizedHost != null && normalizedHost.length() > 253) {
            throw new InvalidResearchFilterException("The host filter is too long.");
        }
        if (normalizedProvider != null && !normalizedProvider.matches("[a-z][a-z0-9-]{0,31}")) {
            throw new InvalidResearchFilterException("The provider filter is invalid.");
        }
        if (publishedFrom != null && publishedTo != null && publishedFrom.isAfter(publishedTo)) {
            throw new InvalidResearchFilterException("The publication date range is reversed.");
        }

        return sources.findByResearchJobIdOrderByRankAsc(jobId).stream()
                .filter(source -> kind == null || source.getType() == kind)
                .filter(source -> normalizedProvider == null || source.getProvider().equals(normalizedProvider))
                .filter(source -> matchesQuery(source, normalizedQuery))
                .filter(source -> matchesHost(source, normalizedHost))
                .filter(source -> matchesDate(source, publishedFrom, publishedTo))
                .toList();
    }

    private static boolean matchesQuery(ResearchSource source, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return contains(source.getTitle(), query)
                || contains(source.getDescription(), query)
                || contains(source.getPublisher(), query)
                || contains(source.getDomain(), query)
                || contains(source.getSearchQuery(), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean matchesHost(ResearchSource source, String host) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String domain = source.getDomain().toLowerCase(Locale.ROOT);
        return domain.equals(host) || domain.endsWith("." + host);
    }

    private static boolean matchesDate(ResearchSource source, LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return true;
        }
        if (source.getPublishedAt() == null) {
            return false;
        }
        LocalDate published = source.getPublishedAt().atZone(ZoneOffset.UTC).toLocalDate();
        return (from == null || !published.isBefore(from))
                && (to == null || !published.isAfter(to));
    }
}


