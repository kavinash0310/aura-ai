# AURA architecture interview guide

## 90-second explanation

“AURA AI is a local-first research and intelligence workspace. Spring Boot owns authentication, request state, evidence provenance, and policy. PostgreSQL stores research jobs and later sources/documents. Research adapters search permitted public sources and normalize results. A focused Python service may handle model-specific work, but it receives only minimized evidence and cannot authorize access or execute arbitrary tools. Reports retain citations, timestamps, uncertainty, and coverage limits. I started with full-text search and deterministic workflows so I can measure quality before adding vector search or autonomous behavior.”

Only describe features actually implemented on the current branch.

## Questions to practice

### Architecture
1. Why modular monolith first, and when split?
2. Why Java application API and Python AI service?
3. What authority stays in Java?
4. How does HTTP request reach PostgreSQL?
5. What happens on Python timeout or malformed response?

### Data/retrieval
6. Why PostgreSQL system of record?
7. Compare FTS, pgvector, FAISS, dedicated vector DB.
8. Why are parse, chunk, embed, index, retrieve separate?
9. How is citation mapped to source/page/chunk?
10. How do you evaluate retrieval precision/recall?

### LLM
11. Why RAG rather than fine-tuning for current information?
12. How do you keep prompt injection from becoming tool authority?
13. How evaluate faithfulness/citation correctness?
14. How control tokens, latency, hosted cost, local memory?
15. Why not multiple agents?

### Reliability/security
16. Explain idempotency and retry risk.
17. Which errors are retriable?
18. How do grants, expiry, revocation work?
19. What prevents SSRF?
20. What changes before remote deployment?

### Cybersecurity
21. How differ CVSS, KEV, EPSS?
22. Why should an LLM not invent severity?
23. How express uncertain product matching?

## Exercise

Draw current M1 request flow and target RAG flow. For each, name one failure and the test that detects it.
