"""Ingest every .md / .txt file in a folder into the configured vector store.

    python -m app.ingestion.cli ../test-data/documents
"""
import argparse
from pathlib import Path

from app.config.settings import get_settings
from app.services import build_services


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("folder", type=Path)
    args = parser.parse_args()

    files = sorted(args.folder.glob("*.md")) + sorted(args.folder.glob("*.txt"))
    if not files:
        raise SystemExit(f"no .md or .txt files in {args.folder}")

    services = build_services(get_settings())
    try:
        for path in files:
            result = services.ingestion.ingest(source=path.name, content=path.read_text(encoding="utf-8"))
            state = "unchanged" if result.unchanged else "ingested"
            print(f"{state:9} {result.document_id} ({result.chunks} chunks)")
    finally:
        services.close()


if __name__ == "__main__":
    main()
