# AURA learning progress

Do not mark a skill complete because code was generated. The owner should explain it and make a small independent change.

## Snapshot — 2026-11-08

- Repository was empty; no previous implementation or conventions existed.
- M1 Spring API is implemented and CI verified; M2 Python service, M5 public search/UI plus explicit bounded page reading and local phrase filtering, M6 synchronous-search recovery, the first M3 private document library (text, Markdown, bounded PDF extraction), M7 exact-CVE/CycloneDX/NVD slices, and the first M4 selected-source report path are implemented. M6 stores provider evidence and success status atomically, then closes interrupted jobs at startup without retries. The page reader accepts one explicitly selected saved source, uses strict public-HTTPS and resource limits, keeps extracted text out of persistence and model reports, and lets the owner search exact phrases locally and download excerpts with offsets. On 2026-11-08 GitHub Actions passed both push and PR workflows on `c607d24`: backend (55 tests), Python (7 tests and Ruff), web (14 tests/build), and Compose. Learning explanations remain unassessed.
- Concepts completed: none assessed yet.
- Skills demonstrated independently: none recorded.
- Background assumption: Java/Spring experience; Python/AI/ML learning; Windows with 16 GB RAM.

## Milestones

| Milestone | Status | Evidence | Owner explanation assessed? | Next action |
|---|---|---|---|---|
| M0 context and architecture | Implemented; review pending | Requirements, ADRs, boundaries, threat model, learning map | No | Review choices; draw request and trust flows |
| M1 Spring API | Implemented; explanation pending | Backend source and passing CI | No | Run tests and explain lifecycle |
| M2 Python service | Implemented; explanation pending | FastAPI health, settings, uv lock, 7 tests, Ruff check | No | Explain ASGI, settings, Pydantic, and test client |
| M3 document lifecycle | First PDF slice implemented and CI verified; explanation pending | Authenticated text/Markdown up to 1 MB and PDF up to 1 MB, 100 pages, and 250,000 extracted characters; text hash, exact-phrase search, character/page citations, preview/extracted-text download, explicit delete | No | Explain strict text decoding, bounded parsing, content hashing, safe preview rendering, offset/page citations, and why phrase matching is not semantic search |
| M4 local report generation | First slice implemented; CI passing; owner explanation pending | Explicit local Ollama synthesis from up to 20 visible provider snippets, Java ownership validation, internal token, bounded JSON Schema output, citation-ID validation, in-memory UI report, Markdown download | No | Explain source membership vs semantic support, prompt injection limits, and local model egress |
| M5 public research slice | Expanded; explicit page reading and local excerpt filtering delivered and CI verified | Brave web/news/discussion, GDELT public news, and Bluesky public-post adapter; provider choice, automatic selection, all-configured aggregation, durable provider outcomes, interrupted-search recovery, filters/downloads, and a one-source page reader with SSRF/resource limits, exact-phrase matches, offsets, and excerpt downloads | No | Explain provider limits, source-query disclosure, DNS pinning, redirect validation, and why indexed search is not the whole internet |
| M6 synchronous search recovery | First slice implemented; CI pass; owner explanation pending | Each provider's source rows and success outcome commit atomically; application startup converts interrupted runs to `FAILED` and never-started runs to `SKIPPED`; completed evidence is kept and no query is repeated | No | Explain transaction boundaries, crash windows, startup recovery, and why multi-instance operation requires leases |
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
