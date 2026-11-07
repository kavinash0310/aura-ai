CREATE TABLE research_documents (
    id UUID PRIMARY KEY,
    title VARCHAR(300) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    media_type VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    character_count INTEGER NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_research_documents_character_count CHECK (character_count >= 0)
);

CREATE INDEX idx_research_documents_uploaded_at
    ON research_documents (uploaded_at DESC);

CREATE INDEX idx_research_documents_content_hash
    ON research_documents (content_hash);
