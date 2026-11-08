ALTER TABLE research_provider_runs
    ADD COLUMN query_order INTEGER NOT NULL DEFAULT 1;

ALTER TABLE research_provider_runs
    ADD COLUMN query_text VARCHAR(300);

UPDATE research_provider_runs
SET query_text = (
    SELECT research_jobs.subject
    FROM research_jobs
    WHERE research_jobs.id = research_provider_runs.research_job_id
);

ALTER TABLE research_provider_runs
    ALTER COLUMN query_text SET NOT NULL;

ALTER TABLE research_provider_runs
    DROP CONSTRAINT uq_research_provider_runs_job_provider;

ALTER TABLE research_provider_runs
    ADD CONSTRAINT uq_research_provider_runs_job_provider_query
    UNIQUE (research_job_id, provider, query_order);

ALTER TABLE research_provider_runs
    ADD CONSTRAINT ck_research_provider_runs_query_order CHECK (query_order >= 1);

ALTER TABLE research_sources
    ADD COLUMN search_query VARCHAR(300);

UPDATE research_sources
SET search_query = (
    SELECT research_jobs.subject
    FROM research_jobs
    WHERE research_jobs.id = research_sources.research_job_id
);

ALTER TABLE research_sources
    ALTER COLUMN search_query SET NOT NULL;
