package ai.aura.api.api;

import ai.aura.api.application.ResearchDocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class ResearchDocumentController {
    private final ResearchDocumentService service;

    public ResearchDocumentController(ResearchDocumentService service) {
        this.service = service;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ResearchDocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title) {
        ResearchDocumentResponse response = ResearchDocumentResponse.from(service.importDocument(file, title), false);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + response.id())).body(response);
    }

    @PostMapping(path = "/{id}/versions", consumes = "multipart/form-data")
    public ResponseEntity<ResearchDocumentResponse> uploadVersion(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title) {
        ResearchDocumentResponse response = ResearchDocumentResponse.from(service.importVersion(id, file, title), false);
        return ResponseEntity.created(URI.create("/api/v1/documents/" + response.id())).body(response);
    }

    @GetMapping
    public List<ResearchDocumentResponse> list() {
        return service.list().stream().map(document -> ResearchDocumentResponse.from(document, false)).toList();
    }

    @GetMapping("/search")
    public List<ResearchDocumentSearchHit> search(@RequestParam("q") String query) {
        return service.search(query).stream().map(document -> ResearchDocumentSearchHit.from(document, query.strip())).toList();
    }

    @GetMapping("/{id}")
    public ResearchDocumentResponse get(@PathVariable UUID id) {
        return ResearchDocumentResponse.from(service.get(id), true);
    }

    @GetMapping("/{id}/versions")
    public List<ResearchDocumentResponse> versions(@PathVariable UUID id) {
        return service.versions(id).stream().map(document -> ResearchDocumentResponse.from(document, false)).toList();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
