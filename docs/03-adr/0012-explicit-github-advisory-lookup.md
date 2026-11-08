# ADR 0012: Explicit GitHub Advisory Database lookup

- Status: Accepted
- Date: 2026-11-08

## Context

AURA's defensive vulnerability workflow already supports CISA KEV, FIRST EPSS, NVD, and OSV. GitHub publishes a global reviewed-advisory REST endpoint that can filter by an exact CVE and serve public resources without authentication. AURA needs additional vendor-advisory coverage while retaining its local-first, explicit-egress policy.

Official reference: [GitHub REST API endpoints for global security advisories](https://docs.github.com/en/rest/security-advisories/global-advisories).

## Problem

The owner needs a way to inspect GitHub-reviewed package advisory evidence for a CVE without sending a complete SBOM, creating a GitHub account connector, or treating third-party package ranges as an asset match.

## Options considered

- Keep OSV only; it is broad and covers many ecosystems, but does not expose GitHub's reviewed source record directly in AURA.
- Add a direct unauthenticated GitHub global-advisory query by exact CVE.
- Add a GitHub account connector for repository-specific advisories; this requires credentials and repository permissions not needed for public CVE research.
- Download and cache the entire advisory database; this increases storage, freshness, deletion, and processing obligations.

## Decision

Add one authenticated, explicit per-CVE action. The API sends only the normalized CVE ID to the fixed public `GET /advisories` endpoint, asks for `type=reviewed`, and accepts records only when GitHub's CVE field or typed identifier exactly repeats the requested ID. It does not use GitHub credentials, query package names, fetch references, or persist returned records. Bound page size, response bytes, parsed fields, timeout, redirects, and memory cache. Do not automatically match affected package ranges to an uploaded SBOM.

## Advantages and disadvantages

- Adds source diversity and access to GHSA identifiers, reviewed descriptions, package ranges, CVSS v3/v4, CWEs, and references.
- Uses a public endpoint without account setup or token handling.
- A point lookup keeps data transfer and egress narrow and attributable to a user action.
- It duplicates some OSV/NVD content; source records can disagree or lag.
- Public unauthenticated rate limits can cause unavailable results.
- A returned range does not prove the owner's software is affected; advisory data is not an asset inventory.

## Consequences

The local UI exposes an explicit lookup on individual CVE findings and includes returned records in user-requested JSON/CSV exports. Responses remain in browser memory unless exported. The server retains a small six-hour memory cache. Empty results mean no matching reviewed record was returned by this endpoint at retrieval time, not that a vulnerability has no advisory. The adapter is a public-source evidence provider, not an account connector.

## Conditions to reconsider

Revisit if GitHub changes the `cve_id` or reviewed type filters, API versioning, public access, rate limits, response fields, or data terms. A repository-specific feature requires a separate decision and explicit owner-granted permissions. Full database synchronization requires an evidenced need plus a retention, update, and deletion design.

## Learning and interview prompts

- How does the exact-CVE check prevent a search response from becoming a false match?
- Why does the API proxy the lookup instead of calling GitHub directly from the browser?
- How do timeout, redirect, body-size, result-count, and cache limits work together?
- Why are CVSS and vulnerable-version ranges evidence, not an asset-specific risk score?
- When would a repository-scoped GitHub connector be justified, and what new permissions would it require?
