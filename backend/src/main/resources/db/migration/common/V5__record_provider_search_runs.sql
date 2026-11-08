ALTER TABLE research_jobs
    DROP CONSTRAINT ck_research_jobs_status;

ALTER TABLE research_jobs
    ADD CONSTRAINT ck_research_jobs_status
    CHECK (status IN ('REQUESTED', 'SEARCHING', 'COMPLETED', 'PARTIAL', 'FAILED'));

CREATE TABLE research_provider_runs (
    id UUID PRIMARY KEY,
    research_job_id UUID NOT NULL REFERENCES research_jobs(id) ON DELETE CASCADE,
    provider VARCHAR(32) NOT NULL,
    run_order INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL,
    result_count INTEGER NOT NULL DEFAULT 0,
    failure_code VARCHAR(64),
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_research_provider_runs_job_provider UNIQUE (research_job_id, provider),
    CONSTRAINT uq_research_provider_runs_job_order UNIQUE (research_job_id, run_order),
    CONSTRAINT ck_research_provider_runs_order CHECK (run_order >= 1),
    CONSTRAINT ck_research_provider_runs_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_research_provider_runs_count CHECK (result_count >= 0)
);

CREATE INDEX idx_research_provider_runs_job_order
    ON research_provider_runs (research_job_id, run_order);
