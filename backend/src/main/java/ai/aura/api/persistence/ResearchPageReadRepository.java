package ai.aura.api.persistence;

import ai.aura.api.domain.ResearchPageRead;
import ai.aura.api.domain.ResearchPageReadStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResearchPageReadRepository extends JpaRepository<ResearchPageRead, UUID> {
    List<ResearchPageRead> findByResearchJobIdAndSourceIdOrderByRequestedAtDesc(
            UUID researchJobId, UUID sourceId, Pageable pageable);

    List<ResearchPageRead> findByStatusOrderByRequestedAtAsc(ResearchPageReadStatus status);
}
