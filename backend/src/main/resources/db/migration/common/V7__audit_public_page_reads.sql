CREATE TABLE research_page_reads (
    id UUID PRIMARY KEY,
    research_job_id UUID NOT NULL REFERENCES research_jobs(id) ON DELETE CASCADE,
    source_id UUID NOT NULL REFERENCES research_sources(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    content_hash VARCHAR(64),
    character_count INTEGER,
    truncated BOOLEAN,
    failure_code VARCHAR(64),
    CONSTRAINT ck_research_page_reads_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'ABANDONED')),
    CONSTRAINT ck_research_page_reads_character_count CHECK (character_count IS NULL OR character_count >= 0)
);

CREATE INDEX idx_research_page_reads_job_source_time
    ON research_page_reads (research_job_id, source_id, requested_at DESC);

CREATE INDEX idx_research_page_reads_status_time
    ON research_page_reads (status, requested_at);
