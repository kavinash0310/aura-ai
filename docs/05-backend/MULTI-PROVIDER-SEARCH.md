# Explicit multi-provider public search

## What this adds

An owner can choose `All configured sources` for one bounded research request. AURA calls each enabled provider, then saves the combined result set while keeping the provider on every source. The existing Automatic option still selects one provider (Brave first, then GDELT, then Bluesky if earlier providers are disabled). This is a small, explicit way to broaden source coverage; it is not a whole-internet crawl.

## Data flow

~~~mermaid
sequenceDiagram
  actor Owner
  participant UI as React workspace
  participant API as Spring API
  participant Service as ResearchSearchService
  participant Brave as Brave provider (if configured)
  participant GDELT as GDELT provider (if enabled)
  participant Bluesky as Bluesky public API (if enabled)
  participant DB as PostgreSQL
  Owner->>UI: Choose All configured sources and start search
  UI->>API: POST job/{id}/search provider=all
  API->>Service: Authenticate, validate, and resolve enabled providers
  Service->>DB: Mark job SEARCHING; create provider-run rows
  Service->>DB: Mark Brave run RUNNING
  Service->>Brave: Send bounded query and filters
  Brave-->>Service: Results or stable provider error
  Service->>DB: Save Brave outcome and count/error code
  Service->>DB: Mark GDELT run RUNNING
  Service->>GDELT: Send bounded query and filters
  GDELT-->>Service: Results or stable provider error
  Service->>DB: Save GDELT outcome and count/error code
  Service->>DB: Mark Bluesky run RUNNING
  Service->>Bluesky: Send topic query and supported filters
  Bluesky-->>Service: Results or stable provider error
  Service->>DB: Save Bluesky outcome and count/error code
  Service->>DB: Save successful batches with provenance
  Service-->>UI: Results, provider outcomes, and coverage state
  Owner->>UI: Filter by provider, source type, text, host, or date
  UI->>API: GET sources / export with same filters
  API-->>UI: Filtered evidence / downloadable file
~~~

Provider calls are sequential, not concurrent. Before contacting providers, AURA records one `PENDING` run for each selected provider. It records `RUNNING` before the request and then either `SUCCEEDED` with a normalized result count or `FAILED` with a stable error code. AURA continues to later configured providers after an individual failure.

After all provider requests finish, AURA saves all successful batches together and marks the research job `COMPLETED` if every provider succeeded, `PARTIAL` if at least one succeeded and at least one failed, or `FAILED` if every provider failed. A partial response is HTTP 200, carries only successfully returned source rows, and includes every provider outcome. A fully failed search retains the audit rows and returns the first provider's stable HTTP error. This preserves useful evidence without making an incomplete provider set look complete.

## Contract and limits

- Search endpoint: authenticated `POST /api/v1/research-jobs/{id}/search`.
- `provider` may be `brave`, `gdelt`, `bluesky`, or `all`; omitted means Automatic.
- All mode means every currently configured provider. Brave is included only when `AURA_BRAVE_API_KEY` exists; GDELT and Bluesky are included when their `AURA_GDELT_ENABLED` and `AURA_BLUESKY_ENABLED` flags are true.
- Each provider gets the same query and result limit. Provider-specific filters vary: Brave and GDELT accept country/language filters; Bluesky accepts language and freshness but not country.
- Current maximum is 100 result rows: up to 60 from Brave, 20 from GDELT, and 20 Bluesky posts.
- Duplicate URLs returned by separate providers stay as separate rows so both source origins remain visible.
- Every selected provider appears in `providerRuns` on search responses, saved-job detail/history, source listing, and exports. Each run reports provider, `PENDING`/`RUNNING`/`SUCCEEDED`/`FAILED`, result count, stable failure code if failed, and start/completion timestamps.
- Jobs use `PARTIAL` when some providers succeeded and some failed. Source listing, export, and report generation accept `COMPLETED` and `PARTIAL`. Search responses and exports include provider outcomes; generated reports include a missing-provider coverage note, while the full run statuses remain available in search history and source exports. When all providers fail, the job remains `FAILED` with its provider-run audit rows.
- `GET /research-jobs/{id}/sources` and `/export` accept `provider` to filter by the source's exact provider name, case-insensitively.
- Downloads remain JSON, CSV, or Markdown and include the provider for every row.

Brave covers web, news, and discussion metadata. GDELT covers only indexed public news over at most three months. Bluesky uses its official public AppView post-search API; it does not connect to accounts or private feeds. No adapter fetches result pages. X remains deferred under [ADR 0008](../03-adr/0008-social-source-compliance-gate.md).

## Code map

- `web/src/App.tsx`: provider choice, scope disclosure, source provider filter.
- `web/src/api.ts`, `web/src/types.ts`: typed request and filter contract.
- `backend/.../ResearchSearchService.java`: Automatic, explicit-provider, and all-configured resolution and execution.
- `backend/.../search/ProviderSearchResult.java`: associates each normalized batch with its provider.
- `backend/.../ResearchJobService.java`: transactional persistence with source provenance, global rank, and provider-run lifecycle.
- `backend/.../domain/ResearchProviderRun.java` and `ResearchProviderRunStatus.java`: durable provider outcome state machine.
- `backend/.../persistence/ResearchProviderRunRepository.java`: per-job reads and batched recent-history outcome lookup.
- `backend/src/main/resources/db/migration/common/V5__record_provider_search_runs.sql`: provider-run table and `PARTIAL` job state constraint.
- `backend/.../ResearchResultService.java`: validated provider filter over saved sources.
- `backend/.../api/ResearchSearchController.java`: search, source listing, and filtered export routes.

V5 adds durable provider-run rows and allows `PARTIAL` in the job status constraint. It does not change the existing research-source schema.

See [PROVIDER-OUTCOME-AUDIT.md](PROVIDER-OUTCOME-AUDIT.md) for the transaction sequence, migration fields, crash/retry limitations, and learning walkthrough.

## Safety, errors, and limitations

- Search starts only after the authenticated owner explicitly clicks Start research.
- All mode can send the same search phrase to more than one provider; the UI names the selection before sending it.
- Providers are fixed adapters with bounded parameters and HTTPS endpoints. AURA still never requests returned article URLs.
- No source means “everything on the internet.” Index coverage, freshness, provider terms, and quotas still apply.
- Failed provider calls do not discard successful source batches. The UI identifies partial jobs and lists each provider's status, count, and failure code. A provider that was called before a later failure has already received the query.
- Bluesky connector details and limitations are documented in [BLUESKY-SEARCH.md](BLUESKY-SEARCH.md). X and account-based connectors remain deferred; see [ADR 0008](../03-adr/0008-social-source-compliance-gate.md).

## Testing

`ResearchSearchApiTest` verifies combined-provider selection, per-source origin, provider-filtered listing/export, partial persistence after one provider fails, and retained audit state when all selected providers fail. The React test verifies provider selection, partial status, failure disclosure, and source filtering. The full Java suite and UI suite should pass before publishing changes.

## Learning and interview prompts

- Why does Automatic select one provider while `all` explicitly sends the query to every enabled provider?
- Why does AURA preserve successful provider batches when another provider fails?
- How do provider-run audit rows keep a `PARTIAL` result from appearing complete?
- Why keep duplicate URLs when two adapters returned the same link?
- What operational guarantees would be needed before retrying a failed provider automatically?
