package ai.aura.api.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ProviderRunOutcomes {

    private ProviderRunOutcomes() {
    }

    public static List<ResearchProviderRun> latestAttempts(List<ResearchProviderRun> runs) {
        Map<ProviderQuery, ResearchProviderRun> latest = new LinkedHashMap<>();
        for (ResearchProviderRun run : runs) {
            ProviderQuery key = new ProviderQuery(run.getProvider(), run.getQueryOrder());
            latest.merge(key, run, (current, candidate) ->
                    candidate.getAttempt() > current.getAttempt() ? candidate : current);
        }
        return List.copyOf(latest.values());
    }

    private record ProviderQuery(String provider, int queryOrder) {
    }
}
