# AURA AI

AURA AI is an evidence-driven research and intelligence workspace. It gathers public-source information, organizes documents and evidence, answers questions with traceable citations, and supports a defensive cybersecurity intelligence workflow.

This is also a practical AI software engineering learning path: a Java/Spring application API, a focused Python AI service, React/TypeScript UI, and PostgreSQL, delivered incrementally with explanations, tests, and architecture records.

## Product boundaries

- Public research searches configured, permitted sources. It does not promise access to every page or post on the internet.
- Reports preserve source links, retrieval times, coverage limits, and the difference between source claims and model interpretation.
- Person-focused research is for public-interest information; no doxxing, invasive profiling, sensitive-trait inference, or private-account access.
- Cybersecurity features are defensive and informational. Any future active checks require explicit scope and approval.
- Retrieved pages and documents are untrusted input. Models cannot grant themselves permissions or execute arbitrary commands.

## Initial stack

- Java 21 + Spring Boot for APIs, persistence, validation, and security.
- Python 3.12 + FastAPI for bounded AI workloads when useful.
- PostgreSQL for the system of record and initial full-text search; vector search follows measured need.
- React + TypeScript for the workspace UI after API contracts stabilize.
- Docker Compose for local Windows development.
- Modular monolith first. Redis, Kafka, Kubernetes, dedicated vector DB, and multi-agent orchestration are deferred until evidence justifies them.

## Current milestone

M1 stores research requests through the authenticated Spring API and PostgreSQL. M2 adds an isolated FastAPI service with validated configuration and a health endpoint. The AI service has no model, database, connector, or external-network capability yet. Research search, documents, synthesis, and exports remain future work.

See [implementation status](docs/04-learning/AURA-IMPLEMENTATION-STATUS.md) and [MVP backlog](docs/00-overview/MVP-AND-BACKLOG.md).

## Local development

Requirements: JDK 21, Docker Desktop with Compose, Git, Maven 3.9+ for local Java builds, and Python 3.12+ with uv 0.13.6 for local AI-service work.

1. Copy .env.example to .env and replace all placeholder values with unique random passwords of at least 24 characters.
2. Keep .env private and untracked.
3. Start the stack with docker compose up --build.
4. Spring health: http://127.0.0.1:8080/api/v1/health
5. AI-service health: http://127.0.0.1:8001/api/v1/health
6. Verify Java tests with mvn -f backend/pom.xml verify.
7. In a terminal, change to ai-service, then run uv sync --locked --all-groups, uv run pytest, and uv run ruff check src tests.

Both host ports bind to loopback. The Python health endpoint is public and returns no user data. The Spring API uses development-only Basic Auth. Do not expose or port-forward either service. See [local development guide](docs/19-operations/LOCAL-DEV.md).

## Start here

- [Product requirements](docs/00-overview/PRODUCT-REQUIREMENTS.md)
- [MVP and backlog](docs/00-overview/MVP-AND-BACKLOG.md)
- [Context and decisions](AURA-CONTEXT-AND-DECISIONS.md)
- [System architecture](docs/02-architecture/SYSTEM-ARCHITECTURE.md)
- [Technology decisions](docs/02-architecture/TECHNOLOGY-DECISIONS.md)
- [Threat model](docs/02-architecture/THREAT-MODEL.md)
- [Learning roadmap](docs/04-learning/AURA-LEARNING-ROADMAP.md)
- [Feature learning map](docs/04-learning/FEATURE-TO-LEARNING-MAP.md)
- [Progress tracker](docs/04-learning/AURA-PROGRESS.md)
- [Interview guide](docs/04-learning/AURA-INTERVIEW-GUIDE.md)

## Learning agreement

Generated code is not evidence of mastery. Each milestone includes prerequisites, review questions, and an explanation exercise. Progress is marked complete only after the owner can explain or modify the feature independently.

