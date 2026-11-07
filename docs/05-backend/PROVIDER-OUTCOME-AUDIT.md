# Provider outcome audit and partial research results

## What this adds

When AURA searches one provider or several, it records each provider's lifecycle and outcome. A failure from one provider no longer erases results already returned by another provider. The saved brief shows which sources succeeded, which provider failed, and its stable error code.

This change is most visible in `All configured sources` mode:

- All selected providers succeed: job status is `COMPLETED`.
- At least one succeeds and at least one fails: status is `PARTIAL`; successful source batches are still available.
- Every selected provider fails: status is `FAILED`; no source batch is saved, but all provider run outcomes remain available in job history and the API returns the first failure's stable error response.

The provider calls remain sequential and explicit. A provider called before a later provider fails may already have received the user's query.

## Data model

Migration V5 adds `research_provider_runs` with:

- `research_job_id` and provider name, unique per job/provider.
- `run_order`, preserving the order of sequential requests.
- `status`: `PENDING`, `RUNNING`, `SUCCEEDED`, or `FAILED`.
- `result_count`, initialized to zero and set on success.
- `failure_code`, set only for a failed run using stable application codes (no raw provider response or credentials).
- `started_at` and `completed_at` timestamps.

The existing job row adds one enum value, `PARTIAL`, to its status constraint. Each source still stores the provider that returned it; no result schema change is needed. Deleting a job cascades to provider run rows and source rows.

## Request lifecycle and transaction boundaries

1. Validate the requested provider selection and resolve enabled providers before starting the job. If none are available, return `503 SEARCH_PROVIDER_NOT_CONFIGURED` and leave the job `REQUESTED`.
2. In one short transaction, lock the job, change it to `SEARCHING`, store the search filters, and persist one `PENDING` provider run for every selected provider.
3. For each provider in order, commit `RUNNING` and `started_at`, then perform the bounded fixed-host HTTP request outside a database transaction.
4. On a valid response, commit `SUCCEEDED`, result count, and `completed_at`. On an expected provider exception, commit `FAILED`, stable failure code, and `completed_at`. An unexpected adapter runtime exception is recorded as `SEARCH_PROVIDER_FAILURE`. Continue to the next selected provider.
5. In a final transaction, save all successful result batches with provider provenance and rank. Set the job to `COMPLETED`, `PARTIAL`, or `FAILED` based on the provider outcomes. For all-failed searches, the run rows and terminal job state commit before the controller returns the error response.

Provider network I/O is not held inside a database transaction. Run status and provider results therefore commit in separate short transactions. A process crash during a provider request can leave a run in `RUNNING`; automated crash recovery and retries are not part of this milestone and should be implemented before background execution is introduced.

## API and UI behavior

`ResearchSearchResponse` adds `providerRuns`. A run response contains provider, status, result count, optional failure code, and start/completion times. A partial synchronous search responds with HTTP 200 and status `PARTIAL`, includes successful rows, and has a coverage note naming failed providers. A completely failed request retains `FAILED` plus all run rows; authenticated `GET /api/v1/research-jobs/{id}` and history listing expose the provider outcomes.

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
- `backend/src/main/java/ai/aura/api/application/ResearchSearchService.java`: sequential provider calls, failure isolation, and final status choice.
- `backend/src/main/java/ai/aura/api/application/ResearchJobService.java`: transaction boundaries, result persistence, and provider-run reads.
- `backend/src/main/java/ai/aura/api/api/ProviderRunResponse.java`: safe API representation.
- `backend/src/main/resources/db/migration/common/V5__record_provider_search_runs.sql`: provider audit table and job status constraint.
- `backend/src/test/java/ai/aura/api/ResearchSearchApiTest.java`: complete, partial, and all-failed scenarios.
- `web/src/App.tsx` and `web/src/App.test.tsx`: partial status and provider failure disclosure.

## Testing and learning prompts

Tests verify that mixed outcomes preserve only successful source rows and report the failed provider, while an all-failed search saves run audits, remains `FAILED`, and returns the first provider error. The UI test checks that the owner sees partial status and the failure code.

- Why does AURA commit a `RUNNING` provider row before making an HTTP request?
- Why is the network request outside a database transaction?
- What state should be recovered if the process crashes while a provider is `RUNNING`?
- Why is `PARTIAL` different from both `COMPLETED` and `FAILED`?
- What additional design is needed before automatic retries are safe?
