package ai.aura.api.application;

import ai.aura.api.api.NvdLookupResponse;
import ai.aura.api.cyber.NvdCveData;
import ai.aura.api.cyber.NvdProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;

@Service
public class NvdLookupService {
    private final NvdProvider provider;

    public NvdLookupService(NvdProvider provider) {
        this.provider = provider;
    }

    public NvdLookupResponse lookup(String suppliedCve) {
        String cve = suppliedCve.strip().toUpperCase(Locale.ROOT);
        Instant requestedAt = Instant.now();
        NvdCveData data = provider.lookup(cve);
        return new NvdLookupResponse(
                requestedAt,
                data.cve(),
                data.status(),
                data.retrievedAt(),
                NvdProvider.SOURCE_URL + cve,
                data.note(),
                data.publishedAt(),
                data.lastModifiedAt(),
                data.vulnerabilityStatus(),
                data.description(),
                data.cvssVersion(),
                data.cvssScore(),
                data.cvssSeverity(),
                data.cvssVector(),
                data.cwes(),
                data.applicability(),
                data.applicabilityTruncated(),
                data.references(),
                data.referencesTruncated()
        );
    }
}
