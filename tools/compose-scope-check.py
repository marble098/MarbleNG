#!/usr/bin/env python3
"""Flag composable-only references used inside non-composable lambda scopes.

`Aether.*` tokens are `@Composable get()` properties, `MaterialTheme.*` too, and `trx()` is a
@Composable function. Reading one inside a draw scope (`Canvas { }`, `drawBehind { }`), an effect
body (`LaunchedEffect { }`), a `remember { }` factory or a gesture callback is the
"@Composable invocations can only happen from the context of a @Composable function" build failure
— and there is no JDK in this sandbox to catch it before CI does.

Inline lambdas (`let`, `run`, `apply`, `also`, `with`, `forEach`, …) carry the composable context,
so the scan looks through them at the enclosing scope.

Usage:
    python3 tools/compose-scope-check.py                 # whole main source set
    python3 tools/compose-scope-check.py --added-only    # only lines this branch added
    python3 tools/compose-scope-check.py path/to/File.kt
"""

from pathlib import Path
import re
import subprocess
import sys

# --------------------------------------------------------------------------------------------
# tokenizer: blank out strings, chars, comments and interpolation payloads, keeping offsets
# --------------------------------------------------------------------------------------------


def skip_string(text, i):
    n = len(text)
    if text.startswith('"""', i):
        j = i + 3
        while j < n:
            if text.startswith('"""', j):
                return j + 3
            if text[j] == "$":
                j = skip_interp(text, j)
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
        if c == "\n":
            return j  # unterminated single-quoted string: bail to the line end
        j += 1
    return n


def skip_char(text, i):
    n = len(text)
    j = i + 1
    while j < n:
        if text[j] == "\\":
            j += 2
            continue
        if text[j] == "'":
            return j + 1
        j += 1
    return n


def skip_interp(text, start):
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
            j = skip_char(text, j)
            continue
        j += 1
    return j


def mask(text):
    n = len(text)
    out = list(text)
    i = 0
    while i < n:
        c = text[i]
        if text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
        elif text.startswith('"""', i) or c == '"':
            j = skip_string(text, i)
        elif c == "'":
            j = skip_char(text, i)
        else:
            i += 1
            continue
        for k in range(i, min(j, n)):
            if out[k] != "\n":
                out[k] = " "
        i = j
    return "".join(out)


# --------------------------------------------------------------------------------------------
# scope classification
# --------------------------------------------------------------------------------------------

# Lambdas that are NOT @Composable: reading a composable-only value inside them fails to build.
NON_COMPOSABLE = {
    # drawing
    "Canvas", "drawBehind", "drawWithContent", "drawWithCache", "drawIntoCanvas", "Path",
    "graphicsLayer",
    # layout / placement callbacks
    "onSizeChanged", "onGloballyPositioned", "onPlaced",
    # effects and state factories
    "LaunchedEffect", "SideEffect", "DisposableEffect", "remember", "rememberSaveable",
    "derivedStateOf", "snapshotFlow", "produceState",
    # gesture + interaction callbacks
    "clickable", "combinedClickable", "kineticClickable", "toggleable", "selectable",
    "pointerInput", "detectTapGestures", "detectDragGestures", "awaitEachGesture",
    "awaitPointerEventScope", "draggable", "scrollable",
    # coroutines
    "launch", "async", "withContext", "withTimeout", "runBlocking",
    # plain callbacks
    "onTextLayout", "onValueChange", "onClick", "onCheckedChange", "onDismissRequest",
    "onAction", "onBack", "onSelect", "onToggle", "onDismiss", "onConfirm", "onLongPress",
    "onExpandedChange", "onNavigate", "onPressed",
}

# Inline / @Composable lambdas: the composable context flows through them.
TRANSPARENT = {
    "let", "run", "apply", "also", "takeIf", "takeUnless", "compose", "composed", "then",
    "with", "forEach", "forEachIndexed", "map", "mapIndexed", "filter", "firstOrNull",
    "lastOrNull", "onEach", "use", "sumOf", "fold", "reduce", "sortedBy", "maxByOrNull",
    "minByOrNull", "count", "any", "all", "none", "find", "indexOfFirst", "getOrDefault",
    "getOrElse", "items", "itemsIndexed", "item", "stickyHeader",
}

