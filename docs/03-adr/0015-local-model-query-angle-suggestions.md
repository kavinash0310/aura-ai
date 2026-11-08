# ADR 0015: Local-model suggestions for reviewed search angles

- Status: Accepted
- Date: 2026-11-08

## Context

AURA already lets an owner enter up to two additional search queries and review the complete three-query plan before starting a public search. Query angle selection is repetitive, but automatically generated queries must not silently expand provider calls or send private context to hosted models.

## Decision

Add an authenticated `POST /api/v1/research/query-plan` endpoint through Spring to the internal Python service. It accepts one bounded topic and, only when local Ollama is configured, asks it for at most two concise public-source angles. The local model receives no provider results, documents, credentials, or source pages. It has no search tool or provider route.

Suggestions are drafts. The browser shows each suggestion separately; the owner explicitly adds one, can edit it, and must still click **Start research** before the search query is sent to selected public providers. A topic change discards in-flight results and current suggestions. If the model is disabled or unavailable, manual query entry remains available.

The prompt asks the model to avoid private or sensitive personal data, exact home addresses, credentials, and real-time location. These instructions do not guarantee model output quality, so owner review is required. The public search remains bounded by the existing three-query and provider-count limits.

## Consequences

- Local Ollama may help the owner explore official statements, timelines, evidence, and independent analysis without turning search into an autonomous loop.
- The feature adds a user-authenticated Spring route and an internal bearer-token-protected Python route.
- Query suggestion generation shares the single-request local model semaphore with report and claim-comparison actions.
- Operational timing/token metadata is logged without the topic or generated queries. Draft suggestions remain in browser memory; if the owner submits them in a search, the existing workflow stores the submitted queries with that saved brief.
- A model may still return poor or unsuitable suggestions; it does not send them anywhere, and public search needs a separate owner action.

## Rejected alternatives

- Automatically search every model suggestion: rejected because it expands outbound requests without a review step.
- Send the topic to a hosted model: rejected for the local-first MVP and because topics may contain sensitive information.
- Add the suggestions directly to the editable plan: rejected because it can overwrite or silently extend owner-entered angles.
- Generate unlimited queries: rejected because the existing provider-call budget is deliberately capped.

## Reconsider when

Change the flow only after evaluating query relevance on a reviewed fixture set, defining retention/consent for query text, and preserving an explicit provider request budget and owner review.

## Learning prompts

- Why does query generation use the local model rather than a hosted provider?
- Why do model suggestions remain separate from the submitted search plan?
- How would you evaluate query usefulness without treating model output as evidence?
- Why is the query count limit enforced by the backend as well as the UI?
