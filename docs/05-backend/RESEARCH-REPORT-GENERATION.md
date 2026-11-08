# Bounded local research reports

## What this adds

An owner can ask AURA to draft a report from sources already saved in a completed research brief. The UI checks the first 20 results currently visible after filters by default and lets the owner change the selection. The report is generated only on click, appears in the current UI session, and can be downloaded as Markdown or as a structured JSON snapshot.

This is a first model integration slice. It uses selected provider snippets only; it does not include page snapshots explicitly read through the separate page-reading action, include the private document library, persist model prose, or establish that a linked page supports every generated claim.

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

## Contract and bounds

- Endpoint: authenticated `POST /api/v1/research-jobs/{id}/report`.
- Input: 1–20 unique source UUIDs from the specified completed job.
- Source data: ID, title, URL, domain, type, publisher, publication time, and provider description capped at 1,200 characters.
- Model output: bounded title, summary, 12 findings maximum, six limitations maximum, and exact source ID lists.
- Model response: Ollama's `/api/chat` endpoint with non-streaming JSON Schema output. See [Ollama chat API](https://docs.ollama.com/api/chat).
- Model endpoint: `http://localhost:11434`, `http://host.docker.internal:11434`, `http://ollama:11434`, or a private IP at port 11434. No redirect is followed.
- Report storage: none. The backend returns the result; the current browser page holds it in component state until navigation or reload; a download creates a separate user-controlled copy. The explicit JSON download includes job/query/provider/timing metadata, the candidate source metadata and snippets provided to the model, and the structured report response. It does not include page text.
- Export limitation: the JSON preserves the exact evidence snapshot and generated response, but not model weights, prompt/runtime configuration, or a guarantee of identical replay.

The Python service rejects source IDs not present in its request. That confirms referential integrity only, not that text is entailed by the cited page. The UI therefore calls the links candidate sources and displays a fixed warning that reports use provider snippets only; page snapshots read elsewhere in the UI are not included or independently verified.

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
- `ai-service/src/aura_ai_service/main.py`: request validation, Ollama call, structured-output validation, citation membership check.

## Testing and next evaluation

The backend API tests should cover authentication, source ownership, 20-source bounds, and the Java/Python request payload. Python tests cover internal-token rejection, disabled/unavailable model behavior, valid response construction, and invented-source rejection. UI tests cover explicit invocation, sent IDs, linked citations, Markdown/JSON download behavior, and JSON evidence metadata without page text.

The next AI quality work is a curated fixture set of source snippets with expected claims and source IDs. Measure citation membership and semantic support separately: membership is deterministic; semantic entailment needs human-reviewed examples and should not be presented as proven solely by another model's score.

## Learning and interview prompts

- Why does Java resolve source IDs instead of trusting text sent by the browser?
- What does structured output guarantee, and what does it not guarantee?
- Why is source-ID membership weaker than citation correctness?
- Why use `asyncio.to_thread` for the blocking standard-library HTTP client?
- What risk remains if a provider snippet contains prompt injection even though the model has no tools?
- Why does report generation fail visibly when Ollama is disabled instead of silently using a hosted service?
