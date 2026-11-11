package ai.aura.api.persistence;

import ai.aura.api.domain.ResearchProviderRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResearchProviderRunRepository extends JpaRepository<ResearchProviderRun, UUID> {
    List<ResearchProviderRun> findByResearchJobIdOrderByRunOrderAsc(UUID researchJobId);

    List<ResearchProviderRun> findByResearchJobIdInOrderByResearchJobIdAscRunOrderAsc(
            Collection<UUID> researchJobIds
    );

    Optional<ResearchProviderRun> findByResearchJobIdAndProviderAndQueryOrderAndAttempt(
            UUID researchJobId,
            String provider,
            int queryOrder,
            int attempt
    );
}
