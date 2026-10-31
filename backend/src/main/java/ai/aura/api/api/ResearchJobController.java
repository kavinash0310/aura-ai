package ai.aura.api.api;

import ai.aura.api.application.ResearchJobService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/research-jobs")
public class ResearchJobController {

    private final ResearchJobService service;

    public ResearchJobController(ResearchJobService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ResearchJobResponse> create(@Valid @RequestBody CreateResearchJobRequest request) {
        ResearchJobResponse response = ResearchJobResponse.from(
                service.create(request.subject(), request.mode())
        );
        URI location = URI.create("/api/v1/research-jobs/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResearchJobResponse get(@PathVariable UUID id) {
        return ResearchJobResponse.from(service.get(id));
    }
}
