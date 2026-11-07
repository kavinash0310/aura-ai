# Threat model

## Assets

Research queries and reports; selected documents and source excerpts; account tokens and grants; provenance and audit history; local database and model configuration.

## Trust boundaries

1. Browser to local UI/API: unauthenticated callers cannot access owner data; the UI sends Basic Auth only to the same-origin loopback API and keeps the entered credentials in component memory.
2. API to PostgreSQL: credentials are externalized; DB is not public.
3. API to search provider: the query and search options leave the local machine only after an authenticated, explicit search request; returned metadata is untrusted.
4. API to vulnerability data providers: the CISA KEV catalog is downloaded on an authenticated lookup, CVE identifiers are sent to FIRST EPSS, and exact CVE IDs are sent to NVD only after an explicit per-item or bounded batch request; data can be stale or incomplete.
5. API to Python/model provider: send minimized evidence; model output is untrusted.
6. Owner-selected text files to document library: text can contain private information or misleading instructions; only authenticated uploads are accepted and the content is not sent to external providers or models.
7. Future local agent to assets: only exact scoped jobs from authenticated server are accepted.

## Threats and controls

| Threat | Example | Control |
|---|---|---|
| Unauthenticated access | Network caller reads research history | Basic Auth on non-health routes; loopback binding; review before remote use |
| Prompt injection | A provider snippet contains instructions aimed at the model | Only selected snippets are sent after an explicit action; source content is marked untrusted; the model has no tools; outputs are schema-bounded and citations must match supplied IDs |
| SSRF | Crafted URL redirects fetcher to metadata endpoint | Validate scheme and resolved IP before/after redirects; block private/reserved destinations |
| Platform abuse | Crawler bypasses source controls | Permitted APIs/feeds/public access; respect terms/rate limits; no login/paywall/CAPTCHA bypass |
| Query disclosure | Search terms reveal interests to the provider | Explain that configured searches are sent to Brave; keep provider key in local `.env`; send no documents or account data; search only on explicit request |
| CVE lookup disclosure | Submitted CVE identifiers reach the EPSS service | Show the provider before submission; accept only exact CVE IDs and a small batch; use a fixed HTTPS host; make requests only after explicit authenticated submission. KEV matching is local against the downloaded catalog |
| Stale vulnerability signal | Cached or delayed data changes priority order | Return provider state, retrieval time, data date, source links, and an explicit stale status; never present EPSS as proof of exploitation or asset exposure |
| Browser credential exposure | A stored Basic Auth password is exposed to scripts or browser storage | Do not persist credentials in local/session storage or cookies; keep them in page memory, send only to same-origin `/api` routes, clear them on disconnect/reload, and keep the app loopback-only |
| Provider quota/cost | Repeated broad searches consume an account allowance | No background search or automatic retries; result limit is capped; provider timeout is bounded; provider billing is controlled by the owner's account |
| Private-person harm | Results aggregate home address or sensitive traits | Public-interest scope; exclude sensitive fields/profiling; identity disambiguation |
| Secret leakage | Token appears in prompt/report/log | Secret store, redaction, structured logging |
| Parser exploit | Malformed PDF exploits parser | Type/size caps, isolated process for risky parsers, archive path validation, no execution |
| Cross-owner leak | Search returns another user's chunk | Current database is a single local-owner workspace; add owner scoping before multi-user or remote deployment |
| Model misinformation | Unsupported claim sounds confident | The report displays citations and fixed coverage limits; ID membership is checked, but semantic support is not yet evaluated; owner reviews report before use |
| AI service impersonation | A local process calls the Python report endpoint | Separate Java-to-Python bearer token; constant-time token comparison; disabled provider fails closed |
| Model egress | Private text is sent to a remote model by configuration | Only loopback/private Ollama endpoints are accepted; no hosted fallback; report sends public provider snippets only, and excludes uploaded library documents |
| Unsafe agent action | Model suggests broad scan | Fixed typed tools, explicit grant, target validation, approval, budgets, cancellation |
| Replay or duplicate work | Retry repeats a check | Idempotency key, job hash, expiry, single-use approval |
| Resource exhaustion | Huge results consume unbounded tokens | Input/result/token/time quotas, step limits, cancellation |
| Source rights issue | Export republishes restricted post text | Source-specific display, storage, retention, deletion rules |
| Spreadsheet formula execution | Exported CSV cell begins with a formula marker | Quote CSV cells and prefix formula-leading values before download |
| Malicious or oversized SBOM | Crafted JSON, excess nesting/components, or an unexpectedly large upload exhausts the API | Authenticated CycloneDX JSON only; 2 MB file cap; component, vulnerability, CVE, and affected-reference caps; strict JSON parsing; local in-memory parsing; no archive extraction or code execution |
| SBOM data disclosure | An inventory file contains private package names or versions | Do not persist or send the SBOM file to providers; only its already-declared CVE IDs go to FIRST EPSS after explicit owner submission; match CISA KEV locally |
| NVD query disclosure or key exposure | A CVE lookup or optional provider key is sent outside the machine | Make one exact-CVE request only after an authenticated per-item action; use the fixed HTTPS NVD endpoint; send the optional key only in a header; do not log the key; bound response time, response fields, and cache size; do not retry automatically |
| Document data exposure | Imported notes contain private or confidential text | Require local owner authentication; limit uploads to 1 MB UTF-8 text/Markdown; store only in the local database; do not send document text to public providers or models; support explicit deletion; keep host ports bound to loopback |
| Malicious document content | Uploaded Markdown contains active markup or binary data disguised as text | Validate UTF-8 and extension, reject NUL and empty content, render previews as escaped text, never execute content or interpret it as HTML, and do not extract archives |

## Current implementation scope

The Python service exposes a public health endpoint and a bearer-token-protected report route. Report generation is disabled unless a local Ollama model is configured. The authorized Java workflow sends at most 20 sources from one completed job, including only provider metadata and descriptions; uploaded documents are excluded. Python has no database, search provider, tools, or arbitrary command execution. It validates structured output and rejects unknown source IDs, but citation membership does not establish that the source semantically supports the text. Search results remain untrusted and AURA does not fetch returned links. The UI does not persist Basic Auth credentials in browser storage.

## Permission levels

L0 explain from existing evidence. L1 search approved public providers and fetch permitted pages. L2 read explicitly selected sources/documents. L3 analyze selected local data without network probes. L4 fixed safe checks against authorized resources with approval. L5 state-changing, exploitative, unrestricted access is out of scope.

## Security defaults

Fail closed on missing provider configuration. No model-generated shell, browser automation, credentials, or unrestricted scanner. No hosted fallback from local-only mode. Local MVP binds loopback and must not be exposed to the public internet. Search is partial and provider-dependent; it is not direct access to private accounts or every social post. Person-focused searches are for public-interest information only, not doxxing, sensitive-trait inference, or tracking private people. Remote deployment needs separate auth, TLS, privacy, retention, and abuse review.

## Residual risks

Public information may be false, incomplete, stale, or wrongly attributed. Search coverage is partial, Brave receives submitted queries, and FIRST receives submitted CVE identifiers under their own terms and retention rules. CISA KEV and EPSS are useful prioritization signals, not proof that a user's software inventory is affected. AURA supports explicit deletion of a research brief and its source rows, but has no automatic retention schedule. Local Basic Auth is for a development milestone, not internet-facing identity. Update this model as connectors and parsers are added.
