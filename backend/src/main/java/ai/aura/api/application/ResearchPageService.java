package ai.aura.api.application;

import ai.aura.api.api.ResearchPageResponse;
import ai.aura.api.api.ResearchPageReadAuditResponse;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchSource;
import ai.aura.api.persistence.ResearchSourceRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class ResearchPageService {
    private final ResearchJobService jobs;
    private final ResearchSourceRepository sources;
    private final PublicPageFetcher fetcher;
    private final ResearchPageReadAuditService audit;

    public ResearchPageService(ResearchJobService jobs, ResearchSourceRepository sources, PublicPageFetcher fetcher,
                               ResearchPageReadAuditService audit) {
        this.jobs = jobs;
        this.sources = sources;
        this.fetcher = fetcher;
        this.audit = audit;
    }

    public ResearchPageResponse fetch(UUID jobId, UUID sourceId) {
        ResearchSource source = findCompletedJobSource(jobId, sourceId);
        var read = audit.start(jobId, sourceId);
        try {
            PublicPageFetcher.FetchedPage page = fetcher.fetch(source.getUrl());
            String contentHash = sha256(page.text());
            audit.succeed(read.getId(), contentHash, page.text().length(), page.truncated(), page.fetchedAt());
            return ResearchPageResponse.from(read.getId(), sourceId, page, contentHash);
        } catch (PublicPageFetchException exception) {
            audit.fail(read.getId(), exception.getCode(), Instant.now());
            throw exception;
        }
    }

    public List<ResearchPageReadAuditResponse> listReads(UUID jobId, UUID sourceId, int limit) {
        findCompletedJobSource(jobId, sourceId);
        if (limit < 1 || limit > 100) {
            throw new InvalidResearchFilterException("Page-read history limit must be from 1 to 100.");
        }
        return audit.listRecent(jobId, sourceId, limit).stream().map(ResearchPageReadAuditResponse::from).toList();
    }

    private ResearchSource findCompletedJobSource(UUID jobId, UUID sourceId) {
        ResearchJob job = jobs.get(jobId);
        if (job.getStatus() != ResearchJobStatus.COMPLETED && job.getStatus() != ResearchJobStatus.PARTIAL) {
            throw new InvalidResearchFilterException("Wait for the research brief to finish before reading a source.");
        }
        return sources.findById(sourceId)
                .filter(candidate -> candidate.getResearchJobId().equals(jobId))
                .orElseThrow(() -> new InvalidResearchFilterException(
                        "Choose a source saved in this research brief."));
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
