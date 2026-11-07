package ai.aura.api.application;

public class InvalidSbomException extends RuntimeException {
    public InvalidSbomException(String message) {
        super(message);
    }
}
