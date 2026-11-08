# Feature-to-learning map

CORE means needed for AURA; CAREER means high value for the target role; OPTIONAL means useful after fundamentals; DEFERRED means no current need.

| AURA feature | Engineering concepts | Technologies | Prerequisites | Priority |
|---|---|---|---|---|
| API foundation | HTTP lifecycle, DI, validation, REST, errors, config | Java 21, Spring Boot 4, Maven | Java OOP, collections, exceptions | CORE / CAREER |
| Authentication | Filter chain, hashing, stateless/session trade-offs, least privilege | Spring Security | HTTP, crypto basics | CORE / CAREER |
| Persistence | Entity lifecycle, repositories, transactions, indexes, migrations | PostgreSQL, JPA/Hibernate, Flyway | SQL, relational modeling | CORE / CAREER |
| Research workflow | State machine, transaction boundaries, restart recovery, idempotency, leases, retries, cancellation | Spring services, PostgreSQL | REST, persistence | CORE / CAREER |
| Python AI service | Type hints, Pydantic, ASGI, async I/O, service authentication, timeouts | Python, FastAPI, `urllib`, Ollama API | Python basics, JSON/HTTP | CAREER |
| Document ingestion | File validation, parsing, metadata, chunking, offsets | Parser adapters, PostgreSQL | Streams, encodings, security | CORE / CAREER |
| Search and RAG | Tokenization, embeddings, similarity, FTS, hybrid search, reranking, citations | PostgreSQL FTS, optional pgvector | Probability, vectors, SQL | CORE / CAREER |
| LLM integration | Prompt injection, structured outputs, source-ID validation, context and latency bounds | Local Ollama `/api/chat`, Pydantic JSON Schema | HTTP, JSON schemas, evidence provenance | CORE / CAREER |
| Research sources | Query planning, quotas, source terms, provider aggregation, per-source provenance, retries, normalization, dedupe | Provider-neutral adapters | HTTP, pagination | CORE |
| Evidence/reports | Provenance, claim/evidence distinction, contradictions, reproducibility | PostgreSQL, Markdown/PDF | Domain modeling | CORE |
| Controlled workflows | Tool permission, state, audit, approval, recovery | Java state machine, optional LangGraph | Workflow fundamentals | CORE / CAREER |
| Cyber intelligence | CVE/CPE/SBOM, KEV, EPSS, CVSS v3/v4, advisory provenance, bounded public APIs, PURL identity, exact OSV version evidence, strict npm SemVer range applicability, deterministic ranking regression fixtures, source freshness, uncertainty | NVD/CISA/FIRST/OSV/GitHub Advisory adapters | Normalization, probability, source trust, HTTP limits, caching, version identifiers, SemVer, test design | CORE |
| UI | Component/state model, accessibility, API types | React, TypeScript, Vite | HTML/CSS/JS, APIs | CORE / CAREER |
| Operations | Containers, CI, logs, metrics, backup/restore | Docker Compose, GitHub Actions | CLI, Linux | CAREER |
| Safety | Auth, prompt injection, SSRF, privacy, audit | Spring Security, policy | Web security | CORE / CAREER |

## AI/ML sequence

Linear algebra basics (vectors, dot products), probability/statistics, NumPy/Pandas, scikit-learn baselines, neural network basics, PyTorch, tokenization, transformers, embeddings, inference, evaluation. AURA initially consumes models; it does not train a foundation model.

## Demonstration standard

A skill is not complete because code exists. The owner should explain the flow, identify failure modes, make a small independent change, and interpret a test or evaluation result.
