# Explicit multi-provider public search

## What this adds

An owner can choose `All configured sources` for one bounded research request and add up to two query angles to the saved topic. AURA calls each enabled provider for each query, then saves the combined result set while keeping both provider and query on every source. The existing Automatic option still selects one provider (Brave first, then GDELT, then Bluesky if earlier providers are disabled). Calls are sequential and the UI discloses the maximum request count before the owner starts. This broadens indexed coverage; it is not a whole-internet crawl.

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
  UI->>API: POST job/{id}/search provider=all queries=[topic, angles]
  API->>Service: Authenticate, validate, and resolve enabled providers
  Service->>DB: Mark job SEARCHING; create provider/query run rows
  Service->>DB: Mark query 1 / Brave run RUNNING
  Service->>Brave: Send query 1 and filters
  Brave-->>Service: Results or stable provider error
  Service->>DB: Save query 1 results and SUCCEEDED outcome atomically
  Service->>DB: Mark query 1 / GDELT run RUNNING
  Service->>GDELT: Send query 1 and filters
  GDELT-->>Service: Results or stable provider error
  Service->>DB: Save GDELT results and SUCCEEDED outcome atomically
  Service->>DB: Mark query 1 / Bluesky run RUNNING
  Service->>Bluesky: Send query 1 and supported filters
  Bluesky-->>Service: Results or stable provider error
  Service->>DB: Save Bluesky results and SUCCEEDED outcome atomically
  Service->>Service: Repeat sequentially for remaining planned queries
  Service->>DB: Mark job COMPLETED, PARTIAL, or FAILED
  Service-->>UI: Results, provider outcomes, and coverage state
  Owner->>UI: Filter by provider, source type, text, host, or date
  UI->>API: GET sources / export with same filters
  API-->>UI: Filtered evidence / downloadable file
~~~

Provider calls are sequential, not concurrent. The owner may submit up to three explicit queries, with the saved job topic as the required first query. Before contacting providers, AURA records one `PENDING` run for each provider/query pair. It records `RUNNING` before each request and then either `SUCCEEDED` with a normalized result count or `FAILED` with a stable error code. AURA continues after an individual failure. Up to three queries across three configured providers means nine sequential requests and, at the maximum provider limit, up to 300 result rows. The provider quota or charge can apply to each request.

After each successful provider request, AURA commits its normalized results and success outcome in the same transaction. It marks the research job `COMPLETED` if every provider succeeded, `PARTIAL` if at least one succeeded and another failed or was skipped, or `FAILED` if no provider succeeded. A partial response is HTTP 200, carries only successfully returned source rows, and includes every provider outcome. A fully failed search retains the audit rows and returns the first provider's stable HTTP error. This preserves useful evidence without making an incomplete provider set look complete.

## Contract and limits

- Search endpoint: authenticated `POST /api/v1/research-jobs/{id}/search`.
- `provider` may be `brave`, `gdelt`, `bluesky`, or `all`; omitted means Automatic.
- All mode means every currently configured provider. Brave is included only when `AURA_BRAVE_API_KEY` exists; GDELT and Bluesky are included when their `AURA_GDELT_ENABLED` and `AURA_BLUESKY_ENABLED` flags are true.
- Each query gets the same result limit. Provider-specific filters vary: Brave and GDELT accept country/language filters; Bluesky accepts language and freshness but not country.
- Per query maximum is 100 result rows: up to 60 from Brave, 20 from GDELT, and 20 Bluesky posts. Across three queries the maximum is 300.
- Duplicate URLs returned by different providers or queries stay as separate rows so both origins remain visible.
- Every provider/query pair appears in `providerRuns` on search responses, saved-job detail/history, source listing, and exports. Each run reports query order/text, provider, `PENDING`/`RUNNING`/`SUCCEEDED`/`FAILED`/`SKIPPED`, result count, stable failure code when relevant, and start/completion timestamps. Source rows carry `searchQuery`.
- Jobs use `PARTIAL` when some providers succeeded and some failed or were skipped during recovery. Source listing, export, and report generation accept `COMPLETED` and `PARTIAL`. Search responses and exports include provider outcomes; generated reports include a missing-provider coverage note, while the full run statuses remain available in search history and source exports. When no provider succeeds, the job remains `FAILED` with its provider-run audit rows.
- `GET /research-jobs/{id}/sources` and `/export` accept `provider` to filter by the source's exact provider name, case-insensitively.
- Downloads remain JSON, CSV, or Markdown and include the provider for every row.

