# AURA learning progress

Do not mark a skill complete because code was generated. The owner should explain it and make a small independent change.

## Snapshot — 2026-11-08

- Repository was empty; no previous implementation or conventions existed.
- M1 Spring API is implemented and CI verified; M2 Python service, M5 public search/UI (including durable per-provider outcomes and partial-result coverage), the first M3 private text/Markdown library, M7 exact-CVE/CycloneDX/NVD slices, and the first M4 selected-source report path are implemented. The provider-outcome slice passes local Maven verification (43 tests, 1 Docker-dependent skip), frontend tests (10 passed), TypeScript check, and production build. GitHub Actions passed both push and PR workflows on `3272e1f`, including backend, Python/Ruff, web, and Compose jobs. Learning explanations remain unassessed.
- Concepts completed: none assessed yet.
- Skills demonstrated independently: none recorded.
- Background assumption: Java/Spring experience; Python/AI/ML learning; Windows with 16 GB RAM.

## Milestones

| Milestone | Status | Evidence | Owner explanation assessed? | Next action |
|---|---|---|---|---|
| M0 context and architecture | Implemented; review pending | Requirements, ADRs, boundaries, threat model, learning map | No | Review choices; draw request and trust flows |
| M1 Spring API | Implemented; explanation pending | Backend source and passing CI | No | Run tests and explain lifecycle |
| M2 Python service | Implemented; explanation pending | FastAPI health, settings, uv lock, 3 tests, Ruff check | No | Explain ASGI, settings, Pydantic, and test client |
| M3 document lifecycle | First slice implemented; explanation pending | Authenticated UTF-8 `.txt`/`.md` upload up to 1 MB, SHA-256 metadata, exact-phrase search, character citations, preview/download, explicit delete | No | Explain strict text decoding, content hashing, safe preview rendering, offset citations, and why phrase matching is not full-text search |
| M4 local report generation | First slice implemented; CI passing; owner explanation pending | Explicit local Ollama synthesis from up to 20 visible provider snippets, Java ownership validation, internal token, bounded JSON Schema output, citation-ID validation, in-memory UI report, Markdown download | No | Explain source membership vs semantic support, prompt injection limits, and local model egress |
| M5 public research slice | Expanded; local checks and CI pass | Brave web/news/discussion, GDELT public news, and Bluesky public-post adapter; provider choice, automatic selection, all-configured aggregation, durable provider outcomes, partial-result reporting, per-source provenance, provider filtering, downloads, and tests | No | Explain provider limits, source-query disclosure, URL validation, and why these indices do not equal the internet |
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
