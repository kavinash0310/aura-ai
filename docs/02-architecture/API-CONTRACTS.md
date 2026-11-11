# API contracts

Base path: `/api/v1`. JSON uses camelCase. Errors use RFC 9457-style Problem Details. IDs are UUIDs and timestamps are ISO-8601 UTC. All routes except health require the local owner Basic Auth account.

## Health

### GET `/api/v1/health` (Spring API)

Public liveness response: `{ "status": "UP", "service": "aura-api" }`.

### GET `/api/v1/health` (Python AI service, port 8001 on loopback)

Public response: `{ "status": "UP", "service": "aura-ai-service", "version": "0.1.0" }`. OpenAPI and interactive docs are disabled. The service has no database or public-search route. Its report, claim-comparison, and query-plan routes require the internal bearer token and are disabled unless local Ollama is explicitly configured.

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
- `queries`: optional ordered list of at most three search strings, each at most 300 characters. If omitted or empty, AURA uses the saved topic only. If supplied, the first item must match the saved topic after trimming (case-insensitive); AURA trims and removes duplicate strings case-insensitively. The UI previews this plan before the owner starts the search.

Brave requests web, news, and discussion result types with strict SafeSearch. GDELT requests the article-list mode and accepts source-country and source-language filters; its search horizon is limited to the last three months, so `py` maps to that three-month maximum. GDELT provides article metadata and titles, not article-body excerpts. Bluesky searches the official public AppView for recent posts, with an optional language and time lower bound; the country filter is not supported there. It stores public post text, a validated post link, and the record/index timestamp when available; author profiles and engagement counts are ignored. AURA stores normalized HTTP(S) URLs and source metadata (provider, type, title, domain, publisher, description when available, published time when known, retrieval time, rank, and originating search query). Brave can return up to three result types (up to 60 results per query); GDELT and Bluesky return at most 20 each per query. In `all` mode the combined maximum is 100 per query, or 300 across a three-query plan; duplicate URLs may appear for different providers or queries so their provenance remains visible. Returned links are not fetched automatically by search. Calls run sequentially in query order then provider order; AURA records one run row for each provider/query pair, continues after individual failures, and saves every successful batch. Any mix of successful and failed runs produces a `PARTIAL` job; if all runs fail, the job is `FAILED` and the first stable provider error is returned. Each selected provider receives every planned query, so provider quotas or charges may apply.

