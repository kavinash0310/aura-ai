package ai.aura.api.persistence;

import ai.aura.api.domain.ResearchSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResearchSourceRepository extends JpaRepository<ResearchSource, UUID> {
    List<ResearchSource> findByResearchJobIdOrderByRankAsc(UUID researchJobId);
    long countByResearchJobId(UUID researchJobId);
}

