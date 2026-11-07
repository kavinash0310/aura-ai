package ai.aura.api.api;

import ai.aura.api.application.InvalidResearchFilterException;
import ai.aura.api.application.ResearchJobService;
import ai.aura.api.application.ResearchResultService;
import ai.aura.api.application.ResearchSearchService;
import ai.aura.api.domain.ResearchJob;
import ai.aura.api.domain.SourceType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/research-jobs")
public class ResearchSearchController {

    private final ResearchSearchService searchService;
    private final ResearchResultService resultService;
    private final ResearchJobService jobService;
    private final JsonMapper jsonMapper;

    public ResearchSearchController(
            ResearchSearchService searchService,
            ResearchResultService resultService,
            ResearchJobService jobService,
            JsonMapper jsonMapper
    ) {
        this.searchService = searchService;
        this.resultService = resultService;
        this.jobService = jobService;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/{id}/search")
    public ResearchSearchResponse search(
            @PathVariable UUID id,
            @Valid @RequestBody RunResearchRequest request
    ) {
        ResearchJob job = searchService.run(id, request.toFilters(), request.provider());
        return response(job, null, null, null, null, null);
    }

    @GetMapping("/{id}/sources")
    public ResearchSearchResponse sources(
            @PathVariable UUID id,
            @RequestParam(required = false) SourceType kind,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String host,
            @RequestParam(required = false) LocalDate publishedFrom,
            @RequestParam(required = false) LocalDate publishedTo
    ) {
        ResearchJob job = jobService.get(id);
        List<ResearchSourceResponse> results = filtered(id, kind, q, host, publishedFrom, publishedTo);
        return ResearchSearchResponse.from(job, results);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "json") String format,
            @RequestParam(required = false) SourceType kind,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String host,
            @RequestParam(required = false) LocalDate publishedFrom,
            @RequestParam(required = false) LocalDate publishedTo
    ) {
        ResearchJob job = jobService.get(id);
        ResearchSearchResponse response = ResearchSearchResponse.from(
                job,
                filtered(id, kind, q, host, publishedFrom, publishedTo)
        );

        String normalizedFormat = format.strip().toLowerCase(Locale.ROOT);
        MediaType mediaType;
        String extension;
        byte[] body;
        try {
            switch (normalizedFormat) {
                case "json" -> {
                    mediaType = MediaType.APPLICATION_JSON;
                    extension = "json";
                    body = jsonMapper.writeValueAsBytes(response);
                }
                case "csv" -> {
                    mediaType = MediaType.parseMediaType("text/csv;charset=UTF-8");
                    extension = "csv";
                    body = csv(response).getBytes(StandardCharsets.UTF_8);
                }
                case "md" -> {
                    mediaType = MediaType.parseMediaType("text/markdown;charset=UTF-8");
                    extension = "md";
                    body = markdown(response).getBytes(StandardCharsets.UTF_8);
                }
                default -> throw new InvalidResearchFilterException("Export format must be json, csv, or md.");
            }
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize research export.", exception);
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"aura-research-" + id + "." + extension + "\"")
                .body(body);
    }

    private List<ResearchSourceResponse> filtered(
            UUID id,
            SourceType kind,
            String q,
            String host,
            LocalDate publishedFrom,
            LocalDate publishedTo
    ) {
        return resultService.list(id, kind, q, host, publishedFrom, publishedTo)
                .stream()
                .map(ResearchSourceResponse::from)
                .toList();
    }

    private ResearchSearchResponse response(
            ResearchJob job,
            SourceType kind,
            String q,
            String host,
            LocalDate from,
            LocalDate to
    ) {
        return ResearchSearchResponse.from(job, filtered(job.getId(), kind, q, host, from, to));
    }

    private static String csv(ResearchSearchResponse response) {
        StringBuilder out = new StringBuilder();
        out.append("provider,rank,kind,title,url,domain,publisher,publishedAt,retrievedAt,description\r\n");
        for (ResearchSourceResponse source : response.results()) {
            out.append(cell(source.provider())).append(',')
                    .append(source.rank()).append(',')
                    .append(cell(source.kind().name())).append(',')
                    .append(cell(source.title())).append(',')
                    .append(cell(source.url())).append(',')
                    .append(cell(source.domain())).append(',')
                    .append(cell(source.publisher())).append(',')
                    .append(cell(source.publishedAt() == null ? "" : source.publishedAt().toString())).append(',')
                    .append(cell(source.retrievedAt().toString())).append(',')
                    .append(cell(source.description()))
                    .append("\r\n");
        }
        return out.toString();
    }

    private static String cell(String value) {
        if (value == null) {
            return "\"\"";
        }
        String safe = value;
        String trimmed = value.stripLeading();
        if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0) {
            safe = "'" + value;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String markdown(ResearchSearchResponse response) {
        StringBuilder out = new StringBuilder();
        out.append("# Research results: ").append(markdownValue(response.query())).append("\n\n")
                .append("Provider: ").append(markdownValue(response.provider())).append("  \n")
                .append("Searched at: ").append(markdownValue(response.searchedAt() == null ? "" : response.searchedAt().toString())).append("  \n")
                .append("Coverage: ").append(markdownValue(response.coverageNote())).append("\n\n");
        for (ResearchSourceResponse source : response.results()) {
            out.append("## ").append(source.rank()).append(". ").append(markdownValue(source.title())).append("\n\n")
                    .append("- Provider: ").append(markdownValue(source.provider())).append("\n")
                    .append("- Type: ").append(markdownValue(source.kind().name())).append("\n")
                    .append("- Publisher: ").append(markdownValue(source.publisher())).append("\n")
                    .append("- Published: ").append(markdownValue(source.publishedAt() == null ? "unknown" : source.publishedAt().toString())).append("\n")
                    .append("- Source: ").append(markdownValue(source.url())).append("\n\n");
            if (source.description() != null && !source.description().isBlank()) {
                out.append(markdownValue(source.description())).append("\n\n");
            }
        }
        return out.toString();
    }

    private static String markdownValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\R", " ")
                .replaceAll("[\\p{Cntrl}]", " ")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("#", "\\#")
                .replace("|", "\\|");
    }
}


