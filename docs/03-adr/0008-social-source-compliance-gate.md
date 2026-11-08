# ADR 0008: Gate social connectors on provider-specific controls

- Status: Accepted
- Date: 2026-11-07
- Scope: X and authenticated/account-based social access

## Context

AURA's research goal includes publicly available social material. Social APIs differ in access rules, quotas, display requirements, content-removal behavior, and whether their data may be stored or sent to a model. AURA stores selected search text and metadata with a research brief until the owner deletes it and does not yet have a general provider deletion-event service.

X's [Search Posts API](https://docs.x.com/x-api/posts/search/introduction) and [Developer Guidelines](https://docs.x.com/developer-guidelines) describe product-specific access and data-handling requirements, including deletion obligations for X Content. The local AURA storage model does not currently provide a provider-specific removal event flow or a tested deletion deadline.

## Decision

- Do not implement X, authenticated account feeds, private feeds, direct messages, or account/profile monitoring in the current milestone.
- Do not scrape social websites, bypass access controls, or use social data for surveillance, sensitive-trait inference, or model training.
- Allow a narrowly bounded public post-search adapter only after checking its official API, access model, data fields, query limits, and user-facing scope. The current Bluesky adapter is recorded separately in ADR 0009.
- Before adding X, design and test its provider-specific attribution, display, retention, removal notifications, deletion deadlines, export behavior, credential storage, rate-limit, and cost controls. Keep X content out of report synthesis until those obligations have been separately reviewed.

## Consequences

- AURA supports explicit, topic-query-driven public Bluesky post search through its unauthenticated public AppView API; it does not connect to a user's Bluesky account or read private feeds.
- AURA still has no X connector, social profile-search mode, follow-list monitor, private-account access, or background social collection.
- Saved results are local snapshots until the owner deletes the research brief. AURA does not currently poll for post changes or propagate provider-side removals to saved/exported copies. This limitation must remain visible and should be addressed before adding providers with stricter removal deadlines.
- `All configured sources` sends the query to Bluesky when the provider is enabled. The UI states this scope; the owner can disable the connector with `AURA_BLUESKY_ENABLED=false`.

## Conditions to reconsider

Reconsider X only after an official provider account, current terms review, known access costs, and a tested compliance flow are in place. Reconsider profile/account access only if the use case and privacy boundaries are explicitly scoped, and it cannot become bulk tracking or personal profiling.

## Learning prompts

- Why does a public API still need provider-specific retention and removal design?
- How does topic-level public-interest research differ from monitoring a person's account?
- What information does AURA disclose when the user chooses all configured providers?
