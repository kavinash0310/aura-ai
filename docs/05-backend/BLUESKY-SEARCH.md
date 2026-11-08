# Bluesky public-post search

## Scope

AURA can query Bluesky's official public AppView for a user-entered topic and save up to 20 recent-sorted public posts per research job. The connector is read-only and uses no account token. It does not search every Bluesky post, retrieve profiles, access private feeds, follow accounts, monitor a person, or fetch post pages during search. The owner may separately use the general one-page reader for a post link already saved in the selected brief.

Choose `Bluesky · public posts` for one-provider search or `All configured sources` to send the query to all enabled providers. Bluesky is enabled by default and can be disabled with `AURA_BLUESKY_ENABLED=false`. Automatic keeps Brave first and GDELT second, then uses Bluesky only if earlier providers are disabled.

## Request and data flow

~~~mermaid
sequenceDiagram
  actor Owner
  participant UI as React workspace
  participant API as Spring API
  participant Provider as BlueskyPublicSearchProvider
  participant AppView as Bluesky public AppView
  participant DB as PostgreSQL
  Owner->>UI: Select Bluesky and start a bounded topic search
  UI->>API: POST job/{id}/search provider=bluesky
  API->>Provider: Validate provider configuration and filters
  Provider->>AppView: GET searchPostsV2 (query, recent, <=20, optional language/since)
  AppView-->>Provider: Public post records and AT URIs
  Provider->>Provider: Validate post URI; cap and normalize text
  Provider-->>API: DISCUSSION source batch with Bluesky provenance
  API->>DB: Persist source metadata after successful search
  API-->>UI: Results and provider-specific coverage note
~~~

The adapter contacts one fixed HTTPS host, applies an eight-second timeout, disables redirects, and maps provider errors to stable application error codes. It accepts only post URIs with the expected `app.bsky.feed.post` collection and safe record-key syntax, then constructs a link at `https://bsky.app`. It ignores author/profile fields and metrics, strips control characters, caps text, deduplicates normalized links, and uses the record timestamp with indexed-time fallback. Query and result text remain untrusted.

The API supports recent, week, month, and year lower bounds through `since`; year means 365 days for this provider. An optional language is sent to the API. AURA's country field is not sent and does not filter Bluesky results. A search limit of 20 caps returned post rows. In all-provider mode the overall combined result cap is 100 (up to 60 Brave + 20 GDELT + 20 Bluesky).

## Privacy, terms, and limitations

- The exact search phrase is sent to Bluesky after the owner explicitly starts a Bluesky or all-provider search. No uploaded documents, connected account details, or local files are sent.
- Bluesky's [API directory](https://bsky.network/docs/api-directory/) describes unauthenticated public endpoints and recommends `public.api.bsky.app` for public-web use. The [rate-limit guidance](https://bsky.network/docs/rate-limits/) may change; the adapter handles 429 responses and does not automatically retry.
- Returned post text and its permalink are saved with the research brief. Deleting the brief removes its saved local sources. AURA does not detect later remote edits/deletions or enforce a Bluesky-specific retention schedule. Users should follow the link for current content; treat downloaded research as a dated snapshot.
- AURA's person-research policy allows only public-interest material and prohibits doxxing, sensitive-trait inference, harassment, invasive tracking, and private-account access. Do not use this topic search as a substitute for profile monitoring.
- If the owner explicitly creates a report, selected snippets may be sent to the configured local Ollama model. No report request runs automatically, and this connector does not train a model.
- This is not a full internet or full Bluesky archive search. Provider indexing, moderation, API availability, query semantics, and result limits affect coverage.

## Code map

- `backend/src/main/java/ai/aura/api/search/BlueskyPublicSearchProvider.java`: fixed endpoint, bounded request, URI validation, normalization, and mapping.
- `backend/src/test/java/ai/aura/api/search/BlueskyPublicSearchProviderTest.java`: request shape, deduplication, URI rejection, timestamps, bounded freshness, and disabled state.
- `backend/src/main/java/ai/aura/api/application/ResearchSearchService.java`: explicit selection, Automatic, and all-enabled resolution.
- `backend/src/main/java/ai/aura/api/api/ResearchSearchResponse.java`: provider-specific coverage note.
- `web/src/App.tsx`: provider selector and source-scope disclosure.
- `compose.yaml`, `.env.example`, and `backend/src/main/resources/application.yml`: opt-out setting and timeout.
- [ADR 0009](../03-adr/0009-bluesky-public-post-search.md): decision and trade-offs.

## Learning prompts

- Why does the API use an explicit selected provider and a fixed endpoint rather than scrape public pages?
- Which source fields are evidence, and why are they still untrusted?
- What lifecycle feature is needed before a remote service with strict deletion deadlines is added?
- Why does “all configured sources” disclose the query to more services than Automatic?
