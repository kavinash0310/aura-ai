ALTER TABLE research_jobs
    ADD COLUMN search_provider VARCHAR(32);

ALTER TABLE research_jobs
    ADD COLUMN search_country VARCHAR(2);

ALTER TABLE research_jobs
    ADD COLUMN search_language VARCHAR(2);

ALTER TABLE research_jobs
    ADD COLUMN search_freshness VARCHAR(2);

ALTER TABLE research_jobs
    ADD COLUMN search_limit INTEGER;

ALTER TABLE research_jobs
    ADD COLUMN search_started_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE research_jobs
    ADD COLUMN search_completed_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE research_jobs
    ADD COLUMN search_result_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE research_jobs
    ADD COLUMN failure_code VARCHAR(64);

ALTER TABLE research_jobs
    ADD CONSTRAINT ck_research_jobs_status
    CHECK (status IN ('REQUESTED', 'SEARCHING', 'COMPLETED', 'FAILED'));

ALTER TABLE research_jobs
    ADD CONSTRAINT ck_research_jobs_search_result_count
    CHECK (search_result_count >= 0);

CREATE TABLE research_sources (
    id UUID PRIMARY KEY,
    research_job_id UUID NOT NULL REFERENCES research_jobs(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    result_rank INTEGER NOT NULL,
    title VARCHAR(1000) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    domain VARCHAR(253) NOT NULL,
    description VARCHAR(4000),
    publisher VARCHAR(300),
    published_at TIMESTAMP WITH TIME ZONE,
    retrieved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_research_sources_job_provider_url UNIQUE (research_job_id, provider, url),
    CONSTRAINT ck_research_sources_rank CHECK (result_rank >= 1),
    CONSTRAINT ck_research_sources_type CHECK (source_type IN ('WEB', 'NEWS', 'DISCUSSION'))
);

CREATE INDEX idx_research_sources_job_rank
    ON research_sources (research_job_id, result_rank);

CREATE INDEX idx_research_sources_job_type
    ON research_sources (research_job_id, source_type);

CREATE INDEX idx_research_sources_job_published
    ON research_sources (research_job_id, published_at);


