package ai.aura.api.api;

import ai.aura.api.application.ResearchReportService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/research-jobs")
public class ResearchReportController {

    private final ResearchReportService reports;

    public ResearchReportController(ResearchReportService reports) {
        this.reports = reports;
    }

    @PostMapping("/{id}/report")
    public ResearchReportResponse generate(
            @PathVariable UUID id,
            @Valid @RequestBody GenerateResearchReportRequest request
    ) {
        return reports.generate(id, request.sourceIds());
    }
}
