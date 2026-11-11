# Bounded local document question answering

## Purpose and limits

An owner can ask a question using up to five excerpts selected from the latest exact-phrase document search results. This is an explicit, local-model convenience for reviewing small passages. It does not run over complete documents, use semantic/vector retrieval, search public providers, or produce a verified answer.

## Data flow

~~~mermaid
sequenceDiagram
  actor Owner
  participant UI as React UI
  participant API as Spring API
  participant DB as PostgreSQL
  participant AI as FastAPI AI service
  participant Ollama as Local Ollama
  Owner->>UI: Search exact phrase in document library
  UI-->>Owner: Show bounded results and character/page citations
  Owner->>UI: Select up to five results, enter question, click Ask
  UI->>API: POST /api/v1/documents/answer with IDs and offsets
  API->>DB: Reload each immutable version
  API->>API: Recheck exact phrase at every submitted offset
  API->>AI: Send the question and bounded excerpts only
  AI->>AI: Redact patterns, validate IDs and quote anchors
  AI->>Ollama: Structured local inference
  Ollama-->>AI: Answer, evidence and limitations
  AI-->>API: Validated result and telemetry
  API-->>UI: Result with stable citations
  UI-->>Owner: Review or explicitly download Markdown/JSON
~~~

Spring owns document access and validates every selected ID against the stored version and exact phrase offsets. It constructs at most 700 characters around a match, bounded to the same PDF page. The API accepts at most five distinct document version IDs. The AI service has no database or document-store access; the internal bearer token protects the request. The model route is disabled unless local Ollama is explicitly enabled, and no hosted fallback exists.

Document text is untrusted. The prompt directs the model to ignore instructions found inside excerpts, use no outside knowledge, say when evidence is insufficient, and return a short quote per evidence item. Before inference the service redacts common credential patterns from the question, metadata, and excerpts. It rejects source IDs that were not submitted and quote anchors that do not appear in the redacted excerpt. These checks confirm which excerpt was quoted, not whether the answer follows from it. The response includes a coverage note, limitations, and version/page/character citations. It is not persisted; Markdown or JSON is created only when the owner downloads it.

## Contract and bounds

- Authenticated endpoint: `POST /api/v1/documents/answer`.
- Input: one question up to 500 characters, the exact phrase used for the search, and one to five distinct result IDs with their match offsets.
- Spring checks the phrase against the immutable stored version and fails if the submitted offsets no longer match.
- Context is at most 700 Java characters per source and does not cross a PDF page boundary.
- Output includes one bounded answer, at most eight evidence entries, one exact quote per evidence entry, citations, limitations, local model counters, and redaction summary.
- The result exists only in browser memory until the owner explicitly exports it.

## Code map

- `web/src/App.tsx`: result selection, explicit question form, answer display, and downloads.
- `web/src/api.ts` and `web/src/types.ts`: request client and response schema.
- `backend/.../DocumentQuestionController.java`: authenticated route.
- `backend/.../ResearchDocumentQuestionService.java`: ownership via stored IDs, bounds, excerpt construction, and citations.
- `backend/.../AiReportClient.java`: internal bearer-token request to the AI service.
- `ai-service/src/aura_ai_service/main.py`: prompt, local inference, redaction, schema validation, quote and ID checks.

## Remaining quality work

The current retrieval is an exact phrase search, not semantic retrieval. Excerpt-level quote matching does not establish entailment or completeness. Evaluation with curated question-and-answer examples, semantic retrieval, document-level access isolation, and multi-user retention controls remain future work.