BAD = [
    (re.compile(r"\bAether\s*\."), "Aether.<token> — @Composable get()"),
    (re.compile(r"\bMarbleTheme\s*\."), "MarbleTheme.<token> — @Composable get()"),
    (re.compile(r"\bMaterialTheme\s*\."), "MaterialTheme.<token> — @Composable get()"),
    (re.compile(r"\btrx(?:OrNull)?\s*\("), "trx() — @Composable"),
    (re.compile(r"\bLocal[A-Z]\w*\s*\.\s*current\b"), "Local*.current — @Composable"),
    (re.compile(r"\b(settingsTitleStyle|settingsRowTitleStyle|settingsBodyStyle)\s*\("),
     "settings*Style() — @Composable"),
]

IDENT = re.compile(r"[A-Za-z_]\w*")


def scan(path: Path, added=None):
    text = path.read_text(encoding="utf-8")
    src = mask(text)
    raw_lines = text.split("\n")
    n = len(src)

    brace_stack = []      # (owner, line)
    call_stack = []       # owner of each open '('
    last_ident = None
    last_closed_call = None
    prev_significant = None
    hits = []
    line = 1
    i = 0

    def innermost(chain):
        for owner in reversed(chain):
            if owner not in TRANSPARENT:
                return owner
        return None

    while i < n:
        c = src[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if c in " \t\r":
            i += 1
            continue
        if c == "(":
            call_stack.append(last_ident)
            prev_significant = "("
            i += 1
            continue
        if c == ")":
            if call_stack:
                last_closed_call = call_stack.pop()
            prev_significant = ")"
            i += 1
            continue
        if c == "{":
            owner = last_closed_call if prev_significant == ")" else last_ident
            if owner is None:
                owner = "<block>"
            brace_stack.append((owner, line))
            prev_significant = "{"
            last_closed_call = None
            last_ident = None
            i += 1
            continue
        if c == "}":
            if brace_stack:
                brace_stack.pop()
            prev_significant = "}"
            last_closed_call = None
            last_ident = None
            i += 1
            continue
        m = IDENT.match(src, i)
        if m:
            word = m.group(0)
            for pat, label in BAD:
                mm = pat.match(src, i)
                if mm:
                    chain = [o for o, _ in brace_stack]
                    owner = innermost(chain)
                    if owner in NON_COMPOSABLE:
                        if added is None or line in added:
                            hits.append((line, " > ".join(chain[-3:]) or "<top>", label,
                                         raw_lines[line - 1].strip()[:100]))
                    i = mm.end()
                    last_ident = word
                    prev_significant = "ident"
                    break
            else:
                last_ident = word
                prev_significant = "ident"
                i = m.end()
            continue
        if c in "=,;":
            last_closed_call = None
        prev_significant = c
        i += 1
    return hits


def added_lines(diff_text, current_file):
    out = set()
    cur = None
    new_ln = 0
    for ln in diff_text.split("\n"):
        if ln.startswith("+++ b/"):
            cur = ln[6:]
            new_ln = 0
            continue
        if ln.startswith("@@"):
            m = re.search(r"\+(\d+)", ln)
            new_ln = (int(m.group(1)) - 1) if m else 0
            continue
        if cur != current_file:
            continue
        if ln.startswith("+"):
            new_ln += 1
            out.add(new_ln)
        elif ln.startswith("-"):
            pass
        else:
            new_ln += 1
    return out


def main(argv):
    root = Path(__file__).resolve().parents[1]
    only_added = "--added-only" in argv
    files = [Path(p) for p in argv if not p.startswith("--")]
    diff = ""
    if only_added or not files:
        base = subprocess.run(["git", "merge-base", "HEAD", "origin/main"], cwd=root,
                              capture_output=True, text=True).stdout.strip() or "HEAD~1"
        diff = subprocess.run(["git", "diff", "-U0", base, "HEAD"], cwd=root,
                              capture_output=True, text=True).stdout
    if not files:
        files = sorted((root / "app/src/main/java").rglob("*.kt"))
    total = 0
    for f in files:
        f = Path(f)
        rel = str(f.relative_to(root)) if f.is_absolute() else str(f)
        added = added_lines(diff, rel) if only_added else None
        for line, chain, label, snippet in scan(f, added):
            total += 1
            print(f"{rel}:{line}  inside [{chain}]  ->  {label}\n        {snippet}")
    print(f"\ncomposable-context violations: {total}")
    return 1 if total else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
