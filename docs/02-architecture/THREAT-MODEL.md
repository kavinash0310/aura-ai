# Threat model

## Assets

Research queries and reports; selected documents and source excerpts; account tokens and grants; provenance and audit history; local database and model configuration.

## Trust boundaries

1. Browser to local UI/API: unauthenticated callers cannot access owner data; the UI sends Basic Auth only to the same-origin loopback API and keeps the entered credentials in component memory.
2. API to PostgreSQL: credentials are externalized; DB is not public.
3. API to search provider: the query and search options leave the local machine only after an authenticated, explicit search request; the selected Brave, GDELT, or Bluesky endpoint is fixed in code, and returned metadata is untrusted.
4. API to vulnerability data providers: the CISA KEV catalog is downloaded on an authenticated lookup, CVE identifiers are sent to FIRST EPSS, and exact CVE IDs are sent to NVD, OSV, or the GitHub Advisory Database only after explicit actions; data can be stale or incomplete.
5. API to Python/model provider: send minimized evidence; model output is untrusted.
6. Owner-selected documents to document library: text can contain private information or misleading instructions; only authenticated uploads are accepted and the content is not sent to external providers or models. PDF parsing is bounded by upload size, page count, extracted-text size, and serialized parser access.
7. Future local agent to assets: only exact scoped jobs from authenticated server are accepted.
8. API to a selected public source page: only an authenticated request for a source already saved in the requested brief can trigger one page read. The source page is untrusted public input; its response text is returned to the browser but is not persisted or included in model reports.
9. UI to the local query planner: an authenticated owner may explicitly send one topic to the configured local Ollama endpoint to draft up to two public search angles. Suggestions are not sent to public search providers unless the owner adds/reviews them and separately starts a search.

## Threats and controls

