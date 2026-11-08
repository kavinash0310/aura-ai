# ADR 0011: Explicit, bounded public-page reading

- Status: Accepted
- Date: 2026-11-08

## Context

Search providers return candidate links, but snippets can omit context needed for careful research. Automatically fetching every result would create a crawler, enlarge the server-side request-forgery and abuse surface, increase outbound traffic, and blur the user's intent. AURA is a local, single-owner defensive research workspace; the owner should choose which page to inspect.

## Decision

Add one authenticated, explicit page-read endpoint for a source already saved in the specified research brief. The browser sends a POST with job and source IDs, not a URL. The API checks source ownership, then performs one unauthenticated public HTTPS read using a custom DNS resolver that rejects non-public IPv4 addresses. Require port 443, constrain redirects to two same-host hops, accept only HTML/XHTML, and cap concurrency, timeouts, raw bytes, extracted characters, and title length. Disable cookies, credentials, compression, automatic redirects, and retries. Parse static content to text, remove active/embedded content, and return it only to browser memory. Do not persist it or send it to the report model.

No background crawling, account logins, private feeds, paywall/CAPTCHA bypass, arbitrary network probes, JavaScript execution, or automatic page reads are included. The existing public-interest restrictions for person research still apply.

## Consequences

- The selected page provides more evidence than a provider snippet while keeping each outbound request user-triggered and attributable to one stored source.
- The page remains untrusted and can be incomplete, inaccurate, manipulative, or prompt-injection shaped. It is shown as text, not executed or sent to the model.
- Coverage remains partial. The response includes a fetch time and digest, but there is no durable page-read audit event yet. Public hostname checks and IPv4-only requests reduce SSRF risk but do not make this route suitable for an internet-facing multi-user deployment without additional authorization, quotas, durable audit, and security review.
- Static HTML extraction is broadly useful but misses JavaScript-rendered content and may include navigation or boilerplate.

## Alternatives considered

- **Fetch every link during search:** rejected because it causes implicit egress, resource use, broad SSRF exposure, and unrequested page access.
- **Use browser-side direct fetch:** rejected for this slice because browser CORS and credential behavior vary, and content is harder to validate and bound consistently.
- **Use a headless browser:** rejected due to larger attack surface, resource demands, and page-code execution.
- **No page reading:** safe but leaves an important research gap; the selected one-page interaction offers a bounded next step.

## Verification

Backend tests check URL allow/deny behavior, representative special IP ranges, content extraction/capping, and brief/source ownership. Frontend tests verify that reading happens only after the user clicks and that the result remains client-side. CI must also compile and run the Java test suite before merge.
