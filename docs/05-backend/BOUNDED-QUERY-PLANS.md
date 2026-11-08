# Bounded, user-reviewed query plans

## Purpose

One search phrase can miss useful public sources. The owner can add up to two focused angles to the saved topic, preview the complete plan, and explicitly start one search. AURA sends no query until the owner clicks **Start research**.

The current version does not ask a model to generate follow-up queries, monitor people, crawl linked pages, or access private accounts. It uses only the selected configured public search providers. Indexed search coverage is partial and cannot establish that all relevant information was found.

## Request and limits

`POST /api/v1/research-jobs/{id}/search` accepts an optional ordered `queries` array. It may contain at most three strings, each at most 300 characters. When present, the first query must match the saved job topic after whitespace trimming and case-insensitive comparison. The server trims entries and removes case-insensitive duplicates. When omitted or empty, it uses only the saved topic.

The plan applies the same country, language, freshness, and result limit to each query. Calls execute sequentially in query order and configured provider order. With three current providers and three queries, AURA can make up to nine search requests. The provider result limit applies to each query, so the documented combined maximum is 300 source rows across three queries in all-provider mode. Provider quotas or charges may apply. Duplicate links across queries remain separate to preserve their origin and audit trail.

## Data and provenance

V8 adds `query_order` and `query_text` to `research_provider_runs`. A run is unique per job, provider, and query order; `run_order` still records the overall sequential execution order. `research_sources.search_query` records the query used for each source. V8 backfills the old job topic for existing run and source rows before requiring query text.

Search responses, saved-job detail, filtered source responses, JSON/CSV/Markdown exports, and the result cards expose this query provenance. The source uniqueness key includes query text, so one public link may appear once for each distinct query/provider origin. Query text and provider outcomes remain in the saved brief until the owner deletes that brief. The exact query is sent to each selected third-party public search provider; use only query terms appropriate to share with that provider.

Each successful provider/query batch and its `SUCCEEDED` audit row commit together. Failed attempts keep a stable error code. Interruption recovery marks an active run failed and runs not yet called skipped; it does not retry. A job is `PARTIAL` if any run succeeds and another fails or is skipped.

## Trust and safety boundary

- The owner supplies every query angle; none is silently inferred or submitted by an autonomous planner.
- At most three query strings are accepted by the API, independent of UI limits.
- Each query is sent only to the providers explicitly selected or the existing Automatic choice.
- All configured sources executes each angle against every enabled provider, with the call count and per-query result cap disclosed in the UI.
- Public search adapters use their fixed provider endpoints and bounded result limits. Results are untrusted metadata, not verified facts.
- AURA does not search private social feeds, connect to a social account, infer sensitive traits, or fetch result URLs during search.
- Saved query strings can contain sensitive information; they are local job data and are deleted with the job.

## Learning checks

- Why is the root query required as the first item?
- How do run order and query order preserve sequential audit history?
- What changes in request volume when one query becomes three and one provider becomes all providers?
- Why are results from duplicate URLs retained when they came from different queries or providers?
- What additional retention and consent rules would an account connector need?

## Code map

- `web/src/App.tsx`: visible search-angle inputs, preview, call-count disclosure, and provenance display.
- `web/src/api.ts` and `web/src/types.ts`: request and response contracts.
- `backend/src/main/java/ai/aura/api/application/ResearchSearchService.java`: plan normalization and bounded sequential execution.
- `backend/src/main/java/ai/aura/api/application/ResearchJobService.java`: per-provider/query audit and source writes.
- `backend/src/main/resources/db/migration/common/V8__record_bounded_query_plans.sql`: backfill and schema change.
- `docs/02-architecture/API-CONTRACTS.md` and `DATA-MODEL.md`: published contract and persistence model.
