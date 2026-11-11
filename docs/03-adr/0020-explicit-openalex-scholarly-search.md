# ADR 0020: Explicit OpenAlex scholarly-work search

- Status: Accepted
- Date: 2026-11-11

## Context

AURA needs another public research source that adds a distinct kind of evidence without turning the bounded search workflow into a crawler. OpenAlex exposes scholarly works and abstracts through a fixed API. Work abstracts are returned as an inverted index and may be absent or incomplete.

Official references: [OpenAlex API authentication](https://help.openalex.org/api/authentication/), [works search](https://help.openalex.org/api/searching/), [works attributes](https://help.openalex.org/data/works/attributes/), and [filters](https://help.openalex.org/api/filtering/).

## Decision

- Add an `openalex` provider that requests only `GET https://api.openalex.org/works` using title, abstract, and keyword search.
- Require the owner to configure an OpenAlex API key. Send it only in the `Authorization: Bearer` header; never put it in the URL or logs.
- Run OpenAlex only when the owner chooses it or explicitly chooses All configured sources. Keep it out of Automatic so enabling the key does not silently route queries or use the owner's provider budget.
- Request no more than the existing per-provider result limit (at most 20). Select only the work ID, title, publication date, authorships, and abstract index. Keep at most three author display names and reconstruct a bounded abstract excerpt locally.
- Accept links only for HTTPS OpenAlex work IDs. Store the returned title, limited author names, reconstructed abstract excerpt, and publication date as ordinary source metadata under the existing saved-brief deletion policy.
- Apply the selected freshness window with OpenAlex's publication-date filter at calendar-day precision. State that country and language filters are unsupported and that the provider is not general-web or author-profile search.
- Do not fetch work pages, download full text, crawl, resolve author profiles, or use private/account data. Returned titles and abstracts remain untrusted evidence.

## Reasons

- The provider adds an academic evidence source using one fixed, bounded outbound request per query.
- Explicit source selection preserves the existing user-controlled egress boundary and makes provider costs or quotas visible before use.
- Requesting a key header avoids exposing the owner's credential in request URLs.
- Strict URL validation and bounded text reconstruction prevent provider response fields from controlling arbitrary links or unbounded saved excerpts.

## Consequences and limitations

- Search text is disclosed to OpenAlex only when `openalex` or All configured sources is selected and a search starts.
- With four providers and three planned queries, All configured sources can make up to 12 sequential requests and return up to 360 source rows at the current per-provider limits.
- Search covers OpenAlex's scholarly index, not all research, the whole web, social media, or a complete list of an author's work. Country and language filters do not apply.
- Abstract reconstruction can be missing or imperfect; publication-date freshness is day-based. AURA does not assert that a result is relevant or verify its linked page.
- The owner's key is used under the quota and plan attached to that OpenAlex account. The provider request and returned metadata remain subject to the provider's current terms.

## Conditions to reconsider

Recheck OpenAlex authentication, usage limits, pricing, terms, search fields, and abstract representation before expanding this connector. Add author-entity search only as a separate explicit provider with its own outcome audit and clear identity-matching limitations.

## Learning prompts

- Why is OpenAlex kept out of Automatic even when its key is configured?
- Why is an OpenAlex inverted-index abstract not treated as full text or verified evidence?
- Why does this source not count as a general author-profile search?
