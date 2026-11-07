# AURA AI

AURA AI is an evidence-driven research and intelligence workspace. It gathers public-source information, organizes documents and evidence, answers questions with traceable citations, and supports a defensive cybersecurity intelligence workflow.

This is also a practical AI software engineering learning path: a Java/Spring application API, a focused Python AI service, React/TypeScript UI, and PostgreSQL, delivered incrementally with explanations, tests, and architecture records.

## Product boundaries

- Public research searches configured, permitted sources. It does not promise access to every page or post on the internet.
- Reports preserve source links, retrieval times, coverage limits, and the difference between source claims and model interpretation.
- Person-focused research is for public-interest information; no doxxing, invasive profiling, sensitive-trait inference, or private-account access.
- Cybersecurity features are defensive and informational. Any future active checks require explicit scope and approval.
- Retrieved pages and documents are untrusted input. Models cannot grant themselves permissions or execute arbitrary commands.

## Initial stack

- Java 21 + Spring Boot for APIs, persistence, validation, and security.
- Python 3.12 + FastAPI for bounded AI workloads when useful.
- PostgreSQL for the system of record and initial full-text search; vector search follows measured need.
- React + TypeScript for the local workspace UI, served through Nginx.
- Docker Compose for local Windows development.
- Modular monolith first. Redis, Kafka, Kubernetes, dedicated vector DB, and multi-agent orchestration are deferred until evidence justifies them.

## Current milestone

M1 stores research requests through the authenticated Spring API and PostgreSQL. M2 adds an isolated FastAPI service. M5 now supports an explicit, bounded search through Brave (web, news, and discussion results) or GDELT (public global news). Automatic selection uses Brave when configured and otherwise GDELT. Sources keep provider provenance, filters, and JSON/CSV/Markdown downloads. The local React workspace can reopen and delete saved briefs. M3 adds a private library for UTF-8 text and Markdown files with bounded uploads, hashes, indexed PostgreSQL candidate search guarded by exact-phrase matching, character citations, preview, download, and deletion. M7 checks exact CVE IDs against CISA KEV and FIRST EPSS, inspects CycloneDX JSON SBOMs up to 2 MB, and can load NVD detail for up to 25 explicitly selected CVEs in one request. SBOM analysis enriches only CVEs already declared in the file; it does not match packages to vulnerabilities. M4 adds explicit, local-model report synthesis from up to 20 saved search-result snippets; reports remain in the UI until downloaded, and citation IDs are checked against the selected source set. AURA still does not search every page or social post, connect to social accounts, fetch result pages, or probe systems.

See [implementation status](docs/04-learning/AURA-IMPLEMENTATION-STATUS.md) and [MVP backlog](docs/00-overview/MVP-AND-BACKLOG.md).

## Local development

Requirements: JDK 21, Docker Desktop with Compose, Git, Maven 3.9+ for local Java builds, and Python 3.12+ with uv 0.13.6 for local AI-service work.

1. Copy .env.example to .env and replace all placeholder secrets with unique random values of at least 24 characters.
2. Keep .env private and untracked. GDELT public news search is enabled by default and needs no key. Add your own `AURA_BRAVE_API_KEY` for Brave web, news, and discussion results. `AURA_NVD_API_KEY` is optional; single-CVE NVD lookups also work without it.
3. Start the stack with docker compose up --build.
4. Open the local research workspace at http://127.0.0.1:3000.
5. Enter the API username and password from `.env`. Credentials stay in page memory and clear on disconnect or reload.
6. Search with Automatic, Brave, or GDELT. Automatic uses Brave when configured, otherwise GDELT. The selected provider receives the query only after you start the search; GDELT searches public news from at most the past three months.
7. Spring health: http://127.0.0.1:8080/api/v1/health; AI-service health: http://127.0.0.1:8001/api/v1/health.
8. Direct API: create a `GENERAL` research job, explicitly run its `/search` route, then use `/sources` filters or `/export?format=json|csv|md`. See [API contracts](docs/02-architecture/API-CONTRACTS.md) for request shapes and limits.
9. In Documents, upload UTF-8 `.txt` or `.md` files up to 1 MB, search for exact phrases, inspect the returned character-offset citation, preview or download the text, and delete it when no longer needed. Files are stored in the local PostgreSQL database; they are not sent to search providers or models.
10. In Cyber intelligence, submit exact CVE IDs or upload a CycloneDX JSON SBOM of 2 MB or less. AURA parses the file in memory and checks only CVEs declared in it; it does not identify package vulnerabilities or scan systems. Use “Load NVD details” on an individual CVE to request its NVD record, CVSS, and CPE applicability data.
11. Optional: to create a cited report, install Ollama and a local model. Set `AURA_AI_INTERNAL_TOKEN` to a separate random secret, `AURA_AI_MODEL_PROVIDER=ollama`, and `AURA_AI_OLLAMA_MODEL` to the installed model name. The Compose AI container calls the local Ollama endpoint at `AURA_AI_OLLAMA_BASE_URL`; keep Ollama reachable only on a trusted local interface. No model call occurs until you click Report, and only the first 20 currently visible source snippets and metadata are sent. Uploaded library documents are not included.
12. Verify Java with `mvn -f backend/pom.xml verify`. For frontend development, use Node 22 in `web` and run `npm ci`, `npm test`, then `npm run build`.
13. In a terminal, change to `ai-service`, then run `uv sync --locked --all-groups`, `uv run pytest`, and `uv run ruff check src tests`.

All host ports bind to loopback. The Python health endpoint is public and returns no user data; report generation requires a separate internal bearer token. The Spring API uses development-only Basic Auth. The web app proxies `/api` to Spring inside the Compose network. An explicit search sends its query to the selected Brave or GDELT provider; an explicit report sends up to 20 selected provider snippets to the configured local Ollama service. Uploaded documents are excluded. Vulnerability and SBOM lookups read the CISA KEV catalog and send only exact CVE identifiers to FIRST EPSS; explicit NVD detail requests send only exact CVE IDs to NVD. SBOM files and lookup results are not persisted by AURA. Provider access is governed by each provider's terms. Do not expose or port-forward the local stack. See [local development guide](docs/19-operations/LOCAL-DEV.md).

## Start here

- [Product requirements](docs/00-overview/PRODUCT-REQUIREMENTS.md)
- [MVP and backlog](docs/00-overview/MVP-AND-BACKLOG.md)
- [Context and decisions](AURA-CONTEXT-AND-DECISIONS.md)
- [System architecture](docs/02-architecture/SYSTEM-ARCHITECTURE.md)
- [Bounded local report design](docs/05-backend/RESEARCH-REPORT-GENERATION.md)
- [Technology decisions](docs/02-architecture/TECHNOLOGY-DECISIONS.md)
- [Threat model](docs/02-architecture/THREAT-MODEL.md)
- [Learning roadmap](docs/04-learning/AURA-LEARNING-ROADMAP.md)
- [Feature learning map](docs/04-learning/FEATURE-TO-LEARNING-MAP.md)
- [Progress tracker](docs/04-learning/AURA-PROGRESS.md)
- [Interview guide](docs/04-learning/AURA-INTERVIEW-GUIDE.md)

## Learning agreement

Generated code is not evidence of mastery. Each milestone includes prerequisites, review questions, and an explanation exercise. Progress is marked complete only after the owner can explain or modify the feature independently.
