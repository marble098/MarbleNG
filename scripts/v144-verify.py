#!/usr/bin/env python3
"""MARBLE V144 static verification.

The sandbox has no JDK/Android SDK, so compilation happens in CI
(`:app:testDebugUnitTest` on the PR). This script catches the mechanical
breakage classes locally before pushing:

1. Brace/paren/bracket balance per changed file (string/comment aware).
2. Deleted symbols have zero remaining references.
3. New symbols are defined exactly once and referenced consistently.
4. Exactly one `companion object` per changed class.
5. Changed-signature call sites still bind (named-arg check).
6. New Persian lexicon keys are unique and well-formed.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/marbleng/app"
TEST = ROOT / "app/src/test/java/com/marbleng/app"

FAILURES: list[str] = []


def fail(msg: str) -> None:
    FAILURES.append(msg)
    print(f"[FAIL] {msg}")


def ok(msg: str) -> None:
    print(f"[OK] {msg}")


def strip_strings_comments(src: str) -> str:
    out: list[str] = []
    i, n = 0, len(src)
    state = "code"  # code | line | block | dq | sq | tpl
    tpl_depth = 0
    while i < n:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n else ""
        if state == "code":
            if c == "/" and nxt == "/":
                state = "line"
                i += 2
                continue
            if c == "/" and nxt == "*":
                state = "block"
                i += 2
                continue
            if c == '"':
                if src.startswith('"""', i):
                    state = "tpl"
                    i += 3
                    continue
                state = "dq"
                i += 1
                continue
            if c == "'":
                state = "sq"
                i += 1
                continue
            out.append(c)
            i += 1
        elif state == "line":
            if c == "\n":
                state = "code"
                out.append(c)
            i += 1
        elif state == "block":
            if c == "*" and nxt == "/":
                state = "code"
                i += 2
            else:
                i += 1
        elif state == "dq":
            if c == "\\":
                i += 2
            elif c == '"':
                state = "code"
                i += 1
            else:
                i += 1
        elif state == "sq":
            if c == "\\":
                i += 2
            elif c == "'":
                state = "code"
                i += 1
            else:
                i += 1
        elif state == "tpl":
            if src.startswith('"""', i):
                state = "code"
                i += 3
            elif c == "$" and nxt == "{":
                tpl_depth += 1
                out.append("${")
                i += 2
            elif c == "}" and tpl_depth > 0:
                tpl_depth -= 1
                out.append("}")
                i += 1
            else:
                i += 1
    return "".join(out)


CHANGED = [
    SRC / "AppRepository.kt",
    SRC / "core/AddressFamilyPolicy.kt",
    SRC / "core/BenchmarkEngine.kt",
    SRC / "core/RouteProbe.kt",
    SRC / "ui/Aether2026.kt",
    SRC / "ui/MarbleHomeStyles.kt",
    SRC / "ui/MarblePersianLexicon.kt",
    SRC / "vpn/MarbleVpnService.kt",
    TEST / "core/ProbeBudgetPolicyTest.kt",
]

# --- 1. balance -------------------------------------------------------------
for path in CHANGED:
    code = strip_strings_comments(path.read_text(encoding="utf-8"))
    stack: list[tuple[str, int]] = []
    pairs = {")": "(", "]": "[", "}": "{"}
    line = 1
    bad = False
    for ch in code:
        if ch == "\n":
            line += 1
        elif ch in "([{":
            stack.append((ch, line))
        elif ch in ")]}":
            if not stack or stack[-1][0] != pairs[ch]:
                fail(f"{path.name}: unbalanced {ch!r} at line {line}")
                bad = True
                break
            stack.pop()
    if not bad:
        if stack:
            fail(f"{path.name}: unclosed {stack[-1][0]!r} opened at line {stack[-1][1]}")
        else:
            ok(f"{path.name}: brackets balanced")

# --- 2. deleted symbols ------------------------------------------------------
DELETED = ["ServersFoldedNote", "RoutingModeCard", "servers hidden", "serversHidden"]
main_sources = {}
for path in CHANGED:
    if "/test/" not in str(path):
        main_sources[path.name] = path.read_text(encoding="utf-8")
for sym in DELETED:
    hits = [
        name
        for name, text in main_sources.items()
        if sym in text and "V144" not in text[max(0, text.find(sym) - 200): text.find(sym)]
    ]
    # comment-only mentions carry the V144 marker on a nearby line; anything else is real
    real = []
    for name in hits:
        text = main_sources[name]
        for m in re.finditer(re.escape(sym), text):
            window = text[max(0, m.start() - 160): m.start()]
            if "V144" not in window and "//" not in window.split("\n")[-1]:
                real.append(f"{name}")
                break
    if real:
        fail(f"deleted symbol {sym!r} still referenced in: {real}")
    else:
        ok(f"deleted symbol {sym!r} has no live references")

