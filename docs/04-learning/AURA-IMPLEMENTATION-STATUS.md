# AURA implementation status

Updated 2026-10-31.

| Area | Status | Evidence |
|---|---|---|
| Project brief and README | Implemented | Root README |
| Requirements and MVP | Documented | docs/00-overview |
| Architecture, data/API boundaries, ADRs, threat model | Documented proposal | docs/02-architecture and docs/03-adr |
| Spring Boot API | M1 branch implementation; awaiting CI verification | backend source |
| PostgreSQL migration and Compose | M1 branch implementation; awaiting CI verification | backend migration and compose.yaml |
| Python AI service | Not implemented | M2 |
| Frontend | Not implemented | After API stabilizes |
| Public web/news/social search | Not implemented | M5; provider selection pending |
| Document ingestion/RAG | Not implemented | M3 |
| LLM/model gateway | Not implemented | M4 |
| Cybersecurity ingestion/SBOM | Not implemented | M7 |
| Local agent/active checks | Not implemented; deferred | Explicit scope and approval required if revisited |

## Verification rule

Do not claim build/tests passed until GitHub Actions is green or a local command ran. Basic Auth is loopback development only. Branch and CI are source of truth.
