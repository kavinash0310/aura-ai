package ai.aura.api.application;

import ai.aura.api.api.ResearchPageResponse;
import ai.aura.api.domain.ResearchSource;
import ai.aura.api.persistence.ResearchSourceRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class ResearchPageService {
    private final ResearchJobService jobs;
    private final ResearchSourceRepository sources;
    private final PublicPageFetcher fetcher;

    public ResearchPageService(ResearchJobService jobs, ResearchSourceRepository sources, PublicPageFetcher fetcher) {
        this.jobs = jobs;
        this.sources = sources;
        this.fetcher = fetcher;
    }

    public ResearchPageResponse fetch(UUID jobId, UUID sourceId) {
        jobs.get(jobId);
        ResearchSource source = sources.findById(sourceId)
                .filter(candidate -> candidate.getResearchJobId().equals(jobId))
                .orElseThrow(() -> new InvalidResearchFilterException(
                        "Choose a source saved in this research brief."));
        PublicPageFetcher.FetchedPage page = fetcher.fetch(source.getUrl());
        return ResearchPageResponse.from(sourceId, page, sha256(page.text()));
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
