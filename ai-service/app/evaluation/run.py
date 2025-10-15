"""Run the research evaluation set and write a JSON report.

    python -m app.evaluation.run                      # defaults: ../test-data, k=5
    python -m app.evaluation.run --k 3 --out eval-results/k3.json

Always uses an in-memory store so results do not depend on what is in the database.
Embedding and LLM providers come from the normal settings (.env).
"""
import argparse
import json
import platform
import sys
from datetime import datetime, timezone
from pathlib import Path

from app.config.settings import Settings
from app.evaluation.metrics import mean, percentile, precision_at_k, recall_at_k, reciprocal_rank
from app.retrieval.memory_store import InMemoryVectorStore
from app.services import build_services

ROOT = Path(__file__).resolve().parents[3]


def ingest_folder(services, folder: Path) -> list[str]:
    ids = []
    for path in sorted(folder.glob("*.md")) + sorted(folder.glob("*.txt")):
        source = path.relative_to(ROOT).as_posix() if path.is_relative_to(ROOT) else path.name
        ids.append(services.ingestion.ingest(source=source, content=path.read_text(encoding="utf-8")).document_id)
    return ids


def evaluate(settings: Settings, eval_file: Path, docs: Path, k: int) -> dict:
    services = build_services(settings, store=InMemoryVectorStore())
    ingest_folder(services, docs)
    dataset = json.loads(eval_file.read_text(encoding="utf-8"))

    rows = []
    for ex in dataset["examples"]:
        relevant = {(r["documentId"], r["section"]) for r in ex["relevant"]}
        retrieval = services.retriever.retrieve(ex["question"], k)
        ranked = [(c.chunk.document_id, c.chunk.section) for c in retrieval.candidates]
        response = services.answers.answer(ex["question"], k)

        row = {
            "id": ex["id"],
            "expect": ex["expect"],
            "answerType": response.answer_type,
            "retrievalLatencyMs": round(retrieval.latency_ms, 2),
            "generationLatencyMs": response.generation.latency_ms if response.generation else None,
            "topScore": round(retrieval.candidates[0].score, 4) if retrieval.candidates else None,
            "retrieved": [f"{d} | {s}" for d, s in ranked],
        }
        if relevant:
            row["recall"] = recall_at_k(ranked, relevant, k)
            row["precision"] = precision_at_k(ranked, relevant, k)
            row["rr"] = reciprocal_rank(ranked, relevant)
        if ex["mustContain"]:
            answer = response.answer.lower()
            row["factsFound"] = all(f.lower() in answer for f in ex["mustContain"])
        rows.append(row)

    answerable = [r for r in rows if r["expect"] == "answer"]
    unanswerable = [r for r in rows if r["expect"] == "insufficient_evidence"]
    advice = [r for r in rows if r["expect"] == "out_of_scope"]
    retrieval_ms = [r["retrievalLatencyMs"] for r in rows if r["expect"] != "out_of_scope"]
    generation_ms = [r["generationLatencyMs"] for r in rows if r["generationLatencyMs"] is not None]

    summary = {
        f"recall@{k}": round(mean([r["recall"] for r in answerable]), 4),
        f"precision@{k}": round(mean([r["precision"] for r in answerable]), 4),
        "mrr": round(mean([r["rr"] for r in answerable]), 4),
        "answeredWhenAnswerable": _share(answerable, lambda r: r["answerType"] in ("generated", "extractive")),
        "answerContainsExpectedFacts": _share(answerable, lambda r: r.get("factsFound", False)),
        "abstainedWhenUnanswerable": _share(unanswerable, lambda r: r["answerType"] == "insufficient_evidence"),
        "declinedAdvice": _share(advice, lambda r: r["answerType"] == "out_of_scope"),
        "retrievalLatencyMsP50": percentile(retrieval_ms, 50),
        "retrievalLatencyMsP95": percentile(retrieval_ms, 95),
        "generationLatencyMsP50": percentile(generation_ms, 50),
        "generationLatencyMsP95": percentile(generation_ms, 95),
    }
    return {
        "runAt": datetime.now(timezone.utc).isoformat(),
        "environment": {
            "python": sys.version.split()[0],
            "platform": platform.platform(),
            "embeddingModel": services.embedder.model,
            "llmProvider": settings.llm_provider,
            "llmModel": settings.llm_model if settings.llm_provider != "extractive" else None,
        },
        "config": {"k": k, "minScore": settings.min_score, "chunkMaxChars": settings.chunk_max_chars,
                   "evalFile": eval_file.name, "evalVersion": dataset.get("version"), "examples": len(rows)},
        "summary": summary,
        "examples": rows,
    }


def _share(rows: list[dict], ok) -> str:
    return f"{sum(1 for r in rows if ok(r))}/{len(rows)}"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--eval", type=Path, default=ROOT / "test-data/eval/research-eval.json")
    parser.add_argument("--docs", type=Path, default=ROOT / "test-data/documents")
    parser.add_argument("--k", type=int, default=5)
    parser.add_argument("--out", type=Path, default=Path("eval-results/latest.json"))
    args = parser.parse_args()

    report = evaluate(Settings(vector_store="memory"), args.eval, args.docs, args.k)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(report, indent=2), encoding="utf-8")

    print(f"Embedding: {report['environment']['embeddingModel']}  LLM: {report['environment']['llmProvider']}")
    for name, value in report["summary"].items():
        print(f"  {name:32} {value}")
    print(f"Report: {args.out}")


if __name__ == "__main__":
    main()
