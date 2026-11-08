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
Implemented: Python 3.12 service, settings via Pydantic Settings, FastAPI lifespan and typed health response, uv lock, isolated container, and a token-protected bounded report route. When explicitly enabled, it calls only a local/private Ollama endpoint using structured output; provider snippets are untrusted and the service has no tools, data connectors, or database access.
Review: Why can POST retry duplicate work? What blocks the event loop? Explain what authority Python does not own.
Review the report route's typed Java-to-Python contract, bearer-token boundary, blocking I/O offload, local endpoint allowlist, and source-ID validation. Explain why citation membership does not prove semantic support.

## Phase 3 — Documents and retrieval
Learn encodings, parser risk, metadata, chunking/offsets, tokenization, inverted indexes, embeddings, vector similarity, access filters.
Implement explicit upload validation → parser → normalized text → chunks → PostgreSQL FTS → retrieval → citations. Benchmark before pgvector.
Implemented first slice: authenticated UTF-8 `.txt`/`.md` upload up to 1 MB plus text-based PDF extraction up to 100 pages and 250,000 characters, normalized/extracted text storage, SHA-256 metadata, exact-phrase search with character/page citations, preview/download, and deletion. Original PDF bytes are not retained; scanned PDFs need OCR, which is not enabled. Versioning, more parsers, chunking, and semantic retrieval remain future work.
Review: How does chunk size alter context/recall? What if retrieval is irrelevant? How is citation tied to page/offset?
Explain keyword vs semantic search and the lifecycle.

## Phase 4 — Model integration
Learn inference, context windows, structured outputs, prompt injection, tool calls, token/cost/latency.
Implemented first slice: one explicit local Ollama report path over up to 20 selected search-result snippets, with JSON Schema output, an internal bearer token, local/private URL validation, single-request serialization, source-ID membership validation, no hosted fallback, and Markdown download. Reports are not stored and uploaded documents are excluded.
Still needed: reusable provider interface, prompt versions, redaction review, latency/token tracking, semantic citation evaluation, curated quality fixtures, optional hosted adapter, and deterministic fallback.
Review local-only no-egress behavior, untrusted output fields, and retry rules.

## Phase 5 — Public research
Learn search APIs, pagination, quotas, source terms, canonical URLs, deduplication, evidence claims, coverage bias.
Implemented first slices: Brave Search for web/news/discussion metadata, GDELT DOC for public global news metadata, and official Bluesky public-post search; the UI lets the owner select one provider, use automatic preference, or query all configured providers. The owner can preview and submit at most three explicit query angles. Calls run sequentially and results record both provider and originating query; source results can be filtered by provider, type, text, host, and date, then downloaded as JSON/CSV/Markdown. Search is authenticated and provider-index coverage is disclosed as bounded. A first local cited synthesis action works over up to 20 filtered provider snippets. A separate explicit action reads one selected saved page through a bounded HTTPS fetcher; its text is not persisted or sent to the report model. Direct X/Twitter or other social-account connectors, multi-page crawling, automatically generated query expansion, claim comparison, and semantic citation evaluation remain future work.
Review: trace SSRF prevention end-to-end, including why hostname checks alone are insufficient, how the HTTP resolver pins validated public IP answers, why every redirect is revalidated, and which separate resource risks each timeout/size/concurrency limit addresses.
Review coverage definition, contradiction retention, and why search cannot prove completeness.

## Phase 6 — Controlled workflow
Learn state machines, leases, idempotency, backoff, audit, approval, typed tools.
Implemented first slice: public search commits each successful provider's source rows and audit outcome atomically. A single-backend startup recovery marks interrupted calls failed, uncalled providers skipped, and derives the job's terminal status without repeating requests.
Still needed: persisted workflow steps, leases/heartbeats for multi-instance execution, retries/backoff, cancellation, approval, and broader error recovery.
Review replay, retries, terminal failures, and policy boundary.

## Phase 7 — Cyber intelligence and operations
Learn CVE/CPE/SBOM, CVSS vs EPSS, KEV semantics, confidence, Docker networking, CI, logs, backups.
Implement adapters, transparent ranking, evaluation, packaging and runbooks.
Review evidence for priority and how restore is verified.

