package ai.aura.api.api;

import ai.aura.api.application.ResearchPageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/research-jobs")
public class ResearchPageController {
    private final ResearchPageService service;

    public ResearchPageController(ResearchPageService service) {
        this.service = service;
    }

    @PostMapping("/{jobId}/sources/{sourceId}/page")
    public ResearchPageResponse fetchPage(@PathVariable UUID jobId, @PathVariable UUID sourceId) {
        return service.fetch(jobId, sourceId);
    }

    @GetMapping("/{jobId}/sources/{sourceId}/page-reads")
    public List<ResearchPageReadAuditResponse> listPageReads(
            @PathVariable UUID jobId,
            @PathVariable UUID sourceId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return service.listReads(jobId, sourceId, limit);
    }
}
