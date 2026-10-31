# ADR 0002: PostgreSQL as system of record and FTS baseline

- Status: Accepted
- Date: 2026-10-31

## Context and problem
AURA needs durable user-scoped jobs, documents, sources, evidence, findings, and audit. RAG may need semantic search. The developer machine has 16 GB RAM.

## Options
PostgreSQL FTS; PostgreSQL plus pgvector; dedicated vector DB such as Qdrant, Weaviate, Milvus, or Chroma; FAISS; separate relational and search stores.

## Decision
Use PostgreSQL as system of record and establish FTS baseline. Add pgvector when curated evaluation demonstrates benefit. Do not add a dedicated vector database before measuring corpus, latency, filtering, and operational needs.

## Trade-offs
One DB simplifies startup, migrations, owner filters, and transactions. FTS may miss paraphrases. pgvector adds embedding lifecycle/index tuning but colocates metadata. Dedicated DB can scale specialized workload, with more services and consistency/access/backup burden. FAISS is useful for experiments, not a full evidence store.

## Consequences
Every retrieval change needs benchmark relevance labels, precision/recall, citation correctness, and latency. Record chunker and embedding model versions. Re-embedding is a controlled migration.

## Reconsider when
Measured volume, concurrency, quality, or tuning proves PostgreSQL insufficient.

## Learning and interview
Explain inverted indexes vs dense embeddings, similarity metrics, metadata filters, transaction consistency, and retrieval evaluation.
