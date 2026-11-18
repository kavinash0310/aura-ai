# AURA learning progress

Do not mark a skill complete because code was generated. The owner should explain it and make a small independent change.

## Snapshot — 2026-11-18

- The repository's `main` contains the staged Spring, Python, PostgreSQL, React, research, document, and defensive vulnerability features described in the implementation status. M8 local alerting and first-pass M9 portfolio materials merged in PR #61 at `d416543`; both push and pull-request workflows passed all four CI groups. Owner-run setup, SMTP delivery, threshold tuning, demo, and learning explanations remain pending.
- Concepts completed: none assessed yet.
- Skills demonstrated independently: none recorded.
- Background assumption: Java/Spring experience; Python/AI/ML learning; Windows with 16 GB RAM.

## Milestones

| Milestone | Status | Evidence | Owner explanation assessed? | Next action |
|---|---|---|---|---|
| M0 context and architecture | Implemented; review pending | Requirements, ADRs, boundaries, threat model, learning map | No | Review choices; draw request and trust flows |
| M1 Spring API | Implemented; explanation pending | Backend source and passing CI | No | Run tests and explain lifecycle |
| M2 Python service | Implemented; explanation pending | FastAPI health, settings, uv lock, 7 tests, Ruff check | No | Explain ASGI, settings, Pydantic, and test client |
| M3 document lifecycle | Document library and bounded chunk-context slice implemented; PR #51 passed all four CI groups; owner explanation pending | Authenticated text/Markdown/CSV/JSON/HTML up to 1 MB and text-based PDF up to 1 MB, 100 pages, and 250,000 extracted characters; bounded CSV/JSON/HTML extraction; text hash, exact-phrase search, deterministic page-bounded Q&A context windows up to 700 characters with 140-character overlap, character/page citations, preview/extracted-text download, explicit delete | No | Explain strict text decoding, bounded parsing, content hashing, safe preview rendering, offset/page citations, chunk windows, and why phrase matching is not semantic search |
| M4 local report generation | First slice implemented; CI passing; owner explanation pending | Explicit local Ollama synthesis from up to 20 visible provider snippets, Java ownership validation, internal token, bounded JSON Schema output, citation-ID validation, in-memory UI report, Markdown download, structured JSON snapshot with source metadata/snippets, provider interface, deterministic query-plan fallback, and synthetic schema/citation/quote evaluation fixtures | No | Explain source membership vs semantic support, prompt injection limits, local model egress, and why an exported JSON snapshot cannot guarantee identical model replay |
| M5 public research slice | Expanded; explicit page reading, local excerpt filtering, metadata-only read audit/history, and bounded query planning delivered and CI verified; search-angle suggestions use a local-rules fallback when Ollama is unavailable | Brave web/news/discussion, GDELT public news, and Bluesky public-post adapter; provider choice, automatic selection, all-configured aggregation, up-to-three-query user-reviewed plans, per-query provider audit and source provenance, interrupted-search recovery, filters/downloads, and one-source page reading with SSRF/resource limits, exact-phrase matches, offsets, excerpt downloads, metadata-only persistent audit history, and restart recovery | No | Explain the 3-query/9-request bound, source-query provenance, DNS pinning, redirect validation, and why indexed search is not the whole internet |
| M6 controlled search workflow | Startup recovery and explicit per-provider retries are implemented; PR #45 passed all four CI groups | Provider results and success outcomes commit atomically; interrupted runs are closed at startup; the owner can retry only the latest failed provider/query up to three total attempts using saved query/filter values, with each attempt retained in history | No | Explain transaction boundaries, crash windows, latest-attempt aggregation, explicit duplicate-request cost, and why multi-instance operation requires leases |
| First local UI slice | Implemented; explanation pending | React/TypeScript workspace, in-memory API credentials, search, filters, downloads, recent history, delete with source cascade | No | Explain credential handling, same-origin proxy, and retention behavior |
| M7 CISA KEV/FIRST EPSS lookup | Implemented; explanation pending | Authenticated CVE-only endpoint, bounded inputs, fixed providers, cache and stale status, ranking explanation, UI, JSON/CSV export | No | Explain the different meanings of KEV and EPSS and how provider freshness is surfaced |
| M7 SBOM review | Existing SBOM/advisory slices implemented; crates.io SemVer range support passed all four CI groups in PR #52 at `b9203fa`; owner explanation pending | Authenticated 2 MB CycloneDX 1.3–1.7 and SPDX 2.3 JSON plus bounded SARIF 2.1.0, exact CVE references only, explicit OSV/GitHub detail, exact local PURL identity correlation, literal exact OSV affected-version comparison for any ecosystem, supported NuGet SemVer precedence, and npm, crates.io, restricted NuGet, and Go module SemVer 2 ranges, JSON/CSV export, no saved uploads | No | Explain why exact version strings need no ecosystem comparator, then describe limits of the supported npm/crates.io/NuGet/Go SemVer ranges |
| M8 database operations | PostgreSQL backups, retention, scheduling, and age-encrypted archives merged in PR #20 at `b6964ba`; isolated temporary-database recovery drill merged in PR #21 at `5012261`; all four CI job groups passed | Encrypted archives can be copied off-device manually; the private identity stays separate; temporary plaintext and drill database data remain | No | Explain encryption versus integrity hashes, key recovery, temporary-file risk, and how to perform a safe restore drill |
| M8 API observability | Optional Actuator health and Prometheus metrics on an isolated management network; implementation in PR #22 | Metrics use a local Prometheus volume with a 15-day/1 GB retention cap and no remote replication | No | Explain metric cardinality, endpoint exposure, and Prometheus retention/backup boundaries |
| M8 alert delivery and resource bounds | Implemented; PR #61 merged at `d416543`; push and PR CI passed all four groups | Four API alert rules, silent local Alertmanager default, opt-in email overlay with file-based secret, loopback UIs, bounded retention, and container memory ceilings | No | Run the local profile, verify an alert, explain alert expressions/grouping/egress, and tune thresholds from observation |
| M9 portfolio materials | First documentation slice merged in PR #61; owner walkthrough pending | Testing guide, demo scenarios, sample document/SBOM, architecture flow, ML/DL learning plan, and a case-study template | No | Run the demo from clean setup and write personal trade-offs and lessons in your own words |
| M8 security review | Scoped repository review and weekly dependency/image/action update schedules; review completed on `2b18401` | Local-only single-owner boundary; remote use remains blocked on TLS, identity, tenant isolation, and PDF process isolation | No | Explain the difference between source review, dependency alerts, and penetration testing |
| M8 first-run packaging | Windows script generates separate secrets and creates `.env` only when absent | Secret values are not printed; inherited filesystem permissions and sync-folder behavior remain the owner's responsibility | No | Explain OS cryptographic randomness, environment interpolation, and why credentials are separate |
| M7 NVD detail lookup | Implemented; explanation pending | Authenticated, explicit one-CVE request; bounded NVD 2.0 fields, six-hour/500-record memory cache, no automatic retry, UI detail and report export | No | Explain CVSS severity versus asset-specific risk and why CPE evidence is not an automatic package match |

## Check-in template

- Date:
- Feature reviewed:
- Concepts explained:
- Independent code change:
- Test/evaluation interpreted:
- Questions remaining:
- Next exercise:
