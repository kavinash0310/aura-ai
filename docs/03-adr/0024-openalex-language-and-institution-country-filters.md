# ADR 0024: OpenAlex language and institution-country filters

- Status: Accepted
- Date: 2026-11-20

## Context

The initial OpenAlex adapter applied only publication-date freshness even though AURA's shared search request already carries optional language and country filters. OpenAlex supports `language` for the indexed language of work metadata and `institutions.country_code` for works associated with author institutions in a country. These are provider-specific meanings: institution country is not author nationality, reader location, or necessarily the location where a work was produced.

Official references: [OpenAlex filtering guide](https://help.openalex.org/api/filtering/) and [OpenAlex work attributes](https://help.openalex.org/data/works/attributes/).

## Decision

- Combine the existing publication-date filter with the selected language and institution-country filters in one bounded OpenAlex request.
- Keep search subject, country, and language owner-selected and send them only on an explicitly started search.
- Describe country filtering as institution-affiliation matching wherever the OpenAlex provider is identified.
- Keep OpenAlex opt-in; this does not broaden the provider to general web search or author-profile search.
- Keep the existing API's two-letter country and language validation and provider response limits.

## Consequences

- OpenAlex results can now honor the same selected language and country inputs as the other providers where their APIs support those filters.
- In All configured sources mode, filter semantics still vary by provider. Bluesky does not apply country filters; OpenAlex country means author institution affiliation.
- The selected filters constrain provider retrieval; they do not guarantee complete regional or language coverage, and records with missing metadata may not match.
