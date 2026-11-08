package ai.aura.api.api;

import ai.aura.api.application.OsvLookupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cyber/vulnerabilities/osv")
public class OsvLookupController {
    private final OsvLookupService service;

    public OsvLookupController(OsvLookupService service) {
        this.service = service;
    }

    @PostMapping("/lookup")
    public OsvLookupResponse lookup(@Valid @RequestBody LookupOsvRequest request) {
        return service.lookup(request.cve());
    }
}
