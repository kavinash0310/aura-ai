# ADR 0017: Quote anchors matched to provider snippets

- Status: Accepted
- Date: 2026-11-11

## Context

Research reports currently check that citations refer to selected source IDs, but that does not confirm that a report finding uses any words present in the supplied evidence. AURA's report model receives search-result titles and provider descriptions, not page snapshots.

## Decision

Allow a report finding to include one short `evidenceQuote` tied to one of its cited source IDs. The AI service checks that the quote appears in the selected source title or provider description after case and whitespace normalization. A mismatched quote rejects the report response. The UI labels successful anchors as matches to provider snippets. Quotes remain optional so a local model that cannot produce a matching excerpt can still return a report.

## Consequences

- A reader can see the exact provider text associated with a finding and follow its source link.
- The anchor is a match against AURA's saved provider snippet; it does not prove that the current linked page contains that text or that it supports the complete finding.
- AURA does not fetch more page content or send page-reader text to the model.
- Semantic entailment checks, source corroboration, and model-quality evaluations remain planned.
- The report prompt version increments when the quote field and validation are introduced.

## Reconsider when

Change the quote contract after evaluating supported models on a reviewed fixture set and deciding whether fetched page snapshots should ever become report evidence.

## Learning prompts

- What does exact quote matching establish, and what does it not establish?
- Why is the quote tied to a source ID rather than treated as standalone text?
- Why is fetched page content still excluded from the report flow?
