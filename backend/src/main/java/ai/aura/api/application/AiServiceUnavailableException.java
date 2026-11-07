package ai.aura.api.application;

public class AiServiceUnavailableException extends RuntimeException {
    public AiServiceUnavailableException() {
        super("The local report model is disabled or unavailable.");
    }
}
