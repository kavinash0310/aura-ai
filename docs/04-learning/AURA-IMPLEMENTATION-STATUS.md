# AURA implementation status

Updated 2026-11-07.

| Area | Status | Evidence |
|---|---|---|
| Project brief and README | Implemented | Root README |
| Requirements and MVP | Documented | docs/00-overview |
| Architecture, data/API boundaries, ADRs, threat model | Documented proposal | docs/02-architecture and docs/03-adr |
| Spring Boot API | M1 foundation plus M5 search, authenticated session check, bounded history, delete routes, M3 text/Markdown document import/search/delete, M7 CVE lookup, bounded CycloneDX SBOM review, explicit single/batch NVD details, and an authenticated selected-source report route; current backend CI passes | backend source and API/provider tests |
| PostgreSQL migration and Compose | M1 plus V2 job/search-result and V3 document schema; H2 migrations pass locally | backend migrations and compose.yaml |
| Python AI service | M2 health plus first M4 local Ollama report route with bearer auth, bounded structured output, local endpoint validation, single-request serialization, and citation-ID validation; current Python tests and Ruff CI pass | ai-service source and report API tests |
| Public web/news/discussion search | Brave and GDELT adapters implemented; selected-provider search and automatic fallback added; backend verification pending | Provider adapters and API tests; GDELT returns bounded public news metadata over at most three months |
| Direct social-media connectors | Not implemented | Separate official, permissioned APIs and account credentials would be needed |
| Web interface | Local React workspace supports search, filters, downloads, history reopen, explicit deletion, text/Markdown document import and search, CVE lookup, CycloneDX SBOM review, explicit single/batch NVD details, and cited report generation/download; 9 UI tests and production build pass locally and CI | web source, container, authenticated API integration |
| Page fetching, research synthesis | No page fetching; first local report synthesis uses selected provider snippets; current report tests pass in CI | [report flow](../05-backend/RESEARCH-REPORT-GENERATION.md); page fetching, claim comparison, semantic citation evaluation remain later work |
| Document ingestion/RAG | First local slice: authenticated UTF-8 `.txt`/`.md` upload up to 1 MB, metadata/hash storage, exact-phrase search with character citations, preview/download, and explicit deletion. Version history, PDF/HTML parsers, PostgreSQL FTS, chunking, and semantic RAG remain | `ResearchDocumentController`, V3 migration, local document-library UI |
| LLM/model gateway | First explicit Ollama-only report integration; reusable provider interface, prompt versions, redaction review, evaluation, and persistence remain | M4; local-only, disabled by default; CI passes |
| Cybersecurity intelligence | Authenticated exact-CVE lookup plus bounded CycloneDX JSON review and explicit single/batch NVD details; only declared CVEs are enriched, component references are resolved where present, SBOM uploads and vulnerability lookup results are not persisted, and CPE criteria are not automatically matched. SPDX/SARIF, vendor advisories, and inventory matching remain | `backend/src/main/java/ai/aura/api/cyber`, SBOM parser/controller, cybersecurity UI |
| Local agent/active checks | Not implemented; deferred | Explicit scope and approval required if revisited |

## Verification rule

Verification on 2026-11-07: the latest GitHub Actions runs for commit `f53d902` passed backend verification, Python tests and Ruff, frontend tests/build, and Compose validation/image builds. Those runs predate the GDELT addition; its backend tests and current UI changes are pending CI. The last verified frontend run had 9 passing tests and a passing build; the current shell lacks Maven and Python/uv. Basic Auth is loopback development only. Brave or GDELT receives a query only after an authenticated explicit search. The local model receives selected provider snippets only after an authenticated explicit report request; uploaded documents are excluded. FIRST receives exact CVE IDs only after authenticated lookup; AURA downloads CISA's catalog and matches locally. NVD receives exact CVE IDs only after an explicit per-item or batch request. Text/Markdown files are stored after explicit authenticated upload and searched locally. SBOM uploads are parsed in memory and only declared CVEs are submitted for KEV/EPSS enrichment; NVD detail is requested separately. Search results are partial and AURA does not fetch returned links. Reports validate source-ID membership but not semantic support. CVE and SBOM lookups are not asset discovery, vulnerability scanning, or a complete risk assessment.
