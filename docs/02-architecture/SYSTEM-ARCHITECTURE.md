# System architecture

## Current repository state

The M1 implementation is one Java/Spring Boot API with PostgreSQL. It persists research jobs but does not search the web, call an LLM, ingest documents, or run cyber checks.

## Target architecture

Keep a small number of justified deployables: React/TypeScript UI, Spring Boot application API, Python FastAPI AI service when AI-specific libraries/workloads justify it, and PostgreSQL. Java owns identity, permissions, persistence, workflow state, provenance, reports, and audit. Python owns model-facing transformations and retrieval experiments. Neither service executes arbitrary commands.

~~~mermaid
flowchart LR
  User --> Web[React and TypeScript workspace]
  Web --> Api[Spring Boot application API]
  Api --> Pg[(PostgreSQL)]
  Api --> Search[Provider adapters]
  Search --> Public[Public web, news, permitted social APIs]
  Api --> Docs[Selected document parsers]
  Docs --> Pg
  Api --> Workflow[Bounded research workflow]
  Workflow --> Py[Optional Python FastAPI AI service]
  Py --> Local[Local model provider]
  Py --> Hosted[Optional hosted provider]
  Workflow --> Reports[Cited reports and exports]
  Api --> Audit[Audit events and approvals]
  Audit --> Pg
~~~

The UI submits requests and displays evidence; Java owns durable state and policy; adapters contact configured sources; PostgreSQL stores jobs, provenance, documents, evidence, and findings; Python is a narrow model service; reports link back to stored evidence.

Arrows represent trust flow: requests pass Java authorization before external reads. Responses are normalized and stored with provenance. Only selected evidence reaches a model. Model output is validated and stored as interpretation. Reports do not replace evidence.

Why: Java matches the owner's backend strengths; Python concentrates AI libraries behind a small service boundary; PostgreSQL provides transactions and FTS without a separate data platform.

**Interview:** “Spring Boot owns the product API and durable workflow. Python is an optional AI boundary, not the authority for access control. PostgreSQL remains the system of record.”

## M1 container diagram

Only the current Spring API and PostgreSQL are implemented in M1. Python, UI, providers, and reports are future containers.

~~~mermaid
flowchart TB
  Owner[Single local owner] -->|HTTP Basic over loopback| Spring[Spring Boot API]
  Spring -->|JDBC| PostgreSQL[(PostgreSQL)]
  Spring -->|Flyway migration at startup| PostgreSQL
~~~

## Research request lifecycle

~~~mermaid
sequenceDiagram
  actor Owner
  participant API as Spring API
  participant Security as Spring Security
  participant Service as ResearchJobService
  participant DB as PostgreSQL
  Owner->>API: POST research request
  API->>Security: authenticate and authorize
  Security-->>API: authenticated owner
  API->>Service: validate and create REQUESTED job
  Service->>DB: insert row in transaction
  DB-->>Service: UUID and timestamp
  Service-->>API: response DTO
  API-->>Owner: 201 Created with Location
~~~

REQUESTED means only that AURA saved the user's intent. Search is not complete.

## Future document/RAG lifecycle

~~~mermaid
flowchart LR
  Upload[Explicit upload] --> Validate[Type, size, hash]
  Validate --> Extract[Parser adapter]
  Extract --> Normalize[Text and metadata]
  Normalize --> Chunk[Chunk with source offsets]
  Chunk --> Index[PostgreSQL full text]
  Index --> Retrieve[Owner-filtered retrieval]
  Retrieve --> Context[Selected evidence context]
  Context --> Model[Optional model]
  Model --> ValidateAnswer[Schema and citation check]
  ValidateAnswer --> Answer[Grounded answer]
~~~

Future flow only. Parsing, chunking, indexing, retrieval, and synthesis remain separate so failures and quality can be measured per stage. pgvector is deferred until semantic retrieval evaluation justifies it.

## Deployment target

Docker Compose runs PostgreSQL and the API, exposed only on loopback. Local model runtime is opt-in. Remote access requires separate auth, TLS, retention, and abuse review.
