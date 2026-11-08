package ai.aura.api.persistence;

import ai.aura.api.domain.ResearchDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResearchDocumentRepository extends JpaRepository<ResearchDocument, UUID> {
    List<ResearchDocument> findAllByOrderByUploadedAtDesc(Pageable pageable);

    List<ResearchDocument> findByContentContainingIgnoreCaseOrderByUploadedAtDesc(String phrase, Pageable pageable);
}
