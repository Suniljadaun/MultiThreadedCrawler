# ADR-007: Embedding and LLM provider abstraction

Status: Accepted

## Context
The research assistant needs embeddings and text generation. The plan says the provider must be configurable,
no API keys in git, and tests must be deterministic. The project should also run on a laptop with no paid API.

## Decision
- Two small interfaces: `EmbeddingClient.embed(texts)` and `LLMClient.complete(system, user)`.
  Only `app/services.py` knows which implementation is used, chosen by environment variables.
- Default embeddings: `hash` (feature hashing of words and word pairs, 512 dims). Deterministic, offline, no download.
- Default generation: `extractive` (copies the best matching source sentences). No LLM call.
- Real models through one OpenAI-compatible HTTP client, which covers OpenAI and a local Ollama with the same code.
- LLM output is never trusted blindly: citations and numbers are checked against the sources (docs/rag.md).

## Alternatives
- sentence-transformers in-process: good semantic embeddings, but pulls in PyTorch (large install, slow CI).
- Provider SDKs (openai, anthropic packages): more features, but one SDK per provider for two HTTP calls.
- LangChain / LlamaIndex: fast to start, but hides the retrieval and citation logic this project wants to show.

## Consequences
- The offline baseline is lexical: it matches words, not meaning. The evaluation shows where that fails.
- Switching embedding model changes vector size and meaning, so documents must be re-ingested.
- Other providers need a new class implementing the same interface.
