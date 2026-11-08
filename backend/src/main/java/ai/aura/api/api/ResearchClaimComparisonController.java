package ai.aura.api.api;

import ai.aura.api.application.ResearchClaimComparisonService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/research-jobs")
public class ResearchClaimComparisonController {

    private final ResearchClaimComparisonService comparisons;

    public ResearchClaimComparisonController(ResearchClaimComparisonService comparisons) {
        this.comparisons = comparisons;
    }

    @PostMapping("/{id}/compare")
    public ResearchClaimComparisonResponse compare(
            @PathVariable UUID id,
            @Valid @RequestBody CompareResearchClaimRequest request
    ) {
        return comparisons.compare(id, request.claim(), request.sourceIds());
    }
}
