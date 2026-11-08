ALTER TABLE research_provider_runs
    DROP CONSTRAINT ck_research_provider_runs_status;

ALTER TABLE research_provider_runs
    ADD CONSTRAINT ck_research_provider_runs_status
    CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED'));
