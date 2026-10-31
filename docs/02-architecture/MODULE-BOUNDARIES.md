# Module boundaries

## Initial deployables

### AURA API — Java/Spring Boot
Owns local authentication, research jobs, connector grants, source metadata, documents, evidence references, report state, security policy, and audit. M1 implements health and research job create/read only.

### AI service — Python/FastAPI (future)
Owns AI-specific schemas, prompt/model adapters, embedding experiments, and evaluation hooks. It does not own users, permissions, source credentials, or workflow authority. Java passes minimized, approved evidence and receives typed results over HTTP.

### Web workspace — React/TypeScript (future)
Owns interaction and presentation. It does not store provider secrets or make authorization decisions.

### PostgreSQL
System of record. Begin with relational tables and full-text search. pgvector is an extension candidate after measurement.

## Java package boundary

- api: REST controllers, request/response schemas, exception mapping.
- application: use-case services and transaction boundaries.
- domain: research job lifecycle and policy concepts.
- persistence: JPA entities, repositories, Flyway migrations.
- security: authentication and request authorization.
- configuration: validated external settings.

Dependencies flow inward: controllers call application services; services enforce use cases; persistence implements repository interfaces. Controllers do not issue SQL and entities are not public API payloads.

## Python package boundary

Future modules: api, settings, model_gateway, prompts, schemas, evaluation, retrieval adapters. Provider SDK details stay behind a gateway interface.

## Communication

Start with HTTP/JSON between Java and Python because it is inspectable, testable, and sufficient for low-volume research. Use gRPC only if typed high-volume calls become a demonstrated need. Kafka/RabbitMQ are not required for a single-instance workflow.

## Ownership rule

Only Java policy code authorizes access to a connector, document, or target. AI service suggestions are untrusted. Calls carry request ID and contract version. Timeout or malformed output becomes workflow failure, never implicit approval.
