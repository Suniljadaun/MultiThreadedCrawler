from app.ingestion.chunker import chunk_document, split_section
from app.ingestion.cleaner import clean
from app.ingestion.parser import parse_markdown
from tests.conftest import SAMPLE


def test_clean_normalises_line_endings_comments_and_blank_lines():
    text = "Title\r\n\r\n\r\n\r\nLine  \r\n<!-- hidden -->Revenue 1,200\x07"
    assert clean(text) == "Title\n\nLine\nRevenue 1,200"


def test_clean_keeps_words_and_numbers():
    assert clean("Margin was 18.5% (up 2.5 pts).") == "Margin was 18.5% (up 2.5 pts)."


def test_parse_uses_h1_as_title_and_builds_section_paths():
    doc = parse_markdown("# Report\n\nintro\n\n## Results\n\n### Software\n\nsw text\n\n## Risks\n\nrisk text", "fallback")
    assert doc.title == "Report"
    assert [(s.heading, s.text) for s in doc.sections] == [
        ("", "intro"),
        ("Results > Software", "sw text"),
        ("Risks", "risk text"),
    ]


def test_parse_without_h1_uses_fallback_title():
    assert parse_markdown("just text", "my-doc").title == "my-doc"


def test_parse_ignores_headings_inside_code_fences():
    doc = parse_markdown("# T\n\n## Real\n\n```\n## not a heading\n```", "f")
    assert [s.heading for s in doc.sections] == ["Real"]


def test_small_paragraphs_are_packed_into_one_chunk():
    assert split_section("one.\n\ntwo.\n\nthree.", max_chars=200) == ["one.\n\ntwo.\n\nthree."]


def test_chunks_respect_max_chars():
    text = "\n\n".join(f"Paragraph {i} " + "word " * 30 for i in range(10))
    chunks = split_section(text, max_chars=300)
    assert len(chunks) > 1
    assert all(len(c) <= 300 for c in chunks)
    # Nothing lost
    assert "".join(chunks).replace("\n", "").replace(" ", "") == text.replace("\n", "").replace(" ", "")


def test_long_paragraph_is_split_on_sentences_and_long_sentence_on_spaces():
    paragraph = "Short sentence. " + ("x" * 50 + " ") * 20
    chunks = split_section(paragraph, max_chars=200)
    assert all(len(c) <= 200 for c in chunks)
    assert chunks[0].startswith("Short sentence.")


def test_chunks_never_cross_sections():
    doc = parse_markdown(SAMPLE, "f")
    pieces = chunk_document(doc, max_chars=1000)
    assert [section for section, _ in pieces] == ["", "Revenue", "Risks"]
