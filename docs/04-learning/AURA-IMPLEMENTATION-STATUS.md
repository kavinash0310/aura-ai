# AURA implementation status

Updated 2026-11-07.

| Area | Status | Evidence |
|---|---|---|
| Project brief and README | Implemented | Root README |
| Requirements and MVP | Documented | docs/00-overview |
| Architecture, data/API boundaries, ADRs, threat model | Documented proposal | docs/02-architecture and docs/03-adr |
| Spring Boot API | M1 foundation plus M5 search, authenticated session check, bounded history, delete routes, M7 CVE lookup, and bounded CycloneDX SBOM review; 26 backend tests pass locally | backend source and API/provider tests |
| PostgreSQL migration and Compose | M1 plus V2 job/search-result schema; H2 migrations pass locally | backend migrations and compose.yaml |
| Python AI service | M2 implemented; 3 tests and Ruff passed locally; health only | ai-service source, locked uv environment, and workflow |
| Public web/news/discussion search | First provider slice implemented; Brave key optional; backend provider/API tests pass locally | Brave adapter, persisted source metadata, authenticated API, filters, exports |
| Direct social-media connectors | Not implemented | Separate official, permissioned APIs and account credentials would be needed |
| Web interface | Local React workspace supports search, filters, downloads, history reopen, explicit deletion, CVE lookup, and CycloneDX SBOM review; 6 UI tests and production build pass locally | web source, container, authenticated API integration |
| Page fetching, AI synthesis | Not implemented | Later milestones; search remains usable through the authenticated API |
| Document ingestion/RAG | Not implemented | Future milestone |
| LLM/model gateway | Not implemented | M4 |
| Cybersecurity intelligence | Authenticated exact-CVE lookup plus bounded CycloneDX JSON review; only declared CVEs are enriched, component references are resolved where present, and uploads are not persisted. NVD enrichment, SPDX/SARIF, vendor advisories, and inventory matching remain | `backend/src/main/java/ai/aura/api/cyber`, SBOM parser/controller, cybersecurity UI |
| Local agent/active checks | Not implemented; deferred | Explicit scope and approval required if revisited |

## Verification rule

Local verification on 2026-11-07: `mvn -B -ntp -f backend/pom.xml verify` passed with 26 tests; `npm test` passed with 6 tests and `npm run build` passed. Basic Auth is loopback development only. The Python service currently exposes only a loopback health endpoint and cannot access a model or data provider. Brave receives a query only after an authenticated explicit search. FIRST receives exact CVE IDs only after authenticated lookup; AURA downloads CISA's catalog and matches locally. SBOM uploads are parsed in memory and only CVEs already declared in the file are submitted for enrichment. Search results are partial and AURA does not fetch the returned links. CVE and SBOM lookups are not asset discovery, vulnerability scanning, or a complete risk assessment.
