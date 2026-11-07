# ADR 0009: Bounded Bluesky public-post search

- Status: Accepted
- Date: 2026-11-07

## Context

The product needs an explicitly selected social source for public-interest topic research. Bluesky documents public unauthenticated `app.bsky.*` GET endpoints and recommends the cached `https://public.api.bsky.app` host for public-web use. Its feed search endpoint can return a bounded set of matching public posts. AURA already supports a small number of fixed HTTP search adapters and preserves source provenance.

Official references: [API Hosts and Auth](https://bsky.network/docs/api-directory/), [Bluesky rate limits](https://bsky.network/docs/rate-limits/), and the [`searchPostsV2` lexicon](https://github.com/bluesky-social/atproto/blob/main/lexicons/app/bsky/feed/searchPostsV2.json).

## Decision

- Add an explicit `bluesky` provider backed only by `GET https://public.api.bsky.app/xrpc/app.bsky.feed.searchPostsV2`.
- Send a user-entered query with `sort=recent`, a limit of at most 20, and optional language and freshness (`since`) filters. Do not use profile, author, engagement, or account-specific endpoints.
- Do not require or retain account credentials. Do not follow redirects, fetch post pages, crawl the network, or run background searches.
- Normalize only post record text, the public permalink derived from a validated AT post URI, and the post/index timestamp. Ignore author profiles and engagement metrics. Treat text as untrusted evidence.
- Make the provider enabled by default with an opt-out environment setting. Automatic retains its existing priority: Brave when keyed, otherwise GDELT, then Bluesky if earlier sources are disabled. All configured sources includes Bluesky when enabled.
- Mark the result type as `DISCUSSION`, preserve `bluesky` provenance, and state that country filters are not applied.
- Keep X and account-based access outside this decision; see [ADR 0008](0008-social-source-compliance-gate.md).

## Reasons

- The connector uses the documented public API, not page scraping or private account access.
- A fixed endpoint, short timeout, no redirects, bounded result count, and no arbitrary result-page fetch limit server-side exposure.
- Explicit selection and provenance let the owner see where a query is sent and which source returned each result.
- A user can include public social material in their research without claiming that it represents all Bluesky posts or the whole internet.

## Consequences and limitations

- The query is disclosed to Bluesky when `bluesky` or `all` is selected and the search is started.
- Country is not an API filter for this provider. Results are bounded, recent-sorted, and subject to the provider's index, moderation, availability, and changing rate limits.
- AURA stores returned text and metadata locally until the owner deletes the brief. It does not currently sync remote edits/deletions or enforce a Bluesky-specific retention window. The saved snapshot is a limitation; users should open the public link for current content.
- In all mode, a provider failure fails the overall job and no result rows are stored, even though earlier providers may already have received the query.
- Source text may be inaccurate, misleading, or harmful; it remains untrusted and must not be treated as verified fact. Person-focused research remains limited to public-interest material under the product boundary.

## Conditions to reconsider

Revisit if Bluesky changes endpoint access, required attribution, moderation or public-web behavior, deletion/retention rules, quotas, or supported search parameters; if AURA adds user-specific feeds; or before implementing remote multi-user storage. Check current official terms and operational limits at that time.

## Learning prompts

- Why is the AppView API request constrained to one topic query and at most 20 results?
- Why does AURA store the provider's post link and timestamp but ignore author metrics?
- How should remote deletion notifications change local retention and exports?
