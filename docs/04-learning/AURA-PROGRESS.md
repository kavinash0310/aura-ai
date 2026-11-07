# AURA learning progress

Do not mark a skill complete because code was generated. The owner should explain it and make a small independent change.

## Snapshot — 2026-11-07

- Repository was empty; no previous implementation or conventions existed.
- M1 Spring API is implemented and CI verified; M2 Python health service and M5 public search/UI are implemented; M7 exact-CVE, bounded CycloneDX JSON review, and explicit single-CVE NVD details are implemented locally. On 2026-11-07, 29 Java tests, 7 UI tests, and the production UI build passed. Learning explanations remain unassessed.
- Concepts completed: none assessed yet.
- Skills demonstrated independently: none recorded.
- Background assumption: Java/Spring experience; Python/AI/ML learning; Windows with 16 GB RAM.

## Milestones

| Milestone | Status | Evidence | Owner explanation assessed? | Next action |
|---|---|---|---|---|
| M0 context and architecture | Implemented; review pending | Requirements, ADRs, boundaries, threat model, learning map | No | Review choices; draw request and trust flows |
| M1 Spring API | Implemented; explanation pending | Backend source and passing CI | No | Run tests and explain lifecycle |
| M2 Python service | Implemented; explanation pending | FastAPI health, settings, uv lock, 3 tests, Ruff check | No | Explain ASGI, settings, Pydantic, and test client |
| M3 document lifecycle | Not started | No document code | No | Design upload limits and text extraction boundary |
| M5 public research slice | Implemented; explanation pending | Optional Brave adapter, persisted provenance, filters, downloads, 12 local backend tests | No | Explain provider boundaries, URL validation, state transitions, and partial coverage |
| First local UI slice | Implemented; explanation pending | React/TypeScript workspace, in-memory API credentials, search, filters, downloads, recent history, delete with source cascade | No | Explain credential handling, same-origin proxy, and retention behavior |
| M7 CISA KEV/FIRST EPSS lookup | Implemented; explanation pending | Authenticated CVE-only endpoint, bounded inputs, fixed providers, cache and stale status, ranking explanation, UI, JSON/CSV export | No | Explain the different meanings of KEV and EPSS and how provider freshness is surfaced |
| M7 CycloneDX SBOM review | Implemented; explanation pending | Authenticated 2 MB JSON upload, bounded components/records/CVEs/references, exact declared-CVE enrichment, BOM reference display, JSON/CSV export, no saved upload | No | Explain why an SBOM CVE declaration is evidence of a report entry, not proof of installed or affected software |
| M7 NVD detail lookup | Implemented; explanation pending | Authenticated, explicit one-CVE request; bounded NVD 2.0 fields, six-hour/500-record memory cache, no automatic retry, UI detail and report export | No | Explain CVSS severity versus asset-specific risk and why CPE evidence is not an automatic package match |

## Check-in template

- Date:
- Feature reviewed:
- Concepts explained:
- Independent code change:
- Test/evaluation interpreted:
- Questions remaining:
- Next exercise:
