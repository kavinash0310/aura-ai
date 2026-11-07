# System architecture

## Current repository state

The repository has an authenticated Java/Spring Boot API, PostgreSQL persistence, an isolated Python FastAPI health service, a local React workspace served through Nginx, a bounded public-search adapter, and read-only vulnerability intelligence. Research queries use the configured Brave Search API and persist source metadata. Vulnerability lookups send explicit CVE IDs to FIRST EPSS and match them against CISA KEV. The SBOM endpoint accepts bounded CycloneDX JSON, parses it in memory, and enriches only the exact CVEs already declared in the file; it does not infer package-to-CVE matches or persist the upload. The app does not crawl the whole web, fetch linked pages, connect directly to social accounts, synthesize reports with a model, ingest arbitrary documents, or probe systems.

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

Arrows represent target trust flow: requests pass Java authorization before external reads. Responses are normalized and stored with provenance. Only selected evidence reaches a model. Model output is validated and stored as interpretation. Reports do not replace evidence.

Why: Java matches the owner's backend strengths; Python concentrates AI libraries behind a small service boundary; PostgreSQL provides transactions and FTS without a separate data platform.

**Interview:** “Spring Boot owns the product API and durable workflow. Python is an optional AI boundary, not the authority for access control. PostgreSQL remains the system of record.”

## Current container and search flow

Spring, Python, and PostgreSQL services are implemented. Only the Spring API has outbound provider connections. The AI service exposes health and does not connect to Java, PostgreSQL, models, or external providers.

~~~mermaid
flowchart TB
  Owner[Single local owner] -->|loopback| Web[React workspace and Nginx]
  Web -->|same-origin HTTP Basic API requests| Spring[Spring Boot API]
  Spring -->|JDBC| PostgreSQL[(PostgreSQL)]
  Spring -->|Flyway migration at startup| PostgreSQL
  Spring -->|query only when configured and requested| Brave[Brave Search API]
  Spring -->|exact CVE IDs only after lookup| CISA[CISA KEV data mirror]
  Spring -->|small exact-ID lookup| FIRST[FIRST EPSS API]
  Owner -->|loopback health only| Python[FastAPI AI service]
~~~

The outbound adapter uses a fixed provider host, a short timeout, strict SafeSearch, and web/news/discussion result types. It keeps only HTTP(S) result URLs, caps text, strips URL fragments, and deduplicates URLs. AURA does not fetch returned URLs, so this search flow does not turn provider result links into arbitrary server-side requests.

## Defensive CVE lookup flow

The authenticated owner submits one to 25 exact CVE IDs. Java normalizes and validates them, loads the CISA KEV catalog from CISA's maintained GitHub mirror (six-hour in-memory cache), and sends the IDs as a small lookup to FIRST EPSS. The providers are fixed in code, requests have bounded timeouts, provider failures are reported independently, and an expired KEV cache may be returned with a stale label. Results link to their sources and sort KEV-listed CVEs before descending EPSS estimates. This is a prioritization signal only; it does not establish that an asset is present or vulnerable. No asset scanning, exploit, or state-changing operation is performed.

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

## Public search lifecycle (implemented)

~~~mermaid
sequenceDiagram
  actor Owner
  participant API as Spring API
  participant Service as Search workflow
  participant Brave as Configured Brave API
  participant DB as PostgreSQL
  Owner->>API: POST job/{id}/search with filters
  API->>Service: authenticate, validate, check provider configuration
  Service->>DB: lock REQUESTED job and mark SEARCHING
  Service->>Brave: send query and bounded search options
  Brave-->>Service: untrusted web/news/discussion result metadata
  Service->>Service: validate URLs, cap fields, deduplicate
  Service->>DB: save source metadata and mark COMPLETED
  API-->>Owner: source results with provider coverage note
  Owner->>API: GET filtered sources or export
  API->>DB: read saved metadata
  API-->>Owner: JSON, CSV, or Markdown download
~~~

The owner invokes each search explicitly. A missing provider key fails before the job leaves `REQUESTED`; provider failures are recorded as a stable failure code. Source content is not treated as verified fact, and search coverage is explicitly partial.

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

Docker Compose runs PostgreSQL, the Spring API, the AI service, and the Nginx-served React UI, with host ports bound to loopback. The search API key is optional and must be supplied by the owner; only an explicit search call sends a query to Brave. The Python service has no outbound integrations or model runtime. Remote access requires separate auth, TLS, retention, and abuse review.
