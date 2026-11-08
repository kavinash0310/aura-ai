package ai.aura.api.application;

import ai.aura.api.domain.ResearchSource;
import ai.aura.api.persistence.ResearchSourceRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchPageServiceTest {
    @Test
    void fetchesOnlyASourceThatBelongsToTheRequestedBrief() {
        UUID jobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ResearchJobService jobs = mock(ResearchJobService.class);
        ResearchSourceRepository sources = mock(ResearchSourceRepository.class);
        PublicPageFetcher fetcher = mock(PublicPageFetcher.class);
        ResearchSource source = mock(ResearchSource.class);
        when(sources.findById(sourceId)).thenReturn(Optional.of(source));
        when(source.getResearchJobId()).thenReturn(jobId);
        when(source.getUrl()).thenReturn("https://example.org/report");
        when(fetcher.fetch(source.getUrl())).thenReturn(new PublicPageFetcher.FetchedPage(
                "https://example.org/report", "example.org", "Report", "Page evidence.", false, Instant.now()));

        var response = new ResearchPageService(jobs, sources, fetcher).fetch(jobId, sourceId);

        assertEquals(sourceId, response.sourceId());
        assertEquals("Page evidence.", response.text());
        assertEquals(14, response.characterCount());
        assertTrue(response.contentHash().matches("[a-f0-9]{64}"));
        verify(fetcher).fetch("https://example.org/report");
    }

    @Test
    void refusesToFetchASourceFromADifferentBrief() {
        UUID jobId = UUID.randomUUID();
        UUID otherJobId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        ResearchJobService jobs = mock(ResearchJobService.class);
        ResearchSourceRepository sources = mock(ResearchSourceRepository.class);
        PublicPageFetcher fetcher = mock(PublicPageFetcher.class);
        ResearchSource source = mock(ResearchSource.class);
        when(sources.findById(sourceId)).thenReturn(Optional.of(source));
        when(source.getResearchJobId()).thenReturn(otherJobId);

        assertThrows(InvalidResearchFilterException.class,
                () -> new ResearchPageService(jobs, sources, fetcher).fetch(jobId, sourceId));
        verify(fetcher, never()).fetch(org.mockito.ArgumentMatchers.anyString());
    }
}
