package ai.aura.api.api;

import ai.aura.api.application.ResearchPageService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
