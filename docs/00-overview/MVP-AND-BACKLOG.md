# MVP and implementation backlog

## MVP definition

The first useful release is a local research workspace that accepts a subject, searches a small set of permitted public sources, keeps source metadata, returns a cited summary, and exports a report. The first code milestone here is smaller: a secure Spring Boot foundation that persists a research request and exposes health. It does not yet perform research.

## Feature priority

| Feature | Priority | Reason |
|---|---|---|
| Spring Boot API, security, PostgreSQL migrations | CORE / CAREER | Product backbone and backend practice |
| Research request lifecycle and stable IDs | CORE | Durable, inspectable entry point |
| Web/news search through replaceable providers | CORE | Main user value; provider access/cost must be checked |
| Source provenance, cited report, export | CORE | Reviewable and reproducible |
| Document imports and full-text search | CORE / CAREER | Personal knowledge before vector search |
| Python AI service/model gateway | CAREER | AI integration without moving all product logic to Python |
| Deterministic workflow and recovery | CORE / CAREER | Reliable multi-step research |
| NVD/KEV/EPSS and SBOM analysis | CORE | Defensive specialist capability |
| pgvector, OCR, reranker | OPTIONAL | Add only after measured retrieval gaps |
| React workspace | CORE | Build after API contracts stabilize |
| Kafka, Kubernetes, dedicated vector DB | DEFERRED | No initial scale need |
| Multi-agent orchestration | DEFERRED | Bounded workflow is simpler and auditable |
| Exploit execution, unrestricted scanning, dark-web collection | REMOVE | Outside initial defensive scope |

## Milestones and acceptance

### M0 — Context and architecture
Deliver requirements, scope, module boundaries, data model, API contract, threat model, ADRs, technology comparisons, diagrams, and learning plan.
**Accept when:** actual code and proposed work are clearly distinguished; links resolve.

### M1 — Spring backend foundation
Deliver Java 21/Spring Boot API, configuration, PostgreSQL/Flyway schema, research request create/read, local Basic Auth, validation/problem responses, tests, Compose, and CI.
**Accept when:** clean clone starts from Windows instructions; migration applies; unauthorized calls fail; invalid requests return client errors; create/read survives restart; CI passes.

### M2 — Python AI service
Deliver Python packaging, FastAPI health, settings, Pydantic schemas, logging/errors. Add Java-to-Python call only for a real task.
**Accept when:** service starts locally, schemas reject bad inputs, HTTP timeout behavior is documented.

### M3 — Document lifecycle
Deliver explicit uploads, type/size validation, parser adapters, metadata, content hashes, versioning, PostgreSQL FTS, retrieval, citations, tests.
**Accept when:** imported content is searchable with stable source/page/chunk references.

### M4 — LLM integration
Add provider interface, local and optional hosted adapters, structured output, prompt versions, redaction, token/latency tracking, deterministic fallback, evaluation.
**Accept when:** local-only mode cannot silently egress; outputs validate; evaluations are recorded.

### M5 — Public research
Add web/news adapters, query planning, normalization, deduplication, evidence, claim comparison, citations, filters, exports, coverage.
**Accept when:** curated queries are reproducible, citations resolve, conflicts persist, source limits show.

### M6 — Controlled workflow
Add persisted steps, typed tools, retries/backoff, cancellation, approval, audit.
**Accept when:** provider failures recover or surface terminal state; policy denies out-of-scope operations.

### M7 — Cybersecurity intelligence
Add NVD, KEV, EPSS, vendor advisories, SBOM JSON/CSV/SARIF, transparent ranking.
**Accept when:** findings include timestamps, match evidence, confidence, and rationale.

### M8 — Workspace and production readiness
Add React pages, observability, security review, backup/restore, packaging, runbooks.
**Accept when:** setup, report flow, auth boundaries, recovery, and limitations are demonstrated from a clean environment.
