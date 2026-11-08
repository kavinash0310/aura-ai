# Data model

## Implemented in M1

### research_jobs
- id: UUID primary key generated in application.
- subject: required string up to 300 characters.
- research_mode: GENERAL or CYBERSECURITY.
- status: REQUESTED at creation.
- created_at: UTC timestamp.

The row records the user's research intent. It does not assert that a search occurred. Future workflow state requires a migration when the workflow is implemented.

## Planned core entities

| Entity | Key fields | Purpose |
|---|---|---|
| users | id, authentication settings | Single owner first; explicit owner foreign key on user data |
| connectors | id, provider, status, encrypted credential reference | Provider connection without exposing token |
| grants | owner, resource scope, capability, purpose, expiry, revoked_at | Fine-grained permissions |
| research_jobs | id, subject, filters, status, idempotency key, timestamps | Durable request and later workflow |
| workflow_steps | job, step type, state, attempt, input/output refs | Recovery and inspection |
| sources | canonical URL/platform, publisher, published/retrieved time, hash, terms | Provenance anchor |
| evidence | source, owner, extracted claim/passage, trust label, parser version | Traceable facts and excerpts |
| documents | owner, original name, content hash, version, parser state | Selected knowledge material |
| chunks | document/source, offsets/page, text, FTS vector, optional embedding | Searchable passages |
| reports | job, template version, generated time, evidence IDs | Reproducible exports |
| findings | CVE/advisory, asset match, risk factors, confidence | Defensive vulnerability view |
| audit_events | actor, policy decision, operation, resources, timestamp, payload hash | Reconstruct decisions |

## Target relationships

~~~mermaid
erDiagram
  USER ||--o{ RESEARCH_JOB : requests
  RESEARCH_JOB ||--o{ WORKFLOW_STEP : contains
  RESEARCH_JOB ||--o{ REPORT : produces
  REPORT }o--o{ EVIDENCE : cites
  SOURCE ||--o{ EVIDENCE : supports
  USER ||--o{ DOCUMENT : owns
  DOCUMENT ||--o{ CHUNK : yields
  USER ||--o{ GRANT : authorizes
  USER ||--o{ AUDIT_EVENT : performs
~~~

The current schema also includes saved `research_sources`, provider-run outcomes, documents, and `research_page_reads`. A page-read audit row references its job and source, stores status/timestamps plus an optional text hash/count/truncation or stable failure code, and cascades when the source or job is deleted. It never stores fetched page text. Most other entities and joins above remain target design.

## Data principles

- Every user datum has an owner key and owner-scoped repository access.
- Store timestamps in UTC; preserve source-local published timestamp and zone when provided.
- Keep source identity, hash, parser version, and retrieval time together.
- Store model interpretation separately from source claims.
- Secrets use OS keychain or deployment secret manager; database stores a reference.
- Add indexes from measured queries, not speculative ones.
- Use Flyway migrations and Hibernate schema validation. Never use Hibernate create/update in deployment.
- Enforce retention and source-specific deletion/display constraints in export.
