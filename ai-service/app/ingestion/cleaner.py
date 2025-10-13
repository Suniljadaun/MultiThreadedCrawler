import re
import unicodedata

_CONTROL = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_HTML_COMMENT = re.compile(r"<!--.*?-->", re.DOTALL)
_BLANK_LINES = re.compile(r"\n{3,}")


def clean(text: str) -> str:
    """Normalise text before parsing. Only removes noise, never changes words or numbers."""
    text = unicodedata.normalize("NFC", text)
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    text = _HTML_COMMENT.sub("", text)
    text = _CONTROL.sub("", text)
    text = "\n".join(line.rstrip() for line in text.split("\n"))
    return _BLANK_LINES.sub("\n\n", text).strip()
