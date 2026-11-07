package ai.aura.api.api;

import ai.aura.api.application.NvdLookupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cyber/vulnerabilities/nvd")
public class NvdLookupController {
    private final NvdLookupService service;

    public NvdLookupController(NvdLookupService service) {
        this.service = service;
    }

    @PostMapping("/lookup")
    public NvdLookupResponse lookup(@Valid @RequestBody LookupNvdRequest request) {
        return service.lookup(request.cve());
    }
}
