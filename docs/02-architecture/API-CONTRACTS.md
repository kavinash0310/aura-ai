# API contracts

Base path: `/api/v1`. JSON uses camelCase. Errors use RFC 9457-style Problem Details. IDs are UUIDs and timestamps are ISO-8601 UTC. All routes except health require the local owner Basic Auth account.

## Health

### GET `/api/v1/health` (Spring API)

Public liveness response: `{ "status": "UP", "service": "aura-api" }`.

### GET `/api/v1/health` (Python AI service, port 8001 on loopback)

Public response: `{ "status": "UP", "service": "aura-ai-service", "version": "0.1.0" }`. OpenAPI and interactive docs are disabled. The service has no model, database, or research-provider route.

### GET `/api/v1/session`

Authenticated check used by the local UI to validate the owner's Basic Auth credentials before searching. Response: `{ "authenticated": true }`. It creates no session and returns no credential or profile data.

## Research jobs

### GET `/api/v1/research-jobs?limit=25`

Lists the most recently created jobs, newest first. `limit` defaults to 25 and must be from 1 to 100. This is a single-owner local history endpoint, not a multi-user search API.

### POST `/api/v1/research-jobs`

Creates a saved request; it does not search yet.

Request: `{ "subject": "Example public topic", "mode": "GENERAL" }`

`subject` is required, nonblank, and at most 300 characters. `mode` is `GENERAL` or `CYBERSECURITY`.

Response: `201 Created`, a `Location` header, and a body containing `id`, `subject`, `mode`, `status` (`REQUESTED`), and `createdAt`.

### GET `/api/v1/research-jobs/{id}`

Returns job state and search metadata. Unknown IDs return `404 RESEARCH_JOB_NOT_FOUND`.

### DELETE `/api/v1/research-jobs/{id}`

Deletes a saved brief and its associated source metadata. Returns `204 No Content`; unknown IDs return `404 RESEARCH_JOB_NOT_FOUND`. The database foreign key cascades to the job's saved sources.

### POST `/api/v1/research-jobs/{id}/search`

Runs one bounded request against the configured Brave Search API. A configured `AURA_BRAVE_API_KEY` is required. Missing configuration returns `503 SEARCH_PROVIDER_NOT_CONFIGURED` and leaves the job `REQUESTED`. A job can be searched once; other states return `409 RESEARCH_JOB_STATE_CONFLICT`.

Optional request fields:

- `country`: two uppercase letters, for example `US`.
- `language`: two lowercase letters, for example `en`.
- `freshness`: `pd` (day), `pw` (week), `pm` (month), or `py` (year).
- `resultLimit`: 1–20 per provider result type; defaults to 20.

The adapter requests web, news, and discussion result types with strict SafeSearch. It stores normalized URLs and source metadata (type, title, domain, publisher, provider description, published time when known, retrieval time, and rank). The provider can return up to three result types, so the combined result count can be as high as 60. Returned links are not fetched by AURA.

Responses include `provider`, `providerResultCount`, source results, and a coverage note. This is a single-provider sample, not a complete view of the internet. Discussion results are not a direct X/Twitter or social-account connector.

### GET `/api/v1/research-jobs/{id}/sources`

Lists saved results for a completed job. Optional filters:

- `kind`: `WEB`, `NEWS`, or `DISCUSSION`.
- `q`: case-insensitive match in title, description, publisher, or domain (maximum 200 characters).
- `host`: exact domain or subdomain match (maximum 253 characters).
- `publishedFrom`, `publishedTo`: inclusive UTC calendar dates. Results with unknown publication time are excluded when a date filter is used.

Reversed dates and invalid filters return `400 INVALID_RESEARCH_FILTER`. A job that has not completed returns `409 RESEARCH_JOB_STATE_CONFLICT`.

### GET `/api/v1/research-jobs/{id}/export?format=json|csv|md`

Downloads the same filtered result set as JSON, CSV, or Markdown. `format` defaults to `json`. CSV fields are quoted and formula-leading values are neutralized. Export includes provider metadata, source links, retrieval times, and the coverage note; it is not a synthesized report.

## Defensive vulnerability lookup

### POST `/api/v1/cyber/vulnerabilities/lookup`

