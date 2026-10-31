# Threat model

## Assets

Research queries and reports; selected documents and source excerpts; account tokens and grants; provenance and audit history; local database and model configuration.

## Trust boundaries

1. Browser to API: unauthenticated callers cannot access owner data.
2. API to PostgreSQL: credentials are externalized; DB is not public.
3. API to providers: responses are untrusted; outbound access is allow-listed and bounded.
4. API to Python/model provider: send minimized evidence; model output is untrusted.
5. User files to parsers: files can be malformed or malicious.
6. Future local agent to assets: only exact scoped jobs from authenticated server are accepted.

## Threats and controls

| Threat | Example | Control |
|---|---|---|
| Unauthenticated access | Network caller reads research history | Basic Auth on non-health routes; loopback binding; review before remote use |
| Prompt injection | Page asks model to leak other evidence | No direct model tools; source labeled untrusted; deterministic policy |
| SSRF | Crafted URL redirects fetcher to metadata endpoint | Validate scheme and resolved IP before/after redirects; block private/reserved destinations |
| Platform abuse | Crawler bypasses source controls | Permitted APIs/feeds/public access; respect terms/rate limits; no login/paywall/CAPTCHA bypass |
| Private-person harm | Results aggregate home address or sensitive traits | Public-interest scope; exclude sensitive fields/profiling; identity disambiguation |
| Secret leakage | Token appears in prompt/report/log | Secret store, redaction, structured logging |
| Parser exploit | Malformed PDF exploits parser | Type/size caps, isolated process for risky parsers, archive path validation, no execution |
| Cross-owner leak | Search returns another user's chunk | Owner scope before retrieval; future row-level security as defense in depth |
| Model misinformation | Unsupported claim sounds confident | Citations, uncertainty, entailment evaluation, human review |
| Unsafe agent action | Model suggests broad scan | Fixed typed tools, explicit grant, target validation, approval, budgets, cancellation |
| Replay or duplicate work | Retry repeats a check | Idempotency key, job hash, expiry, single-use approval |
| Resource exhaustion | Huge results consume unbounded tokens | Input/result/token/time quotas, step limits, cancellation |
| Source rights issue | Export republishes restricted post text | Source-specific display, storage, retention, deletion rules |

## Permission levels

L0 explain from existing evidence. L1 search approved public providers and fetch permitted pages. L2 read explicitly selected sources/documents. L3 analyze selected local data without network probes. L4 fixed safe checks against authorized resources with approval. L5 state-changing, exploitative, unrestricted access is out of scope.

## Security defaults

Fail closed on missing scope/configuration. No model-generated shell, browser automation, credentials, or unrestricted scanner. No hosted fallback from local-only mode. Local MVP binds loopback and must not be exposed to the public internet. Remote deployment needs separate auth, TLS, privacy, and abuse review.

## Residual risks

Public information may be false, incomplete, stale, or wrongly attributed. Search coverage is partial. Local Basic Auth is for a development milestone, not internet-facing identity. Update this model as connectors and parsers are added.
