# AURA implementation status

Updated 2026-11-07.

| Area | Status | Evidence |
|---|---|---|
| Project brief and README | Implemented | Root README |
| Requirements and MVP | Documented | docs/00-overview |
| Architecture, data/API boundaries, ADRs, threat model | Documented proposal | docs/02-architecture and docs/03-adr |
| Spring Boot API | M1 foundation plus M5 search, authenticated session check, bounded history, delete routes, M3 text/Markdown document import/search/delete, M7 CVE lookup, bounded CycloneDX SBOM review, and on-demand NVD details; 31 backend tests pass locally | backend source and API/provider tests |
| PostgreSQL migration and Compose | M1 plus V2 job/search-result and V3 document schema; H2 migrations pass locally | backend migrations and compose.yaml |
| Python AI service | M2 implemented; 3 tests and Ruff passed locally; health only | ai-service source, locked uv environment, and workflow |
| Public web/news/discussion search | First provider slice implemented; Brave key optional; backend provider/API tests pass locally | Brave adapter, persisted source metadata, authenticated API, filters, exports |
| Direct social-media connectors | Not implemented | Separate official, permissioned APIs and account credentials would be needed |
| Web interface | Local React workspace supports search, filters, downloads, history reopen, explicit deletion, text/Markdown document import and search, CVE lookup, CycloneDX SBOM review, and on-demand NVD details; 8 UI tests and production build pass locally | web source, container, authenticated API integration |
| Page fetching, AI synthesis | Not implemented | Later milestones; search remains usable through the authenticated API |
| Document ingestion/RAG | First local slice: authenticated UTF-8 `.txt`/`.md` upload up to 1 MB, metadata/hash storage, exact-phrase search with character citations, preview/download, and explicit deletion. Version history, PDF/HTML parsers, PostgreSQL FTS, chunking, and semantic RAG remain | `ResearchDocumentController`, V3 migration, local document-library UI |
| LLM/model gateway | Not implemented | M4 |
| Cybersecurity intelligence | Authenticated exact-CVE lookup plus bounded CycloneDX JSON review and explicit single-CVE NVD detail; only declared CVEs are enriched, component references are resolved where present, SBOM uploads and vulnerability lookup results are not persisted, and CPE criteria are not automatically matched. Automated/multi-CVE NVD enrichment, SPDX/SARIF, vendor advisories, and inventory matching remain | `backend/src/main/java/ai/aura/api/cyber`, SBOM parser/controller, cybersecurity UI |
| Local agent/active checks | Not implemented; deferred | Explicit scope and approval required if revisited |

## Verification rule

Local verification on 2026-11-07: `mvn -B -ntp -f backend/pom.xml verify` passed with 31 tests; `npm test` passed with 8 tests and `npm run build` passed. Basic Auth is loopback development only. The Python service currently exposes only a loopback health endpoint and cannot access a model or data provider. Brave receives a query only after an authenticated explicit search. FIRST receives exact CVE IDs only after authenticated lookup; AURA downloads CISA's catalog and matches locally. NVD receives one exact CVE only after an explicit per-item request. Text/Markdown files are stored only after explicit authenticated upload and are searched locally; they are not sent to external providers or models. SBOM uploads are parsed in memory and only CVEs already declared in the file are submitted for KEV/EPSS enrichment; NVD detail is separately requested per CVE. Search results are partial and AURA does not fetch the returned links. CVE and SBOM lookups are not asset discovery, vulnerability scanning, or a complete risk assessment.
