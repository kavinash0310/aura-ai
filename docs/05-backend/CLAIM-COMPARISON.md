# Bounded claim comparison

## Purpose

An owner can enter one specific claim and ask AURA to compare it with up to 20 checked public search results after filtering. The UI checks the first 20 visible sources by default and lets the owner change that selection. This is a user-directed assessment of selected snippets, not a fact-check, a general-purpose search, a person profile, or a truth verdict. AURA does not generate claims, expand the search, fetch pages, or include private library documents for this action.

## Data flow

~~~mermaid
sequenceDiagram
  actor Owner
  participant UI as React UI
  participant API as Spring API
  participant DB as PostgreSQL
  participant AI as FastAPI AI service
  participant Ollama as Local Ollama
  Owner->>UI: Type claim and click Compare claim
  UI->>API: POST /research-jobs/{id}/compare with claim and source IDs
  API->>DB: Check job state and resolve source ownership
  API->>AI: Send claim and bounded metadata/snippets
  AI->>Ollama: Structured-output comparison request
  Ollama-->>AI: JSON assessment and evidence
  AI->>AI: Validate schema, stance consistency, citation IDs, and optional quote anchors
  AI-->>API: Assessment and selected-source links
  API-->>UI: Response with snippet-only coverage note
  UI-->>Owner: Render, or explicitly download Markdown/JSON
~~~

Spring requires an authenticated completed or partially completed job. It resolves every source ID against that job, rejects duplicate or foreign IDs, and caps each source description at 1,200 characters. The internal Python route requires the separate bearer token. Python has no database, web, shell, or search-provider tools. Inference uses the configured local Ollama model only; no hosted fallback is enabled.

## Contract and validation

- Authenticated endpoint: `POST /api/v1/research-jobs/{id}/compare`.
- Input: one nonblank user-written claim of at most 500 characters and one to 20 distinct checked source IDs from the specified saved job.
- Assessment: `SUPPORTED`, `CHALLENGED`, `MIXED`, or `INSUFFICIENT`, always relative to the supplied snippets.
- Evidence: at most eight items, each labeled `SUPPORTS`, `CHALLENGES`, `CONTEXT`, or `UNCLEAR`, with one to five exact source IDs and an optional short quote anchor.
- Output includes bounded explanation/limitations and citations built from the server's selected-source records.
- Python rejects malformed JSON, inconsistent assessment/evidence labels, and every cited ID absent from the request. A quote anchor must reference a source cited by that evidence item and match its supplied title or provider description after case and whitespace normalization; otherwise the response fails closed.

A matching quote is a text anchor to the saved provider result, not proof that the linked page currently contains it or that the excerpt entails the model's stance. Quotes are optional. The UI labels anchors as provider-snippet matches, links them to the matching candidate source, and preserves them in Markdown and JSON downloads.

Retrieved metadata and descriptions are untrusted. The prompt tells the local model to ignore instructions inside source content, use no outside knowledge, avoid inferring private or sensitive personal information, and treat missing evidence as inconclusive. These instructions and output validation reduce risk but cannot ensure model accuracy. A supported label does not prove the claim; the owner should open and review candidate sources.

The model receives only the user-entered claim and selected saved metadata/snippets after the explicit button action. It does not receive page text from the separate page-reading feature, account data, or uploaded documents. Results are transient in browser memory. Markdown and JSON downloads are explicit user actions; they contain the assessment, prompt version/available timing and token counts, and cited source metadata/snippets. AURA does not store comparison prose in the database. The AI service's completion logs record operation, configured model, prompt version, and available usage metrics without claim text, snippets, or model output.

The comparison prompt version is `claim-comparison-v2`. Prompt versions live in code and must change when the system prompt, output schema, or generation settings change. Ollama's `total_duration`, `prompt_eval_count`, and `eval_count` response fields provide generation duration and token counts when available; missing values remain null and do not fail the request. These usage fields do not identify an immutable model artifact or guarantee repeatable output.

## Code map

- `web/src/App.tsx`: explicit claim field, comparison result, candidate links, and downloads.
- `web/src/api.ts`, `web/src/types.ts`: typed client request and response.
- `backend/.../ResearchClaimComparisonController.java`: authenticated endpoint.
- `backend/.../ResearchClaimComparisonService.java`: job-state, source-ownership, bounds, and partial-coverage checks.
- `backend/.../AiReportClient.java`: internal bearer-token call to Python.
- `ai-service/src/aura_ai_service/main.py`: schema, local Ollama invocation, stance consistency, citation membership, optional quote-to-snippet matching, and coverage label.

## Remaining quality work

This validation checks whether a citation ID was selected and whether an optional quote appears in a saved provider title/description; it does not test semantic support. Curated human-reviewed examples, source-level reliability/context indicators, and model artifact tracking are still needed before using model assessments in consequential decisions. Search coverage is limited to the configured public providers and indexed results; AURA does not search the entire internet.
