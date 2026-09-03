#!/usr/bin/env python3
"""Structural sanity check for Kotlin sources (no JDK in this sandbox).

It is *not* a Kotlin parser. It tokenizes strings, chars, comments and `${}`
interpolations well enough to catch the mistakes that actually break a build after a
large mechanical edit: unterminated literals, unbalanced braces/parens/brackets and
stray double quotes.
"""

from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def skip_interpolation(text: str, start: int):
    n = len(text)
    j = start + 1
    if j >= n or text[j] != "{":
        while j < n and (text[j].isalnum() or text[j] == "_"):
            j += 1
        return j
    depth = 1
    j += 1
    while j < n and depth:
        c = text[j]
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        elif c == '"':
            j = skip_string(text, j)
            continue
        elif c == "'":
            j += 1
            while j < n and text[j] != "'":
                if text[j] == "\\":
                    j += 1
                j += 1
        j += 1
    return j


def skip_string(text: str, i: int) -> int:
    """i points at the opening quote; return the index just past the closing quote."""
    n = len(text)
    if text.startswith('"""', i):
        j = i + 3
        while j < n:
            if text.startswith('"""', j):
                return j + 3
            if text[j] == "$":
                j = skip_interpolation(text, j)
                continue
            j += 1
        return n
    j = i + 1
    while j < n:
        c = text[j]
        if c == "\\":
            j += 2
            continue
        if c == '"':
            return j + 1
        if c == "$":
            j = skip_interpolation(text, j)
            continue
        if c == "\n":
            return j  # unterminated on this line
        j += 1
    return n


def check(path: Path):
    text = path.read_text(encoding="utf-8")
    n = len(text)
    i = 0
    stack = []
    line = 1
    problems = []
    pairs = {"}": "{", ")": "(", "]": "["}
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            if j < 0:
                problems.append(f"line {line}: unterminated block comment")
                break
            line += text.count("\n", i, j)
            i = j + 2
            continue
        if c == '"':
            before = line
            j = skip_string(text, i)
            line += text.count("\n", i, j)
            if j >= n or (text[j - 1] != '"' and not text.startswith('"""', i)):
                problems.append(f"line {before}: unterminated string literal")
                i = j
                continue
            i = j
            continue
        if c == "'":
            j = i + 1
            while j < n and text[j] != "'":
                if text[j] == "\\":
                    j += 1
                if text[j] == "\n":
                    break
                j += 1
            if j < n and text[j] == "'":
                i = j + 1
                continue
            problems.append(f"line {line}: unterminated char literal")
            i = j + 1
            continue
        if c in "({[":
            stack.append((c, line))
            i += 1
            continue
        if c in ")}]":
            if not stack:
                problems.append(f"line {line}: unmatched '{c}'")
            elif stack[-1][0] != pairs[c]:
                problems.append(
                    f"line {line}: '{c}' closes '{stack[-1][0]}' opened on line {stack[-1][1]}"
                )
                stack.pop()
            else:
                stack.pop()
            i += 1
            continue
        i += 1
    for open_ch, open_line in stack:
        problems.append(f"unclosed '{open_ch}' opened on line {open_line}")
    return problems


def main() -> int:
    targets = sys.argv[1:]
    if not targets:
        targets = [str(p.relative_to(ROOT)) for p in (ROOT / "app/src").rglob("*.kt")]
    bad = 0
    for rel in targets:
        path = ROOT / rel
        if not path.is_file():
            print(f"[SKIP] {rel}")
            continue
        problems = check(path)
        if problems:
            bad += 1
            print(f"[FAIL] {rel}")
            for problem in problems[:12]:
                print(f"        {problem}")
        else:
            print(f"[OK]   {rel}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
