package ai.aura.api.api;

import ai.aura.api.application.InvalidResearchFilterException;
import ai.aura.api.application.AiServiceUnavailableException;
import ai.aura.api.application.InvalidDocumentException;
import ai.aura.api.application.InvalidSbomException;
import ai.aura.api.application.PublicPageFetchException;
import ai.aura.api.application.ResearchJobConflictException;
import ai.aura.api.application.ResearchJobNotFoundException;
import ai.aura.api.application.ResearchDocumentNotFoundException;
import ai.aura.api.search.SearchProviderException;
import ai.aura.api.search.SearchProviderNotConfiguredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResearchJobNotFoundException.class)
    ProblemDetail handleNotFound(ResearchJobNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "The requested research job does not exist.",
                "Research job not found", "RESEARCH_JOB_NOT_FOUND");
    }

    @ExceptionHandler(ResearchDocumentNotFoundException.class)
    ProblemDetail handleDocumentNotFound(ResearchDocumentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "The requested research document does not exist.",
                "Research document not found", "RESEARCH_DOCUMENT_NOT_FOUND");
    }

    @ExceptionHandler(InvalidDocumentException.class)
    ProblemDetail handleInvalidDocument(InvalidDocumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(),
                "Invalid research document", "INVALID_DOCUMENT");
    }

    @ExceptionHandler(ResearchJobConflictException.class)
    ProblemDetail handleConflict(ResearchJobConflictException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage(),
                "Research job state conflict", "RESEARCH_JOB_STATE_CONFLICT");
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    ProblemDetail handleAiServiceUnavailable(AiServiceUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(),
                "Local AI service unavailable", "AI_SERVICE_UNAVAILABLE");
    }

    @ExceptionHandler(SearchProviderNotConfiguredException.class)
    ProblemDetail handleProviderNotConfigured(SearchProviderNotConfiguredException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(),
                "Search provider is not configured", "SEARCH_PROVIDER_NOT_CONFIGURED");
    }

    @ExceptionHandler(SearchProviderException.class)
    ProblemDetail handleProviderFailure(SearchProviderException exception) {
        HttpStatus status = exception.getCode().equals("SEARCH_PROVIDER_RATE_LIMITED")
                || exception.getCode().equals("SEARCH_PROVIDER_UNAVAILABLE")
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.BAD_GATEWAY;
        return problem(status, "The public search provider did not return usable results.",
                "Search provider error", exception.getCode());
    }

    @ExceptionHandler(PublicPageFetchException.class)
    ProblemDetail handlePublicPageFetch(PublicPageFetchException exception) {
        return problem(exception.getStatus(), exception.getMessage(),
                "Public page could not be read", exception.getCode());
    }

    @ExceptionHandler(InvalidResearchFilterException.class)
    ProblemDetail handleInvalidFilter(InvalidResearchFilterException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(),
                "Invalid research filter", "INVALID_RESEARCH_FILTER");
    }

    @ExceptionHandler(InvalidSbomException.class)
    ProblemDetail handleInvalidSbom(InvalidSbomException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage(),
                "Invalid SBOM", "INVALID_SBOM");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleOversizedUpload(MaxUploadSizeExceededException exception) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded files exceed the configured request size limit.",
                "Upload is too large", "UPLOAD_TOO_LARGE");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage())
        );
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                "One or more request fields are invalid.",
                "Validation failed",
                "VALIDATION_FAILED"
        );
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    private static ProblemDetail problem(HttpStatus status, String detail, String title, String code) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("code", code);
        return problem;
    }
}

