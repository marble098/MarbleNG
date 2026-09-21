#!/usr/bin/env python3
"""MARBLE_MATERIAL_YOU_REFRESH_V185 — one-shot icon ramp bump.

Finds every HomeVectorIcon( / HomeIconTile( call (paren-matched, multi-line safe) and
enlarges the Modifier.size(NN.dp) argument inside it per SIZE_MAP, so the whole product's
inline icons ride the larger Material icon ramp in one pass.
"""
import re
import sys

SIZE_MAP = {
    11.0: 13.0, 13.0: 14.0, 14.0: 15.0, 15.0: 16.0, 16.0: 18.0,
    17.0: 19.0, 18.0: 20.0, 19.0: 21.0, 20.0: 22.0, 22.0: 24.0,
}

CALL_RE = re.compile(r"\b(HomeVectorIcon|HomeIconTile)\s*\(")
SIZE_RE = re.compile(r"(size\s*\(\s*)(\d+(?:\.\d+)?)(\.dp\s*\))")


def fmt(v: float) -> str:
    return str(int(v)) if v == int(v) else str(v)


def process(text: str) -> tuple[str, int]:
    out = []
    pos = 0
    bumps = 0
    while True:
        m = CALL_RE.search(text, pos)
        if not m:
            out.append(text[pos:])
            break
        out.append(text[pos:m.end()])
        # Walk to the matching close paren of this call.
        depth = 1
        i = m.end()
        while i < len(text) and depth > 0:
            c = text[i]
            if c == "(":
                depth += 1
            elif c == ")":
                depth -= 1
            i += 1
        body = text[m.end():i]

        def bump(sm: re.Match) -> str:
            nonlocal bumps
            v = float(sm.group(2))
            if v in SIZE_MAP:
                bumps += 1
                return sm.group(1) + fmt(SIZE_MAP[v]) + sm.group(3)
            return sm.group(0)

        out.append(SIZE_RE.sub(bump, body))
        pos = i
    return "".join(out), bumps


def main() -> None:
    total = 0
    for path in sys.argv[1:]:
        with open(path, encoding="utf-8") as fh:
            text = fh.read()
        new, n = process(text)
        if n:
            with open(path, "w", encoding="utf-8") as fh:
                fh.write(new)
            print(f"{path}: {n} icons enlarged")
            total += n
    print(f"total: {total}")


if __name__ == "__main__":
    main()
