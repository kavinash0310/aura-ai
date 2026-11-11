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

M1 stores research requests through the authenticated Spring API and PostgreSQL. M2 adds an isolated FastAPI service. M5 supports explicit, bounded searches through Brave (web, news, and discussion results), GDELT (public global news), the official Bluesky public-post search API, and opt-in OpenAlex scholarly works. An owner can ask the configured local Ollama model to suggest up to two angles, review/add/edit them, then preview and send up to three queries to one or all configured public providers; calls run sequentially and each source retains the provider and query that found it. OpenAlex is excluded from Automatic and runs only when selected or included in All configured sources. Filters and downloads operate on the result set. With three queries and four configured providers, the current maximum is 12 requests and up to 360 results. This widens coverage but does not search the whole internet. Bluesky public-post search needs no account credentials; AURA does not connect to accounts or private feeds. The local React workspace can reopen and delete saved briefs. M3 adds a private library for UTF-8 text and Markdown files with bounded uploads, hashes, indexed PostgreSQL candidate search guarded by exact-phrase matching, character citations, preview, download, and deletion. M7 checks exact CVE IDs against CISA KEV and FIRST EPSS, reviews CycloneDX 1.3–1.7 and SPDX 2.3 SBOMs or SARIF 2.1.0 reports up to 2 MB, and supports explicit NVD batch detail plus per-CVE OSV and GitHub-reviewed advisory lookup. Advisory lookups are user-directed and send the selected exact CVE only. After an explicit OSV lookup, AURA correlates exact PURL identities locally and matches literal OSV affected-version entries for any ecosystem. Complete npm SemVer 2 and canonical Go module SemVer 2 ranges are assessed locally when OSV returns structured `SEMVER` events; other ecosystem/order types remain not assessed. Go versions must use a `v` prefix, and the evaluator does not validate module path/major consistency or pseudo-version ancestry. This is not an asset-risk decision. SBOM analysis enriches only declared CVEs; SARIF extraction uses exact rule IDs and ignores source messages and paths. M4 adds explicit, local-model report synthesis from up to 20 saved search-result snippets and a user-directed comparison of a typed claim against up to 20 filtered snippets; both actions are explicit, transient, and exportable as Markdown/JSON. Report findings may include quote anchors matched against provider titles/descriptions; matching does not prove the linked page supports the finding. Comparison citations are constrained to selected source IDs. Neither action uses fetched page text or private documents. The owner can explicitly read one saved public HTTPS page at a time under strict size, destination, redirect, and timeout limits, search its extracted text locally by exact phrase, and download cited excerpts with offsets. Page text is temporary and not included in reports or comparisons; each explicit read has a durable metadata-only audit row with outcome and text hash, never the page text. AURA does not crawl the web or probe systems.

See [implementation status](docs/04-learning/AURA-IMPLEMENTATION-STATUS.md) and [MVP backlog](docs/00-overview/MVP-AND-BACKLOG.md).

Optional local health and Prometheus metrics are available through the Compose `observability` profile. See the [observability runbook](docs/19-operations/OBSERVABILITY.md) for the isolated management port and retention boundaries.

Local AI report and claim-comparison responses include prompt template versions and available Ollama timing/token counters. Completion logs contain usage metadata only; prompt text, source snippets, claims, and model output are excluded.

