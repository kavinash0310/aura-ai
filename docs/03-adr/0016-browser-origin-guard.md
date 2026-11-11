# ADR 0016: Browser-origin guard for the local API

- Status: Accepted
- Date: 2026-11-11

## Context

The local API uses stateless HTTP Basic authentication and has Spring CSRF token checks disabled. A browser can cache Basic credentials, so a hostile website may attempt to submit requests to a local API even when it cannot read the response. AURA binds host ports to loopback, but local-only deployment does not remove this browser threat.

## Decision

Add a Spring Security filter that rejects a request when its `Origin` header is present and is not an exact member of `AURA_ALLOWED_BROWSER_ORIGINS`, or when Fetch Metadata identifies it as `cross-site`. The default allowlist contains the two local UI origins `http://127.0.0.1:3000` and `http://localhost:3000`. The API does not enable CORS. Requests without browser-origin metadata remain available to authenticated command-line clients.

## Consequences

- Browser-originated calls from the configured local UI continue to work without changing the existing API client.
- The browser guard runs before Basic authentication and rejects disallowed requests with HTTP 403.
- Local deployments on a different UI origin must configure that exact origin; wildcard origins are not supported.
- This is a defense-in-depth check. It does not stop same-origin script injection, replace a CSRF token for a remotely hosted browser application, or cover legacy browsers that omit both headers.
- The app remains loopback-only and Basic Auth remains development-only.

## Reconsider when

If the API is ever exposed beyond the local machine or called by a browser hosted on another site, replace this local guard with a reviewed authentication and CSRF design, TLS, per-user authorization, rate limits, and explicit CORS rules if required.

## Learning prompts

- Why can HTTP Basic credentials make a local browser app vulnerable to cross-site request forgery?
- Why does an exact origin allowlist not enable CORS?
- Which clients can omit `Origin` and Fetch Metadata, and what trade-off does that create?
