#!/usr/bin/env python3
"""MARBLE_SERVERS_LANGUAGE_V114 — one-shot copy rename.

The product vocabulary changed: every user-visible "node" is a "server" and the
"Library" tab is "Servers". This rewrites *string literal text only*.

Code is protected three ways:
  1. only the characters inside a Kotlin string literal are considered;
  2. every `${...}` interpolation and `$identifier` inside a literal is skipped, so
     `subscriptionNodeCount` / `libraryProfiles` never turn into unknown symbols;
  3. literals that are machine keys (kebab-case event ids, diagnostics names,
     animation labels) are left untouched.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]

TARGETS = [
    "app/src/main/java/com/marbleng/app/ui/Aether2026.kt",
    "app/src/main/java/com/marbleng/app/ui/MarbleHomeStyles.kt",
    "app/src/main/java/com/marbleng/app/ui/MarbleSignatureHome.kt",
    "app/src/main/java/com/marbleng/app/ui/MarbleDesignSystem.kt",
    "app/src/main/java/com/marbleng/app/ui/MarblePermissionOnboarding.kt",
    "app/src/main/java/com/marbleng/app/ui/MarbleApp.kt",
    "app/src/main/java/com/marbleng/app/AppRepository.kt",
    "app/src/main/java/com/marbleng/app/MainActivity.kt",
    "app/src/main/java/com/marbleng/app/vpn/MarbleVpnService.kt",
    "app/src/main/java/com/marbleng/app/quicktile/MarbleQuickTileService.kt",
    "app/src/main/java/com/marbleng/app/core/BugFinder.kt",
    "app/src/main/java/com/marbleng/app/core/IranShield.kt",
]

# Longest phrase first: "Library source" must not become "Servers source".
REPLACEMENTS = [
    ("Library nodes", "servers"),
    ("Library node", "server"),
    ("library nodes", "servers"),
    ("Library source", "server source"),
    ("Library sources", "server sources"),
    ("library source", "server source"),
    ("the Library", "Servers"),
    ("in Library", "in Servers"),
    ("Back to library", "Back to servers"),
    ("Config library", "Server inventory"),
    ("VIEW NODES", "VIEW SERVERS"),
    ("NODES", "SERVERS"),
    ("Nodes", "Servers"),
    ("nodes", "servers"),
    ("Node", "Server"),
    ("node", "server"),
    ("Library", "Servers"),
    ("library", "servers"),
]

# Machine identifiers that happen to live in string literals.
SKIP_LITERAL = re.compile(
    r"""^(?:
        [a-z0-9]+(?:-[a-z0-9]+)+      |  # kebab-case event / animation ids
        [a-z]+[A-Z][A-Za-z]*          |  # camelCase keys ("nodes", "nodeCount")
        [A-Z0-9_]+                       # SCREAMING_SNAKE enums
    )$""",
    re.VERBOSE,
)


def skip_interpolation(text: str, start: int) -> int:
    """`start` points at `$`; return the index just past the whole interpolation.

    Handles `${ ... }` blocks that span lines, nest braces, contain their own string or
    char literals, and the short `$identifier` form.
    """
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
            if text.startswith('"""', j):
                k = text.find('"""', j + 3)
                j = n if k < 0 else k + 2
            else:
                j += 1
                while j < n and text[j] != '"':
                    if text[j] == "\\":
                        j += 1
                    j += 1
        elif c == "'":
            j += 1
            while j < n and text[j] != "'":
                if text[j] == "\\":
                    j += 1
                j += 1
        elif c == "/" and j + 1 < n and text[j + 1] == "/":
            k = text.find("\n", j)
            j = n if k < 0 else k - 1
        elif c == "/" and j + 1 < n and text[j + 1] == "*":
            k = text.find("*/", j + 2)
            j = n if k < 0 else k + 1
        j += 1
    return j


def literal_is_machine_key(text: str) -> bool:
    return bool(SKIP_LITERAL.match(text.strip()))


def rewrite_plain(chunk: str) -> str:
    out = chunk
    for old, new in REPLACEMENTS:
        # Word-ish boundaries so "nobody" never becomes "serverbody".
        out = re.sub(
            r"(?<![A-Za-z0-9_])" + re.escape(old) + r"(?![A-Za-z0-9_])",
            new.replace("\\", "\\\\"),
            out,
        )
    return out


def rewrite_literal(body: str) -> str:
    """Rewrite a literal's text while skipping every `$` interpolation inside it."""
    out = []
    i = 0
    n = len(body)
    plain_start = 0
    while i < n:
        ch = body[i]
        if ch == "\\" and i + 1 < n:  # keep escapes verbatim
            i += 2
            continue
        if ch == "$":
            out.append(rewrite_plain(body[plain_start:i]))
            j = skip_interpolation(body, i)
            out.append(body[i:j])
            i = j
            plain_start = j
            continue
        i += 1
    out.append(rewrite_plain(body[plain_start:]))
    return "".join(out)


def rewrite_source(text: str) -> str:
    out = []
    i = 0
    n = len(text)
    while i < n:
        ch = text[i]
        if ch == "/" and i + 1 < n and text[i + 1] == "/":  # line comment
            j = text.find("\n", i)
            j = n if j < 0 else j
            out.append(text[i:j])
            i = j
            continue
        if ch == "/" and i + 1 < n and text[i + 1] == "*":  # block comment
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append(text[i:j])
            i = j
            continue
        if ch == '"':
            triple = text.startswith('"""', i)
            if triple:
                j = text.find('"""', i + 3)
                j = n if j < 0 else j + 3
                body = text[i + 3 : j - 3]
                out.append('"""' + rewrite_literal(body) + '"""')
                i = j
                continue
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    j += 1
                    break
                if text[j] == "$" and j + 1 < n and text[j + 1] == "{":
                    # A `${...}` interpolation may span lines and hold its own literals;
                    # skipping it whole keeps multi-line interpolations inside one literal.
                    j = skip_interpolation(text, j)
                    continue
                if text[j] == "\n":
                    break
                j += 1
            body = text[i + 1 : j - 1] if text[j - 1] == '"' else text[i + 1 : j]
            closed = j <= n and text[j - 1] == '"'
            if literal_is_machine_key(body):
                out.append(text[i:j])
            else:
                out.append('"' + rewrite_literal(body) + ('"' if closed else ""))
            i = j
            continue
        if ch == "'":  # char literal
            j = i + 1
            while j < n and text[j] != "'":
                if text[j] == "\\":
                    j += 1
                j += 1
            j = min(n, j + 1)
            out.append(text[i:j])
            i = j
            continue
        out.append(ch)
        i += 1
    return "".join(out)


def main() -> int:
    changed = 0
    for rel in TARGETS:
        path = ROOT / rel
        if not path.is_file():
            print(f"skip (missing): {rel}")
            continue
        before = path.read_text(encoding="utf-8")
        after = rewrite_source(before)
        if after != before:
            path.write_text(after, encoding="utf-8")
            changed += 1
            print(f"rewrote {rel}")
        else:
            print(f"unchanged {rel}")
    print(f"files changed: {changed}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
