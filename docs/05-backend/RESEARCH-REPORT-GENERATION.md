# Bounded local research reports

## What this adds

An owner can ask AURA to draft a report from sources already saved in a completed research brief. The UI checks the first 20 results currently visible after filters by default and lets the owner change the selection. The report is generated only on click, appears in the current UI session, and can be downloaded as Markdown or as a structured JSON snapshot.

This is a bounded model integration slice. It uses selected provider snippets only; it does not include page snapshots explicitly read through the separate page-reading action, include the private document library, persist model prose, or establish that a linked page supports every generated claim.

## Data flow

~~~mermaid
sequenceDiagram
  actor Owner
  participant UI as React UI
  participant API as Spring API
  participant DB as PostgreSQL
  participant AI as FastAPI AI service
  participant Ollama as Local Ollama
  Owner->>UI: Filter result list and click Report
  UI->>API: POST /research-jobs/{id}/report with source IDs
  API->>DB: Load job and resolve IDs within its source rows
  API->>AI: Send query and bounded source metadata/snippets
  AI->>Ollama: Structured-output chat request
  Ollama-->>AI: JSON report
  AI->>AI: Validate shape and source-ID membership
  AI-->>API: Report, source links, fixed coverage note
  API-->>UI: JSON response
  UI-->>Owner: Render linked report; download Markdown or JSON snapshot
~~~

Java remains the authority for user authentication, job state, and source ownership. Python has no database or search credentials. A separate random `AURA_AI_INTERNAL_TOKEN` protects the internal route. The Python service's model provider defaults to `disabled`; no hosted fallback is implemented.

The four current structured AI actions share a small `StructuredModelProvider` interface. Their routes retain the prompts, JSON schemas, input redaction, response validation, citations, and per-action generation bounds. The Ollama adapter owns the Ollama request format and translates its duration/token counters into provider-neutral completion metadata. Calls still run through the single-process model semaphore and the configured local endpoint allowlist. Adding another provider requires a separately reviewed adapter and explicit configuration; the interface does not enable hosted inference or grant a provider access to tools, files, or search.

## Contract and bounds

- Endpoint: authenticated `POST /api/v1/research-jobs/{id}/report`.
- Input: 1–20 unique source UUIDs from the specified completed job.
- Source data: ID, title, URL, domain, type, publisher, publication time, and provider description capped at 1,200 characters.
- Model output: bounded title, summary, 12 findings maximum, six limitations maximum, exact source ID lists, and an optional quote anchor of up to 240 characters per finding.
- Model response: Ollama's `/api/chat` endpoint with non-streaming JSON Schema output. See [Ollama chat API](https://docs.ollama.com/api/chat).
- Model endpoint: `http://localhost:11434`, `http://host.docker.internal:11434`, `http://ollama:11434`, or a private IP at port 11434. No redirect is followed.
- Report storage: none. The backend returns the result; the current browser page holds it in component state until navigation or reload; a download creates a separate user-controlled copy. The explicit JSON download includes job/query/provider/timing metadata, the candidate source metadata and snippets provided to the model, and the structured report response. It does not include page text.
- Export limitation: the JSON preserves the exact evidence snapshot and generated response, but not model weights, prompt/runtime configuration, or a guarantee of identical replay.

## Model-run audit metadata

