# API contracts

Base path: /api/v1. JSON uses camelCase. Errors use RFC 9457-style Problem Details. IDs are UUIDs and timestamps ISO-8601 UTC.

## Implemented M1 routes

### GET /api/v1/health
Public liveness response: { "status": "UP", "service": "aura-api" }.

### POST /api/v1/research-jobs
Requires local HTTP Basic authentication.

Request: { "subject": "Example public topic", "mode": "GENERAL" }

Validation: subject is nonblank and at most 300 characters; mode is required and is GENERAL or CYBERSECURITY.

Response 201 with Location header:
{ "id": "uuid", "subject": "Example public topic", "mode": "GENERAL", "status": "REQUESTED", "createdAt": "2026-10-31T00:00:00Z" }

### GET /api/v1/research-jobs/{id}
Requires authentication. Returns the response fields above; returns 404 Problem Details if the UUID is unknown.

A REQUESTED job is a persisted request only. M1 does not search sources.

## Planned contract rules

- Every request has a server-generated request ID; propagate it to downstream AI calls.
- Do not accept arbitrary tool names or active target URLs from clients.
- Use idempotency keys before automatic retry can repeat side effects.
- Use cursor pagination for large collections.
- Contract changes are documented. Java/Python uses versioned JSON schemas and explicit timeout behavior.
- Export routes enforce owner access and source-specific data-use rules.
- Hosted-model use is visible in response and report provenance.

## Future routes (not implemented)

- GET /api/v1/research-jobs — owner-scoped history.
- POST /api/v1/research-jobs/{id}/run — schedule bounded workflow.
- POST /api/v1/documents — selected document upload.
- POST /api/v1/knowledge/search — owner-filtered full-text retrieval.
- GET /api/v1/reports/{id}/export?format=md|pdf|json|csv.
- GET /api/v1/findings — defensive vulnerability findings.
