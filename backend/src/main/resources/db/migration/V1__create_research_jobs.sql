CREATE TABLE research_jobs (
    id UUID PRIMARY KEY,
    subject VARCHAR(300) NOT NULL,
    research_mode VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_research_jobs_created_at
    ON research_jobs (created_at DESC);