The document library extracts text from PDFs (up to 100 pages and 250,000 characters); it does not retain original PDF bytes or run OCR. See the [document API contract](docs/02-architecture/API-CONTRACTS.md#personal-document-library).

Bluesky connector details and its current limits are documented in [BLUESKY-SEARCH.md](docs/05-backend/BLUESKY-SEARCH.md).
Provider outcome persistence and partial-result behavior are explained in [PROVIDER-OUTCOME-AUDIT.md](docs/05-backend/PROVIDER-OUTCOME-AUDIT.md).
The query planner limits a search to three visible angles and retains query provenance; local Ollama may suggest up to two drafts, but the owner must add/review them and start the search separately; see [BOUNDED-QUERY-PLANS.md](docs/05-backend/BOUNDED-QUERY-PLANS.md).

## Local development

Requirements: JDK 21, Docker Desktop with Compose, Git, Maven 3.9+ for local Java builds, and Python 3.12+ with uv 0.13.6 for local AI-service work.

1. Copy .env.example to .env and replace all placeholder secrets with unique random values of at least 24 characters.
2. Keep .env private and untracked. GDELT public news and Bluesky public-post search are enabled by default and need no key or account credentials; use `AURA_GDELT_ENABLED=false` or `AURA_BLUESKY_ENABLED=false` to disable either connector. Add your own `AURA_BRAVE_API_KEY` for Brave web, news, and discussion results. `AURA_OPENALEX_API_KEY` is optional; OpenAlex runs only when selected directly or through All configured sources, not Automatic. `AURA_NVD_API_KEY` is optional; single-CVE NVD lookups also work without it.
3. Start the stack with docker compose up --build.
4. Open the local research workspace at http://127.0.0.1:3000.
5. Enter the API username and password from `.env`. Credentials stay in page memory and clear on disconnect or reload.
6. Search with Automatic, All configured sources, Brave, GDELT, Bluesky, or OpenAlex. Automatic uses one configured provider; All configured sources queries Brave when keyed, GDELT and Bluesky when enabled, and OpenAlex when keyed. Providers receive the query only after you start the search. If some providers fail, AURA keeps successful results and labels the job Partial; if all fail, it saves the provider outcomes and reports the error. GDELT searches public news from at most the past three months. Bluesky searches bounded public posts; it does not apply country filters. OpenAlex searches scholarly works by title, abstract, and keywords; abstracts may be absent, and country/language filters do not apply.
7. Spring health: http://127.0.0.1:8080/api/v1/health; AI-service health: http://127.0.0.1:8001/api/v1/health.
8. Direct API: create a `GENERAL` research job, explicitly run its `/search` route, then use `/sources` filters or `/export?format=json|csv|md`. See [API contracts](docs/02-architecture/API-CONTRACTS.md) for request shapes and limits.
9. In Documents, upload UTF-8 `.txt` or `.md` files, or text-based `.pdf` files, up to 1 MB. PDFs are limited to 100 pages and 250,000 extracted characters; scanned PDFs need OCR, which is not enabled. Search exact phrases, inspect character/page citations, preview extracted text, download PDF text as `.pdf.extracted.txt`, and delete it when no longer needed. Only normalized/extracted text is stored in PostgreSQL; source PDF bytes are not retained, and documents are not sent to search providers or models.
10. In Cyber intelligence, submit exact CVE IDs or upload a CycloneDX 1.3–1.7 / SPDX 2.3 JSON SBOM or SARIF 2.1.0 report, up to 2 MB. AURA parses the file in memory and checks only CVEs declared in it; it does not infer package vulnerabilities or scan systems. SPDX CVEs are read only from explicit security advisory references; external links are not fetched. SARIF CVEs are read only from exact rule IDs; messages and paths are ignored. Use “Load NVD details,” “Load OSV advisory,” or “Load GitHub advisories” for explicit per-CVE evidence. Provider requests send the selected exact CVE only. After OSV returns, the browser may correlate exact PURL identities with the selected finding's local components and compare exact affected-version strings for any ecosystem. Complete npm SemVer 2 and canonical Go module SemVer 2 range evidence can also be evaluated locally when OSV supplies structured `SEMVER` events; Go inventory versions need a `v` prefix, and AURA does not validate module-path consistency or pseudo-version ancestry. Other ecosystem ranges remain not assessed.
11. Optional: to create a cited report or compare a claim, install Ollama and a local model. Set `AURA_AI_INTERNAL_TOKEN` to a separate random secret, `AURA_AI_MODEL_PROVIDER=ollama`, and `AURA_AI_OLLAMA_MODEL` to the installed model name. The Compose AI container calls the local Ollama endpoint at `AURA_AI_OLLAMA_BASE_URL`; keep Ollama reachable only on a trusted local interface. No model call occurs until you click Report or Compare claim. Up to 20 checked source snippets and metadata are sent; the first 20 are checked by default, and you can change that below the filters. Uploaded library documents and explicitly read page text are not included.
12. Verify Java with `mvn -f backend/pom.xml verify`. For frontend development, use Node 22 in `web` and run `npm ci`, `npm test`, then `npm run build`.
13. In a terminal, change to `ai-service`, then run `uv sync --locked --all-groups`, `uv run pytest`, and `uv run ruff check src tests`.

All host ports bind to loopback. The Python health endpoint is public and returns no user data; report generation requires a separate internal bearer token. The Spring API uses development-only Basic Auth. The web app proxies `/api` to Spring inside the Compose network. An explicit search sends its query to the selected Brave, GDELT, and/or Bluesky provider(s); an explicit report sends up to 20 selected provider snippets to the configured local Ollama service. Uploaded documents are excluded. Explicit CVE, SBOM, and SARIF lookups match against CISA KEV and send only exact CVE identifiers to FIRST EPSS; explicit NVD, OSV, and GitHub Advisory Database requests send only selected CVE IDs to those providers. SBOM/SARIF files and lookup results are not persisted by AURA. Provider access is governed by each provider's terms. Do not expose or port-forward the local stack. See the [local development guide](docs/19-operations/LOCAL-DEV.md).

## Start here

- [Product requirements](docs/00-overview/PRODUCT-REQUIREMENTS.md)
- [MVP and backlog](docs/00-overview/MVP-AND-BACKLOG.md)
- [Context and decisions](AURA-CONTEXT-AND-DECISIONS.md)
- [System architecture](docs/02-architecture/SYSTEM-ARCHITECTURE.md)
- [Bluesky public-post search](docs/05-backend/BLUESKY-SEARCH.md)
- [Provider outcome audit](docs/05-backend/PROVIDER-OUTCOME-AUDIT.md)
- [Database backup and restore runbook](docs/19-operations/DATABASE-BACKUP-RESTORE.md)
- [Bounded local report design](docs/05-backend/RESEARCH-REPORT-GENERATION.md)
- [Multi-provider search design](docs/05-backend/MULTI-PROVIDER-SEARCH.md)
- [Technology decisions](docs/02-architecture/TECHNOLOGY-DECISIONS.md)
- [Threat model](docs/02-architecture/THREAT-MODEL.md)
- [Learning roadmap](docs/04-learning/AURA-LEARNING-ROADMAP.md)
- [Feature learning map](docs/04-learning/FEATURE-TO-LEARNING-MAP.md)
- [Progress tracker](docs/04-learning/AURA-PROGRESS.md)
- [Interview guide](docs/04-learning/AURA-INTERVIEW-GUIDE.md)

## Learning agreement

Generated code is not evidence of mastery. Each milestone includes prerequisites, review questions, and an explanation exercise. Progress is marked complete only after the owner can explain or modify the feature independently.
