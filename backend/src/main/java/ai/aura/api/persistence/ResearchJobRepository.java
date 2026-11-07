package ai.aura.api.persistence;

import ai.aura.api.domain.ResearchJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ResearchJobRepository extends JpaRepository<ResearchJob, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ResearchJob job where job.id = :id")
    Optional<ResearchJob> findByIdForUpdate(@Param("id") UUID id);
}

