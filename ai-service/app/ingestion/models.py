from dataclasses import dataclass, field
from datetime import datetime, timezone


@dataclass(frozen=True)
class Section:
    heading: str  # heading path, e.g. "Annual Report 2025 > Revenue"
    text: str


@dataclass(frozen=True)
class ParsedDocument:
    title: str
    sections: list[Section]


@dataclass(frozen=True)
class Chunk:
    document_id: str
    chunk_id: str  # "<document_id>:<index>", stable for the same content and settings
    index: int
    title: str
    source: str
    section: str
    text: str
    created_at: datetime = field(default_factory=lambda: datetime.now(timezone.utc))

    @property
    def location(self) -> str:
        # Markdown has no pages, so the location is the section path. Never invent page numbers.
        return f"section: {self.section}" if self.section else "section: (introduction)"
