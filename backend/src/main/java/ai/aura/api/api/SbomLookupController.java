package ai.aura.api.api;

import ai.aura.api.application.InvalidSbomException;
import ai.aura.api.application.CycloneDxSbomParser;
import ai.aura.api.application.SbomLookupService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/cyber/sbom")
public class SbomLookupController {

    private final SbomLookupService service;

    public SbomLookupController(SbomLookupService service) {
        this.service = service;
    }

    @PostMapping(value = "/lookup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SbomLookupResponse lookup(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new InvalidSbomException("Choose a non-empty CycloneDX, SPDX, or SARIF JSON file.");
        }
        if (file.getSize() > CycloneDxSbomParser.MAX_FILE_BYTES) {
            throw new InvalidSbomException("SBOM JSON files must be 2 MB or smaller.");
        }
        try {
            return service.lookup(file.getBytes());
        } catch (IOException exception) {
            throw new InvalidSbomException("The uploaded file could not be read.");
        }
    }
}
