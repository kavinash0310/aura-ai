# ADR 0003: Deterministic workflow before autonomous loops

- Status: Accepted
- Date: 2026-10-31

## Context
Research follows a known sequence: validate, search, fetch, extract, deduplicate, compare, synthesize, report. Retrieved instructions are untrusted.

## Decision
Implement named persisted steps and typed tools. Models may propose bounded plans, but deterministic policy validates each operation. No shell, arbitrary URL fetch, unrestricted browser, or general-purpose executor. Start with one orchestrator.

## Trade-offs
Determinism is less flexible than an autonomous loop but easier to audit, test, retry, and explain. LangGraph may later justify itself for durable branching/checkpoint state. LangChain/LlamaIndex may help integrations but should be compared with direct SDKs.

## Consequences
Persist step state and errors. Add idempotency before retries can repeat side effects. Require approval for future active checks. Model output cannot expand grants.

## Reconsider when
A demonstrated workflow needs branching, pause/resume, or human-in-loop state beyond a small state machine.
