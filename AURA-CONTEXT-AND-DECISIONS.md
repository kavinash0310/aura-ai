# AURA context and decisions

## Repository inspection

The repository was empty when inspected: no source files, documentation, instructions, ADRs, or commit history existed. A project README was added to establish the brief. The implementation branch is codex/phase-0-foundation; no previous code was available to preserve.

## Context carried into this repository

Available AURA planning described a personal, local-first cybersecurity intelligence assistant. A later clarification broadened it into a public-source research assistant: search for a person, country, organization, event, or topic across configured web, news, and public social sources; filter findings; and download cited results. The attached mission adds an engineering education goal and asks the architecture to align with a Java/Spring backend career path.

Useful decisions to retain:

- Evidence and source provenance are core product data. Reports link claims to retrieved sources and show timestamps and uncertainty.
- Public research and cybersecurity intelligence share collection, normalization, retrieval, evidence, and reporting capabilities.
- Connectors and targets require explicit scope. Retrieved content is untrusted.
- A local-first, single-owner initial release is appropriate; owner scoping supports future multi-user isolation.
- A bounded workflow with policy checks is safer and easier to explain than unrestricted autonomous agents.
- CVE severity, KEV membership, EPSS, asset context, and confidence remain visible as separate risk inputs.
- Local inference is an option; hosted model calls are explicit data egress.

## Decisions refined by the mission

| Earlier direction | Updated decision | Why |
|---|---|---|
| Python API as the first application backend | Java 21 and Spring Boot are the primary application API; Python FastAPI is a later, focused AI service | Uses the owner's established Java/Spring skills while adding meaningful Python/AI experience |
| Build research and security modules together | Start with a small Spring Boot foundation, then deliver document intelligence, AI, research, workflows, and cyber intelligence in separate milestones | Each change stays reviewable |
| Search “all the internet” | Search configured, permitted providers and show coverage limits | No service indexes every page or exposes all social history |
| Search a named person broadly | Limit to public-interest information, require identity disambiguation, and exclude invasive personal data or profiling | Avoids turning research into surveillance or doxxing |
| LLM agents as an advanced capability | Start with deterministic workflow; use an LLM only inside typed, policy-checked operations | Better reliability, control, and interview clarity |
| Vector database as a default RAG component | Compare PostgreSQL full-text search first; add pgvector when evaluation shows semantic retrieval is needed | Less infrastructure on a 16 GB developer machine |
| Security testing as broad capability | Keep initial cyber features informational; later checks must be fixed, scoped, safe, and approved | Fits defensive use |

## Scope decisions

**CORE:** cited public research; document ingestion and retrieval; reproducible reports; owner-scoped evidence; defensive vulnerability intelligence.

**CAREER:** Spring Boot API, persistence/migrations, Python service boundary, RAG evaluation, security, testing, observability, architecture records.

**OPTIONAL:** OCR, embeddings/reranking, saved searches, alerts, provider breadth, hosted deployment.

**DEFERRED:** Kafka, Redis, Kubernetes, dedicated vector DB, multi-agent systems, multi-tenancy, autonomous scans, dark-web research, unrestricted browser/shell/computer control.

## Open questions

1. Which web search provider has acceptable cost and permitted use?
2. Which social platforms have public search access suitable for the owner's use and budget?
3. What document volume and languages justify OCR, embeddings, or a separate worker?
4. What authentication is needed before remote access?
5. Does measured RAG quality justify pgvector after full-text search has a baseline?

## Implementation truth

Only repository contents and CI results count as implemented. Proposed architecture and roadmap items must be marked future work. The owner must demonstrate understanding before a learning item is marked complete.