Responses include `provider`, `providerResultCount`, `providerRuns`, source results with each source's actual provider and `searchQuery`, and a coverage note. `providerRuns` contains one item per provider/query pair, including `queryOrder`, the exact query, `PENDING`/`RUNNING`/`SUCCEEDED`/`FAILED`/recovery-only `SKIPPED` status, result count, optional stable failure code, and timestamps. In `all` mode, the job's provider is `all`; each source retains its provider and originating query. `PARTIAL` runs return HTTP 200 and keep successful batches; all-failed runs retain provider outcomes on the job and return the first provider error. Successful source rows and their run outcome commit together. At startup, interrupted searches are reconciled without automatic retries; source rows from completed provider transactions remain available. Saved-job creation initializes `providerRuns` to an empty list; saved-job detail/history includes outcomes. Source listing and exports accept jobs in `COMPLETED` or `PARTIAL` state and carry the same coverage/outcome metadata. Coverage remains bounded, not a crawl of the internet. Bluesky results are public-post search only; AURA does not connect to an account or private feed. See the [GDELT DOC API overview](https://blog.gdeltproject.org/gdelt-doc-2-0-api-debuts/) and [Bluesky's API directory](https://bsky.network/docs/api-directory/) for provider behavior.

The [bounded query plan design](../05-backend/BOUNDED-QUERY-PLANS.md) describes persistence, request volume, and safe-use limits.

### POST `/api/v1/research-jobs/{id}/report`

Requires the local owner Basic Auth account and a `COMPLETED` or `PARTIAL` job. Request `{ "sourceIds": ["<source UUID>"] }` with one to 20 distinct source IDs from this job. The server resolves the IDs against the selected job and sends only their bounded metadata and provider descriptions plus the original query to the internal Python service. This action runs only after the owner clicks the report button. The local Ollama provider is disabled unless explicitly configured; there is no hosted fallback.

The response includes a title, summary, findings, model-generated limitations, the Ollama model name, generated time, prompt version, optional generation duration/token counts, cited source IDs, and source links. Each finding may include one short quote tied to a cited source. When present, Python verifies the quote matches that source's title or provider description after case and whitespace normalization. This only anchors the finding to the provider snippet; it does not prove that the linked page contains the text or that it supports the claim. Quotes are optional. Even if a page was explicitly read elsewhere in the UI, its text is not included or independently verified by the report. After filtering, the UI checks the first 20 visible sources by default and lets the owner change the checked set before generation. Reports are not persisted by AURA. The owner may download Markdown or an explicit JSON snapshot containing the query/job metadata, the candidate-source metadata supplied to the model, and the structured response. It excludes explicitly read page text; it preserves the evidence snapshot but does not guarantee identical regeneration.

### POST `/api/v1/research/query-plan`

Requires local-owner Basic Auth. Request: `{ "topic": "Example public topic" }`; topic is required and at most 300 characters. Spring sends the topic to the internal Python service using the shared bearer token. If local Ollama is enabled, it returns at most two distinct suggestions and model-run metadata. It makes no public search request and does not create or persist a job. The UI displays drafts with an explicit **Add** action; the owner can edit them and must still click **Start research** before selected public providers receive any query. If local AI is disabled or unavailable, the request returns `503 AI_SERVICE_UNAVAILABLE`; manual query entry remains available.

The Java-to-Python call uses `AURA_AI_INTERNAL_TOKEN`, which must be a separate random secret. Python accepts only Ollama at `localhost`, `host.docker.internal`, `ollama`, or a private IP on port 11434. `AURA_AI_MODEL_PROVIDER` defaults to `disabled`. Local model configuration is described in the local development guide.

### POST `/api/v1/research-jobs/{id}/compare`

Requires the local owner Basic Auth account and a `COMPLETED` or `PARTIAL` job. Request `{ "claim": "A specific statement", "sourceIds": ["<source UUID>"] }`; the claim must be nonblank and at most 500 characters, and the list must contain one to 20 distinct IDs from this job. After filtering, the UI checks the first 20 results by default and lets the owner choose sources; it sends checked IDs only after the owner clicks Compare claim.

The local model returns an assessment (`SUPPORTED`, `CHALLENGED`, `MIXED`, or `INSUFFICIENT`), a short explanation, and up to eight evidence items labeled `SUPPORTS`, `CHALLENGES`, `CONTEXT`, or `UNCLEAR`. Each item may include a short quote tied to one of its cited IDs; Python checks that it matches the saved title or provider description after case/whitespace normalization. This anchors the text to the provider snippet but does not prove semantic support or linked-page contents. Python checks structure, stance consistency, quote membership, and citation membership; Spring independently checks job/source ownership. The result is an AI assessment of selected snippets, not a fact-check or proof. Missing snippets are not evidence that a statement is false. The model receives no page snapshots, private documents, connected-account data, or outside search results. The result is not persisted; Markdown and JSON are available as explicit local downloads. See the [claim comparison design](../05-backend/CLAIM-COMPARISON.md).

All model-backed endpoints return prompt version and optional Ollama duration/token counters. The AI service logs the same operational metadata without logging user input or generated content. See the [local report model-run metadata](../05-backend/RESEARCH-REPORT-GENERATION.md).

### GET `/api/v1/research-jobs/{id}/sources`

Lists saved results for a completed or partially completed job. Optional filters:

- `kind`: `WEB`, `NEWS`, or `DISCUSSION`.
- `provider`: exact provider name such as `brave`, `gdelt`, or `bluesky` (case-insensitive).
- `q`: case-insensitive match in title, description, publisher, domain, or originating search query (maximum 200 characters).
- `host`: exact domain or subdomain match (maximum 253 characters).
- `publishedFrom`, `publishedTo`: inclusive UTC calendar dates. Results with unknown publication time are excluded when a date filter is used.

Reversed dates and invalid filters return `400 INVALID_RESEARCH_FILTER`. A job that has not completed or partially completed returns `409 RESEARCH_JOB_STATE_CONFLICT`.

### POST `/api/v1/research-jobs/{id}/sources/{sourceId}/page`

Reads one public page after an explicit authenticated request. The source ID must already belong to the specified completed or partially completed brief. There is no caller-supplied URL field, so the API fetches only the saved source URL. AURA allows HTTPS on port 443 only, public hostnames with public IPv4 DNS answers, and at most two redirects that remain on the original hostname. It disables cookies, credentials, compression, automatic redirects, and retries; allows one active read; and limits the response to 1 MB, extracted static text to 100,000 characters, DNS lookup to three seconds, connect/pool and response/socket waits to a few seconds, and the entire request/redirect sequence to 20 seconds. HTML/XHTML only. IPv6-only hosts and JavaScript-rendered pages are unsupported.

The response contains `readId`, `sourceId`, the final page URL and host, title, character count, SHA-256 digest of extracted text, truncation flag, fetch timestamp, text, and a coverage note. A metadata-only audit row is committed before the fetch and updated with status, completion time, hash/count/truncation or a stable failure code. It never stores the fetched text; the row is deleted with its source or brief. The digest identifies the returned text snapshot; it does not prove the page's origin or truth. The body is not saved by AURA, sent to the AI service, or included in reports. The UI holds it in component memory until that view is replaced or reloaded; a user may explicitly download a plain-text copy. Possible errors include `400 PUBLIC_PAGE_URL_BLOCKED`, `413 PUBLIC_PAGE_TOO_LARGE`, `415 PUBLIC_PAGE_NOT_HTML`, `429 PUBLIC_PAGE_FETCH_BUSY`, and `502 PUBLIC_PAGE_FETCH_FAILED`.

### GET `/api/v1/research-jobs/{id}/sources/{sourceId}/page-reads?limit=10`

Returns recent metadata-only read attempts for a saved source in a completed or partially completed brief. `limit` defaults to 10 and must be from 1 to 100. Each row includes status (`RUNNING`, `SUCCEEDED`, `FAILED`, or `ABANDONED`), request/completion times, optional content hash/count/truncation, and a stable failure code. It does not return page text or a page URL. Startup recovery marks interrupted `RUNNING` rows as `ABANDONED`; this currently assumes one backend instance.

### GET `/api/v1/research-jobs/{id}/export?format=json|csv|md`

Downloads the same filtered result set as JSON, CSV, or Markdown. `format` defaults to `json`. CSV fields are quoted and formula-leading values are neutralized. Exports include provider metadata, source links, retrieval times, provider-run outcomes, and the coverage note; CSV uses `SOURCE` and `PROVIDER_RUN` record types so outcomes remain machine-readable. An export is not a synthesized report.

## Defensive vulnerability lookup

### POST `/api/v1/cyber/vulnerabilities/lookup`

Requires the local owner Basic Auth account. Request: `{ "cves": ["CVE-2024-12345"] }`. The list must contain 1–25 CVE IDs (`CVE-YYYY-NNNN...`); IDs are normalized to uppercase and duplicates are removed. After this explicit request, AURA downloads the fixed CISA KEV catalog and matches the identifiers locally; it sends the identifiers to FIRST's EPSS lookup API.

The response includes one finding per distinct input ID, provider status, retrieval time, provider data date where available, source links, CISA KEV fields, EPSS score and percentile, a priority rank, and the rationale. `knownExploited` is `null` if KEV is unavailable, `false` only when an available catalog has no match, and `true` for a catalog match. KEV entries sort first; within each group results sort by descending EPSS score, with original input order breaking ties. This is a transparent ordering rule, not an asset-specific risk score. EPSS is an estimate of exploitation probability in the next 30 days; a high estimate is not evidence that a particular asset is affected or currently exploited.

The CISA catalog is read from the [CISA-maintained KEV data mirror](https://github.com/CISAGov/kev-data), which documents that it mirrors the canonical CISA catalog and synchronizes shortly after CISA updates. A successful catalog snapshot is cached in memory for six hours. If refresh fails, an existing snapshot is returned with `kev.status=STALE`; without a snapshot the source is marked unavailable. EPSS uses FIRST's [lookup API](https://api.first.org/epss/), intended for one CVE or small batches, not bulk synchronization. Source failures are independent, so one provider can still return findings while the other is unavailable.

No result performs network probing, asset discovery, vulnerability scanning, exploit execution, or patching. AURA does not persist these lookups; download JSON or CSV from the local UI if a copy is needed.

### POST `/api/v1/cyber/sbom/lookup`

Requires the local owner Basic Auth account. Send one `file` part as `multipart/form-data`. Accepted inputs are CycloneDX JSON with `specVersion` 1.3–1.7, SPDX 2.3 JSON (`spdxVersion: "SPDX-2.3"`), and SARIF 2.1.0 JSON (`version: "2.1.0"`). Files are limited to 2 MB and 10,000 CycloneDX components, SPDX packages, or SARIF results; SARIF logs also allow at most 100 runs. CycloneDX inputs allow up to 2,000 vulnerability records. SPDX inputs allow at most 10,000 external references. All formats allow at most 25 distinct CVE IDs and 10,000 affected-reference pairs. Malformed JSON, unsupported formats/versions, duplicate component/package identifiers, and limit violations return `400 INVALID_SBOM`; a request above the configured multipart cap returns `413 UPLOAD_TOO_LARGE`.

The CycloneDX parser reads component metadata and maps `affects[].ref` to component `bom-ref` values. The SPDX 2.3 parser reads packages and their `SPDXID` values, and extracts a CVE only from an `externalRefs` entry whose category is `SECURITY`, type is `advisory`, and locator is either an exact CVE ID or an exact NVD CVE-detail URL. The SARIF 2.1.0 parser counts results and extracts a CVE only when an exact CVE ID appears in `results[].ruleId`, or in the referenced `tool.driver.rules[].id` when a result has no `ruleId` and supplies a valid `ruleIndex`. It does not interpret rule descriptions, messages, file paths, source snippets, or external URLs. It does not fetch external references. A reference that cannot be resolved remains visible in the response. No format uses package names, versions, PURLs, messages, or CPEs to infer missing CVEs, and no format establishes that an inventory is installed or affected. Only exact, declared CVE IDs are enriched using the same CISA KEV and FIRST EPSS behavior described above; no provider is called when the file declares no CVEs. SPDX security references follow the [SPDX 2.3 specification](https://spdx.github.io/spdx-spec/v2.3/) and its [advisory reference guidance](https://spdx.github.io/spdx-spec/v2.3/how-to-use/). SARIF parsing follows the [OASIS SARIF 2.1.0 standard](https://docs.oasis-open.org/sarif/sarif/v2.1.0/os/sarif-v2.1.0-os.html).

The response includes format/spec version, component/package/result and CVE counts, declared CVEs, affected package or SARIF finding references, resolved local names/versions/PURLs where available, the enrichment response (or `null`), and a coverage note. SARIF counts include all results, but only references with an exact declared CVE are returned as findings. Uploads are parsed in memory and are not saved to AURA's database. The web UI supports JSON and CSV downloads of the review. Uploaded files are not sent to CISA or FIRST; public-service egress is limited to the exact CVE identifiers for EPSS and a KEV catalog refresh/match.

### POST `/api/v1/cyber/vulnerabilities/nvd/lookup`

Requires the local owner Basic Auth account. Request exactly one CVE ID: `{ "cve": "CVE-2024-12345" }`. AURA validates the identifier and makes one explicit exact-ID request to the fixed NVD API 2.0 endpoint. The optional `AURA_NVD_API_KEY` is sent only in the API key header; it is not required for lookup. No request is made until the user selects “Load NVD details” for a CVE. There are no automatic retries.

### POST `/api/v1/cyber/vulnerabilities/nvd/lookup/batch`

Requires the local owner Basic Auth account. Request `{ "cves": ["CVE-2024-12345", "CVE-2023-54321"] }` with 1–25 exact CVE IDs. AURA normalizes and de-duplicates them, then makes one NVD API 2.0 request with only those IDs. The response returns a status and bounded detail for each distinct identifier in request order. The local UI exposes this only through an explicit batch action for the current CVE list or the declared SBOM findings; uploading or analyzing an SBOM does not trigger NVD lookup. See the [NVD Vulnerabilities API](https://nvd.nist.gov/developers/vulnerabilities). There are no automatic retries.

Each result status is `AVAILABLE`, `NOT_FOUND`, `RATE_LIMITED`, or `UNAVAILABLE` and includes request/retrieval timestamps, NVD record link, description (up to 2,000 characters), CVSS severity/version/vector when available, CWE identifiers, up to 50 CPE applicability criteria, and up to 10 HTTPS references. Truncation flags indicate when CPE criteria or references were capped. Results are held in a six-hour, memory-only cache capped at 500 records; unavailable and rate-limited responses are not cached. A 429 is returned as `RATE_LIMITED` without retry. CPE criteria are evidence from NVD and are not automatically matched to SBOM packages. NVD CVSS is severity information, not an asset-specific risk assessment.

NVD receives only the requested exact CVE identifiers. SBOM and SARIF files and component inventories are not sent to NVD. AURA does not persist NVD responses.

### POST `/api/v1/cyber/vulnerabilities/osv/lookup`

Requires the local owner Basic Auth account. Request exactly one CVE ID: `{ "cve": "CVE-2024-12345" }`. AURA validates the ID and makes one explicit HTTPS request to OSV's fixed `GET /v1/vulns/{id}` endpoint. It does not follow redirects. The UI exposes this as a per-CVE “Load OSV advisory” action; there is no batch or automatic lookup. OSV receives only the exact CVE ID. See the [OSV vulnerability-by-ID API](https://google.github.io/osv.dev/get-v1-vulns/). AURA makes no automatic retries.

The response status is `AVAILABLE`, `NOT_FOUND`, `RATE_LIMITED`, or `UNAVAILABLE`. Available records include retrieval time, OSV record ID, summary and details (capped at 500 and 5,000 characters), up to 20 aliases, up to 20 affected package entries, up to 100 exact versions and 10 range entries per package (with up to 50 structured events each), and up to 10 HTTPS references. Range and version caps have explicit truncation flags. Response bodies are capped at 256 KB and requests time out after eight seconds. References are displayed but never fetched. A six-hour in-memory cache holds up to 500 records; unavailable and rate-limited responses are not cached. AURA does not persist advisory responses.

OSV's affected package and version data is third-party advisory evidence. AURA does not send local package inventories to OSV. After an explicit OSV lookup initiated from an SBOM finding, the browser first correlates advisory packages with that finding's local components by exact PURL identity after removing only the version segment. It compares literal OSV affected-version entries by exact equality for any ecosystem and evaluates strict npm SemVer 2 ranges locally when the OSV advisory is available, not withdrawn, complete, and supported. Other ecosystem ranges and OSV `ECOSYSTEM`/`GIT` range types remain `NOT_ASSESSED`; package-name fallback is not used. A `NO_MATCH_IN_RETURNED_OSV_VERSION_DATA` result is not proof of safety beyond the source record. The local identity and version assessment are included in explicit SBOM JSON/CSV downloads. A CVE advisory or PURL identity match alone does not prove that a particular installed product is affected.

### POST `/api/v1/cyber/vulnerabilities/github-advisories/lookup`

Requires the local owner Basic Auth account. Request exactly one CVE ID: `{ "cve": "CVE-2024-12345" }`. AURA validates the identifier and makes one explicit request to GitHub's public global-advisories endpoint, filtered by that exact `cve_id` and `type=reviewed`. No GitHub account or token is used. The action is available per CVE from the UI; SBOM review never triggers this lookup automatically. The API receives only the selected CVE identifier. See GitHub's [REST API documentation for global security advisories](https://docs.github.com/en/rest/security-advisories/global-advisories).

Responses have status `AVAILABLE`, `NOT_FOUND`, `RATE_LIMITED`, or `UNAVAILABLE`, and include retrieval time plus up to ten exact-CVE advisory records. Each record is bounded to 3,000 description characters, 20 CWE identifiers, 20 affected package entries, and 10 HTTPS references. CVSS v3 and v4 values are exposed when supplied by GitHub. Response bodies are limited to 256 KB; requests time out after eight seconds, do not follow redirects, and are not retried. Available and not-found results are cached in memory for six hours, up to 500 CVEs; rate-limited and unavailable results are not cached. AURA does not persist advisory responses.

Only reviewed advisories are requested. Each returned record must repeat the exact CVE in GitHub's `cve_id` field or CVE identifiers; records for other CVEs are discarded. Advisory text, severity, affected package ranges, and CVSS are source claims, not proof of local exposure or asset-specific risk. AURA does not submit package inventories, infer package matches, or fetch reference links. The UI displays data from the public API; JSON/CSV downloads occur only when the owner explicitly requests an export.

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
- X/account connectors and complete-internet crawling are not implemented. Bluesky public-post search is a bounded official-API connector. Local-model report synthesis and user-directed claim comparison are bounded first slices over selected provider snippets; neither is a fact-check. The separate page-read action fetches only one selected saved source after an explicit request.
- Person research is limited to public-interest material. Do not use this for doxxing, sensitive-trait inference, invasive tracking, or private-account access.
- Research history is capped to the newest 100 results per request; there is no cursor pagination yet. Queries and source metadata are retained until the owner deletes a brief. Remote deployments still need documented retention, audit, backup, and account-isolation policies.
- Future work: cursor-paginated history; source-specific adapters with their own quotas and terms; document versioning, PostgreSQL full-text search, richer parsers, chunking and owner-filtered retrieval; semantic citation evaluation, report retention/deletion; vendor advisories and inventory matching.
