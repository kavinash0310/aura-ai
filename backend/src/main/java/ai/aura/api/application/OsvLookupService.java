package ai.aura.api.application;

import ai.aura.api.api.OsvLookupResponse;
import ai.aura.api.cyber.OsvProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
public class OsvLookupService {
    private final OsvProvider provider;

    public OsvLookupService(OsvProvider provider) {
        this.provider = provider;
    }

    public OsvLookupResponse lookup(String suppliedCve) {
        String cve = suppliedCve.strip().toUpperCase(Locale.ROOT);
        var data = provider.lookup(cve);
        return new OsvLookupResponse(
                Instant.now(),
                data.cve(),
                data.status(),
                data.retrievedAt(),
                OsvProvider.SOURCE_URL + cve,
                data.osvId(),
                data.summary(),
                data.details(),
                data.publishedAt(),
                data.modifiedAt(),
                data.withdrawnAt(),
                data.aliases(),
                data.affectedPackages(),
                data.affectedPackagesTruncated(),
                data.references(),
                data.referencesTruncated(),
                data.note()
        );
    }
}
