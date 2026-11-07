package ai.aura.api.api;

import ai.aura.api.application.ResearchJobService;
import ai.aura.api.application.InvalidResearchFilterException;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.ResearchProviderRun;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.List;
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
                service.create(request.subject(), request.mode()),
                List.of()
        );
        URI location = URI.create("/api/v1/research-jobs/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public List<ResearchJobResponse> list(@RequestParam(defaultValue = "25") int limit) {
        if (limit < 1 || limit > 100) {
            throw new InvalidResearchFilterException("History limit must be from 1 to 100.");
        }
        List<ResearchJob> jobs = service.listRecent(limit);
        Map<UUID, List<ResearchProviderRun>> runs = service.listProviderRuns(
                jobs.stream().map(ResearchJob::getId).toList()
        );
        return jobs.stream()
                .map(job -> ResearchJobResponse.from(job, runs.getOrDefault(job.getId(), List.of())))
                .toList();
    }

    @GetMapping("/{id}")
    public ResearchJobResponse get(@PathVariable UUID id) {
        return ResearchJobResponse.from(service.get(id), service.listProviderRuns(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
