package ai.aura.api.search;

public class SearchProviderNotConfiguredException extends RuntimeException {
    public SearchProviderNotConfiguredException() {
        super("No public search provider is configured.");
    }
}


