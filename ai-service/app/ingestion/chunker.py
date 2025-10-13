import re

from app.ingestion.models import ParsedDocument

_SENTENCE_END = re.compile(r"(?<=[.!?])\s+")


def split_section(text: str, max_chars: int) -> list[str]:
    """Pack paragraphs into chunks of at most max_chars. A chunk never crosses a section,
    so every chunk has one exact location to cite."""
    pieces: list[str] = []
    for paragraph in (p.strip() for p in text.split("\n\n")):
        if not paragraph:
            continue
        if len(paragraph) <= max_chars:
            pieces.append(paragraph)
        else:
            pieces.extend(_split_long(paragraph, max_chars))

    chunks: list[str] = []
    current = ""
    for piece in pieces:
        candidate = f"{current}\n\n{piece}" if current else piece
        if len(candidate) <= max_chars:
            current = candidate
        else:
            chunks.append(current)
            current = piece
    if current:
        chunks.append(current)
    return chunks


def _split_long(paragraph: str, max_chars: int) -> list[str]:
    out: list[str] = []
    current = ""
    for sentence in _SENTENCE_END.split(paragraph):
        while len(sentence) > max_chars:
            # A single huge "sentence": cut at the last space before the limit
            cut = sentence.rfind(" ", 0, max_chars)
            cut = cut if cut > 0 else max_chars
            if current:
                out.append(current)
                current = ""
            out.append(sentence[:cut].strip())
            sentence = sentence[cut:].strip()
        candidate = f"{current} {sentence}" if current else sentence
        if len(candidate) <= max_chars:
            current = candidate
        else:
            out.append(current)
            current = sentence
    if current:
        out.append(current)
    return out


def chunk_document(doc: ParsedDocument, max_chars: int) -> list[tuple[str, str]]:
    """Returns (section, text) pairs in document order."""
    result: list[tuple[str, str]] = []
    for section in doc.sections:
        for text in split_section(section.text, max_chars):
            result.append((section.heading, text))
    return result
