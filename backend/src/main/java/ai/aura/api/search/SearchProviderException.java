package ai.aura.api.search;

public class SearchProviderException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    public SearchProviderException(String code, boolean retryable) {
        super("The configured search provider could not complete the request.");
        this.code = code;
        this.retryable = retryable;
    }

    public String getCode() { return code; }
    public boolean isRetryable() { return retryable; }
}


