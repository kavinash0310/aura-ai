package ai.aura.api.persistence;

import ai.aura.api.domain.ResearchJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ResearchJobRepository extends JpaRepository<ResearchJob, UUID> {
}
