ALTER TABLE research_provider_runs
    ADD COLUMN attempt INTEGER NOT NULL DEFAULT 1;

ALTER TABLE research_provider_runs
    DROP CONSTRAINT uq_research_provider_runs_job_provider_query;

ALTER TABLE research_provider_runs
    ADD CONSTRAINT uq_research_provider_runs_job_provider_query_attempt
    UNIQUE (research_job_id, provider, query_order, attempt);

ALTER TABLE research_provider_runs
    ADD CONSTRAINT ck_research_provider_runs_attempt CHECK (attempt BETWEEN 1 AND 3);
