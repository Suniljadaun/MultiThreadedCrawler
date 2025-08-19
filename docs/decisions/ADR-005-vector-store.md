# ADR-005: pgvector for the vector store

Status: Accepted

## Context
The research assistant needs to store document chunk embeddings and search them by similarity.

## Decision
- Use PostgreSQL with the pgvector extension.
- The AI service uses its own schema (`research`) with `documents` and `document_chunks` tables.

## Alternatives
- Qdrant: built for vector search with more filtering and scaling features, but adds another service to run.
- In-memory (FAISS): fast, but data is lost on restart.

## Consequences
- One less service in Docker Compose.
- Chunk text, metadata and embeddings live together, so citations are easy to trace.
- If the dataset grows large, Qdrant can be reconsidered.
