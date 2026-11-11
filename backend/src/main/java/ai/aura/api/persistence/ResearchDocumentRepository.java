package ai.aura.api.persistence;

import ai.aura.api.domain.ResearchDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResearchDocumentRepository extends JpaRepository<ResearchDocument, UUID> {
    @Query("""
            select document from ResearchDocument document
            where document.versionNumber = (
                select max(latest.versionNumber) from ResearchDocument latest
                where latest.logicalDocumentId = document.logicalDocumentId
            )
            order by document.uploadedAt desc
            """)
    List<ResearchDocument> findLatestDocuments(Pageable pageable);

    @Query("""
            select count(document) from ResearchDocument document
            where document.versionNumber = (
                select max(latest.versionNumber) from ResearchDocument latest
                where latest.logicalDocumentId = document.logicalDocumentId
            )
            """)
    long countLatestDocuments();

    List<ResearchDocument> findByLogicalDocumentIdOrderByVersionNumberDesc(UUID logicalDocumentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select document from ResearchDocument document
            where document.logicalDocumentId = :logicalDocumentId
              and document.versionNumber = 1
            """)
    Optional<ResearchDocument> lockVersionGroup(@Param("logicalDocumentId") UUID logicalDocumentId);

    @Query("""
            select document from ResearchDocument document
            where document.versionNumber = (
                select max(latest.versionNumber) from ResearchDocument latest
                where latest.logicalDocumentId = document.logicalDocumentId
            )
              and lower(document.content) like lower(concat('%', :phrase, '%'))
            order by document.uploadedAt desc
            """)
    List<ResearchDocument> searchLatestByPhrase(@Param("phrase") String phrase, Pageable pageable);

    void deleteByLogicalDocumentId(UUID logicalDocumentId);
}
