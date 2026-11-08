# Explicit public-page reading

## Purpose and product boundary

Search providers return titles, links, and short descriptions. AURA can now read one linked page when the owner presses **Read page**. The action is attached to a result already stored in the selected brief. It is not a whole-web crawler, a background job, or a social-account connector. Search coverage remains limited to the configured indexes and public APIs.

The first slice reads static public HTML. It does not run page JavaScript, sign in, bypass a paywall/CAPTCHA, retrieve private feeds, or probe a host. Page content is untrusted evidence. Person research remains limited to public-interest information; AURA must not become a tool for doxxing, sensitive-trait inference, harassment, or invasive monitoring.

## Request and trust flow

~~~mermaid
sequenceDiagram
  actor Owner
  participant UI as React workspace
  participant API as Authenticated Spring API
  participant DB as PostgreSQL
  participant DNS as Bounded public DNS resolver
  participant Page as Selected public HTTPS host
  Owner->>UI: Click Read page on one saved result
  UI->>API: POST /research-jobs/{jobId}/sources/{sourceId}/page
  API->>DB: Confirm a completed brief and matching source
  API->>DB: Save a RUNNING metadata-only audit row
  API->>DNS: Resolve hostname through bounded worker pool
  DNS-->>API: Public IPv4 addresses only
  API->>Page: One bounded unauthenticated HTTPS GET
  Page-->>API: At most 1 MB HTML/XHTML response
  API->>API: Strip active/embedded elements; extract capped text; hash it
  API->>DB: Save outcome, time, hash, character count, truncation flag
  API-->>UI: Page metadata and text; text is not persisted
  UI-->>Owner: Render escaped text; allow explicit .txt download
~~~

The API receives only job and source IDs; the browser cannot submit an arbitrary URL. Java verifies that the brief is completed or partial and the source belongs to that exact brief, then saves a `RUNNING` audit row before any outbound request. Each attempt is later recorded as `SUCCEEDED`, `FAILED`, or (after a restart) `ABANDONED`. The DNS resolver checks the addresses used by the HTTP connection, so a hostname cannot pass one lookup and then resolve to a private IP for a connection. If DNS returns a mixture of public and non-public IPv4 addresses, the request is rejected. IPv6 destinations are intentionally unsupported in this slice.

## Limits and defaults

| Control | Current limit |
|---|---|
| Trigger | Explicit authenticated POST for one saved source |
| Scheme and port | HTTPS, standard port 443 |
| Host | Multi-label public DNS hostname; no literal IPs or local-use suffixes |
| DNS | Maximum 3 seconds; 2 daemon workers and queue of 2; only public IPv4 answers accepted |
| Redirects | Automatic handling off; at most 2 manual redirects, same hostname only, each revalidated |
| Concurrent reads | 1 per backend instance |
| Network | 3-second connect/pool wait; 5-second response/socket timeouts |
| Whole operation | Hard 20-second deadline across the initial request and redirects; each in-flight request is canceled at the deadline |
| Response | HTML/XHTML only; identity encoding; at most 1 MiB raw bytes |
| Extraction | Static body text only; at most 100,000 characters; title at most 300 characters |
| HTTP state | No cookies, credentials/auth cache, content compression, or automatic retries |
| Retention | Page text is never saved by the server or sent to a model; metadata-only audit rows persist until their source/job is deleted; browser text state lasts until view replacement/reload; explicit text download is user-controlled |

Page content is parsed as data using jsoup. AURA removes `script`, `style`, `noscript`, `template`, `svg`, `iframe`, `object`, and `embed` elements, then returns normalized text. React displays that string as escaped text. No DOM from the remote page is inserted into the application, and JavaScript is not executed. The SHA-256 field fingerprints the extracted text in this response; it is not a signature or a claim that the publisher is trustworthy.

## Local evidence filtering

After reading a page, the owner can search its extracted text locally for an exact, case-sensitive phrase up to 200 characters. AURA shows up to 200 matches with each phrase's zero-based character offset and up to 120 characters of context on either side. If more matches exist, the UI marks the list as capped. “Download matching excerpts” creates a plain-text file with the source URL, fetch time, full-text hash, query phrase, excerpt text, and offsets. This is browser-side processing; the phrase and text are not sent to the API or model.

## API and errors

