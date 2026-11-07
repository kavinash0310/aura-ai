# AURA implementation status

Updated 2026-11-07.

| Area | Status | Evidence |
|---|---|---|
| Project brief and README | Implemented | Root README |
| Requirements and MVP | Documented | docs/00-overview |
| Architecture, data/API boundaries, ADRs, threat model | Documented proposal | docs/02-architecture and docs/03-adr |
| Spring Boot API | M1 foundation plus M5 search, authenticated session check, bounded history, delete routes, M3 text/Markdown document import/search/delete, M7 CVE lookup, bounded CycloneDX SBOM review, explicit single/batch NVD details, and an authenticated selected-source report route; latest report tests are pending CI | backend source and API/provider tests |
| PostgreSQL migration and Compose | M1 plus V2 job/search-result and V3 document schema; H2 migrations pass locally | backend migrations and compose.yaml |
| Python AI service | M2 health plus first M4 local Ollama report route with bearer auth, bounded structured output, local endpoint validation, single-request serialization, and citation-ID validation; latest Python checks are pending CI | ai-service source and report API tests |
| Public web/news/discussion search | First provider slice implemented; Brave key optional; backend provider/API tests pass locally | Brave adapter, persisted source metadata, authenticated API, filters, exports |
| Direct social-media connectors | Not implemented | Separate official, permissioned APIs and account credentials would be needed |
| Web interface | Local React workspace supports search, filters, downloads, history reopen, explicit deletion, text/Markdown document import and search, CVE lookup, CycloneDX SBOM review, and explicit single/batch NVD details; 8 UI tests and production build pass locally | web source, container, authenticated API integration |
| Page fetching, research synthesis | No page fetching; first local report synthesis uses selected provider snippets, and report tests are pending CI | [report flow](../05-backend/RESEARCH-REPORT-GENERATION.md); page fetching, claim comparison, semantic citation evaluation remain later work |
| Document ingestion/RAG | First local slice: authenticated UTF-8 `.txt`/`.md` upload up to 1 MB, metadata/hash storage, exact-phrase search with character citations, preview/download, and explicit deletion. Version history, PDF/HTML parsers, PostgreSQL FTS, chunking, and semantic RAG remain | `ResearchDocumentController`, V3 migration, local document-library UI |
| LLM/model gateway | First explicit Ollama-only report integration; reusable provider interface, prompt versions, redaction review, evaluation, and persistence remain | M4; local-only, disabled by default |
| Cybersecurity intelligence | Authenticated exact-CVE lookup plus bounded CycloneDX JSON review and explicit single/batch NVD details; only declared CVEs are enriched, component references are resolved where present, SBOM uploads and vulnerability lookup results are not persisted, and CPE criteria are not automatically matched. SPDX/SARIF, vendor advisories, and inventory matching remain | `backend/src/main/java/ai/aura/api/cyber`, SBOM parser/controller, cybersecurity UI |
| Local agent/active checks | Not implemented; deferred | Explicit scope and approval required if revisited |

## Verification rule

Base verification before the report change on 2026-11-07: `mvn -B -ntp -f backend/pom.xml verify` ran 34 tests with 33 passing and the Docker-dependent PostgreSQL test skipped; `npm test` passed with 8 tests and `npm run build` passed. After this change, `npm test` passed with 9 tests and `npm run build` passed. This shell has no Maven, Python, or uv executable, so the new Java/Python tests were not run locally; CI is the next verification gate. Basic Auth is loopback development only. Brave receives a query only after an authenticated explicit search. The local model receives selected provider snippets only after an authenticated explicit report request; uploaded documents are excluded. FIRST receives exact CVE IDs only after authenticated lookup; AURA downloads CISA's catalog and matches locally. NVD receives exact CVE IDs only after an explicit per-item or batch request. Text/Markdown files are stored after explicit authenticated upload and searched locally. SBOM uploads are parsed in memory and only declared CVEs are submitted for KEV/EPSS enrichment; NVD detail is requested separately. Search results are partial and AURA does not fetch returned links. Reports validate source-ID membership but not semantic support. CVE and SBOM lookups are not asset discovery, vulnerability scanning, or a complete risk assessment.
