from app.retrieval.store import ScoredChunk

INSUFFICIENT_MARKER = "INSUFFICIENT_EVIDENCE"

SYSTEM_PROMPT = f"""You answer questions about financial documents using ONLY the numbered sources given.
Rules:
- Every sentence must end with at least one citation like [1] or [2][3] pointing to the sources it uses.
- Use only facts, numbers and names that appear in the sources. Do not use outside knowledge.
- Do not give investment advice or recommendations.
- If the sources do not contain enough evidence, reply with exactly: {INSUFFICIENT_MARKER}"""


def build_user_prompt(question: str, chunks: list[ScoredChunk]) -> str:
    blocks = [
        f"[{i}] {c.chunk.title} ({c.chunk.location})\n{c.chunk.text}"
        for i, c in enumerate(chunks, start=1)
    ]
    return "Sources:\n\n" + "\n\n".join(blocks) + f"\n\nQuestion: {question}\nAnswer:"
