package ai.aura.api.api;

import ai.aura.api.application.GitHubAdvisoryLookupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cyber/vulnerabilities/github-advisories")
public class GitHubAdvisoryLookupController {
    private final GitHubAdvisoryLookupService service;

    public GitHubAdvisoryLookupController(GitHubAdvisoryLookupService service) {
        this.service = service;
    }

    @PostMapping("/lookup")
    public GitHubAdvisoryLookupResponse lookup(@Valid @RequestBody LookupGitHubAdvisoryRequest request) {
        return service.lookup(request.cve());
    }
}
