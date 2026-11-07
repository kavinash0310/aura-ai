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
Implemented: Python 3.12 service, settings via Pydantic Settings, FastAPI lifespan and typed health response, uv lock, isolated container, and CI tests. No AI call or network access is present yet.
Review: Why can POST retry duplicate work? What blocks the event loop? Explain what authority Python does not own.
Next: define a typed, bounded Java-to-Python contract only when a real model transformation is added.

## Phase 3 — Documents and retrieval
Learn encodings, parser risk, metadata, chunking/offsets, tokenization, inverted indexes, embeddings, vector similarity, access filters.
Implement explicit upload validation → parser → normalized text → chunks → PostgreSQL FTS → retrieval → citations. Benchmark before pgvector.
Implemented first slice: authenticated UTF-8 `.txt`/`.md` upload up to 1 MB, normalized line endings, SHA-256 metadata, local storage, exact-phrase search with character-offset citations, preview/download, and deletion. Full-text indexing, versioning, richer parsers, chunking, and semantic retrieval remain future work.
Review: How does chunk size alter context/recall? What if retrieval is irrelevant? How is citation tied to page/offset?
Explain keyword vs semantic search and the lifecycle.

## Phase 4 — Model integration
Learn inference, context windows, structured outputs, prompt injection, tool calls, token/cost/latency.
Implement provider interface, local adapter, optional hosted adapter, prompt versions, schema validation, redaction, curated evaluations.
Review local-only no-egress behavior, untrusted output fields, and retry rules.

## Phase 5 — Public research
Learn search APIs, pagination, quotas, source terms, canonical URLs, deduplication, evidence claims, coverage bias.
Implemented first slice: one optional Brave Search adapter for web/news/discussion metadata, bounded queries, safe URL filtering, result persistence, source filters, and JSON/CSV/Markdown export. Search is explicit and authenticated. Direct X/Twitter or other social-account connectors, linked-page fetching, query planning, claim comparison, cited synthesis, and multiple providers remain future work.
Review coverage definition, contradiction retention, and why search cannot prove completeness.

## Phase 6 — Controlled workflow
Learn state machines, leases, idempotency, backoff, audit, approval, typed tools.
Implement persisted steps, tools, cancellation, approval, error recovery.
Review replay, retries, terminal failures, and policy boundary.

## Phase 7 — Cyber intelligence and operations
Learn CVE/CPE/SBOM, CVSS vs EPSS, KEV semantics, confidence, Docker networking, CI, logs, backups.
Implement adapters, transparent ranking, evaluation, packaging and runbooks.
Review evidence for priority and how restore is verified.