Brave covers web, news, and discussion metadata. GDELT covers only indexed public news over at most three months. Bluesky uses its official public AppView post-search API; it does not connect to accounts or private feeds. Search adapters never fetch result pages automatically; an owner may separately read one selected saved source using the bounded flow in [public-page reading](PUBLIC-PAGE-READING.md). X remains deferred under [ADR 0008](../03-adr/0008-social-source-compliance-gate.md).

## Code map

- `web/src/App.tsx`: provider choice, query plan editor and call disclosure, per-source query provenance.
- `web/src/api.ts`, `web/src/types.ts`: typed request and filter contract.
- `backend/.../ResearchSearchService.java`: Automatic, explicit-provider, and all-configured resolution and execution.
- `backend/.../ResearchJobService.java`: transactional provider result persistence, source provenance, global rank, provider-run lifecycle, and interrupted-search recovery.
- `backend/.../domain/ResearchProviderRun.java` and `ResearchProviderRunStatus.java`: durable provider outcome state machine.
- `backend/.../persistence/ResearchProviderRunRepository.java`: per-job reads and batched recent-history outcome lookup.
- `backend/src/main/resources/db/migration/common/V5__record_provider_search_runs.sql`, `V6__allow_skipped_provider_runs.sql`, and `V8__record_bounded_query_plans.sql`: provider/query-run table evolution, `PARTIAL` job state, recovery status, and source-query backfill.
- `backend/.../ResearchResultService.java`: validated provider filter over saved sources.
- `backend/.../api/ResearchSearchController.java`: search, source listing, and filtered export routes.

V5 adds durable provider-run rows and allows `PARTIAL` in the job status constraint. V8 adds per-query run identity and source provenance, backfilling the saved topic for existing rows.

See [PROVIDER-OUTCOME-AUDIT.md](PROVIDER-OUTCOME-AUDIT.md) for the transaction sequence, migration fields, restart recovery, retry limits, and learning walkthrough.
See [BOUNDED-QUERY-PLANS.md](BOUNDED-QUERY-PLANS.md) for request validation, per-query persistence, request-volume limits, and the user-reviewed planning boundary.

## Safety, errors, and limitations

- Search starts only after the authenticated owner explicitly clicks Start research.
- The visible query plan is limited to three user-entered strings. Each selected provider receives every planned query after the owner clicks Start research.
- All mode can send up to nine requests for a three-query plan across the three current providers; the UI discloses this before sending it.
- Providers are fixed adapters with bounded parameters and HTTPS endpoints. AURA still never requests returned article URLs.
- No source means “everything on the internet.” Index coverage, freshness, provider terms, and quotas still apply.
- Failed provider calls do not discard successful source batches. The UI identifies partial jobs and lists each provider's status, count, and failure code. A provider that was called before a later failure has already received the query.
- On startup, the single local backend marks interrupted in-flight providers failed and uncalled providers skipped. It keeps any already committed source rows and never retries the query automatically.
- Bluesky connector details and limitations are documented in [BLUESKY-SEARCH.md](BLUESKY-SEARCH.md). X and account-based connectors remain deferred; see [ADR 0008](../03-adr/0008-social-source-compliance-gate.md).

## Testing

`ResearchSearchApiTest` verifies combined-provider selection, per-source origin, provider-filtered listing/export, partial persistence after one provider fails, and retained audit state when all selected providers fail. The React test verifies provider selection, partial status, failure disclosure, and source filtering. The full Java suite and UI suite should pass before publishing changes.

## Learning and interview prompts

- Why does Automatic select one provider while `all` explicitly sends the query to every enabled provider?
- Why does AURA preserve successful provider batches when another provider fails?
- How do provider-run audit rows keep a `PARTIAL` result from appearing complete?
- Why keep duplicate URLs when two adapters returned the same link?
- What operational guarantees would be needed before retrying a failed provider automatically?
