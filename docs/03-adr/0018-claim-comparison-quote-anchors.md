# ADR 0018: Claim-comparison quote anchors

- Status: Accepted
- Date: 2026-11-11

## Context

Claim comparisons label selected snippets as support, challenge, context, or unclear. Like reports, they previously checked citation membership but did not show which supplied words were associated with each evidence item.

## Decision

Allow an evidence item to include one optional short quote tied to one of its cited sources. The AI service matches the quote against the source title or provider description after normalizing case and whitespace. A mismatched quote fails the request. The UI labels it as a provider-snippet match and links to its candidate source.

## Consequences

- The owner can inspect a concrete text anchor for a model's comparison stance.
- Exact matching does not show that the quote logically supports the stance, represents the whole page, or remains on the linked page.
- Quotes remain optional because local models may not reproduce snippets exactly.
- Claim-comparison prompt version advances to v2; model evaluations and semantic evidence checks remain planned.

## Reconsider when

Change the evidence format after evaluating quote reliability across supported local models and reviewed comparison examples.

## Learning prompts

- Why is quote-to-snippet matching a useful but limited validation?
- Why does quote provenance need to belong to a source already selected for comparison?
- Which reviewed cases should be included in a model-quality evaluation set?
