import re

from app.ingestion.models import ParsedDocument, Section

_HEADING = re.compile(r"^(#{1,6})\s+(.+?)\s*#*\s*$")


def parse_markdown(text: str, fallback_title: str) -> ParsedDocument:
    """Split markdown (or plain text) into sections by heading. H1 is the title, H2+ build the section path."""
    title = None
    stack: list[tuple[int, str]] = []
    sections: list[Section] = []
    buffer: list[str] = []
    in_fence = False

    def flush() -> None:
        body = "\n".join(buffer).strip()
        if body:
            sections.append(Section(" > ".join(h for _, h in stack), body))
        buffer.clear()

    for line in text.split("\n"):
        if line.lstrip().startswith("```"):
            in_fence = not in_fence
            buffer.append(line)
            continue
        match = None if in_fence else _HEADING.match(line)
        if not match:
            buffer.append(line)
            continue
        flush()
        level, heading = len(match.group(1)), match.group(2).strip()
        if level == 1 and title is None:
            title = heading
            stack.clear()
            continue
        while stack and stack[-1][0] >= level:
            stack.pop()
        stack.append((level, heading))
    flush()

    return ParsedDocument(title=title or fallback_title, sections=sections)
