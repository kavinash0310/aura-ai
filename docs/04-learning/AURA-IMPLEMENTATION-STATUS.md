# AURA implementation status

Updated 2026-11-07.

| Area | Status | Evidence |
|---|---|---|
| Project brief and README | Implemented | Root README |
| Requirements and MVP | Documented | docs/00-overview |
| Architecture, data/API boundaries, ADRs, threat model | Documented proposal | docs/02-architecture and docs/03-adr |
| Spring Boot API | M1 foundation plus M5 search, authenticated session check, bounded history, and delete routes; 14 backend tests pass locally | backend source and API/provider tests |
| PostgreSQL migration and Compose | M1 plus V2 job/search-result schema; H2 migrations pass locally | backend migrations and compose.yaml |
| Python AI service | M2 implemented; 3 tests and Ruff passed locally; health only | ai-service source, locked uv environment, and workflow |
| Public web/news/discussion search | First provider slice implemented; Brave key optional; 12 backend tests pass locally | Brave adapter, persisted source metadata, authenticated API, filters, exports |
| Direct social-media connectors | Not implemented | Separate official, permissioned APIs and account credentials would be needed |
| Web interface | Local React workspace supports search, filters, downloads, history reopen, and explicit deletion; 4 UI tests and build pass locally | web source, container, authenticated API integration |
| Page fetching, AI synthesis | Not implemented | Later milestones; search remains usable through the authenticated API |
| Document ingestion/RAG | Not implemented | Future milestone |
| LLM/model gateway | Not implemented | M4 |
| Cybersecurity ingestion/SBOM | Not implemented | M7 |
| Local agent/active checks | Not implemented; deferred | Explicit scope and approval required if revisited |

## Verification rule

Do not claim build/tests passed until GitHub Actions is green or a local command ran. Basic Auth is loopback development only. The Python service currently exposes only a loopback health endpoint and cannot access a model or data provider. Brave search is performed only after an authenticated request and only when the owner supplies a key; the provider receives the query. Search results are partial and AURA does not fetch the returned links.
