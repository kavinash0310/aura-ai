# Explicit multi-provider public search

## What this adds

An owner can choose `All configured sources` for one bounded research request. AURA calls each enabled provider, then saves the combined result set while keeping the provider on every source. The existing Automatic option still selects one provider (Brave first, then GDELT). This is a small, explicit way to broaden source coverage; it is not a whole-internet crawl.

## Data flow

~~~mermaid
sequenceDiagram
  actor Owner
  participant UI as React workspace
  participant API as Spring API
  participant Service as ResearchSearchService
  participant Brave as Brave provider (if configured)
  participant GDELT as GDELT provider (if enabled)
  participant DB as PostgreSQL
  Owner->>UI: Choose All configured sources and start search
  UI->>API: POST job/{id}/search provider=all
  API->>Service: Authenticate, validate, and resolve enabled providers
  Service->>DB: Mark job SEARCHING with provider=all
  Service->>Brave: Send bounded query and filters
  Brave-->>Service: Normalized source batch
  Service->>GDELT: Send bounded query and filters
  GDELT-->>Service: Normalized source batch
  Service->>DB: Save all batches with per-source provider provenance
  Service-->>UI: Combined results and coverage warning
  Owner->>UI: Filter by provider, source type, text, host, or date
  UI->>API: GET sources / export with same filters
  API-->>UI: Filtered evidence / downloadable file
~~~

Provider calls are sequential, not concurrent. If any selected call fails, the job is marked `FAILED` and no source rows are saved. A provider called earlier may already have received the query. When all calls succeed, they are persisted together in one database transaction.

## Contract and limits

- Search endpoint: authenticated `POST /api/v1/research-jobs/{id}/search`.
- `provider` may be `brave`, `gdelt`, or `all`; omitted means Automatic.
- All mode means every currently configured provider. Brave is included only when `AURA_BRAVE_API_KEY` exists; GDELT is included when `AURA_GDELT_ENABLED=true`.
- Each provider gets the same query, country, language, freshness, and result-limit values. Current maximum is 80 result rows: up to 60 from Brave and 20 from GDELT.
- Duplicate URLs returned by separate providers stay as separate rows so both source origins remain visible.
- `GET /research-jobs/{id}/sources` and `/export` accept `provider` to filter by the source's exact provider name, case-insensitively.
- Downloads remain JSON, CSV, or Markdown and include the provider for every row.

Brave covers web, news, and discussion metadata. GDELT covers only indexed public news over at most three months. Neither adapter fetches result pages, and Brave discussion results are not direct X/Twitter or social-feed access.

## Code map

- `web/src/App.tsx`: provider choice, scope disclosure, source provider filter.
- `web/src/api.ts`, `web/src/types.ts`: typed request and filter contract.
- `backend/.../ResearchSearchService.java`: Automatic, explicit-provider, and all-configured resolution and execution.
- `backend/.../search/ProviderSearchResult.java`: associates each normalized batch with its provider.
- `backend/.../ResearchJobService.java`: transactional persistence with source provenance and global rank.
- `backend/.../ResearchResultService.java`: validated provider filter over saved sources.
- `backend/.../api/ResearchSearchController.java`: search, source listing, and filtered export routes.

No schema migration was required: research source rows already have a provider field, and job provider metadata already stores the selection (`all` for this mode).

## Safety, errors, and limitations

- Search starts only after the authenticated owner explicitly clicks Start research.
- All mode can send the same search phrase to more than one provider; the UI names the selection before sending it.
- Providers are fixed adapters with bounded parameters and HTTPS endpoints. AURA still never requests returned article URLs.
- No source means “everything on the internet.” Index coverage, freshness, provider terms, and quotas still apply.
- The all-or-nothing result rule keeps partial source sets from appearing complete, but it can discard successful batches when a later source fails. Future partial-success support needs persisted per-provider run status and visible failure coverage.
- Direct social connectors remain deferred until official API access and provider-specific retention/deletion requirements are designed; see [ADR 0008](../03-adr/0008-social-source-compliance-gate.md).

## Testing

`ResearchSearchApiTest` verifies combined-provider selection, per-source origin, provider-filtered listing and export, and all-or-nothing failure behavior. The React test confirms the UI sends `provider=all` and passes the selected provider through to source filtering. The full Java suite and UI suite should pass before publishing changes.

## Learning and interview prompts

- Why does Automatic select one provider while `all` explicitly sends the query to every enabled provider?
- Why are provider batches persisted only after every selected call succeeds?
- Why keep duplicate URLs when two adapters returned the same link?
- What would need to change before preserving partial success safely?
