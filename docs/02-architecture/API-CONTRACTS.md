# API contracts

Base path: `/api/v1`. JSON uses camelCase. Errors use RFC 9457-style Problem Details. IDs are UUIDs and timestamps are ISO-8601 UTC. All routes except health require the local owner Basic Auth account.

## Health

### GET `/api/v1/health` (Spring API)

Public liveness response: `{ "status": "UP", "service": "aura-api" }`.

### GET `/api/v1/health` (Python AI service, port 8001 on loopback)

Public response: `{ "status": "UP", "service": "aura-ai-service", "version": "0.1.0" }`. OpenAPI and interactive docs are disabled. The service has no database or research-provider route. Its report route is separate, requires the internal bearer token, and is disabled unless local Ollama is explicitly configured.

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

Runs one bounded research job. The optional `provider` is `brave`, `gdelt`, `bluesky`, or `all`; when omitted, AURA selects the first configured provider (Brave, then GDELT, then Bluesky). `all` queries every currently configured provider in order. GDELT and Bluesky public search are enabled by default and need no key or account credentials; set `AURA_GDELT_ENABLED=false` or `AURA_BLUESKY_ENABLED=false` to disable them. Brave requires the owner's `AURA_BRAVE_API_KEY`. If no provider is available, the request returns `503 SEARCH_PROVIDER_NOT_CONFIGURED` and leaves the job `REQUESTED`. A job can be searched once; other states return `409 RESEARCH_JOB_STATE_CONFLICT`.

Optional request fields:

- `country`: two uppercase letters, for example `US`.
- `language`: two lowercase letters, for example `en`.
- `freshness`: `pd` (day), `pw` (week), `pm` (month), or `py` (year).
- `resultLimit`: 1–20 per provider result type; defaults to 20.
- `provider`: `brave`, `gdelt`, `bluesky`, `all`, or `null` for automatic selection.

Brave requests web, news, and discussion result types with strict SafeSearch. GDELT requests the article-list mode and accepts source-country and source-language filters; its search horizon is limited to the last three months, so `py` maps to that three-month maximum. GDELT provides article metadata and titles, not article-body excerpts. Bluesky searches the official public AppView for recent posts, with an optional language and time lower bound; the country filter is not supported there. It stores public post text, a validated post link, and the record/index timestamp when available; author profiles and engagement counts are ignored. AURA stores normalized HTTP(S) URLs and source metadata (provider, type, title, domain, publisher, description when available, published time when known, retrieval time, and rank). Brave can return up to three result types (up to 60 results); GDELT and Bluesky return at most 20 each. In `all` mode the combined maximum is 100, and duplicate URLs may appear with separate provider provenance. Returned links are not fetched by AURA. Calls are sequential; AURA records a run row for each provider, continues after individual failures, and saves every successful batch. Any mix of successful and failed providers produces a `PARTIAL` job; if all providers fail, the job is `FAILED` and the first stable provider error is returned. A provider called before a later failure has already received the query.