| Threat | Example | Control |
|---|---|---|
| Unauthenticated access | Network caller reads research history | Basic Auth on non-health routes; loopback binding; review before remote use |
| Prompt injection | A provider snippet contains instructions aimed at the model | Only selected snippets are sent after an explicit action; source content is marked untrusted; the model has no tools; outputs are schema-bounded and citations must match supplied IDs |
| SSRF | A saved result or redirect targets loopback, private services, or cloud metadata | Fetch only a saved source after an explicit POST; require HTTPS on port 443 and a multi-label hostname; resolve through a bounded custom DNS resolver and connect only to public IPv4 answers; reject special/private ranges and mixed private/public answers; disable automatic redirects and permit at most two same-host redirects; disable cookies, auth, compression, and retries; cap response at 1 MB and extracted text at 100,000 characters; allow one active fetch; apply short connection, pool, response, socket, and DNS timeouts. IPv6-only hosts are unsupported. |
| Malicious page content | Page embeds scripts, hidden text, or prompt-injection instructions | Parse static HTML as untrusted data; remove script/style/noscript/template/SVG/iframe/object/embed elements; display text in escaped React text/pre elements; do not execute page code or send page text to the model |
| Page privacy and retention | A page contains personal or confidential information | Fetch only after a user click; source must belong to the selected brief; do not store fetched text or log it; keep it only in browser component memory unless the owner explicitly downloads it; retain person-research restrictions and public-interest scope |
| Platform abuse | Crawler bypasses source controls | Permitted APIs/feeds/public access; respect terms/rate limits; no login/paywall/CAPTCHA bypass |
| Query disclosure | Search terms reveal interests to one or more providers | Show Automatic, one-provider, or all-configured selection; keep the optional Brave key in local `.env`; send no documents or account data; query only on explicit request; allow GDELT and Bluesky to be disabled |
| Social-content misuse | A public-post query becomes a tool for targeting or profiling a person | Restrict the product boundary to public-interest research; prohibit private-account access, doxxing, sensitive-trait inference, harassment, invasive tracking, and bulk profile monitoring; use Bluesky topic search only |
| Stale social snapshot | A saved post is edited or removed at its source | Preserve source link and retrieval time; treat local copies and exports as dated snapshots; document that AURA has no remote deletion-sync or retention schedule; retain only in an owner-controlled local brief until deletion |
| CVE lookup disclosure | Submitted CVE identifiers reach the EPSS service | Show the provider before submission; accept only exact CVE IDs and a small batch; use a fixed HTTPS host; make requests only after explicit authenticated submission. KEV matching is local against the downloaded catalog |
| Stale vulnerability signal | Cached or delayed data changes priority order | Return provider state, retrieval time, data date, source links, and an explicit stale status; never present EPSS as proof of exploitation or asset exposure |
| Browser credential exposure | A stored Basic Auth password is exposed to scripts or browser storage | Do not persist credentials in local/session storage or cookies; keep them in page memory, send only to same-origin `/api` routes, clear them on disconnect/reload, and keep the app loopback-only |
| Provider quota/cost | Repeated broad searches consume an account allowance | No background search or automatic retries; result limit is capped; provider timeout is bounded; provider billing is controlled by the owner's account |
| Private-person harm | Results aggregate home address or sensitive traits | Public-interest scope; exclude sensitive fields/profiling; identity disambiguation |
| Secret leakage | Token appears in prompt/report/log | Secret store, redaction, structured logging |
| Parser exploit | Malformed PDF exploits parser | Type/size/page/output caps and serialized PDF parsing; consider process isolation before remote deployment; validate archive paths and never execute uploaded content |
| Cross-owner leak | Search returns another user's chunk | Current database is a single local-owner workspace; add owner scoping before multi-user or remote deployment |
| Model misinformation | Unsupported claim sounds confident | The report displays citations and fixed coverage limits; ID membership is checked, but semantic support is not yet evaluated; owner reviews report before use |
| AI service impersonation | A local process calls the Python report endpoint | Separate Java-to-Python bearer token; constant-time token comparison; disabled provider fails closed |
| Model egress | Private text is sent to a remote model by configuration | Only loopback/private Ollama endpoints are accepted; no hosted fallback; report sends public provider snippets only, and excludes uploaded library documents |
| Unreviewed model-generated query | A model suggestion is sent to a public provider without the owner's review | Query planning calls local Ollama only; suggestions stay drafts until the owner adds them, can edit them, and clicks the existing Start research action; no automatic search or provider calls |
| Unsafe agent action | Model suggests broad scan | Fixed typed tools, explicit grant, target validation, approval, budgets, cancellation |
| Replay or duplicate work | Retry repeats a check | Idempotency key, job hash, expiry, single-use approval |
| Resource exhaustion | Huge results consume unbounded tokens | Input/result/token/time quotas, step limits, cancellation |
| Source rights issue | Export republishes restricted post text | Source-specific display, storage, retention, deletion rules |
| Spreadsheet formula execution | Exported CSV cell begins with a formula marker | Quote CSV cells and prefix formula-leading values before download |
| Malicious or oversized SBOM | Crafted JSON, excess nesting/packages/references, or an unexpectedly large upload exhausts the API | Authenticated CycloneDX 1.3–1.7, SPDX 2.3, or SARIF 2.1.0 JSON only; 2 MB file cap; package/component/result/run, external-reference, CVE, vulnerability, and affected-reference caps; strict JSON parsing; local in-memory parsing; no archive extraction, external-reference fetching, or code execution |
| SBOM data disclosure | An SBOM or SARIF file contains private package names, source paths, or snippets | Do not persist or send SBOM/SARIF files to providers; SARIF messages and paths stay unread; only exact declared CVE IDs go to FIRST EPSS after explicit owner submission; match CISA KEV locally |
| OSV advisory disclosure | An exact CVE lookup sends an identifier to a public advisory provider | Send only one exact CVE after an explicit per-item click to the fixed HTTPS API; do not send package names, versions, inventories, or files; reject redirects; bound response size/time and cache in memory only; no retries |
| False SBOM vulnerability match | Similar package names or unsupported version semantics create a false affected result | For components already attached to a declared-CVE finding, require exact PURL identity; compare OSV affected-version strings by literal equality; assess ranges only with complete strict npm SemVer 2 evidence; treat unsupported, malformed, withdrawn, or truncated data as `NOT_ASSESSED`; label no-match as limited source evidence and retain the SBOM's transient local boundary |
| GitHub advisory disclosure | An exact-CVE lookup sends an identifier to GitHub's public advisory endpoint | Use the fixed public endpoint without account credentials; request only reviewed records for one exact CVE after a per-item action; do not send package names, inventories, or files; reject redirects; cap response size, fields, time, and cache; no retries |
| NVD query disclosure or key exposure | A CVE lookup or optional provider key is sent outside the machine | Make one exact-CVE request only after an authenticated per-item action; use the fixed HTTPS NVD endpoint; send the optional key only in a header; do not log the key; bound response time, response fields, and cache size; do not retry automatically |
| Document data exposure | Imported notes contain private or confidential text | Require local owner authentication; limit uploads to 1 MB; store only normalized text or extracted PDF text in the local database; do not send document text to public providers or models; support explicit deletion; keep host ports bound to loopback |
| Malicious document content | Uploaded Markdown contains active markup, or a malformed PDF targets the parser | Validate UTF-8 and extension, reject NUL and empty content, render previews as escaped text, never execute content or interpret it as HTML, and do not extract archives; PDFBox parsing is restricted to 100 pages and 250,000 extracted characters and serialized to reduce concurrent memory pressure |

