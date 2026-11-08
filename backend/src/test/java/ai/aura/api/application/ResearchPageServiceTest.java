package ai.aura.api.application;

import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchJobStatus;
import ai.aura.api.domain.ResearchPageRead;
import ai.aura.api.domain.ResearchSource;
import ai.aura.api.persistence.ResearchSourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchPageServiceTest {
    @Test
    void fetchesOnlyACompletedBriefSourceAndRecordsMetadataWithoutPageText() {
        UUID jobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ResearchJobService jobs = mock(ResearchJobService.class);
        ResearchSourceRepository sources = mock(ResearchSourceRepository.class);
        PublicPageFetcher fetcher = mock(PublicPageFetcher.class);
        ResearchPageReadAuditService audit = mock(ResearchPageReadAuditService.class);
        completedJob(jobs, jobId);
        source(sources, sourceId, jobId, "https://example.org/report");
        ResearchPageRead read = ResearchPageRead.running(jobId, sourceId, Instant.now());
        when(audit.start(jobId, sourceId)).thenReturn(read);
        when(fetcher.fetch("https://example.org/report")).thenReturn(new PublicPageFetcher.FetchedPage(
                "https://example.org/report", "example.org", "Report", "Page evidence.", false, Instant.now()));

        var response = new ResearchPageService(jobs, sources, fetcher, audit).fetch(jobId, sourceId);

        assertEquals(read.getId(), response.readId());
        assertEquals(sourceId, response.sourceId());
        assertEquals("Page evidence.", response.text());
        assertEquals(14, response.characterCount());
        assertTrue(response.contentHash().matches("[a-f0-9]{64}"));
        verify(fetcher).fetch("https://example.org/report");
        verify(audit).succeed(eq(read.getId()), eq(response.contentHash()), eq(14), eq(false), any(Instant.class));
    }

    @Test
    void failedFetchIsRecordedWithTheStableErrorCode() {
        UUID jobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ResearchJobService jobs = mock(ResearchJobService.class);
        ResearchSourceRepository sources = mock(ResearchSourceRepository.class);
        PublicPageFetcher fetcher = mock(PublicPageFetcher.class);
        ResearchPageReadAuditService audit = mock(ResearchPageReadAuditService.class);
        completedJob(jobs, jobId);
        source(sources, sourceId, jobId, "https://example.org/report");
        ResearchPageRead read = ResearchPageRead.running(jobId, sourceId, Instant.now());
        when(audit.start(jobId, sourceId)).thenReturn(read);
        when(fetcher.fetch("https://example.org/report")).thenThrow(new PublicPageFetchException(
                HttpStatus.BAD_GATEWAY, "PUBLIC_PAGE_FETCH_FAILED", "Unavailable"));

        assertThrows(PublicPageFetchException.class,
                () -> new ResearchPageService(jobs, sources, fetcher, audit).fetch(jobId, sourceId));
        verify(audit).fail(eq(read.getId()), eq("PUBLIC_PAGE_FETCH_FAILED"), any(Instant.class));
    }

    @Test
    void refusesIncompleteJobsAndSourcesFromAnotherBriefBeforeStartingAnAudit() {
        UUID jobId = UUID.randomUUID();
        UUID otherJobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ResearchJobService jobs = mock(ResearchJobService.class);
        ResearchSourceRepository sources = mock(ResearchSourceRepository.class);
        PublicPageFetcher fetcher = mock(PublicPageFetcher.class);
        ResearchPageReadAuditService audit = mock(ResearchPageReadAuditService.class);
        ResearchJob incomplete = mock(ResearchJob.class);
        when(jobs.get(jobId)).thenReturn(incomplete);
        when(incomplete.getStatus()).thenReturn(ResearchJobStatus.SEARCHING);
        ResearchPageService service = new ResearchPageService(jobs, sources, fetcher, audit);

        assertThrows(InvalidResearchFilterException.class, () -> service.fetch(jobId, sourceId));
        verify(sources, never()).findById(sourceId);

        when(incomplete.getStatus()).thenReturn(ResearchJobStatus.COMPLETED);
        ResearchSource source = mock(ResearchSource.class);
        when(sources.findById(sourceId)).thenReturn(Optional.of(source));
        when(source.getResearchJobId()).thenReturn(otherJobId);
        assertThrows(InvalidResearchFilterException.class, () -> service.fetch(jobId, sourceId));
        verify(fetcher, never()).fetch(org.mockito.ArgumentMatchers.anyString());
        verify(audit, never()).start(jobId, sourceId);
    }

    @Test
    void exposesOnlyRecentAuditRowsForAValidJobAndSourcePair() {
        UUID jobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ResearchJobService jobs = mock(ResearchJobService.class);
        ResearchSourceRepository sources = mock(ResearchSourceRepository.class);
        PublicPageFetcher fetcher = mock(PublicPageFetcher.class);
        ResearchPageReadAuditService audit = mock(ResearchPageReadAuditService.class);
        completedJob(jobs, jobId);
        source(sources, sourceId, jobId, "https://example.org/report");
        ResearchPageRead read = ResearchPageRead.running(jobId, sourceId, Instant.now());
        when(audit.listRecent(jobId, sourceId, 10)).thenReturn(List.of(read));

        var history = new ResearchPageService(jobs, sources, fetcher, audit).listReads(jobId, sourceId, 10);

        assertEquals(1, history.size());
        assertEquals(read.getId(), history.get(0).id());
        verify(audit).listRecent(jobId, sourceId, 10);
        assertThrows(InvalidResearchFilterException.class,
                () -> new ResearchPageService(jobs, sources, fetcher, audit).listReads(jobId, sourceId, 101));
    }

    private static void completedJob(ResearchJobService jobs, UUID jobId) {
        ResearchJob job = mock(ResearchJob.class);
        when(jobs.get(jobId)).thenReturn(job);
        when(job.getStatus()).thenReturn(ResearchJobStatus.COMPLETED);
    }

    private static void source(ResearchSourceRepository sources, UUID sourceId, UUID jobId, String url) {
        ResearchSource source = mock(ResearchSource.class);
        when(sources.findById(sourceId)).thenReturn(Optional.of(source));
        when(source.getResearchJobId()).thenReturn(jobId);
        when(source.getUrl()).thenReturn(url);
    }
}
