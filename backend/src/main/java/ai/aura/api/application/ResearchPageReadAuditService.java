package ai.aura.api.application;

import ai.aura.api.domain.ResearchPageRead;
import ai.aura.api.domain.ResearchPageReadStatus;
import ai.aura.api.persistence.ResearchPageReadRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ResearchPageReadAuditService {
    private final ResearchPageReadRepository reads;

    public ResearchPageReadAuditService(ResearchPageReadRepository reads) {
        this.reads = reads;
    }

    public ResearchPageRead start(UUID jobId, UUID sourceId) {
        return reads.save(ResearchPageRead.running(jobId, sourceId, Instant.now()));
    }

    @Transactional
    public void succeed(UUID readId, String hash, int characterCount, boolean truncated, Instant completedAt) {
        requireRunning(readId).succeed(hash, characterCount, truncated, completedAt);
    }

    @Transactional
    public void fail(UUID readId, String failureCode, Instant completedAt) {
        requireRunning(readId).fail(failureCode, completedAt);
    }

    @Transactional(readOnly = true)
    public List<ResearchPageRead> listRecent(UUID jobId, UUID sourceId, int limit) {
        return reads.findByResearchJobIdAndSourceIdOrderByRequestedAtDesc(jobId, sourceId, PageRequest.of(0, limit));
    }

    @Transactional
    public void recoverInterruptedReads() {
        Instant recoveredAt = Instant.now();
        List<ResearchPageRead> interrupted = reads.findByStatusOrderByRequestedAtAsc(ResearchPageReadStatus.RUNNING);
        interrupted.forEach(read -> read.abandon(recoveredAt));
    }

    private ResearchPageRead requireRunning(UUID readId) {
        return reads.findById(readId).orElseThrow(() -> new IllegalStateException("Page read audit record is missing."));
    }
}
