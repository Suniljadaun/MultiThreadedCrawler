import re

from app.embeddings.hash_embedding import tokenize
from app.retrieval.store import ScoredChunk

_SENTENCE_END = re.compile(r"(?<=[.!?])\s+")
_LIST_MARK = re.compile(r"^\s*(?:[-*+]|\d+\.)\s+")


def extractive_answer(question: str, chunks: list[ScoredChunk], max_sentences: int = 2) -> tuple[str, list[int]]:
    """No-LLM answer: the source sentences that share the most words with the question, copied verbatim.

    Returns the answer text with [n] citations and the cited source numbers.
    """
    wanted = set(tokenize(question))
    candidates = []  # (overlap, chunk rank, sentence order, sentence)
    for ref, scored in enumerate(chunks, start=1):
        for order, line in enumerate(_sentences(scored.chunk.text)):
            overlap = len(wanted & set(tokenize(line)))
            if overlap:
                candidates.append((-overlap, ref, order, line))

    if not candidates:
        return "", []
    picked = sorted(candidates)[:max_sentences]
    picked.sort(key=lambda c: (c[1], c[2]))  # keep document order for readability
    answer = " ".join(f"{sentence} [{ref}]" for _, ref, _, sentence in picked)
    cited = list(dict.fromkeys(ref for _, ref, _, _ in picked))
    return answer, cited


def _sentences(text: str) -> list[str]:
    out = []
    for line in text.split("\n"):
        line = _LIST_MARK.sub("", line).strip()
        if line and not line.startswith("|"):
            out.extend(s.strip() for s in _SENTENCE_END.split(line) if s.strip())
    return out
