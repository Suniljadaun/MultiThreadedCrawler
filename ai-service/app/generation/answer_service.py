import logging
import time

from app.generation.base import LLMClient
from app.generation.citations import check_answer
from app.generation.extractive import extractive_answer
from app.generation.prompt import INSUFFICIENT_MARKER, SYSTEM_PROMPT, build_user_prompt
from app.generation.scope import is_personal_advice
from app.retrieval.retriever import RetrievalResult, Retriever
from app.retrieval.store import ScoredChunk
from app.schemas.research import GenerationInfo, QueryResponse, RetrievalInfo, Source

log = logging.getLogger(__name__)

INSUFFICIENT_TEXT = "I don't have enough retrieved evidence to answer this reliably."
OUT_OF_SCOPE_TEXT = (
    "I can't give personal investment advice. I can answer questions about what the stored documents say."
)
DISCLAIMER = "Educational research tool over synthetic documents. Not investment advice."


class AnswerService:
    def __init__(self, retriever: Retriever, llm: LLMClient | None, default_top_k: int):
        self._retriever = retriever
        self._llm = llm  # None = extractive mode, no LLM call
        self._default_top_k = default_top_k

    def answer(self, question: str, top_k: int | None = None) -> QueryResponse:
        if is_personal_advice(question):
            return QueryResponse(answer_type="out_of_scope", answer=OUT_OF_SCOPE_TEXT, sources=[],
                                 retrieval=None, generation=None, disclaimer=DISCLAIMER)

        k = top_k or self._default_top_k
        retrieval = self._retriever.retrieve(question, k)
        info = RetrievalInfo(top_k=k, candidates=len(retrieval.candidates), min_score=self._retriever.min_score,
                             latency_ms=round(retrieval.latency_ms, 2))
        if not retrieval.chunks:
            return self._insufficient(info, None)

        if self._llm is None:
            started = time.perf_counter()
            text, cited = extractive_answer(question, retrieval.chunks)
            generation = GenerationInfo(provider="extractive", model="none", latency_ms=_since(started))
            if not cited:
                return self._insufficient(info, generation)
            return self._response("extractive", text, cited, retrieval, info, generation)

        started = time.perf_counter()
        raw = self._llm.complete(SYSTEM_PROMPT, build_user_prompt(question, retrieval.chunks))
        generation = GenerationInfo(provider=self._llm.provider, model=self._llm.model, latency_ms=_since(started))

        if INSUFFICIENT_MARKER in raw:
            return self._insufficient(info, generation)
        checked = check_answer(raw, [c.chunk.text for c in retrieval.chunks], question)
        if checked.invalid:
            log.warning("LLM cited non-existent sources %s; they were removed", checked.invalid)
        if not checked.cited or checked.unsupported_numbers:
            # No usable citation, or numbers the sources don't contain: refuse rather than risk a made-up fact
            log.warning("LLM answer rejected: cited=%s unsupported_numbers=%s",
                        checked.cited, checked.unsupported_numbers)
            return self._insufficient(info, generation)
        return self._response("generated", checked.text, checked.cited, retrieval, info, generation)

    def _response(self, answer_type: str, text: str, cited: list[int], retrieval: RetrievalResult,
                  info: RetrievalInfo, generation: GenerationInfo) -> QueryResponse:
        sources = [_source(ref, retrieval.chunks[ref - 1]) for ref in sorted(cited)]
        return QueryResponse(answer_type=answer_type, answer=text, sources=sources, retrieval=info,
                             generation=generation, disclaimer=DISCLAIMER)

    @staticmethod
    def _insufficient(info: RetrievalInfo, generation: GenerationInfo | None) -> QueryResponse:
        return QueryResponse(answer_type="insufficient_evidence", answer=INSUFFICIENT_TEXT, sources=[],
                             retrieval=info, generation=generation, disclaimer=DISCLAIMER)


def _source(ref: int, scored: ScoredChunk) -> Source:
    c = scored.chunk
    return Source(ref=ref, document_id=c.document_id, chunk_id=c.chunk_id, title=c.title, location=c.location,
                  score=round(scored.score, 4), snippet=c.text)


def _since(started: float) -> float:
    return round((time.perf_counter() - started) * 1000, 2)