Requires the local owner Basic Auth account. Request: `{ "cves": ["CVE-2024-12345"] }`. The list must contain 1–25 CVE IDs (`CVE-YYYY-NNNN...`); IDs are normalized to uppercase and duplicates are removed. After this explicit request, AURA downloads the fixed CISA KEV catalog and matches the identifiers locally; it sends the identifiers to FIRST's EPSS lookup API.

The response includes one finding per distinct input ID, provider status, retrieval time, provider data date where available, source links, CISA KEV fields, EPSS score and percentile, a priority rank, and the rationale. `knownExploited` is `null` if KEV is unavailable, `false` only when an available catalog has no match, and `true` for a catalog match. KEV entries sort first; within each group results sort by descending EPSS score, with original input order breaking ties. This is a transparent ordering rule, not an asset-specific risk score. EPSS is an estimate of exploitation probability in the next 30 days; a high estimate is not evidence that a particular asset is affected or currently exploited.

The CISA catalog is read from the [CISA-maintained KEV data mirror](https://github.com/CISAGov/kev-data), which documents that it mirrors the canonical CISA catalog and synchronizes shortly after CISA updates. A successful catalog snapshot is cached in memory for six hours. If refresh fails, an existing snapshot is returned with `kev.status=STALE`; without a snapshot the source is marked unavailable. EPSS uses FIRST's [lookup API](https://api.first.org/epss/), intended for one CVE or small batches, not bulk synchronization. Source failures are independent, so one provider can still return findings while the other is unavailable.

No result performs network probing, asset discovery, vulnerability scanning, exploit execution, or patching. AURA does not persist these lookups; download JSON or CSV from the local UI if a copy is needed.

### POST `/api/v1/cyber/sbom/lookup`

Requires the local owner Basic Auth account. Send one `file` part as `multipart/form-data`. The accepted input is CycloneDX JSON with `specVersion` 1.3–1.7; files are limited to 2 MB, 10,000 components, 2,000 vulnerability records, 25 distinct CVE IDs, and 10,000 distinct affected-reference pairs. Malformed JSON, unsupported formats/versions, duplicate component `bom-ref` values, and limit violations return `400 INVALID_SBOM`; a request above the configured multipart cap returns `413 SBOM_TOO_LARGE`.

The parser reads component metadata and references, then extracts only CVE identifiers already present in the SBOM's `vulnerabilities[].id` values. It maps `affects[].ref` to component `bom-ref` values where possible. A reference that cannot be resolved remains visible in the response. AURA does not use package names, versions, PURLs, or CPEs to infer missing CVEs, and does not establish that an inventory is installed or affected. Those exact declared CVEs are enriched using the same CISA KEV and FIRST EPSS behavior described above; no provider is called when the SBOM declares no CVEs.

The response includes format/spec version, component and CVE counts, declared CVEs, affected references and resolved component names/versions/PURLs, the enrichment response (or `null`), and a coverage note. Uploads are parsed in memory and are not saved to AURA's database. The web UI supports JSON and CSV downloads of the review. The SBOM file itself is not sent to CISA or FIRST; the public-service egress is limited to the CVE identifiers for EPSS and a KEV catalog refresh/match.

## Provider errors

- `502 SEARCH_PROVIDER_FAILURE` / `SEARCH_PROVIDER_INVALID_RESPONSE`: provider request or response failed.
- `503 SEARCH_PROVIDER_RATE_LIMITED` / `SEARCH_PROVIDER_UNAVAILABLE`: provider rate limit or temporary unavailability.
- Errors leave the job in `FAILED` with a stable `failureCode`; raw provider details and credentials are not returned.

## Current limits and next contracts

- Search sends the query to the configured external provider only when the owner invokes the search route. Provider access and possible charges are governed by that provider account.
- No direct social-media connector, complete-internet crawl, page fetch, AI synthesis, cross-source fact checking, or claim comparison is implemented.
- Person research is limited to public-interest material. Do not use this for doxxing, sensitive-trait inference, invasive tracking, or private-account access.
- Research history is capped to the newest 100 results per request; there is no cursor pagination yet. Queries and source metadata are retained until the owner deletes a brief. Remote deployments still need documented retention, audit, backup, and account-isolation policies.
- Future work: cursor-paginated history; source-specific adapters with their own quotas and terms; document upload and owner-filtered retrieval; cited synthesis; report retention/deletion; NVD enrichment, SPDX/SARIF SBOM support, vendor advisories, and inventory matching.
