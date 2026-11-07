ALTER TABLE research_documents
    ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', coalesce(content, ''))) STORED;

CREATE INDEX idx_research_documents_search_vector
    ON research_documents USING GIN (search_vector);
