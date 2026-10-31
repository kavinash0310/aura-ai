# AURA AI

AURA AI is an evidence-driven research and intelligence workspace. It gathers public-source information, organizes documents and evidence, answers questions with traceable citations, and supports a defensive cybersecurity intelligence workflow.

The project is also a practical engineering learning path for its owner: a Java/Spring backend, a focused Python AI service, React/TypeScript UI, and PostgreSQL, added incrementally with explanations, tests, and architecture records.

## Product boundaries

- Public research searches configured, permitted sources. It does not promise access to every page or post on the internet.
- Research results preserve source links, retrieval times, and coverage limits. Claims and model interpretations stay distinguishable.
- Person-focused research is limited to public-interest information; no doxxing, invasive profiling, sensitive-trait inference, or private-account access.
- Cybersecurity features are defensive and informational. Active checks require explicit scope and approval; exploit execution and unrestricted system access are outside initial scope.
- Retrieved pages and documents are untrusted input. The model cannot grant itself permissions or execute arbitrary commands.

## Initial engineering direction

- Java 21 + Spring Boot for application APIs, persistence, validation, and security.
- Python + FastAPI for AI workflows once a separate service is justified.
- PostgreSQL for the system of record and initial full-text search; add vector search only after evaluating a real retrieval need.
- React + TypeScript for the workspace UI, introduced after core API contracts settle.
- Local-first development on Windows with 16 GB RAM; hosted model providers remain optional.
- A modular monolith first. Redis, Kafka, Kubernetes, dedicated vector databases, and multi-agent orchestration are deferred until evidence justifies them.

## Project status

This repository was empty at the start of the project. The first milestone is the product and architecture baseline plus a small, runnable Spring Boot foundation. Features are added in reviewable stages; the status documents distinguish implemented work from proposals.

## Start here

- [Product requirements](docs/00-overview/PRODUCT-REQUIREMENTS.md)
- [MVP and backlog](docs/00-overview/MVP-AND-BACKLOG.md)
- [System architecture](docs/02-architecture/SYSTEM-ARCHITECTURE.md)
- [Technology decisions](docs/02-architecture/TECHNOLOGY-DECISIONS.md)
- [Learning roadmap](docs/04-learning/AURA-LEARNING-ROADMAP.md)
- [Implementation status](docs/04-learning/AURA-IMPLEMENTATION-STATUS.md)
- [Progress tracker](docs/04-learning/AURA-PROGRESS.md)

## Development

The implementation setup and exact prerequisites will be documented alongside the first backend milestone. Never commit real credentials. See `.env.example` for configuration names once the database-backed API is in place.

## Learning agreement

Codex-generated code is not evidence that the owner has mastered the concept. Every milestone includes prerequisite material, implementation notes, review questions, and an explanation exercise. Progress is recorded only after the owner can explain or modify the feature independently.