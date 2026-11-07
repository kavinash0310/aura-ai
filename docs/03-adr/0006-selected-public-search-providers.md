# ADR 0006: Selected public search providers

- Status: Accepted
- Date: 2026-11-07

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
- The provider host is fixed, response fields and result counts are capped, and AURA never fetches returned article URLs.
- Provider provenance is already a first-class field in saved sources and is included in exports.
- No social-account credentials, scraping, or personal data store is added.

## Advantages and disadvantages

- Advantage: clean local installs can make bounded public-news searches without a third-party API key.
- Advantage: Brave and GDELT have distinct visible coverage and source identity.
- Disadvantage: GDELT returns news metadata and headlines, not article-body text, and indexes only part of the public news web.
- Disadvantage: GDELT search is limited to the past three months; the UI's `py` time choice therefore maps to three months for that provider.
- Disadvantage: provider selection is exclusive per job; merging, per-provider error audits, social connectors, and claim comparison are still future work.

## Conditions to reconsider

Review source terms, service availability, quotas, and data retention before enabling any additional provider. Add direct social connectors only when official APIs, user grants, safe retention/display rules, and a concrete use case are established. Do not replace the adapters with unrestricted crawling.

## Learning and interview prompts

- Explain why a no-key public API still creates a network trust boundary.
- Compare query disclosure, coverage, response shape, timeouts, and provenance between providers.
- Explain how fixed-host server requests avoid turning search results into an SSRF fetcher.
