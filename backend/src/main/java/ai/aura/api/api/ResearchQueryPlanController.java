package ai.aura.api.api;

import ai.aura.api.application.AiReportClient;
import ai.aura.api.application.AiResearchQueryPlanRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/research")
public class ResearchQueryPlanController {

    private final AiReportClient aiReportClient;

    public ResearchQueryPlanController(AiReportClient aiReportClient) {
        this.aiReportClient = aiReportClient;
    }

    @PostMapping("/query-plan")
    public ResearchQueryPlanResponse suggest(
            @Valid @RequestBody SuggestResearchQueriesRequest request
    ) {
        return aiReportClient.suggestQueries(new AiResearchQueryPlanRequest(request.topic().strip()));
    }
}
