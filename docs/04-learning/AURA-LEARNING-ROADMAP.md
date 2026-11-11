# AURA learning roadmap

Use Learn → Understand → Implement → Review → Explain → Extend. Do not skip fundamentals for trendy tools.

## Phase 0 — Product and architecture
Learn requirements, boundaries, C4 views, ADRs, data modeling, threat modeling. Explain current code vs target design. Draw request and trust flows. Review what crosses each boundary and what is excluded.

## Phase 1 — Spring API foundation
Learn Java records/classes/enums/exceptions, HTTP/REST, Maven, Spring DI and bean lifecycle, MVC, validation, security filters, SQL, transactions, JPA, migrations, test layers.
Implement health, validated research-job create/read, local Basic Auth, Flyway schema, PostgreSQL Compose, Problem Details, integration tests, CI.
Review: Why DTO not entity? What rolls back? Why migration vs Hibernate schema generation? Why 401 vs 403? What does REQUESTED mean?
Explain one POST from security filter through controller/service/repository/transaction and response Location.
Extend only after basics: pagination, idempotency, audit, OIDC.

## Phase 2 — Python AI service
Learn Python OOP/typing, virtual environments, Pydantic, FastAPI, ASGI, asyncio/blocking I/O, packaging/lock files, HTTP timeout/retry.
Implemented: Python 3.12 service, settings via Pydantic Settings, FastAPI lifespan and typed health response, uv lock, isolated container, token-protected report and claim-comparison routes, and a local query-plan route on the active branch (CI pending). When explicitly enabled, model actions call only a local/private Ollama endpoint using structured output; provider snippets are untrusted and the service has no search tools, data connectors, or database access.
Review: Why can POST retry duplicate work? What blocks the event loop? Explain what authority Python does not own.
Review the report route's typed Java-to-Python contract, bearer-token boundary, blocking I/O offload, local endpoint allowlist, and source-ID validation. Explain why citation membership does not prove semantic support.

## Phase 3 — Documents and retrieval
Learn encodings, parser risk, metadata, chunking/offsets, tokenization, inverted indexes, embeddings, vector similarity, access filters.
Implement explicit upload validation → parser → normalized text → chunks → PostgreSQL FTS → retrieval → citations. Benchmark before pgvector.
Implemented: authenticated UTF-8 `.txt`/`.md`/`.csv`/`.json`/`.html`/`.htm` uploads up to 1 MB plus text-based PDF extraction up to 100 pages and 250,000 characters. CSV is capped at 10,000 rows and 200 columns per row; JSON at 50,000 nodes and 64 levels; HTML parsing at 50,000 parser nodes and 64 open elements; extracted text at 250,000 characters. Normalized/extracted text, SHA-256 metadata, exact-phrase search with character/page citations, preview/download, deletion, and stable version history are implemented. New versions have separate IDs and immutable text hashes; current list/search returns only the latest; history can be inspected and older versions opened; deletion removes the group. Original PDF/CSV/JSON/HTML bytes are not retained; scanned PDFs need OCR, which is not enabled. Chunking and semantic retrieval remain future work.
Review: How does chunk size alter context/recall? What if retrieval is irrelevant? How is citation tied to page/offset?
Explain keyword vs semantic search and the lifecycle.

## Phase 4 — Model integration
Learn inference, context windows, structured outputs, prompt injection, tool calls, token/cost/latency.
Implemented first slice: one explicit local Ollama report path over up to 20 selected search-result snippets, with JSON Schema output, an internal bearer token, local/private URL validation, single-request serialization, source-ID membership validation, no hosted fallback, and Markdown download. Reports are not stored and uploaded documents are excluded.
Implemented: explicit local-only report, claim-comparison, and query-planning calls use versioned prompts, schema validation, citation checks, and timing/token counters. A deterministic secret-pattern pass redacts common credential strings across model-bound text fields and shows counts in the response coverage note without returning matches.
Implemented: CI runs a local synthetic redaction evaluation that reports supported-pattern recall, benign-text specificity, and known blind-spot detection without printing fixture contents. It is a small controlled fixture set, not an estimate of real-world secret-detection coverage.
Still needed: reusable provider interface, semantic citation evaluation, curated report-quality fixtures, optional hosted adapter, and deterministic fallback.
Review local-only no-egress behavior, untrusted output fields, and retry rules.

## Phase 5 — Public research
Learn search APIs, pagination, quotas, source terms, canonical URLs, deduplication, evidence claims, coverage bias.
Implemented first slices: Brave Search for web/news/discussion metadata, GDELT DOC for public global news metadata, and official Bluesky public-post search; the UI lets the owner select one provider, use automatic preference, or query all configured providers. The owner can preview and submit at most three explicit query angles; the optional local Ollama feature suggests at most two drafts, and bounded local rules provide generic drafts when Ollama is disabled or unavailable. The owner must add/review each suggestion before a separate search action. Calls run sequentially and results record both provider and originating query; source results can be filtered by provider, type, text, host, and date, then downloaded as JSON/CSV/Markdown. Search is authenticated and provider-index coverage is disclosed as bounded. Local cited synthesis and user-written claim comparison work over up to 20 checked filtered provider snippets. A separate explicit action reads one selected saved page through a bounded HTTPS fetcher; its text is not persisted or sent to the report or comparison model. Direct X/Twitter or other social-account connectors, multi-page crawling, query usefulness evaluation, and semantic citation evaluation remain future work.
Review: trace SSRF prevention end-to-end, including why hostname checks alone are insufficient, how the HTTP resolver pins validated public IP answers, why every redirect is revalidated, and which separate resource risks each timeout/size/concurrency limit addresses.
Review coverage definition, contradiction retention, and why search cannot prove completeness.

## Phase 6 — Controlled workflow
Learn state machines, leases, idempotency, backoff, audit, approval, typed tools.
Implemented first slice: public search commits each successful provider's source rows and audit outcome atomically. A single-backend startup recovery marks interrupted calls failed, uncalled providers skipped, and derives the job's terminal status without repeating requests.
Implemented next slice: the owner can explicitly retry one latest failed provider/query up to three total attempts. Query text and filters come from the saved job, each attempt remains separately auditable, and only the latest attempt determines current coverage. The user click authorizes the repeated public request; there is no automatic retry.
Still needed: multi-instance leases/heartbeats, asynchronous retry scheduling, cancellation, and broader error recovery.
Review replay, retries, terminal failures, and policy boundary.

## Phase 7 — Cyber intelligence and operations
Learn CVE/CPE/SBOM, CVSS vs EPSS, KEV semantics, confidence, Docker networking, CI, logs, backups.
Implement adapters, transparent ranking, evaluation, packaging and runbooks.
Review evidence for priority and how restore is verified.