Responses include `provider`, `providerResultCount`, `providerRuns`, source results with each source's actual provider, and a coverage note. `providerRuns` contains one item per selected provider with its `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, or recovery-only `SKIPPED` status, result count, optional stable failure code, and timestamps. In `all` mode, the job's provider is `all`; each source retains its provider name. `PARTIAL` runs return HTTP 200 and keep successful batches; all-failed runs retain provider outcomes on the job and return the first provider error. Successful provider rows and their audit outcome commit together. At startup, interrupted searches are reconciled without automatic retries; source rows from completed provider transactions remain available. Saved-job creation initializes `providerRuns` to an empty list; saved-job detail/history includes outcomes. Source listing and exports accept jobs in `COMPLETED` or `PARTIAL` state and carry the same coverage/outcome metadata. Coverage remains bounded, not a crawl of the internet. Bluesky results are public-post search only; AURA does not connect to an account or private feed. See the [GDELT DOC API overview](https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/) and [Bluesky's API directory](https://bsky.network/docs/api-directory/) for provider behavior.

### POST `/api/v1/research-jobs/{id}/report`

Requires the local owner Basic Auth account and a `COMPLETED` or `PARTIAL` job. Request `{ "sourceIds": ["<source UUID>"] }` with one to 20 distinct source IDs from this job. The server resolves the IDs against the selected job and sends only their bounded metadata and provider descriptions plus the original query to the internal Python service. This action runs only after the owner clicks the report button. The local Ollama provider is disabled unless explicitly configured; there is no hosted fallback.

The response includes a title, summary, findings, model-generated limitations, the Ollama model name, generated time, cited source IDs, and source links. The Python service rejects citations that are not among the supplied sources, but this verifies ID membership only; it does not prove that a source supports a claim. A fixed coverage note says that AURA did not fetch or independently verify linked pages. The UI sends the first 20 currently visible sources, so apply filters before generating a report. Reports are not persisted by AURA; the user may download the current result as Markdown.

The Java-to-Python call uses `AURA_AI_INTERNAL_TOKEN`, which must be a separate random secret. Python accepts only Ollama at `localhost`, `host.docker.internal`, `ollama`, or a private IP on port 11434. `AURA_AI_MODEL_PROVIDER` defaults to `disabled`. Local model configuration is described in the local development guide.

### GET `/api/v1/research-jobs/{id}/sources`

Lists saved results for a completed or partially completed job. Optional filters:

- `kind`: `WEB`, `NEWS`, or `DISCUSSION`.
- `provider`: exact provider name such as `brave`, `gdelt`, or `bluesky` (case-insensitive).
- `q`: case-insensitive match in title, description, publisher, or domain (maximum 200 characters).
- `host`: exact domain or subdomain match (maximum 253 characters).
- `publishedFrom`, `publishedTo`: inclusive UTC calendar dates. Results with unknown publication time are excluded when a date filter is used.

Reversed dates and invalid filters return `400 INVALID_RESEARCH_FILTER`. A job that has not completed or partially completed returns `409 RESEARCH_JOB_STATE_CONFLICT`.

### GET `/api/v1/research-jobs/{id}/export?format=json|csv|md`

Downloads the same filtered result set as JSON, CSV, or Markdown. `format` defaults to `json`. CSV fields are quoted and formula-leading values are neutralized. Exports include provider metadata, source links, retrieval times, provider-run outcomes, and the coverage note; CSV uses `SOURCE` and `PROVIDER_RUN` record types so outcomes remain machine-readable. An export is not a synthesized report.

## Defensive vulnerability lookup

### POST `/api/v1/cyber/vulnerabilities/lookup`

Requires the local owner Basic Auth account. Request: `{ "cves": ["CVE-2024-12345"] }`. The list must contain 1–25 CVE IDs (`CVE-YYYY-NNNN...`); IDs are normalized to uppercase and duplicates are removed. After this explicit request, AURA downloads the fixed CISA KEV catalog and matches the identifiers locally; it sends the identifiers to FIRST's EPSS lookup API.

The response includes one finding per distinct input ID, provider status, retrieval time, provider data date where available, source links, CISA KEV fields, EPSS score and percentile, a priority rank, and the rationale. `knownExploited` is `null` if KEV is unavailable, `false` only when an available catalog has no match, and `true` for a catalog match. KEV entries sort first; within each group results sort by descending EPSS score, with original input order breaking ties. This is a transparent ordering rule, not an asset-specific risk score. EPSS is an estimate of exploitation probability in the next 30 days; a high estimate is not evidence that a particular asset is affected or currently exploited.

The CISA catalog is read from the [CISA-maintained KEV data mirror](https://github.com/CISAGov/kev-data), which documents that it mirrors the canonical CISA catalog and synchronizes shortly after CISA updates. A successful catalog snapshot is cached in memory for six hours. If refresh fails, an existing snapshot is returned with `kev.status=STALE`; without a snapshot the source is marked unavailable. EPSS uses FIRST's [lookup API](https://api.first.org/epss/), intended for one CVE or small batches, not bulk synchronization. Source failures are independent, so one provider can still return findings while the other is unavailable.

No result performs network probing, asset discovery, vulnerability scanning, exploit execution, or patching. AURA does not persist these lookups; download JSON or CSV from the local UI if a copy is needed.

### POST `/api/v1/cyber/sbom/lookup`

Requires the local owner Basic Auth account. Send one `file` part as `multipart/form-data`. The accepted input is CycloneDX JSON with `specVersion` 1.3–1.7; files are limited to 2 MB, 10,000 components, 2,000 vulnerability records, 25 distinct CVE IDs, and 10,000 distinct affected-reference pairs. Malformed JSON, unsupported formats/versions, duplicate component `bom-ref` values, and limit violations return `400 INVALID_SBOM`; a request above the configured multipart cap returns `413 UPLOAD_TOO_LARGE`.

The parser reads component metadata and references, then extracts only CVE identifiers already present in the SBOM's `vulnerabilities[].id` values. It maps `affects[].ref` to component `bom-ref` values where possible. A reference that cannot be resolved remains visible in the response. AURA does not use package names, versions, PURLs, or CPEs to infer missing CVEs, and does not establish that an inventory is installed or affected. Those exact declared CVEs are enriched using the same CISA KEV and FIRST EPSS behavior described above; no provider is called when the SBOM declares no CVEs.

The response includes format/spec version, component and CVE counts, declared CVEs, affected references and resolved component names/versions/PURLs, the enrichment response (or `null`), and a coverage note. Uploads are parsed in memory and are not saved to AURA's database. The web UI supports JSON and CSV downloads of the review. The SBOM file itself is not sent to CISA or FIRST; the public-service egress is limited to the CVE identifiers for EPSS and a KEV catalog refresh/match.

### POST `/api/v1/cyber/vulnerabilities/nvd/lookup`

Requires the local owner Basic Auth account. Request exactly one CVE ID: `{ "cve": "CVE-2024-12345" }`. AURA validates the identifier and makes one explicit exact-ID request to the fixed NVD API 2.0 endpoint. The optional `AURA_NVD_API_KEY` is sent only in the API key header; it is not required for lookup. No request is made until the user selects “Load NVD details” for a CVE. There are no automatic retries.

### POST `/api/v1/cyber/vulnerabilities/nvd/lookup/batch`

Requires the local owner Basic Auth account. Request `{ "cves": ["CVE-2024-12345", "CVE-2023-54321"] }` with 1–25 exact CVE IDs. AURA normalizes and de-duplicates them, then makes one NVD API 2.0 request with only those IDs. The response returns a status and bounded detail for each distinct identifier in request order. The local UI exposes this only through an explicit batch action for the current CVE list or the declared SBOM findings; uploading or analyzing an SBOM does not trigger NVD lookup. See the [NVD Vulnerabilities API](https://nvd.nist.gov/developers/vulnerabilities). There are no automatic retries.

Each result status is `AVAILABLE`, `NOT_FOUND`, `RATE_LIMITED`, or `UNAVAILABLE` and includes request/retrieval timestamps, NVD record link, description (up to 2,000 characters), CVSS severity/version/vector when available, CWE identifiers, up to 50 CPE applicability criteria, and up to 10 HTTPS references. Truncation flags indicate when CPE criteria or references were capped. Results are held in a six-hour, memory-only cache capped at 500 records; unavailable and rate-limited responses are not cached. A 429 is returned as `RATE_LIMITED` without retry. CPE criteria are evidence from NVD and are not automatically matched to SBOM packages. NVD CVSS is severity information, not an asset-specific risk assessment.

NVD receives only the requested exact CVE identifiers. SBOM files and component inventories are not sent to NVD. AURA does not persist NVD responses.

## Personal document library

All document routes require the local owner Basic Auth account. Document upload is an explicit local action; file contents are stored in PostgreSQL and are not sent to Brave, FIRST, CISA, NVD, or an AI model.

### POST `/api/v1/documents`

Send one `file` part and an optional `title` part as `multipart/form-data`. `.txt` and `.md` files must use valid UTF-8. Text-based `.pdf` files are parsed with PDFBox; they must have a PDF signature, contain no more than 100 pages, and yield no more than 250,000 extracted characters. Scanned PDFs with no text layer and encrypted PDFs are unsupported; OCR is not enabled. Every upload is limited to 1 MB and the local library is capped at 100 documents. Filenames are reduced to a basename, text line endings are normalized to LF, empty/binary/control-heavy text is rejected, and SHA-256 is calculated over normalized or extracted UTF-8 text. PDF bytes are not retained: the response `mediaType` remains `application/pdf` to indicate the source format, while stored `content` is extracted text with form-feed page separators. The response is `201 Created`, includes metadata and a `Location` header, and does not include content. Repeated uploads create separate document records; version history is not implemented.

### GET `/api/v1/documents`

Returns up to the 100 newest documents as metadata only. There is no pagination yet.

### GET `/api/v1/documents/{id}`

Returns metadata and full stored text for one document. The local UI previews at most 20,000 characters. Text/Markdown downloads retain their text filename; a PDF download is named `{originalFilename}.extracted.txt` because original PDF bytes are not stored.

### GET `/api/v1/documents/search?q=...`

Searches up to 10 documents for a case-insensitive exact phrase. `q` must be 2–100 letters, numbers, spaces, periods, commas, underscores, or hyphens. PostgreSQL uses an indexed full-text query to find and rank candidates, then checks the literal phrase before returning a result. Its `simple` text-search configuration does not stem words; H2 uses the portable phrase-match fallback. Results include a bounded snippet, offsets into normalized/extracted document text, content hash, and a stable `aura-doc://{id}#chars={start}-{end}` citation. PDF hits also include the 1-based page and use `aura-doc://{id}#page={page}&chars={start}-{end}`; offsets include form-feed page separators. Chunking and semantic retrieval remain future work.

### DELETE `/api/v1/documents/{id}`

Deletes the document and its stored text. Returns `204 No Content`; an unknown ID returns `404 RESEARCH_DOCUMENT_NOT_FOUND`.

Invalid content returns `400 INVALID_DOCUMENT`; requests exceeding the global multipart cap return `413 UPLOAD_TOO_LARGE`. Uploaded files are never executed. PDFs are parsed in-process with strict byte, page, and extracted-text limits; other formats are not parsed as HTML, archives, or office documents.


## Provider errors

- `502 SEARCH_PROVIDER_FAILURE` / `SEARCH_PROVIDER_INVALID_RESPONSE`: provider request or response failed.
- `503 SEARCH_PROVIDER_RATE_LIMITED` / `SEARCH_PROVIDER_UNAVAILABLE`: provider rate limit or temporary unavailability.
- Errors leave the job in `FAILED` with a stable `failureCode`; raw provider details and credentials are not returned.

## Current limits and next contracts

- Search sends the query to the selected configured external provider or providers only when the owner invokes the search route. Provider access and possible charges are governed by those provider accounts.
- X/account connectors, complete-internet crawl, page fetch, cross-source fact checking, and claim comparison are not implemented. Bluesky public-post search is a bounded official-API connector. Local-model synthesis is a bounded first slice over provider snippets only.
- Person research is limited to public-interest material. Do not use this for doxxing, sensitive-trait inference, invasive tracking, or private-account access.
- Research history is capped to the newest 100 results per request; there is no cursor pagination yet. Queries and source metadata are retained until the owner deletes a brief. Remote deployments still need documented retention, audit, backup, and account-isolation policies.
- Future work: cursor-paginated history; source-specific adapters with their own quotas and terms; document versioning, PostgreSQL full-text search, richer parsers, chunking and owner-filtered retrieval; cited synthesis; report retention/deletion; automated NVD enrichment, SPDX/SARIF SBOM support, vendor advisories, and inventory matching.
