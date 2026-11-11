# Provider outcome audit and partial research results

## What this adds

When AURA searches one provider or several, it records each provider's lifecycle and outcome. A failure from one provider no longer erases results already returned by another provider. The saved brief shows which sources succeeded, which provider failed or was skipped during recovery, and its stable error code.

This change is most visible in `All configured sources` mode:

- All selected providers succeed: job status is `COMPLETED`.
- At least one succeeds and at least one fails or is skipped after interruption: status is `PARTIAL`; successful source batches are still available.
- Every selected provider fails: status is `FAILED`; no source batch is saved, but all provider run outcomes remain available in job history and the API returns the first failure's stable error response.

The provider calls remain sequential and explicit. A provider called before a later provider fails may already have received the user's query.

## Data model

Migration V5 adds `research_provider_runs`; V8 adds per-query provenance and V10 adds retained manual retry attempts. Each run stores:

- `research_job_id`, provider name, query order, and exact query text, unique per job/provider/query/attempt.
- `run_order`, preserving the overall order of sequential requests; calls run query-first, then provider.
- `attempt`, starting at 1 and capped at 3 total attempts for one provider/query pair.
- `status`: `PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, or `SKIPPED` when a provider was never called because an earlier request was interrupted.
- `result_count`, initialized to zero and set on success.
- `failure_code`, set only for a failed run using stable application codes (no raw provider response or credentials).
- `started_at` and `completed_at` timestamps.

The existing job row adds one enum value, `PARTIAL`, to its status constraint. V8 adds a non-null `search_query` to each source and backfills existing sources from their job topic. Deleting a job cascades to provider run rows and source rows.

## Request lifecycle and transaction boundaries

1. Validate the requested provider selection and resolve enabled providers before starting the job. If none are available, return `503 SEARCH_PROVIDER_NOT_CONFIGURED` and leave the job `REQUESTED`.
2. In one short transaction, lock the job, change it to `SEARCHING`, store the search filters, and persist one `PENDING` provider/query run for every selected combination. A request can include at most three explicit queries and the first must match the saved topic.
3. For each query and then provider in order, commit `RUNNING` and `started_at`, then perform the bounded fixed-host HTTP request outside a database transaction.
4. On a valid response, one short transaction saves that provider's normalized source rows with provenance/rank and commits `SUCCEEDED`, result count, and `completed_at` together. On an expected provider exception, commit `FAILED`, stable failure code, and `completed_at`. An unexpected adapter runtime exception is recorded as `SEARCH_PROVIDER_FAILURE`. Continue to the next selected provider.
5. In a final transaction, set the job to `COMPLETED`, `PARTIAL`, or `FAILED` based on the durable provider outcomes. For all-failed searches, the run rows and terminal job state commit before the controller returns the error response.

Provider network I/O is not held inside a database transaction. On application startup, a recovery runner reconciles any job left in `SEARCHING`: a `RUNNING` provider is marked `FAILED/SEARCH_INTERRUPTED`, remaining `PENDING` providers are marked `SKIPPED/SEARCH_INTERRUPTED_BEFORE_PROVIDER`, and the job becomes `COMPLETED`, `PARTIAL`, or `FAILED` based on already committed evidence. The job is failed closed if provider counts do not match persisted source rows, which also protects searches interrupted during an upgrade from the earlier V5 transaction design. No provider request is retried automatically. This recovery assumes the current single-backend deployment; multi-instance deployments need an ownership lease or heartbeat so one live instance cannot recover another instance's active work.

### Explicit retry of one failed provider/query

An authenticated owner can retry the latest `FAILED` attempt by calling `POST /api/v1/research-jobs/{id}/providers/{provider}/queries/{queryOrder}/retry?attempt={attempt}`. The route accepts no query or filter body: it reuses the exact stored query and filters, sends one request to the same configured provider, and permits at most three total attempts. The attempt number is a stale-request guard; a duplicate request, a retry of an old attempt, a non-failed outcome, or a fourth attempt returns a conflict without contacting the provider.

Each retry creates a new provider-run row. Prior failures are never overwritten. Successful results and the new `SUCCEEDED` outcome commit in one transaction. The job's terminal status and coverage use the latest attempt for each provider/query pair, while the API and exports retain the full attempt history. A user click is the approval to repeat that exact public query; AURA does not retry automatically. A failed retry is saved and remains eligible for another explicit retry until the three-attempt cap.

## API and UI behavior

`ResearchSearchResponse` adds `providerRuns`. A run response contains provider, query order/text, attempt, status, result count, optional failure code, and start/completion times. Every source response and export includes its `searchQuery`. A partial synchronous search responds with HTTP 200 and status `PARTIAL`, includes successful rows, and has a coverage note naming failed or skipped latest attempts. A completely failed request retains `FAILED` plus all run rows; authenticated `GET /api/v1/research-jobs/{id}` and history listing expose the provider outcomes. The UI offers a retry action only for the latest failed attempt and labels the current attempt out of three. Deletion is rejected while a job is actively `SEARCHING`.

Source listing, export, and selected-source report generation accept jobs in `COMPLETED` or `PARTIAL` state. The UI labels partial jobs and shows each provider's result count or stable failure code. If every provider fails and the search endpoint returns an error, the UI refreshes history so the failed job's persisted outcomes are still visible.

JSON and Markdown exports include the provider run summary. CSV exports retain the source columns, add job coverage/status columns, and append typed `PROVIDER_RUN` rows in addition to `SOURCE` rows. This preserves provider outcome information even when there are no source rows.

## Why this design

- Persisting outcome rows makes provider coverage auditable after the synchronous response is gone.
- Keeping successful batches makes the feature useful through transient outages while clearly labeling incomplete coverage.
- Returning an error only when every provider fails keeps an empty/failed request distinct from an incomplete but usable result set.
- Stable failure codes avoid leaking raw provider messages and credentials.
- Sequential execution remains simple for one local owner. Background queues, automatic retry, cancellation, and run resumption require a separate persisted workflow design.

## Code map

- `backend/src/main/java/ai/aura/api/domain/ResearchProviderRun.java`: provider outcome state machine.
- `backend/src/main/java/ai/aura/api/domain/ResearchProviderRunStatus.java`: persisted lifecycle states.
- `backend/src/main/java/ai/aura/api/application/ResearchSearchService.java`: sequential provider calls, explicit per-provider retry, failure isolation, and final status choice.
- `backend/src/main/java/ai/aura/api/domain/ProviderRunOutcomes.java`: latest-attempt aggregation while preserving all audit rows.
- `backend/src/main/java/ai/aura/api/application/ResearchJobService.java`: transaction boundaries, provider result persistence, outcome recovery, and provider-run reads.
- `backend/src/main/java/ai/aura/api/application/ResearchSearchRecovery.java`: startup recovery hook for interrupted single-instance searches.
- `backend/src/main/java/ai/aura/api/api/ProviderRunResponse.java`: safe API representation.
- `backend/src/main/resources/db/migration/common/V5__record_provider_search_runs.sql`, `V6__allow_skipped_provider_runs.sql`, `V8__record_bounded_query_plans.sql`, and `V10__record_manual_provider_retries.sql`: provider/query audit, recovery state, source provenance, and bounded attempts.
- `backend/src/test/java/ai/aura/api/ResearchSearchApiTest.java`: complete, partial, and all-failed scenarios.
- `web/src/App.tsx` and `web/src/App.test.tsx`: partial status and provider failure disclosure.

## Testing and learning prompts

Tests verify that mixed outcomes preserve only successful source rows and report the failed provider, an all-failed search saves run audits and returns the first provider error, a user-triggered retry appends a new attempt while retaining the failure row, stale or unauthenticated retries are rejected, and recovery preserves committed evidence while marking active/not-started providers interrupted/skipped.

For the bounded per-query audit design, see [BOUNDED-QUERY-PLANS.md](BOUNDED-QUERY-PLANS.md).

- Why does AURA commit a `RUNNING` provider row before making an HTTP request?
- Why is the network request outside a database transaction?
- Why must source persistence and `SUCCEEDED` status commit atomically?
- What state should be recovered if the process crashes while a provider is `RUNNING` or still `PENDING`?
- Why is `PARTIAL` different from both `COMPLETED` and `FAILED`?
- What additional design is needed before automatic retries are safe?