## Current implementation scope

The Python service exposes a public health endpoint and bearer-token-protected report, claim-comparison, and query-plan routes. Model-backed actions are disabled unless a local Ollama model is configured. The authorized Java workflow sends at most 20 sources from one completed job for report/claim analysis, including only provider metadata and descriptions; uploaded documents and explicitly read page snapshots are excluded. Query planning sends only the topic and returns draft suggestions without searching. Python has no database, public search provider, tools, or arbitrary command execution. It validates structured output and rejects unknown source IDs, but citation membership does not establish that a source semantically supports the text. The UI does not persist Basic Auth credentials in browser storage. Search does not automatically fetch links; a separate explicit action reads only one selected saved source using the public-page limits above.

## Permission levels

L0 explain from existing evidence. L1 search approved public providers and fetch permitted pages. L2 read explicitly selected sources/documents, including one selected public page at a time. L3 analyze selected local data without network probes. L4 fixed safe checks against authorized resources with approval. L5 state-changing, exploitative, unrestricted access is out of scope.

## Security defaults

Fail closed on missing provider configuration. No model-generated shell, browser automation, credentials, or unrestricted scanner. No hosted fallback from local-only mode. Local MVP binds loopback and must not be exposed to the public internet. Search is partial and provider-dependent; it is not direct access to private accounts or every social post. Person-focused searches are for public-interest information only, not doxxing, sensitive-trait inference, or tracking private people. Bluesky text can remain in a saved local brief after remote removal, because AURA currently has no deletion-sync process. Remote deployment needs separate auth, TLS, privacy, retention, and abuse review.

## Residual risks

Public information may be false, incomplete, stale, or wrongly attributed. Search coverage is partial; selected providers receive submitted queries, GDELT covers only indexed news within its horizon, and Bluesky does not apply country filtering. FIRST receives submitted CVE identifiers under its terms and retention rules. CISA KEV and EPSS are useful prioritization signals, not proof that a user's software inventory is affected. AURA supports explicit deletion of a research brief and its source rows, but has no automatic retention schedule or social-post removal synchronization. Local Basic Auth is for a development milestone, not internet-facing identity. Update this model as connectors and parsers are added.
