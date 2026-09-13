#!/usr/bin/env python3
"""Retrace an obfuscated MarbleNG crash report with an R8 mapping file.

MarbleNG release builds are shrunk and obfuscated by R8 and every class file carries
`r8-map-id-<hash>` instead of a real source file name, so an on-device stack trace looks
like `at ko0.c(r8-map-id-...:16)`. The build that produced the APK also produced
`app/build/outputs/mapping/release/mapping.txt`; this tool turns such a trace back into
the original class, method and source line, and prints the matching source lines.

Usage:
    python3 retrace.py --mapping app/build/outputs/mapping/release/mapping.txt \
                       --crash .github/retrace/crash.txt
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import sys
from dataclasses import dataclass, field

CLASS_RE = re.compile(r"^(\S.*?) -> (\S+):$")
MEMBER_RE = re.compile(r"^\s{2,}(.+?) -> (\S+)$")
LINE_RE = re.compile(r"^(\d+):(\d+):(.*?)(?::(\d+):(\d+))?$")
FRAME_RE = re.compile(r"^\s*at\s+([\w$.<>]+)\.([\w$<>]+)\((?:([\w$.$ -]+):(\d+))?\)\s*$")


@dataclass
class Method:
    obf_name: str
    signature: str
    original_name: str
    obf_start: int | None = None
    obf_end: int | None = None
    orig_start: int | None = None
    orig_end: int | None = None

    def original_line(self, obf_line: int | None) -> int | None:
        if obf_line is None:
            return self.orig_start
        if self.obf_start is None or self.obf_end is None:
            return self.orig_start
        if not (self.obf_start <= obf_line <= self.obf_end):
            return None
        if self.orig_start is None:
            return None
        if self.orig_end is None or self.obf_end == self.obf_start:
            return self.orig_start
        shift = obf_line - self.obf_start
        limit = self.orig_end - self.orig_start
        return self.orig_start + min(shift, limit)


@dataclass
class ClassEntry:
    original: str
    methods: list[Method] = field(default_factory=list)
    fields: list[tuple[str, str]] = field(default_factory=list)
    raw: list[str] = field(default_factory=list)


class Mapping:
    def __init__(self, path: str) -> None:
        self.classes: dict[str, ClassEntry] = {}
        self._parse(path)

    def _parse(self, path: str) -> None:
        current: ClassEntry | None = None
        with open(path, "r", encoding="utf-8", errors="replace") as handle:
            for line in handle:
                line = line.rstrip("\n")
                if not line.strip():
                    current = None
                    continue
                if not line.startswith((" ", "\t")):
                    match = CLASS_RE.match(line)
                    if match:
                        original, obf = match.group(1), match.group(2)
                        entry = ClassEntry(original=original)
                        self.classes[obf] = entry
                        current = entry
                    else:
                        current = None
                    continue
                if current is None:
                    continue
                match = MEMBER_RE.match(line)
                if not match:
                    continue
                current.raw.append(line.strip())
                left, obf_name = match.group(1), match.group(2)
                line_match = LINE_RE.match(left)
                if line_match:
                    obf_start = int(line_match.group(1))
                    obf_end = int(line_match.group(2))
                    signature = line_match.group(3)
                    orig_start = int(line_match.group(4)) if line_match.group(4) else None
                    orig_end = int(line_match.group(5)) if line_match.group(5) else None
                    current.methods.append(
                        Method(
                            obf_name=obf_name,
                            signature=signature,
                            original_name=_method_name(signature),
                            obf_start=obf_start,
                            obf_end=obf_end,
                            orig_start=orig_start,
                            orig_end=orig_end,
                        )
                    )
                else:
                    if "(" in left:
                        current.methods.append(
                            Method(
                                obf_name=obf_name,
                                signature=left,
                                original_name=_method_name(left),
                            )
                        )
                    else:
                        current.fields.append((obf_name, left))

    def resolve(self, obf_class: str, obf_method: str, obf_line: int | None):
        entry = self.classes.get(obf_class)
        if entry is None:
            return None, None
        candidates = [m for m in entry.methods if m.obf_name == obf_method]
        if not candidates:
            return entry, None
        with_lines = [m for m in candidates if m.original_line(obf_line) is not None]
        if with_lines:
            best = max(
                with_lines,
                key=lambda m: (m.obf_start or 0) <= (obf_line or 0) <= (m.obf_end or 0),
            )
            return entry, best
        return entry, candidates[0]


def _method_name(signature: str) -> str:
    if "(" not in signature:
        return signature
    head = signature.split("(", 1)[0]
    return head.split()[-1] if head.split() else head


def parse_crash(path: str) -> list[tuple[str, str, int | None, str]]:
    frames = []
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        for line in handle:
            match = FRAME_RE.match(line)
            if not match:
                continue
            frames.append(
                (
                    match.group(1),
                    match.group(2),
                    int(match.group(4)) if match.group(4) else None,
                    line.strip(),
                )
            )
    return frames


def source_path(original_class: str) -> str | None:
    base = original_class.split("$", 1)[0]
    relative = base.replace(".", "/")
    for suffix in (".kt", ".java"):
        candidate = os.path.join("app/src/main/java", relative + suffix)
        if os.path.isfile(candidate):
            return candidate
    return None


def print_source(original_class: str, line: int | None, context: int = 4) -> None:
    if line is None:
        return
    path = source_path(original_class)
    if not path:
        print(f"      (source file for {original_class} not present in this checkout)")
        return
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        lines = handle.readlines()
    start = max(0, line - 1 - context)
    end = min(len(lines), line + context)
    print(f"      --- {path}:{line} ---")
    for index in range(start, end):
        marker = ">>" if index == line - 1 else "  "
        print(f"      {marker} {index + 1:5d} | {lines[index].rstrip()}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mapping", default="app/build/outputs/mapping/release/mapping.txt")
    parser.add_argument("--crash", default=".github/retrace/crash.txt")
    args = parser.parse_args()

    if not os.path.isfile(args.mapping):
        print(f"::error::mapping file not found: {args.mapping}")
        return 1

    with open(args.mapping, "rb") as handle:
        digest = hashlib.sha256(handle.read()).hexdigest()
    print(f"mapping={args.mapping} bytes={os.path.getsize(args.mapping)} sha256={digest}")

    mapping = Mapping(args.mapping)
    print(f"classes in mapping: {len(mapping.classes)}")
    print()
    print("=== RETRACED STACK TRACE ===")

    frames = parse_crash(args.crash)
    for index, (obf_class, obf_method, obf_line, raw) in enumerate(frames, start=1):
        entry, method = mapping.resolve(obf_class, obf_method, obf_line)
        print(f"[{index:02d}] {raw}")
        if entry is None:
            print(f"      -> {obf_class}.{obf_method} (not in this mapping)")
            continue
        if method is None:
            print(f"      -> {entry.original}.{obf_method} (method not in mapping)")
            continue
        original_line = method.original_line(obf_line)
        print(
            f"      -> {entry.original}.{method.original_name}"
            f"  [source line {original_line}]  signature={method.signature}"
        )
        print_source(entry.original, original_line)

    print()
    print("=== MAPPING ENTRIES FOR THE OBFUSCATED CLASSES IN THE TRACE ===")
    for obf_class in sorted({frame[0] for frame in frames}):
        entry = mapping.classes.get(obf_class)
        if entry is None:
            print(f"{obf_class}: <absent>")
            continue
        print(f"{obf_class} -> {entry.original}")
        for raw in entry.raw:
            print(f"    {raw}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
