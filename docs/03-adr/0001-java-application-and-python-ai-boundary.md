# ADR 0001: Java application API and focused Python AI boundary

- Status: Accepted for staged implementation
- Date: 2026-10-31

## Context
The owner has Java/Spring experience and is learning Python/AI. AURA needs durable APIs, permissions, persistence, and later AI workflows. A second service creates overhead.

## Problem
How can application and AI responsibilities be placed without building unnecessary microservices?

## Options considered
All Java using direct SDK or Spring AI; all Python/FastAPI; Java plus one Python AI service; many fine-grained services.

## Decision
Spring Boot is the product API and source of authority. Add one FastAPI service only when model integration, Python-specific retrieval, or evaluation justify it. Start with one Java deployable and PostgreSQL.

## Reasons and trade-offs
This uses existing backend strength while learning a useful AI boundary. HTTP/JSON is easy to inspect and contract-test. Costs include two runtimes, two dependency graphs, network failures, deployment, and duplicated observability/security. If Spring AI meets requirements, Python can be deferred. Service count is not a goal.

## Consequences
Java owns identity, authorization, workflow, persistence, audit, and credentials. Python receives minimized approved evidence and returns typed results; it cannot grant permission or call arbitrary tools. Versioned JSON, timeouts, bounded retries, and integration tests are required.

## Reconsider when
AI is simple enough for Spring AI; Python workload scales independently; or service overhead exceeds its value.

## Learning and interview
Explain DI, request lifecycle, HTTP serialization, timeout behavior, and the cost of process boundary vs library call.
