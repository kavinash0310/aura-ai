# MVP and implementation backlog

## MVP definition

The first useful release is a local research workspace that accepts a subject, searches a small set of permitted public sources, keeps source metadata, returns a cited summary, and exports a report. The repository has the Spring foundation, an isolated Python health service, a first authenticated search slice, a local React workspace, and a first private document-library slice. The UI starts searches, reopens recent briefs, filters stored source metadata, downloads JSON/CSV/Markdown, deletes a brief with its source rows, and can import/search/preview/delete UTF-8 text and Markdown; page reading, cross-source analysis, and synthesized reports remain planned.

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
| React workspace | CORE / FIRST UI SLICE DELIVERED | Local UI is wired to the current API; bounded history and deletion are delivered, reports follow later |
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
**Delivered first slice:** authenticated `.txt` and `.md` uploads with strict UTF-8 validation, a 1 MB limit, normalized line endings, SHA-256 metadata, local PostgreSQL storage, indexed token candidate search with an exact-phrase guard, relevance ordering, character-offset citations, preview/download, and explicit deletion. PostgreSQL uses a generated `simple` text-search vector and GIN index; H2 tests retain the portable phrase-match fallback.
**Still needed:** document version history, PDF/HTML/CSV/JSON parsers, chunking, and access-scoped retrieval beyond the current single local owner. Search currently has no stemming or semantic retrieval.
**Accept when:** imported content is searchable with stable source/page/chunk references.

### M4 — LLM integration
Add provider interface, local and optional hosted adapters, structured output, prompt versions, redaction, token/latency tracking, deterministic fallback, evaluation.
**Accept when:** local-only mode cannot silently egress; outputs validate; evaluations are recorded.

### M5 — Public research
Add web/news adapters, query planning, normalization, deduplication, evidence, claim comparison, citations, filters, exports, coverage.
**Delivered slice:** one optional Brave provider returns bounded web/news/discussion results; the API persists provider provenance and timestamps, supports type/text/host/date filters, and downloads JSON/CSV/Markdown. Search is invoked explicitly and the response states that coverage is partial.
**Still needed:** official direct social providers, additional search providers, permitted page fetching, query planning, claim comparison, cited synthesis, and broader evaluations.
**Accept when:** curated queries are reproducible, citations resolve, conflicts persist, source and coverage limits show, and each provider's terms/rate limits are tested.

### M6 — Controlled workflow
Add persisted steps, typed tools, retries/backoff, cancellation, approval, audit.
**Accept when:** provider failures recover or surface terminal state; policy denies out-of-scope operations.

### M7 — Cybersecurity intelligence
**Delivered first slice:** authenticated, read-only lookup for up to 25 explicit CVE IDs against the CISA KEV catalog and FIRST EPSS lookup API. It reports source status and dates, caches KEV data for six hours, explains KEV-first/EPSS-second ordering, and downloads JSON or CSV. The UI also accepts CycloneDX JSON up to 2 MB, resolves only declared BOM references, and enriches only CVE IDs listed by the SBOM; uploads are not persisted. A user can request one bounded NVD CVE detail record at a time, with CVSS/CWE evidence and capped CPE applicability and references.
**Still needed:** automated or multi-CVE NVD enrichment, SPDX and SARIF support, vendor advisories, inventory matching, and broader priority evaluations. No host scanning or exploit checks are included.
**Accept when:** findings include timestamps, exact match evidence, confidence limits, and rationale; supported SBOM parsing has bounded size/type validation and fixtures; rankings are evaluated against documented remediation scenarios.

### M8 — Workspace and production readiness
Add React pages, observability, security review, backup/restore, packaging, runbooks.
**Accept when:** setup, report flow, auth boundaries, recovery, and limitations are demonstrated from a clean environment.
