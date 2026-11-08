# MVP and implementation backlog

## MVP definition

The first useful release is a local research workspace that accepts a subject, searches permitted public sources, keeps source metadata, returns a bounded cited summary, and exports results. The repository has the Spring foundation, an isolated Python AI service, authenticated bounded public-source search, a local React workspace, and a private document-library slice. The UI starts searches, reopens recent briefs, filters stored source metadata, downloads JSON/CSV/Markdown, deletes a brief with its source rows, imports/searches/previews/deletes UTF-8 text and Markdown, and can create a cited report from selected search snippets using an explicitly configured local model. Bounded explicit page reading, local exact-phrase filtering, and matching-excerpt downloads are delivered; cross-source claim comparison and semantic citation verification remain planned.

The document library also accepts text-based PDFs with bounded extraction, page-aware citations, and clearly labeled extracted-text downloads; it does not retain the uploaded PDF bytes or perform OCR.

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
| React workspace | CORE / FIRST UI SLICE DELIVERED | Local UI is wired to the current API; bounded history, deletion, and Markdown/JSON report export are delivered |
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
**Delivered first slice:** authenticated `.txt` and `.md` uploads with strict UTF-8 validation, plus text-based PDF uploads through PDFBox. Uploads are limited to 1 MB; PDFs are limited to 100 pages and 250,000 extracted characters. Normalized/extracted text is stored locally with SHA-256 metadata, indexed candidate search with an exact-phrase guard, relevance ordering, character citations, PDF page citations, preview/download, and explicit deletion. PDF source bytes are not retained; scanned PDFs need OCR, which is not enabled. PostgreSQL uses a generated `simple` text-search vector and GIN index; H2 tests retain the portable phrase-match fallback.
**Still needed:** document version history, HTML/CSV/JSON parsers, chunking, OCR evaluation, and access-scoped retrieval beyond the current single local owner. Search currently has no stemming or semantic retrieval.
**Accept when:** imported content is searchable with stable source/page/chunk references.

### M4 — LLM integration
Add provider interface, local and optional hosted adapters, structured output, prompt versions, redaction, token/latency tracking, deterministic fallback, evaluation.
**Accept when:** local-only mode cannot silently egress; outputs validate; evaluations are recorded.

### M5 — Public research
Add web/news adapters, query planning, normalization, deduplication, evidence, claim comparison, citations, filters, exports, coverage.
**Delivered slice:** Brave returns bounded web/news/discussion results; GDELT returns bounded public news results; Bluesky returns bounded public posts through its official public API. Automatic selection uses Brave when configured, otherwise GDELT and then Bluesky; All configured sources queries every enabled provider. The API persists per-source provider provenance and provider-run status/counts/failure codes, supports provider/type/text/host/date filters, and downloads the filtered JSON/CSV/Markdown set. Search is invoked explicitly and coverage limits are shown. Mixed provider outcomes preserve successful results in a `PARTIAL` job; an all-failed run stays `FAILED` with its provider audit. Bluesky does not apply country filters or connect to accounts/private feeds.
**Delivered next slice:** An owner can explicitly read one selected source already saved in a brief. The endpoint checks source ownership and applies public-HTTPS/DNS/redirect/size/time/concurrency limits. It extracts static HTML as text and keeps it transient in the UI; it is not included in model reports. Exact phrases can be matched locally and excerpts downloaded. A durable metadata-only audit tracks status, timestamps, hash/count/truncation, and stable failure codes; restart recovery marks interrupted reads.
**Report export:** Reports remain transient in the UI, with explicit Markdown and JSON snapshot downloads. The JSON carries candidate source metadata/snippets and model response, but not page text or enough runtime state to guarantee identical model replay.
**Still needed:** X/account connectors after provider-specific retention and deletion controls, additional search providers, query planning, claim comparison, semantic citation checks, and broader evaluations. No multi-page crawling or JavaScript rendering is planned for this slice.
**Accept when:** curated queries are reproducible, citations resolve, conflicts persist, source and coverage limits show, and each provider's terms/rate limits are tested.

### M6 — Controlled workflow
Add persisted steps, typed tools, retries/backoff, cancellation, approval, audit.
**Delivered first slice:** synchronous public searches persist each provider's results atomically with its success outcome and reconcile interrupted jobs on single-backend startup; interrupted providers are not retried automatically. Multi-instance leases, retry scheduling, cancellation, and approval remain future work.
**Accept when:** provider failures recover or surface terminal state; policy denies out-of-scope operations.

### M7 — Cybersecurity intelligence
**Delivered first slice:** authenticated, read-only lookup for up to 25 explicit CVE IDs against the CISA KEV catalog and FIRST EPSS lookup API. It reports source status and dates, caches KEV data for six hours, explains KEV-first/EPSS-second ordering, and downloads JSON or CSV. The UI also accepts CycloneDX JSON up to 2 MB, resolves only declared BOM references, and enriches only CVE IDs listed by the SBOM; uploads are not persisted. A user can request bounded NVD detail for one or up to 25 exact CVE IDs in a single explicit action, with CVSS/CWE evidence and capped CPE applicability and references.
**Still needed:** SPDX and SARIF support, vendor advisories, inventory matching, and broader priority evaluations. No host scanning or exploit checks are included.
**Accept when:** findings include timestamps, exact match evidence, confidence limits, and rationale; supported SBOM parsing has bounded size/type validation and fixtures; rankings are evaluated against documented remediation scenarios.

### M8 — Workspace and production readiness
Add React pages, observability, security review, backup/restore, packaging, runbooks.
**Accept when:** setup, report flow, auth boundaries, recovery, and limitations are demonstrated from a clean environment.
