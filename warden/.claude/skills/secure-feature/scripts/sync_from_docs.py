#!/usr/bin/env python3
"""Builds SKILL.md from the "Skill secure-feature" tab of the Warden doc on Claude Docs.

The skill is written and reviewed on Claude Docs, but Claude Code only loads skills from files.
After editing the tab:

  1. Export the tab as Markdown (Claude Docs `export` tool, or Download > Markdown in the app).
  2. python3 .claude/skills/secure-feature/scripts/sync_from_docs.py exported.md
  3. Commit .claude/skills/secure-feature/SKILL.md

Mapping
  frontmatter  = the tab's first ```yaml block (must contain name: and description:)
  body         = everything after that block, up to the heading "## Phụ lục" (notes for humans only)

  --check      exit 1 if SKILL.md differs from what the export would produce (drift detection)
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

SKILL_FILE = Path(__file__).resolve().parents[1] / "SKILL.md"
YAML_BLOCK = re.compile(r"```ya?ml[^\n]*\n(.*?)\n```", re.S)
STOP_HEADING = re.compile(r"^#{1,3}\s+Phụ lục", re.M)


def build(markdown: str) -> str:
    markdown = markdown.replace("\r\n", "\n")
    match = YAML_BLOCK.search(markdown)
    if not match:
        raise ValueError("no ```yaml block with the frontmatter was found in the export")
    front = match.group(1).strip()
    for required in ("name", "description"):
        if not re.search(rf"^{required}:\s*\S", front, re.M):
            raise ValueError(f"frontmatter is missing '{required}:'")
    name = re.search(r"^name:\s*(\S+)", front, re.M).group(1)

    rest = markdown[match.end():]
    stop = STOP_HEADING.search(rest)
    body = (rest[:stop.start()] if stop else rest).strip()
    if not body:
        raise ValueError("skill body is empty")

    return (f"---\n{front}\n---\n\n"
            f"<!-- Generated from the 'Skill {name}' tab of the Warden doc on Claude Docs by scripts/sync_from_docs.py. "
            f"Edit the tab, then re-sync; see CLAUDE.md. -->\n\n"
            f"# {name}\n\n{body}\n")


def main() -> int:
    parser = argparse.ArgumentParser(description="Build SKILL.md from a Claude Docs Markdown export.")
    parser.add_argument("export", type=Path, help="Markdown file exported from the Claude Docs skill doc")
    parser.add_argument("--out", type=Path, default=SKILL_FILE, help="target file (default: %(default)s)")
    parser.add_argument("--check", action="store_true", help="only report whether the target is in sync")
    args = parser.parse_args()

    try:
        content = build(args.export.read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        print(f"sync_from_docs: {exc}", file=sys.stderr)
        return 2

    if args.check:
        current = args.out.read_text(encoding="utf-8") if args.out.exists() else ""
        in_sync = current == content
        print("SKILL.md is in sync with the doc" if in_sync else "SKILL.md differs from the doc: run without --check")
        return 0 if in_sync else 1

    args.out.write_text(content, encoding="utf-8")
    print(f"Wrote {args.out} ({content.count(chr(10))} lines)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
