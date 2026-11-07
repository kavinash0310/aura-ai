# System architecture

## Current repository state

The repository has an authenticated Java/Spring Boot API, PostgreSQL persistence, a Python FastAPI AI service, a local React workspace served through Nginx, bounded Brave and GDELT public-search adapters, read-only vulnerability intelligence, and a private document library. Research queries use one explicitly selected provider and persist source metadata with provider provenance. Vulnerability lookups send explicit CVE IDs to FIRST EPSS and match them against CISA KEV; users can separately request one or up to 25 exact-CVE NVD details. The SBOM endpoint accepts bounded CycloneDX JSON, parses it in memory, and enriches only the exact CVEs already declared in the file; it does not infer package-to-CVE matches or persist the upload. Selected UTF-8 text and Markdown files are stored in PostgreSQL and searched as exact phrases with character-offset citations. An explicit report action sends up to 20 selected search-result snippets to a configured local Ollama model and checks the returned source IDs. It does not fetch linked pages, connect directly to social accounts, include uploaded documents in inference, or probe systems.

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

The UI submits requests and displays evidence; Java owns durable state and policy; adapters contact configured sources; PostgreSQL stores jobs, provenance, documents, evidence, and findings; Python makes bounded model transformations; reports link back to stored evidence.

Arrows represent product trust flow: requests pass Java authorization before provider calls. Search responses are normalized and stored with provenance. Only selected evidence reaches the explicitly configured local model. Model output is schema-validated and shown as interpretation; it is not stored as verified evidence.

Why: Java matches the owner's backend strengths; Python concentrates AI libraries behind a small service boundary; PostgreSQL provides transactions and FTS without a separate data platform.

**Interview:** “Spring Boot owns the product API and durable workflow. Python is an optional AI boundary, not the authority for access control. PostgreSQL remains the system of record.”

## Current container and search flow

Spring, Python, and PostgreSQL services are implemented. The Spring API calls fixed research/vulnerability providers and, only for an explicit report request, makes an internal authenticated call to Python. Python calls only an explicitly configured local Ollama endpoint. The AI service has no database or research-provider access.

~~~mermaid
flowchart TB
  Owner[Single local owner] -->|loopback| Web[React workspace and Nginx]
  Web -->|same-origin HTTP Basic API requests| Spring[Spring Boot API]
  Spring -->|JDBC| PostgreSQL[(PostgreSQL)]
  Spring -->|Flyway migration at startup| PostgreSQL
  Spring -->|explicit query, selected provider| Search[Brave Search API or GDELT DOC API]
  Spring -->|exact CVE IDs only after lookup| CISA[CISA KEV data mirror]
  Spring -->|small exact-ID lookup| FIRST[FIRST EPSS API]
  Spring -->|selected source metadata only, on explicit report| Python[FastAPI AI service]
  Python -->|local endpoint, when enabled| Ollama[Local Ollama model]
  Owner -->|loopback health only| Python
~~~

Each search adapter uses a fixed HTTPS provider host, a short timeout, and an HTTP client that does not follow redirects. Brave applies strict SafeSearch and returns web/news/discussion results; GDELT returns only bounded public news metadata, and it has no API key. Both adapters keep only HTTP(S) result URLs, cap fields, strip URL fragments, and deduplicate URLs. AURA does not fetch returned URLs, so search does not turn provider result links into arbitrary server-side requests. Automatic selection prefers Brave when the owner configured a key, then falls back to GDELT; the UI also permits an explicit choice.

## Local document import and retrieval

The owner uploads one UTF-8 `.txt` or `.md` file to the authenticated API. Java validates the extension, encoded text, control characters, and 1 MB limit; normalizes line endings; calculates SHA-256; and stores the text and metadata in PostgreSQL. A PostgreSQL generated `simple` search vector and GIN index find and rank candidates; a case-insensitive literal phrase check preserves the API's exact-match behavior and citation offsets. H2 uses a portable phrase-match fallback. The browser previews escaped text and can download or delete it. No parser executes document content, and the current document workflow makes no external provider or model calls.

## Defensive CVE lookup flow

The authenticated owner submits one to 25 exact CVE IDs. Java normalizes and validates them, loads the CISA KEV catalog from CISA's maintained GitHub mirror (six-hour in-memory cache), and sends the IDs as a small lookup to FIRST EPSS. The providers are fixed in code, requests have bounded timeouts, provider failures are reported independently, and an expired KEV cache may be returned with a stale label. Results link to their sources and sort KEV-listed CVEs before descending EPSS estimates. This is a prioritization signal only; it does not establish that an asset is present or vulnerable. No asset scanning, exploit, or state-changing operation is performed.

## NVD detail flow

The owner clicks “Load NVD details” for one CVE already displayed in a vulnerability result or SBOM review. The authenticated Java API validates the exact identifier, checks its bounded in-memory cache, and if needed requests that single ID from NVD API 2.0 over fixed HTTPS. The adapter applies an eight-second timeout, sends an optional API key in a header, extracts bounded record fields, and returns source status. It never retries automatically or sends the SBOM itself. CPE applicability is displayed as source evidence and is not matched automatically to inventory. Results are memory-only and are included in exports only for the current report.

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
  participant Provider as Selected Brave or GDELT API
  participant DB as PostgreSQL
  Owner->>API: POST job/{id}/search with filters
  API->>Service: authenticate, validate, check provider configuration
  Service->>DB: lock REQUESTED job and mark SEARCHING
  Service->>Provider: send query and bounded search options
  Provider-->>Service: untrusted public result metadata
  Service->>Service: validate URLs, cap fields, deduplicate
  Service->>DB: save source metadata and mark COMPLETED
  API-->>Owner: source results with provider coverage note
  Owner->>API: GET filtered sources or export
  API->>DB: read saved metadata
  API-->>Owner: JSON, CSV, or Markdown download
~~~

The owner invokes each search explicitly. If the selected provider is disabled or lacks a required key, the request fails before the job leaves `REQUESTED`; provider failures are recorded as a stable failure code. Source content is not treated as verified fact, and search coverage is explicitly partial. GDELT indexes public global news and has a maximum three-month horizon; its discussion or article title metadata is not a direct social-feed search.

## Local cited report flow

~~~mermaid
sequenceDiagram
  actor Owner
  participant Web as React workspace
  participant Java as Authenticated Spring API
  participant DB as PostgreSQL
  participant Python as Token-protected FastAPI
  participant Model as Local Ollama
  Owner->>Web: Click Report after filtering results
  Web->>Java: POST job/{id}/report with up to 20 source IDs
  Java->>DB: Load completed job and verify every source belongs to it
  Java->>Python: Send query and bounded source metadata/snippets
  Python->>Model: Structured-output prompt; sources labeled untrusted
  Model-->>Python: Report content and source IDs
  Python->>Python: Validate schema and cited IDs against input set
  Python-->>Java: Report plus source links and fixed coverage warning
  Java-->>Web: Render report for this UI session
  Owner->>Web: Download Markdown if desired
~~~

No model request runs in the background. No uploaded document or page content is sent. Unknown citation IDs fail the request. Valid source IDs are only candidate citations; no page fetch or semantic-support check is currently performed.

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

Docker Compose runs PostgreSQL, the Spring API, the AI service, and the Nginx-served React UI, with host ports bound to loopback. Brave's API key is optional and must be supplied by the owner; GDELT public news search is enabled by default and may be disabled. Only an explicit search call sends a query to the selected provider. The Python report route requires the internal service token and has no configured model by default; if enabled, it calls only the local/private Ollama address validated by settings. Remote access requires separate auth, TLS, retention, and abuse review.
