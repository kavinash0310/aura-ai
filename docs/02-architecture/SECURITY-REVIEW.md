# Repository security review

**Review date:** 2026-11-11  
**Reviewed revision:** `2b18401` (the `main` branch after PR #22)  
**Review type:** scoped source and configuration review for the local, single-owner product boundary.

This review covers the Spring API, Python model service, React/Nginx UI, Compose topology, public-page fetcher, upload/parsing paths, provider egress, authentication/origin checks, backup and restore scripts, and the existing threat model. It is not a penetration test, a dependency vulnerability scan, or an assessment of a public deployment.

## Result

The static review did not confirm a high-severity exploitable defect in the reviewed local-use paths. The implementation has useful controls for its intended local mode: host ports bind to loopback; authenticated routes use HTTP Basic with a BCrypt password encoder; browser-origin checks supplement stateless authentication; public-page reads are explicit, host-validated, time- and size-bounded, and do not follow cross-host redirects; uploads have strict type and size limits; model calls are disabled by default and use a separate internal token; and backup encryption is opt-in and documented.

The main risk is deployment scope. AURA is a local, single-owner workspace, not a remotely hosted multi-user service. Keep the stack on a trusted machine and do not port-forward it.

## Risks to close before remote deployment

| Priority | Risk | Current boundary | Required before changing that boundary |
|---|---|---|---|
| Blocker | Basic Auth is carried over local HTTP; there is one configured owner and no row-level tenant isolation | Host ports bind to loopback; the local development guide says remote exposure is unsupported | Add TLS, a maintained identity/session model, CSRF protection appropriate to that model, per-user data ownership, and deployment review |
| High | PDF parsing runs inside the API JVM | Uploads are capped at 1 MB, 100 pages, and 250,000 extracted characters; parsing is serialized | Run parsing in a resource-limited worker process or disable untrusted PDF uploads on a remote service |
| Medium | Expensive authenticated operations have no general per-user rate limit | Single local owner; requests are explicit and provider work is bounded | Add per-identity limits and concurrency budgets before accepting untrusted network clients |
| Medium | Research content and PostgreSQL volumes are plaintext at the application layer | Local storage is owner-controlled; age encryption is available for backup archives; full-disk encryption is recommended | Define storage encryption, key custody, retention, and recovery for the actual deployment |
| Low | Health and Prometheus endpoints are unauthenticated | Only read-only endpoints are exposed; Compose keeps the management port off-host and limits the scrape network to API and Prometheus | Preserve equivalent network isolation or add endpoint authentication when moving to another topology |

## Dependency maintenance

The repository now defines weekly Dependabot version updates for GitHub Actions, Maven, uv, npm, Dockerfiles, and Docker Compose. These updates create reviewable pull requests; they do not replace enabling repository security alerts, reviewing advisories, or rebuilding and redeploying images after fixes. GitHub documents the supported ecosystem values and manifests in its [Dependabot ecosystem reference](https://docs.github.com/en/code-security/reference/supply-chain-security/supported-ecosystems-and-repositories).

## Review limits

- No external penetration test, fuzzing campaign, malicious-document exercise, production network assessment, or secret-rotation drill was performed.
- Repository CI verifies builds and tests but does not establish production security or availability.
- The Python model service accepts only the configured local Ollama targets, but a trusted local model endpoint can still return misleading or prompt-injected content. AURA validates schema and source membership, not factual support.
- Data deletion is logical. PostgreSQL volumes, backup files, and temporary restore/drill data need encrypted storage and owner-managed retention.

Revisit this review whenever the deployment becomes remotely reachable, gains another user, adds an account connector, or enables active security checks.
