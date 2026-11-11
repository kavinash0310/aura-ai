ALTER TABLE research_documents ADD COLUMN logical_document_id UUID;
ALTER TABLE research_documents ADD COLUMN version_number INTEGER NOT NULL DEFAULT 1;

UPDATE research_documents SET logical_document_id = id;

ALTER TABLE research_documents ALTER COLUMN logical_document_id SET NOT NULL;
ALTER TABLE research_documents
    ADD CONSTRAINT uq_research_document_logical_version UNIQUE (logical_document_id, version_number);
ALTER TABLE research_documents
    ADD CONSTRAINT chk_research_document_version_number CHECK (version_number > 0);

CREATE INDEX idx_research_document_versions
    ON research_documents (logical_document_id, version_number DESC);
