package ai.aura.api.persistence;

import ai.aura.api.domain.ResearchDocument;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Repository
public class ResearchDocumentSearch {
    private static final String POSTGRES_SEARCH = """
            SELECT d.id, d.title, d.original_filename, d.media_type, d.content_hash,
                   d.content, d.character_count, d.uploaded_at
            FROM research_documents d
            WHERE d.search_vector @@ plainto_tsquery('simple', :phrase)
              AND strpos(lower(d.content), lower(:phrase)) > 0
            ORDER BY ts_rank(d.search_vector, plainto_tsquery('simple', :phrase)) DESC,
                     d.uploaded_at DESC
            LIMIT :limit OFFSET :offset
            """;

    private final DataSource dataSource;
    private final ResearchDocumentRepository repository;

    @PersistenceContext
    private EntityManager entityManager;

    private boolean postgresql;

    public ResearchDocumentSearch(DataSource dataSource, ResearchDocumentRepository repository) {
        this.dataSource = dataSource;
        this.repository = repository;
    }

    @PostConstruct
    void detectDatabaseProduct() {
        try (Connection connection = dataSource.getConnection()) {
            postgresql = "PostgreSQL".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Could not identify the database used for document search.", exception);
        }
    }

    public List<ResearchDocument> searchExactPhrase(String phrase, Pageable pageable) {
        if (!postgresql || phrase.codePoints().noneMatch(Character::isLetterOrDigit)) {
            return repository.findByContentContainingIgnoreCaseOrderByUploadedAtDesc(phrase, pageable);
        }

        return entityManager.createNativeQuery(POSTGRES_SEARCH, ResearchDocument.class)
                .setParameter("phrase", phrase)
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset())
                .getResultList();
    }
}
