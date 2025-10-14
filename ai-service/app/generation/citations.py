import re
from dataclasses import dataclass

_CITATION = re.compile(r"\[(\d{1,2})\]")
_NUMBER = re.compile(r"\d[\d,]*(?:\.\d+)?")


@dataclass(frozen=True)
class CheckedAnswer:
    text: str
    cited: list[int]  # valid source numbers, in order of first use
    invalid: list[int]  # numbers that point to no source; removed from text
    unsupported_numbers: list[str]  # numbers in the answer found in no cited source


def _numbers(text: str) -> set[str]:
    return {n.replace(",", "").rstrip(".") for n in _NUMBER.findall(text)}


def check_answer(answer: str, source_texts: list[str], question: str) -> CheckedAnswer:
    """Validate an LLM answer against the sources it was given.

    - citations must point to a real source number, invalid ones are stripped
    - every number in the answer must appear in a cited source (or in the question)
    """
    valid_range = range(1, len(source_texts) + 1)
    cited: list[int] = []
    invalid: list[int] = []
    for n in (int(m) for m in _CITATION.findall(answer)):
        target = cited if n in valid_range else invalid
        if n not in target:
            target.append(n)

    text = _CITATION.sub(lambda m: m.group(0) if int(m.group(1)) in valid_range else "", answer)
    text = re.sub(r"[ \t]{2,}", " ", text).strip()

    evidence = set().union(*(_numbers(source_texts[n - 1]) for n in cited)) if cited else set()
    allowed = evidence | _numbers(question)
    # Citation markers themselves are not facts
    unsupported = sorted(_numbers(_CITATION.sub("", text)) - allowed)
    return CheckedAnswer(text, cited, invalid, unsupported)
