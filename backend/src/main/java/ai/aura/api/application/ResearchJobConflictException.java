package ai.aura.api.application;

public class ResearchJobConflictException extends RuntimeException {
    public ResearchJobConflictException() {
        super("This research job is not in a state that allows this operation.");
    }
}