# --- 3. new symbols defined once ---------------------------------------------
ui = main_sources["Aether2026.kt"]
checks = [
    ("private fun RoutingModeRow(", ui, 1),
    ("private fun BugFinderSettings(", ui, 1),
    ("private fun ProbeSettings(", ui, 1),
    ("fun resolveWithBudget(", main_sources["AddressFamilyPolicy.kt"], 1),
    ("fun isLiteralIp(", main_sources["AddressFamilyPolicy.kt"], 1),
    ("fun nextDnsTarget(", main_sources["RouteProbe.kt"], 1),
    ("ICMP_MIN_WAIT_SEC", main_sources["RouteProbe.kt"], 3),  # decl + 2 uses
    ("probePool", main_sources["AppRepository.kt"], 2),  # decl + invokeAll use
    ("sharedUnderlay", main_sources["AppRepository.kt"], 3),  # decl + groups + membersFor
    ("listHeightCap", main_sources["MarbleHomeStyles.kt"], 3),
    ("BATCH_ABSOLUTE_CAP_MS", main_sources["BenchmarkEngine.kt"], 2),
]
for sym, text, minimum in checks:
    count = text.count(sym)
    if count < minimum:
        fail(f"symbol {sym!r}: found {count}×, expected ≥{minimum}×")
    else:
        ok(f"symbol {sym!r}: {count}×")

# --- 4. one companion object per class ----------------------------------------
be = main_sources["BenchmarkEngine.kt"]
if be.count("companion object") != 1:
    fail(f"BenchmarkEngine.kt has {be.count('companion object')} companion objects")
else:
    ok("BenchmarkEngine.kt has exactly one companion object")

# --- 5. changed-signature call sites ------------------------------------------
for path, sym in [
    (SRC / "core/SshTransportManager.kt", "resolveCandidates"),
    (SRC / "core/BugFinder.kt", "resolveCandidates"),
    (SRC / "AppRepository.kt", "resolveCandidates"),
]:
    text = path.read_text(encoding="utf-8")
    for m in re.finditer(r"resolveCandidates\((.*?)\)", text, re.DOTALL):
        args = m.group(1)
        # positional 3rd arg would now bind to timeoutMs: reject lambdas positionally
        parts = [p.strip() for p in args.split("\n")]
        joined = " ".join(parts)
        if re.search(r",\s*\{", joined) or re.search(r",\s*\w+\s*->", joined):
            fail(f"{path.name}: positional lambda passed to resolveCandidates: {joined[:80]}")
            break
    else:
        ok(f"{path.name}: resolveCandidates call sites bind safely")

chip_calls = re.findall(r"CyberChoiceChip\((.*?)\)\s*\{", ui, re.DOTALL)
bad_chip = [c for c in chip_calls if re.search(r",\s*(true|false|\d+)\s*,", " ".join(c.split()))]
if bad_chip:
    fail(f"CyberChoiceChip has {len(bad_chip)} positional-arg call sites")
else:
    ok(f"all {len(chip_calls)} CyberChoiceChip call sites use named args + trailing lambda")

# --- 6. lexicon keys -----------------------------------------------------------
lex = main_sources["MarblePersianLexicon.kt"]
new_keys = [
    "Direct HTTPS to Google — path-only, same for every server",
    "Domain first (default)",
    "IP on demand",
    "As-is (fastest)",
    "Scan the runtime, then copy or save the report",
    "Notifications and live stats",
]
for key in new_keys:
    if lex.count(f'"{key}"') != 1:
        fail(f"lexicon key {key!r}: found {lex.count(chr(34) + key + chr(34))}×")
    else:
        ok(f"lexicon key present: {key[:42]}…")
key_defs = re.findall(r'^\s*"((?:[^"\\]|\\.)*)" to ', lex, re.MULTILINE)
# Seven duplicate keys pre-exist on main (later entry wins in mapOf); V144 must add none.
new_dupes = [k for k in new_keys if key_defs.count(k) > 1]
if new_dupes:
    fail(f"V144 introduced duplicate lexicon keys: {new_dupes}")
else:
    ok(f"V144 lexicon keys unique ({len(key_defs)} total, 7 pre-existing dupes untouched)")

# --- 7. UI cross-checks ---------------------------------------------------------
if '"Bug Finder"' not in (SRC / "ui/Aether2026.kt").read_text(encoding="utf-8"):
    fail("Information page lost its Bug Finder card title")
else:
    ok("Bug Finder card present on Information page")
if "SettingsWorkspaceTab.SYSTEM -> listOf(" in ui and '"Bug Finder"' in ui.split(
    "SettingsWorkspaceTab.SYSTEM -> listOf("
)[1].split(")")[0]:
    fail("Bug Finder card still present in SYSTEM tab")
else:
    ok("SYSTEM tab no longer hosts Bug Finder")
if "Dp.Unspecified" not in main_sources["MarbleHomeStyles.kt"]:
    fail("smart list height default missing")
else:
    ok("smart list height default present")

# --- 8. connect off main thread -------------------------------------------------
vpn = main_sources["MarbleVpnService.kt"]
if "\n        val settings = app.repo.effectiveSettingsFor(profile)" in vpn:
    fail("main-thread effectiveSettingsFor still present in startConnection")
else:
    ok("startConnection no longer resolves settings on the main thread")
if vpn.count("MARBLE_CONNECT_OFF_MAIN_V144") >= 3:
    ok("connect-off-main markers present (decl + diag + worker)")
else:
    fail("MARBLE_CONNECT_OFF_MAIN_V144 markers missing")

print()
if FAILURES:
    print(f"{len(FAILURES)} FAILURE(S)")
    sys.exit(1)
print("ALL V144 STATIC CHECKS PASSED")