`POST /api/v1/research-jobs/{jobId}/sources/{sourceId}/page` has no request body. It records a metadata-only audit entry before fetching and updates its outcome afterward. Its JSON response contains `readId`, `sourceId`, final URL and host, title, SHA-256 text digest, character count, truncation flag, fetch time, extracted `text`, and a fixed coverage note. Failures return stable Problem Details codes:

- `PUBLIC_PAGE_URL_BLOCKED` (`400`): URL or destination violates the public HTTPS policy.
- `PUBLIC_PAGE_TOO_LARGE` (`413`): raw response exceeds 1 MiB.
- `PUBLIC_PAGE_NOT_HTML` (`415`): response is not HTML or XHTML.
- `PUBLIC_PAGE_FETCH_BUSY` (`429`): another page read is active.
- `PUBLIC_PAGE_FETCH_FAILED` (`502`): timeout, DNS/network error, unsupported response, or unsuccessful upstream status.

`GET /api/v1/research-jobs/{jobId}/sources/{sourceId}/page-reads?limit=10` returns up to 100 recent audit rows for that saved source. Each row contains the read ID, status, timestamps, optional text hash/count/truncation flag, and stable failure code. It never includes the fetched text or page URL. Startup recovery marks any leftover `RUNNING` row `ABANDONED` with `PUBLIC_PAGE_READ_INTERRUPTED`; this assumes one backend instance.

The report route is separate: it continues to receive only selected saved provider metadata and snippets. Page text never enters the report request, even if the owner has read it in the UI.

## Code map

- `backend/.../PublicPageFetcher.java`: URL policy, DNS pinning/filtering, HTTP limits, redirect policy, HTML extraction, and concurrency cap.
- `backend/.../ResearchPageService.java`: job/source ownership check, audit lifecycle, fetch invocation, and returned-text digest.
- `backend/.../ResearchPageReadAuditService.java` and `ResearchPageReadRecovery.java`: metadata-only audit listing and restart recovery.
- `backend/src/main/resources/db/migration/common/V7__audit_public_page_reads.sql`: durable audit schema with job/source deletion cascades.
- `backend/.../ResearchPageController.java`: authenticated endpoint. Global API security protects it like other non-health routes.
- `web/src/App.tsx` and `web/src/researchPageSearch.ts`: explicit read action, local exact-phrase filtering, excerpt offsets/context, temporary page display, and plain-text downloads.
- `web/src/api.ts` and `web/src/types.ts`: typed browser request/response.
- `PublicPageFetcherTest` and `ResearchPageServiceTest`: URL/IP/content limits and source ownership checks.

## Known limitations and next work

This does not search the internet by itself; it reads one result that a configured search provider already returned. Search is not complete or exhaustive. JavaScript-rendered sites, IPv6-only sites, authentication-required pages, PDFs linked from search, robots/terms automation policy evaluation, and multi-page crawling are not implemented. The owner is responsible for using pages they are permitted to access and respecting the destination site's applicable rules; AURA does not automatically interpret robots.txt or terms of service. AURA persists a metadata-only audit row for each attempt: source ID, request/completion times, outcome, extracted-text hash/count/truncation, and a stable failure code. The fetched text and requested page URL are not duplicated into the audit table; the source URL remains in the existing source record. Rows are deleted when the source or brief is deleted. Startup recovery assumes one backend instance. The current single-owner local Basic Auth deployment is not production internet authentication. If AURA is later exposed remotely, add per-user authorization, rate quotas and abuse controls, a reviewed privacy/retention policy, and a security review before allowing outbound page requests. A later page-reading feature should keep explicit allowlists, review provider terms, evaluate extracted text quality, and never turn model suggestions into automatic fetches.

## Learning notes

- SSRF defense is layered: do not accept arbitrary caller URLs, validate scheme/port/host, validate DNS results, and revalidate every redirect.
- DNS validation must be connected to the HTTP client's resolver. A validation lookup followed by a separate client lookup leaves a rebinding gap.
- A timeout, response-byte cap, extraction cap, and concurrency cap limit different resource risks; none replaces the others.
- HTML parsing extracts evidence; it does not make the content safe or true. Render only as escaped text and treat instructions inside pages as untrusted data.
- A content digest provides a stable fingerprint for this extracted snapshot, not cryptographic provenance from the remote publisher.
