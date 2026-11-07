# ADR 0008: Gate direct social-source connectors on compliance design

- Status: Accepted
- Date: 2026-11-07

## Context

The product direction includes public social-media research. AURA currently stores provider result text and metadata in PostgreSQL until a brief is deleted. It has no provider-specific content-removal feed, deletion queue, expiry schedule, or social-content display rules.

X documents a Recent Search endpoint limited to the past seven days and a full-archive endpoint available on pay-per-use or Enterprise access in its [Search Posts API overview](https://docs.x.com/x-api/posts/search/introduction). Its [Developer Guidelines](https://docs.x.com/developer-guidelines) require deletion of X Content within 24 hours when requested by X or a user, or when content is removed/suspended on X. The guidelines also prohibit scraping, surveillance/user tracking, sensitive-trait derivation, and AI/ML training on X data; they require attribution and restrictions on altering displayed content.

## Decision

Do not add direct social-platform search or scraping in the current implementation. Keep provider adapters limited to official APIs with an explicit user-selected source. Before adding any direct social connector, design its provider-specific retention, deletion-event handling, content display, attribution, export, credential storage, rate-limit, and cost behavior. Do not use social data for surveillance, sensitive-trait inference, or model training. Keep X content out of AURA's model synthesis until the applicable provider terms are specifically reviewed.

## Reasons

- AURA's indefinite-until-user-deletion research storage cannot currently guarantee X's documented 24-hour content-removal deadlines.
- AURA lacks a mechanism to detect a post removed after the search result was saved.
- Official API access and retention obligations differ across social platforms and cannot be assumed from one adapter.
- This keeps public-interest topic research possible without enabling personal tracking or intrusive profiling.

## Consequences

- Current search covers Brave web/news/discussions and GDELT indexed news; neither is presented as direct X access.
- A direct X connector remains deferred until data lifecycle and account terms can be met and tested.
- A connector must be explicit and separately selectable; `All configured sources` means only providers that are implemented and enabled.

## Conditions to reconsider

Reconsider when an official provider account and token are available, access costs and limits are known, the provider's current terms are reviewed, and AURA implements removal-event ingestion plus a tested deletion process meeting the provider's deadline. Keep a connector disabled when compliance notifications cannot be received or processed.

## Learning prompts

- Explain why an API token alone is not enough to safely persist third-party social content.
- Design a deletion event flow that removes post text and exports within the required window.
- Explain why topic-level public-interest analysis differs from monitoring an individual account.
