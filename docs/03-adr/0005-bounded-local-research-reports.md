# ADR 0005: Bounded local research reports

- Status: Accepted
- Date: 2026-11-07

## Context

AURA stores provider search snippets and source metadata but does not yet fetch result pages or produce a cited summary. The owner has 16 GB RAM and prefers local/open-source operation. Retrieved snippets are untrusted and may contain instructions.

## Problem

Add useful synthesis without making an LLM a permission authority, silently sending data to a hosted model, or treating a generated citation as proof.

## Options considered

- Keep raw source lists only and defer AI integration.
- Generate reports in Spring with a hosted provider SDK.
- Add an explicit local Ollama call behind the Python AI service.
- Allow an autonomous agent to search/fetch/call models in a loop.

## Selected decision

Use one explicit, bounded report action. Java validates job and source ownership, then sends no more than 20 selected source metadata records and provider snippets to the token-protected Python service. Python uses the local Ollama chat endpoint and JSON Schema output, then validates output structure and citation IDs against the supplied set. The UI holds the result in memory and offers Markdown download. The model defaults to disabled; there is no hosted fallback.

## Reasons

This moves the AI boundary into actual use while keeping authorization and persistence in Java. Local inference limits data egress and is compatible with the user's learning goal. A fixed workflow is simpler to audit than an agent loop. Source IDs are checked before rendering so the model cannot fabricate a link identifier, but this is explicitly weaker than semantic citation verification.

## Advantages and disadvantages

Advantages: local-only behavior, minimized input, bounded cost and context, explicit user action, source traceability, structured validation, and no report data persisted by default.

Disadvantages: local models can be slow or weak; user setup is required; provider descriptions may be incomplete; JSON compliance varies by model; citation membership does not prove support; no report is retained until the owner downloads it.

## Consequences

The Python service has an internal bearer-token boundary and local/private Ollama URL validation. Tests must cover authentication, input bounds, source ownership, schema validation, and citation membership. Documentation must preserve the distinction between candidate citation and verified evidence. Before reports are stored or used for consequential decisions, add user review, semantic evaluation, prompt/version tracking, and deletion controls.

## Conditions to reconsider

Revisit the provider boundary if a hosted model becomes a user-approved requirement, local models cannot meet quality targets, an evaluation suite supports semantic citation checks, or report persistence becomes necessary for reproducibility.

## Learning and interview prompts

- Why is Java authoritative for source access?
- What guarantees do structured outputs provide?
- How would you evaluate faithfulness without trusting the generator to grade itself?
- What is the operational cost of a second service?
