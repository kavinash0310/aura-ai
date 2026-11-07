package ai.aura.api.application;

import java.util.UUID;

public class ResearchDocumentNotFoundException extends RuntimeException {
    public ResearchDocumentNotFoundException(UUID id) {
        super("Research document not found: " + id);
    }
}
