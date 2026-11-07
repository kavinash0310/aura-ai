# Technology decisions

“Not needed initially” is different from “not useful.”

## Java 21 and Spring Boot 4.1.2 — CORE / CAREER

**What/problem:** Java is the main application language; Spring Boot provides dependency injection, HTTP APIs, validation, persistence integration, and security conventions. It fits the owner's existing Java/Spring skills and builds backend depth. JDK 21 is selected for LTS support and broad compatibility; Boot 4.1.2 officially supports Java 17 through 27. Maven is familiar and widely used.

**Advantages:** mature ecosystem, explicit types, Spring Security/JPA, interview relevance. **Costs:** more setup and memory than a small Python API; Boot 4 modular starter changes require current docs.

**Alternatives:** Python-only FastAPI is appropriate for an AI-first prototype with fewer steps, but underuses Java experience. All-Java can call models and Spring AI may be sufficient for simple orchestration. Python is justified when model/retrieval libraries, evaluation, or data processing need Python. Reconsider the Python service if AI stays simple.

**Learn/interview:** JVM/runtime, DI, HTTP, validation, transactions, JPA/Hibernate, security filters, testing. Explain the flow through filter, dispatcher, controller, service, transaction, repository.

## Python 3 and FastAPI — CAREER / later

**What/problem:** Python supports model adapters, evaluation, embeddings, and common ML libraries. FastAPI uses type hints and Pydantic schemas to validate HTTP inputs and publish OpenAPI.

**Advantages:** AI ecosystem and quick iteration. **Costs:** second runtime, dependency graph, deployment, network failures, observability.

Alternatives: Spring AI is suitable if it covers needed models and structured output. Direct SDKs are simpler for one provider. LangChain has broad integrations but adds abstractions. LangGraph is deferred unless durable branching/checkpoint state exceeds a simple workflow. LlamaIndex may help with connectors/RAG but should be evaluated against direct code.

**Learn/interview:** Python typing, Pydantic, ASGI, async I/O, packaging, timeouts/retries. Explain why a process boundary costs more than an in-process library.

## PostgreSQL 18 — CORE

**What/problem:** System of record for jobs, provenance, documents, evidence, findings, audit. Relational constraints and transactions maintain consistency. Flyway manages schema; Hibernate validates it.

Start with full-text search. pgvector adds vector similarity and may complement FTS when evaluation shows semantic search benefit.

Alternatives: FAISS is in-process and useful for experiments, not a complete relational/permissioned store. Chroma simplifies local experiments. Qdrant, Weaviate, Milvus can serve larger/specialized workloads but add deployment, access, backup, and consistency costs. A dedicated DB is appropriate when measured scale or filtering needs justify it. MySQL is viable but offers no advantage for this product.

**Learn/interview:** indexes, query plans, transactions, isolation, FTS ranking. Explain inverted index vs dense vector retrieval.

## React and TypeScript — CORE / later

React builds the workspace; TypeScript catches contract mistakes. Add after API stabilizes. Server-rendered templates are simpler for a tiny interface; React improves frontend portfolio breadth.

## HTTP/JSON — CORE

Use REST/JSON for browser and Java/Python boundaries: inspectable and adequate for low-volume research. gRPC may fit high-throughput typed calls or streaming. Kafka/RabbitMQ solve durable async messaging and event fanout, but are unnecessary for the initial single app. Redis is useful for shared cache/rate limits/queues after DB-backed patterns prove insufficient.

## Docker Compose — CORE

Reproducible local PostgreSQL and API on Windows. Kubernetes helps multi-node orchestration but has no initial need. Add only with actual deployment requirements.

## Model choices

The first report path uses Ollama's local `/api/chat` with JSON Schema output behind the Python service. It is explicitly enabled and has no hosted fallback. Local inference is optional and memory-constrained at 16 GB; choose a small installed model based on measured latency and report quality. Do not train a foundation model. A multi-provider gateway, hosted model, prompt versioning, and evaluations follow only when a real requirement and test set exist. Fine-tuning is considered only after measured retrieval/prompt approaches fail.

**Ollama trade-offs:** Ollama provides a local HTTP interface for model inference and supports JSON or JSON Schema response formatting. This solves the initial report synthesis use case without transmitting evidence to a hosted provider. It consumes local memory and compute, model output quality varies, and the application must validate all model output. Alternatives include a direct hosted SDK (simpler operations but explicit data egress and cost) or Spring AI (appropriate if Java should own model calls and no Python AI-specific workload emerges). Reconsider the Python boundary if local model calls remain the only AI need.

## Official references checked 2026-10-31

- Spring Boot requirements/starters: https://docs.spring.io/spring-boot/system-requirements.html and https://docs.spring.io/spring-boot/reference/using/build-systems.html
- Java SE 21: https://docs.oracle.com/en/java/javase/21/docs/specs/
- PostgreSQL current: https://www.postgresql.org/docs/current/
- FastAPI tutorial: https://fastapi.tiangolo.com/tutorial/
- Ollama chat endpoint and JSON Schema response format (checked 2026-11-07): https://docs.ollama.com/api/chat
