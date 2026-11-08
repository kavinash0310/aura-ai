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
  API->>DB: Confirm brief exists and source belongs to it
  API->>DNS: Resolve hostname through bounded worker pool
  DNS-->>API: Public IPv4 addresses only
  API->>Page: One bounded unauthenticated HTTPS GET
  Page-->>API: At most 1 MB HTML/XHTML response
  API->>API: Strip active/embedded elements; extract capped text; hash it
  API-->>UI: Page metadata and text; no database write
  UI-->>Owner: Render escaped text; allow explicit .txt download
~~~

The API receives only the saved result URL; the browser cannot submit an arbitrary URL. Java verifies that the job exists and the source row belongs to that exact job before any outbound request. The DNS resolver checks the addresses used by the HTTP connection, so a hostname cannot pass one lookup and then resolve to a private IP for a connection. If DNS returns a mixture of public and non-public IPv4 addresses, the request is rejected. IPv6 destinations are intentionally unsupported in this slice.

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
| Retention | No server/database/model persistence; browser component state until view replacement/reload; explicit text download is user-controlled |

Page content is parsed as data using jsoup. AURA removes `script`, `style`, `noscript`, `template`, `svg`, `iframe`, `object`, and `embed` elements, then returns normalized text. React displays that string as escaped text. No DOM from the remote page is inserted into the application, and JavaScript is not executed. The SHA-256 field fingerprints the extracted text in this response; it is not a signature or a claim that the publisher is trustworthy.

## API and errors

`POST /api/v1/research-jobs/{jobId}/sources/{sourceId}/page` has no request body. Its JSON response contains `sourceId`, final URL and host, title, SHA-256 text digest, character count, truncation flag, fetch time, extracted `text`, and a fixed coverage note. Failures return stable Problem Details codes:

- `PUBLIC_PAGE_URL_BLOCKED` (`400`): URL or destination violates the public HTTPS policy.
- `PUBLIC_PAGE_TOO_LARGE` (`413`): raw response exceeds 1 MiB.
- `PUBLIC_PAGE_NOT_HTML` (`415`): response is not HTML or XHTML.
- `PUBLIC_PAGE_FETCH_BUSY` (`429`): another page read is active.
- `PUBLIC_PAGE_FETCH_FAILED` (`502`): timeout, DNS/network error, unsupported response, or unsuccessful upstream status.

The report route is separate: it continues to receive only selected saved provider metadata and snippets. Page text never enters the report request, even if the owner has read it in the UI.

## Code map

- `backend/.../PublicPageFetcher.java`: URL policy, DNS pinning/filtering, HTTP limits, redirect policy, HTML extraction, and concurrency cap.
- `backend/.../ResearchPageService.java`: job/source ownership check, fetch invocation, and returned-text digest.
- `backend/.../ResearchPageController.java`: authenticated endpoint. Global API security protects it like other non-health routes.
- `web/src/App.tsx`: explicit read action, temporary page display, and plain-text download.
- `web/src/api.ts` and `web/src/types.ts`: typed browser request/response.
- `PublicPageFetcherTest` and `ResearchPageServiceTest`: URL/IP/content limits and source ownership checks.

## Known limitations and next work

This does not search the internet by itself; it reads one result that a configured search provider already returned. Search is not complete or exhaustive. JavaScript-rendered sites, IPv6-only sites, authentication-required pages, PDFs linked from search, robots/terms automation policy evaluation, and multi-page crawling are not implemented. The owner is responsible for using pages they are permitted to access and respecting the destination site's applicable rules; AURA does not automatically interpret robots.txt or terms of service. The UI returns a timestamp and text hash, but AURA does not yet keep a durable audit event for each page read. The current single-owner local Basic Auth deployment is not production internet authentication. If AURA is later exposed remotely, add per-user authorization, rate quotas and abuse controls, durable audit records, privacy/retention policy, and a security review before allowing outbound page requests. A later page-reading feature should keep explicit allowlists, review provider terms, evaluate extracted text quality, and never turn model suggestions into automatic fetches.

## Learning notes

- SSRF defense is layered: do not accept arbitrary caller URLs, validate scheme/port/host, validate DNS results, and revalidate every redirect.
- DNS validation must be connected to the HTTP client's resolver. A validation lookup followed by a separate client lookup leaves a rebinding gap.
- A timeout, response-byte cap, extraction cap, and concurrency cap limit different resource risks; none replaces the others.
- HTML parsing extracts evidence; it does not make the content safe or true. Render only as escaped text and treat instructions inside pages as untrusted data.
- A content digest provides a stable fingerprint for this extracted snapshot, not cryptographic provenance from the remote publisher.
