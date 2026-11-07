# ADR 0007: Explicit multi-provider public search

- Status: Accepted
- Date: 2026-11-07

## Context

AURA initially ran one public search provider per research job. That kept egress and provenance simple, but prevented a user from comparing the coverage of providers already configured in their local installation.

## Decision

Keep Automatic as one-provider selection (Brave when configured, otherwise GDELT). Add an explicit `all` mode that calls every currently configured provider sequentially. Persist returned sources only after all provider calls succeed. If any call fails, mark the job failed and save no source rows; earlier providers may already have received the query. Each saved source records the provider that returned it. Duplicate URLs from different providers are retained as distinct provenance records. Add provider filtering to source listing and exports.

## Reasons

- Broader coverage is explicitly requested and visible before the query is sent.
- The number and identity of queried providers are bounded by the local configuration.
- A source keeps its actual provider identity for review and export.
- No arbitrary crawl or result-page fetch is added.
- Existing sources remain available if one provider is not configured; only enabled providers are queried.

## Consequences

- The query can be disclosed to more than one provider in `all` mode.
- The result count can reach 80 with the current adapters: up to 60 Brave results and 20 GDELT articles.
- Duplicate URLs may appear when indexes overlap.
- Provider calls are sequential. A later provider failure fails the job and prevents persistence, although previous providers may already have received the query.
- Coverage is still incomplete and bounded by each provider's index, filters, quotas, and terms.

## Conditions to reconsider

Add partial-success persistence only when per-provider run status can be stored and shown accurately. Add additional providers only after reviewing their APIs, terms, quotas, data retention, and deletion obligations. Direct social connectors require a separate provider-specific review; scraping and broad account surveillance remain out of scope.

## Learning prompts

- Explain why Automatic and All configured sources have different egress behavior.
- Explain why source rows persist each adapter's identity even when a research job says `all`.
- Explain the failure trade-off when one provider succeeds and a later provider fails.
