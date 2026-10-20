#!/usr/bin/env python3
"""Extract the approved DOCX into docs/design.md for implementation agents.

The DOCX/PDF remain the authoritative artifacts. This Markdown mirror preserves
headings, prose, and tables in document order so coding agents can read the
approved specification without parsing Word XML.
"""

from __future__ import annotations

import re
from pathlib import Path

from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph

ROOT = Path(__file__).resolve().parent.parent
DOCX = ROOT / "docs" / "ProLean_Technical_Design_Document.docx"
OUT = ROOT / "docs" / "design.md"


def clean(text: str) -> str:
    text = text.replace("\u00a0", " ").replace("\t", " ")
    text = re.sub(r"[ ]{2,}", " ", text)
    return text.strip()


def table_to_md(table: Table) -> list[str]:
    rows: list[list[str]] = []
    for row in table.rows:
        cells = []
        seen = set()
        for cell in row.cells:
            key = id(cell._tc)
            if key in seen:
                continue
            seen.add(key)
            value = clean(cell.text).replace("|", "\\|").replace("\n", "<br>")
            cells.append(value)
        rows.append(cells)
    if not rows:
        return []
    width = max(len(r) for r in rows)
    rows = [r + [""] * (width - len(r)) for r in rows]
    lines = ["| " + " | ".join(rows[0]) + " |", "|" + "|".join(["---"] * width) + "|"]
    lines.extend("| " + " | ".join(r) + " |" for r in rows[1:])
    return lines


def main() -> None:
    doc = Document(str(DOCX))
    out: list[str] = [
        "# ProLean Technical Design (approved v1.1)",
        "",
        "> This Markdown file is a text extraction of the approved Word document "
        "(`ProLean_Technical_Design_Document.docx`, v1.1-draft, 20 October 2026). "
        "The DOCX/PDF remain authoritative. Diagrams are in `docs/diagrams/` and are "
        "embedded in the DOCX/PDF.",
        "",
        "**Status:** Approved by the user on 20 October 2026. Implementation may start.",
        "",
        "**Scope:** Four Spring Boot microservices (auth, user/project, task, timeline), "
        "PostgreSQL (one database per service), and a responsive React/TypeScript frontend. "
        "No API gateway is deployed in v1; Kong/APISIX/Traefik/Spring Cloud Gateway are "
        "compared as options only. Deployment is Docker Compose.",
        "",
        "---",
        "",
    ]

    body = doc.element.body
    paragraph_index = 0
    for child in body.iterchildren():
        if child.tag.endswith("}p"):
            paragraph = Paragraph(child, doc)
            style = paragraph.style.name if paragraph.style is not None else "Normal"
            text = clean(paragraph.text)
            if not text:
                continue
            if style.startswith("toc"):
                continue
            if style.startswith("Heading"):
                level = 1
                try:
                    level = int(style.split()[-1])
                except ValueError:
                    pass
                out.extend(["#" * min(level, 6) + " " + text, ""])
            elif style == "List Bullet":
                out.append("- " + text)
            elif style == "List Number":
                out.append("1. " + text)
            else:
                out.extend([text, ""])
            paragraph_index += 1
        elif child.tag.endswith("}tbl"):
            table = Table(child, doc)
            lines = table_to_md(table)
            if lines:
                out.extend(lines)
                out.append("")

    OUT.write_text("\n".join(out).rstrip() + "\n", encoding="utf-8")
    print(f"wrote {OUT} ({OUT.stat().st_size} bytes, {paragraph_index} paragraphs)")


if __name__ == "__main__":
    main()
