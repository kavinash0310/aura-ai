# ADR 0006: Selected public search providers

- Status: Accepted
- Date: 2026-11-07

> The initial provider set and one-provider-per-job behavior were extended by [ADR 0007](0007-explicit-multi-provider-search.md) and [ADR 0009](0009-bluesky-public-post-search.md). Those records add explicit all-configured search and a bounded official Bluesky public-post connector.

## Context

The initial public research feature used one optional Brave adapter. That left a clean-install with no API key unable to search and gave AURA only one provider's coverage. The product needs additional source coverage while preserving clear egress, provenance, and user control.

## Problem

How should AURA add an independent news index without scraping social sites, hiding provider use, or making a large crawler?

## Options considered

1. Continue with Brave only and require the owner to configure a paid/limited provider account.
2. Add GDELT DOC as a second public news adapter, leave Brave optional, and make provider selection explicit with a safe automatic preference.
3. Crawl arbitrary result URLs and social sites directly.

## Selected decision

Add a fixed-host GDELT DOC article-list adapter. The UI offers Automatic, Brave, and GDELT. Automatic prefers Brave when the owner has configured a key and otherwise uses GDELT. Each research job uses one provider; stored rows retain the provider identifier. GDELT is enabled by default, needs no key, returns only public news metadata, and can be disabled with `AURA_GDELT_ENABLED=false`.

## Reasons

- Adds global public news coverage with a bounded HTTP API request.
- The owner sees the provider choice and searches only after an explicit action.
- The provider host is fixed, redirects are disabled, response fields and result counts are capped, and AURA never fetches returned article URLs.
- Provider provenance is already a first-class field in saved sources and is included in exports.
- No social-account credentials, scraping, or personal data store is added.

## Advantages and disadvantages

- Advantage: clean local installs can make bounded public-news searches without a third-party API key.
- Advantage: Brave and GDELT have distinct visible coverage and source identity.
- Disadvantage: GDELT returns news metadata and headlines, not article-body text, and indexes only part of the public news web.
- Disadvantage: GDELT search is limited to the past three months; the UI's `py` time choice therefore maps to three months for that provider.
- Disadvantage: merging, per-provider error audits, X/account connectors, and claim comparison remain future work.

## Conditions to reconsider

Review source terms, service availability, quotas, and data retention before enabling any additional provider. For social connectors, use a documented official API and verify safe retention/display rules; require user grants only when accessing account-specific data. Keep X and account feeds deferred under ADR 0008. Do not replace the adapters with unrestricted crawling.

## Learning and interview prompts

- Explain why a no-key public API still creates a network trust boundary.
- Compare query disclosure, coverage, response shape, timeouts, and provenance between providers.
- Explain how fixed-host server requests avoid turning search results into an SSRF fetcher.
