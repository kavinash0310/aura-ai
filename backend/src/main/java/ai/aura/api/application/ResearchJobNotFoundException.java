package ai.aura.api.application;

import java.util.UUID;

public class ResearchJobNotFoundException extends RuntimeException {

    public ResearchJobNotFoundException(UUID id) {
        super("Research job not found: " + id);
    }
}
