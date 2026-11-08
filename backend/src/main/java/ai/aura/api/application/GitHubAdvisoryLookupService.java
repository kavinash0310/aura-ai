package ai.aura.api.application;

import ai.aura.api.api.GitHubAdvisoryLookupResponse;
import ai.aura.api.cyber.GitHubAdvisoryProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
public class GitHubAdvisoryLookupService {
    private final GitHubAdvisoryProvider provider;

    public GitHubAdvisoryLookupService(GitHubAdvisoryProvider provider) {
        this.provider = provider;
    }

    public GitHubAdvisoryLookupResponse lookup(String suppliedCve) {
        String cve = suppliedCve.strip().toUpperCase(Locale.ROOT);
        var data = provider.lookup(cve);
        return new GitHubAdvisoryLookupResponse(
                Instant.now(), data.cve(), data.status(), data.retrievedAt(), data.advisories(),
                data.advisoriesTruncated(), data.note()
        );
    }
}