Report, claim-comparison, and query-plan responses identify their prompt templates (`research-report-v3`, `claim-comparison-v3`, and `research-query-plan-v2`) and expose Ollama's total generation duration in milliseconds plus prompt and output token counts when the configured Ollama build returns them. The values come from Ollama's response fields [`total_duration`, `prompt_eval_count`, and `eval_count`](https://docs.ollama.com/api/chat). A missing or invalid metric is returned as `null` and does not fail report generation. The UI displays available values; JSON and Markdown downloads preserve the prompt version and available counters.

Before any of these local model calls, the AI service redacts common credential patterns from every text value in the request, including the user topic/claim, source title/description, publisher, and URL. It masks PEM private-key blocks, Bearer/Basic tokens, common provider-formatted API keys, and values assigned to credential-like names such as `api_key`, `access_token`, `password`, and `client_secret`. The response coverage note shows counts by category and the number of affected fields without repeating the matched values in that audit note. Quote validation runs against the redacted source text; citation links still come from the server's original source records. The detector is pattern-based and cannot guarantee that every secret is found; original user inputs remain available only to the local application and are not sent to hosted models.

The AI service writes a completion log with operation, configured model name, prompt version, duration, and token counts. It does not log the prompt, claim, source snippets, generated report, or generated comparison. These logs are operational telemetry, not a durable per-user audit database. Prompt versions are maintained in code and must be incremented when the system prompt, output schema, or generation settings change. They identify the template, not an immutable model artifact or a reproducibility guarantee.

The Python service rejects source IDs not present in its request. Findings may also include one short evidence quote. When present, Python checks that the quote matches the cited source's title or provider description after normalizing case and whitespace; unmatched quotes fail the report request. This is an exact-text anchor to the provider snippet, not proof that the linked page contains the text or that the quote supports the full finding. Quotes are optional because local models do not always reproduce them reliably. The UI labels them as provider-snippet matches and preserves them in Markdown and JSON downloads. The private document library and page snapshots read elsewhere are not included.

## Safety and failure behavior

- No request is made during search, upload, page load, or history reopen.
- AURA sends only the selected saved result metadata and public provider snippets. It never sends stored personal documents to this route.
- Retrieved source text is untrusted. The model has no tools, browser access, shell, credentials, or database connection.
- Missing model configuration returns a service-unavailable error. Invalid model JSON or invented citations fails the request rather than displaying a partial report.
- There are no automatic retries; Ollama receives one bounded request with a 90-second default timeout (maximum 180 seconds) and a 64 KiB response cap. The Java client times out slightly after the AI-service maximum.
- Model output is still untrusted. User review is required before relying on findings.

## Code map

- `web/src/App.tsx`: explicit action, report display, Markdown rendering, and structured JSON snapshot download.
- `web/src/api.ts`, `web/src/types.ts`: typed client contract.
- `backend/.../ResearchReportController.java`: authenticated route.
- `backend/.../ResearchReportService.java`: completed-job check, source ownership, field truncation, input construction.
- `backend/.../AiReportClient.java`: internal token and timeout-bound Java-to-Python call.
- `ai-service/src/aura_ai_service/config.py`: disabled-by-default model and local endpoint validation.
- `ai-service/src/aura_ai_service/main.py` and `redaction.py`: request validation, versioned prompt constants, pre-inference credential-pattern masking, output validation, citation membership, quote-to-redacted-snippet matching, and content-free usage logging.
- `ai-service/src/aura_ai_service/model_providers.py`: provider-neutral structured completion contract and the bounded, redirect-blocking Ollama adapter/transport.
- `ai-service/scripts/evaluate_reports.py` and `ai-service/evaluation/research-report-cases.jsonl`: offline synthetic checks for schema validity, citation membership, exact quote matching, and the boundary between those checks and semantic support.

## Testing and next evaluation

The backend API tests should cover authentication, source ownership, 20-source bounds, and the Java/Python request payload. Python tests cover internal-token rejection, disabled/unavailable model behavior, valid response construction, invented-source rejection, provider request mapping, usage-counter normalization, response-size bounds, and generation limits. UI tests cover explicit invocation, sent IDs, linked citations, Markdown/JSON download behavior, and JSON evidence metadata without page text.

CI runs `ai-service/scripts/evaluate_reports.py` against synthetic source/report fixtures. It checks output shape, source-ID membership, and exact quote anchors; one fixture has valid IDs and an exact quote while contradicting the source, to show that these checks do not establish semantic support. Semantic labels are fixture annotations, not model scores. Next, curate human-reviewed examples and measure semantic support separately; do not present another model's score as proof. The provider interface currently has only the local Ollama adapter; it is an extension seam, not evidence that additional model backends have been evaluated.

## Learning and interview prompts

- Why does Java resolve source IDs instead of trusting text sent by the browser?
- What does structured output guarantee, and what does it not guarantee?
- Why is source-ID membership weaker than citation correctness?
- Why use `asyncio.to_thread` for the blocking standard-library HTTP client?
- What risk remains if a provider snippet contains prompt injection even though the model has no tools?
- Why does report generation fail visibly when Ollama is disabled instead of silently using a hosted service?
