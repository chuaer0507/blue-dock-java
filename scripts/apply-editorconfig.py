#!/usr/bin/env python3
"""按 .editorconfig 做机械格式化：LF、末尾换行、裁剪行尾空白（*.md 除外）。"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {".git", "target", "public", "data", "node_modules", ".idea", "bin", "dist"}
TEXT_EXTS = {
    ".java",
    ".xml",
    ".yml",
    ".yaml",
    ".sql",
    ".md",
    ".json",
    ".properties",
    ".sh",
    ".conf",
    ".txt",
    ".imports",
    ".example",
    ".http",
    ".csv",
    ".ts",
    ".tsx",
    ".js",
    ".jsx",
    ".css",
}
TEXT_NAMES = {".gitignore", ".gitattributes", ".editorconfig", "Makefile", "Dockerfile"}


def should_skip(path: Path) -> bool:
    return any(part in SKIP_DIRS for part in path.parts)


def is_candidate(path: Path) -> bool:
    name = path.name
    if name in TEXT_NAMES or name.startswith("Dockerfile"):
        return True
    if name.endswith(".env.example") or ".env." in name and name.endswith(".example"):
        return True
    return path.suffix.lower() in TEXT_EXTS


def format_text(text: str, *, trim_trailing: bool) -> str:
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    lines = text.split("\n")
    if lines and lines[-1] == "":
        lines = lines[:-1]
    out: list[str] = []
    for line in lines:
        if trim_trailing:
            line = line.rstrip(" \t")
        out.append(line)
    body = "\n".join(out)
    if body:
        body += "\n"
    return body


def main() -> int:
    changed = 0
    scanned = 0
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or should_skip(path.relative_to(ROOT)) or not is_candidate(path):
            continue
        try:
            raw = path.read_bytes()
        except OSError:
            continue
        if b"\0" in raw[:1024]:
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            print(f"skip non-utf8: {path.relative_to(ROOT)}", file=sys.stderr)
            continue
        scanned += 1
        trim = path.suffix.lower() != ".md"
        formatted = format_text(text, trim_trailing=trim)
        if formatted != text:
            path.write_text(formatted, encoding="utf-8", newline="\n")
            changed += 1
            print(f"updated: {path.relative_to(ROOT)}")
    print(f"apply-editorconfig: scanned={scanned} changed={changed}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
