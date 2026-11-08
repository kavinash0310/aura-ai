package ai.aura.api.api;

import java.time.Instant;
import java.util.List;

public record NvdBatchLookupResponse(Instant requestedAt, List<NvdLookupResponse> results) {
    public NvdBatchLookupResponse {
        results = List.copyOf(results);
    }
}
