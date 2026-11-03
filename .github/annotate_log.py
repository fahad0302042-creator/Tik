#!/usr/bin/env python3
"""Turn a Gradle log into GitHub Actions error annotations.

CI logs cannot be read from outside the runner in this setup, so the interesting
part of a failing build is republished as `::error::` annotations, which are
readable through the checks API.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

MAX_ANNOTATIONS = 12
CHUNK = 1000

INTERESTING = re.compile(
    r"error:|e: (file|/)|FAILED|FAILURE:|What went wrong|Caused by|Execution failed|"
    r"Could not (resolve|find|determine)|Cannot |No value has been specified|"
    r"Unresolved reference|None of the following",
    re.IGNORECASE,
)


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def collect(log: str) -> list[str]:
    lines = log.splitlines()

    picked: list[str] = []
    marker = next(
        (i for i, line in enumerate(lines) if "What went wrong" in line or "FAILURE:" in line),
        None,
    )
    if marker is not None:
        picked.extend(lines[marker:])

    for line in lines:
        if INTERESTING.search(line) and line.rstrip() not in picked:
            picked.append(line.rstrip())

    if not picked:
        picked = lines[-60:]

    # keep order, drop duplicates
    seen: set[str] = set()
    unique: list[str] = []
    for line in picked:
        if line in seen:
            continue
        seen.add(line)
        unique.append(line)
    return unique


def main() -> None:
    if len(sys.argv) < 2:
        print("usage: annotate_log.py <logfile>")
        return
    path = Path(sys.argv[1])
    if not path.is_file():
        print(f"::error::log file {path} was never created")
        return

    lines = collect(path.read_text(encoding="utf-8", errors="replace"))
    text = "\n".join(lines)
    chunks = [text[i : i + CHUNK] for i in range(0, len(text), CHUNK)][:MAX_ANNOTATIONS]
    if len(text) > CHUNK * MAX_ANNOTATIONS:
        chunks[-1] += "\n... (truncated)"
    for chunk in chunks:
        print(f"::error::{escape(chunk)}")


if __name__ == "__main__":
    main()
