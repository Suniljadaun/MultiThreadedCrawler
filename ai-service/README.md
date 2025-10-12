# ai-service

Research assistant for FinIntel: answers questions about stored documents and cites the chunks it used.
Design: [docs/rag.md](../docs/rag.md). Evaluation: [docs/ai-evaluation.md](../docs/ai-evaluation.md).

## Setup (Windows PowerShell)

Requires Python 3.11+ and the Postgres container from the root `docker compose up -d`.

```powershell
cd ai-service
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
copy .env.example .env
```

## Run

```powershell
python -m app.ingestion.cli ..\test-data\documents     # load the synthetic documents
uvicorn app.main:app --port 8000                        # API on http://localhost:8000, docs at /docs
```

## Test

```powershell
pytest                    # fast tests, no Docker
pytest -m integration     # real PostgreSQL + pgvector in Docker (Testcontainers)
python -m app.evaluation.run   # retrieval / answer evaluation, writes eval-results/latest.json
```

## Providers

Defaults need no API key and no model download: `hash` embeddings and `extractive` answers.
Set `EMBEDDING_PROVIDER=openai` / `LLM_PROVIDER=openai` to use any OpenAI-compatible API
(OpenAI, or a local Ollama at `http://localhost:11434/v1`). See `.env.example`.
Changing the embedding model or dimension needs a re-ingest (see docs/rag.md).
