#!/usr/bin/env python3
"""MARBLE_ONE_LANGUAGE_V281 — the design-system conformance gate.

`scripts/system-integrity-check.py` pins *behaviour*: which composable exists, which string a page
prints, which control retired. This gate pins the *language* the whole product is drawn in — the
scales, the kit and the tone table — so that the next screen cannot quietly reintroduce a fifth
corner, a seventh wash or a second card.

It reads the scales out of `ui/theme/MarbleTokens.kt` rather than repeating them here: if a rung is
added to the token layer, the gate follows it. What the gate forbids is a *literal* that bypasses
the token layer at all.

Six rules:

  1. shape   — every `RoundedCornerShape(N.dp)` is a rung of `MarbleRadius.SCALE`
  2. space   — every gap, `PaddingValues` and padding argument is a rung of `MarbleSpace.SCALE`
  3. alpha   — every `copy(alpha = X)` literal is a rung of `MarbleWash` or `MarbleVeil`
  4. kit     — an interactive Material 3 primitive is drawn only by the component that owns it
  5. roles   — each kit component is declared once, and the retired duplicates delegate to it
  6. tone    — each verdict mapper is declared once and used, and one resolver maps tone to colour

Exemptions are written down, not implicit: the dock family draws an optic (glass thickness, lens
magnification, halo physics) whose numbers are pinned by the V245/V248/V262/V264/V273/V279 suites,
and the theme files *declare* the rungs, so their literals are the scale itself.

Gradle/Kotlin remain the compile authority; this is a source-wide drift gate and exits non-zero.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
UI = ROOT / "app/src/main/java/com/marbleng/app/ui"
TOKENS = UI / "theme/MarbleTokens.kt"

# The dock optic family: drawn physics, not surfaces. See the module KDoc of MarbleVeil.
DOCK_FAMILY = {
    "GlassLensIndicator.kt",
    "LiquidGlassBottomBar.kt",
    "LiquidLensNavigationBar.kt",
    "MarbleDockBackdrop.kt",
    "MarbleDockWings.kt",
    "MarbleLiquidDock.kt",
    "MarbleLumenDock.kt",
    "NavigationItemView.kt",
}

# Files that *declare* a scale, so their literals are the rungs themselves.
SCALE_DECLARATIONS = {
    "theme/MarbleShape.kt",
    "theme/MarbleTokens.kt",
    "theme/MarbleTheme.kt",
    "theme/MarbleType.kt",
    "model/DockStudio.kt",
}

# Where a raw Material 3 primitive is allowed, and the single job it is allowed to do there.
PRIMITIVE_OWNERS = {
    "MarbleComponents.kt": "the kit: every Marble* component is drawn here and only here",
    "MarbleExpressivePages.kt": "the page shell: Scaffold, TopAppBar and its navigation icon",
    "MarbleApp.kt": "the chrome root: NavigationRail, BadgedBox and the status Badge",
    "MarbleWave.kt": "the wave indicators: the product's spinners and progress bars",
}

INTERACTIVE_PRIMITIVES = (
    "AlertDialog",
    "AssistChip",
    "Badge",
    "BadgedBox",
    "Button",
    "Card",
    "Checkbox",
    "CircularProgressIndicator",
    "DatePicker",
    "ElevatedButton",
    "FilledIconButton",
    "FilledTonalButton",
    "FilledTonalIconButton",
    "FilterChip",
    "HorizontalDivider",
    "Icon",
    "IconButton",
    "LinearProgressIndicator",
    "ListItem",
    "ModalBottomSheet",
    "NavigationBar",
    "NavigationDrawer",
    "NavigationRail",
    "OutlinedButton",
    "OutlinedIconButton",
    "OutlinedTextField",
    "RadioButton",
    "Scaffold",
    "SegmentedButton",
    "Slider",
    "Snackbar",
    "Switch",
    "TabRow",
    "TextField",
    "TextButton",
    "TopAppBar",
)

# A retired duplicate must now delegate to the kit component that does its job.
DELEGATIONS = {
    "ui/SettingsJitterPages.kt": ("LiveStat", "MarbleStatTile"),
    "ui/SettingsSystemPages.kt": ("SubscriptionOverviewTile", "MarbleStatTile"),
    "ui/ServersScreen.kt": ("SubscriptionStatCell", "MarbleStatTile"),
    "ui/SettingsNetworkCouncilPages.kt": ("CouncilMetricRow", "MarbleMetricRow"),
    "ui/IpIdentityScreen.kt": ("IpSkeletonLine", "MarbleSkeleton"),
    "ui/MarblePermissionOnboarding.kt": ("ConnectionPermissionDialog", "MarbleAlertDialog"),
}

# One resolver, one table: declared exactly once, used at least once.
TONE_MAPPERS = (
    "marbleToneColor",
    "marbleOnToneColor",
    "marbleToneContainer",
    "marbleOnToneContainer",
    "connectPhaseTone",
    "heroTone",
    "healthTone",
    "pingTone",
    "protocolTone",
    "pingBadgeTone",
    "dockSlotTone",
    "planTone",
    "sourceStripeTone",
    "severityTone",
    "filterVerdictTone",
    "tlsProbeVerdict",
)

KIT_COMPONENTS = (
    "MarbleAlertDialog",
    "MarbleBadge",
    "MarbleBottomSheet",
    "MarbleButton",
    "MarbleCard",
    "MarbleDivider",
    "MarbleEmptyState",
    "MarbleIconButton",
    "MarbleLargeHeader",
    "MarbleLevelBars",
    "MarbleListItem",
    "MarbleMetricRow",
    "MarbleSearchField",
    "MarbleSectionHeader",
    "MarbleSkeleton",
    "MarbleSlider",
    "MarbleStatTile",
    "MarbleSwitch",
    "MarbleSwitchRow",
    "MarbleTextField",
)


def strip_noise(text: str) -> str:
    """Comments, string and character literals out — with every newline kept in place.

    A single left-to-right scan, because the shortcut of "strip block comments, then strings"
    quietly eats code: a `/*` inside a string literal (a glob, a regex, a doc sample) pairs with the
    next `*/` a thousand lines away. Line numbers have to survive, so a skipped run contributes the
    newlines it contained and nothing else.
    """
    out: list[str] = []
    i = 0
    n = len(text)

    def skip_to(index: int, terminator: str) -> int:
        """Advance past a run, keeping its newlines so line numbers still line up."""
        while index < n and not text.startswith(terminator, index):
            if text[index] == "\n":
                out.append("\n")
            index += 1
        return index + len(terminator)

    while i < n:
        char = text[i]
        following = text[i + 1] if i + 1 < n else ""
        if char == "/" and following == "/":
            # Stop *before* the newline: the ordinary path below emits it, so the next line of code
            # keeps its own number.
            while i < n and text[i] != "\n":
                i += 1
            continue
        if char == "/" and following == "*":
            i = skip_to(i + 2, "*/")
            continue
        if text.startswith('"""', i):
            out.append('""')
            i = skip_to(i + 3, '"""')
            continue
        if char == '"':
            out.append('""')
            i += 1
            while i < n and text[i] != '"':
                i += 2 if text[i] == "\\" else 1
            i += 1
            continue
        if char == "'":
            out.append("''")
            i += 1
            while i < n and text[i] != "'":
                i += 2 if text[i] == "\\" else 1
            i += 1
            continue
        out.append(char)
        i += 1
    return "".join(out)


def ui_files() -> list[Path]:
    return sorted(p for p in UI.rglob("*.kt"))


def read_scale(name: str, kind: str) -> tuple[list[float], list[str]]:
    """The rungs of a scale object and the constant names that list them.

    Read from the token layer that declares them, so adding a rung there widens the gate here
    instead of leaving the gate behind.
    """
    source = TOKENS.read_text(encoding="utf-8")
    block = re.search(rf"object {name} \{{(.*?)\n\}}", source, re.S)
    if not block:
        raise AssertionError(f"missing token object: {name}")
    body = block.group(1)
    values = {
        m.group(1): float(m.group(2))
        for m in re.finditer(rf"const val (\w+): {kind} = ([\d._]+)f?", body)
    }
    listed = re.search(rf"val (?:SCALE|LADDER): List<{kind}> = listOf\(([^)]*)\)", body)
    if not listed:
        raise AssertionError(f"{name} declares no SCALE/LADDER list")
    names = [key.strip() for key in listed.group(1).split(",") if key.strip()]
    unknown = [key for key in names if key not in values]
    if unknown:
        raise AssertionError(f"{name} lists rungs it does not declare: {unknown}")
    return [values[key] for key in names], names


def check_scales(violations: list[str]) -> dict[str, list[float]]:
    """Rule 6a: a scale lists its own rungs, ascending, with no duplicates."""
    ladders: dict[str, list[float]] = {}
    for name, kind in (
        ("MarbleRadius", "Int"),
        ("MarbleSpace", "Int"),
        ("MarbleElevation", "Int"),
        ("MarbleWash", "Float"),
        ("MarbleVeil", "Float"),
    ):
        rungs, _ = read_scale(name, kind)
        ladders[name] = rungs
        if rungs != sorted(rungs):
            violations.append(f"{name} lists its rungs out of order: {rungs}")
        if len(set(rungs)) != len(rungs):
            violations.append(f"{name} repeats a rung: {rungs}")
    return ladders


def check_shape(ladders: dict[str, list[float]], violations: list[str]) -> None:
    """Rule 1: a corner is a rung of the shape ladder, or a shape the theme already named."""
    rungs = {int(v) for v in ladders["MarbleRadius"]}
    pattern = re.compile(r"RoundedCornerShape\((\d+)\.dp\)")
    for path in ui_files():
        rel = path.relative_to(UI).as_posix()
        if rel in SCALE_DECLARATIONS or path.name in DOCK_FAMILY:
            continue
        for number, line in _matches(path, pattern):
            if int(number) not in rungs:
                violations.append(f"{rel}:{line}: corner {number}.dp is not on MarbleRadius.SCALE")


def check_space(ladders: dict[str, list[float]], violations: list[str]) -> None:
    """Rule 2: every gap and every padding is a rung of the spacing scale."""
    rungs = {int(v) for v in ladders["MarbleSpace"]}
    patterns = (
        re.compile(r"spacedBy\((\d+)\.dp\)"),
        re.compile(r"PaddingValues\((\d+)\.dp\)"),
        re.compile(r"\.padding\((\d+)\.dp\)"),
        re.compile(r"\b(?:start|end|top|bottom|horizontal|vertical|all) = (\d+)\.dp"),
    )
    for path in ui_files():
        rel = path.relative_to(UI).as_posix()
        if rel in SCALE_DECLARATIONS or path.name in DOCK_FAMILY:
            continue
        for pattern in patterns:
            for number, line in _matches(path, pattern):
                value = int(number)
                if value == 0 or value in rungs:
                    continue
                violations.append(f"{rel}:{line}: gap {value}.dp is not on MarbleSpace.SCALE")


def check_alpha(ladders: dict[str, list[float]], violations: list[str]) -> None:
    """Rule 3: a literal alpha is a wash or a veil — nothing in between the two scales."""
    rungs = set(ladders["MarbleWash"]) | set(ladders["MarbleVeil"])
    pattern = re.compile(r"copy\(alpha = (0\.\d+|1(?:\.0+)?)f?\)")
    for path in ui_files():
        rel = path.relative_to(UI).as_posix()
        if rel in SCALE_DECLARATIONS or path.name in DOCK_FAMILY:
            continue
        for number, line in _matches(path, pattern):
            if float(number) not in rungs:
                violations.append(
                    f"{rel}:{line}: alpha {number} is neither a MarbleWash nor a MarbleVeil rung"
                )


def check_kit_ownership(violations: list[str]) -> None:
    """Rule 4: an interactive primitive is drawn only by the component that owns it."""
    allowed = set(PRIMITIVE_OWNERS) | DOCK_FAMILY
    for path in ui_files():
        if path.name in allowed:
            continue
        text = strip_noise(path.read_text(encoding="utf-8"))
        for name in INTERACTIVE_PRIMITIVES:
            for match in re.finditer(re.escape(name) + r"\(", text):
                start = match.start()
                if start >= 6 and text[start - 6:start] == "Marble":
                    continue  # the kit's own component
                if start and (text[start - 1].isalnum() or text[start - 1] in "._"):
                    continue  # a longer identifier, e.g. CircularWavyProgressIndicator
                line = text.count("\n", 0, start) + 1
                violations.append(
                    f"{path.relative_to(UI).as_posix()}:{line}: raw {name}( outside the kit"
                )


def check_delegations(violations: list[str]) -> None:
    """Rule 5: a retired duplicate delegates to the kit component that now does its job."""
    for rel, (duplicate, kit) in DELEGATIONS.items():
        text = (UI.parent / rel).read_text(encoding="utf-8")
        if f"fun {duplicate}(" not in text:
            continue  # the duplicate is gone entirely, which is even better
        if f"{kit}(" not in text:
            violations.append(f"{rel}: {duplicate} does not delegate to {kit}")


def check_kit_is_single(violations: list[str]) -> None:
    """Rule 5a: one declaration per kit component, all of them in the kit file."""
    kit = (UI / "MarbleComponents.kt").read_text(encoding="utf-8")
    for name in KIT_COMPONENTS:
        declared = len(re.findall(rf"^internal fun {name}\(|^fun {name}\(", kit, re.M))
        if declared != 1:
            violations.append(f"MarbleComponents.kt declares {name} {declared} times (want 1)")
        elsewhere = [
            p.relative_to(UI).as_posix()
            for p in ui_files()
            if p.name != "MarbleComponents.kt"
            and re.search(rf"^(internal |private )?fun {name}\(", p.read_text(encoding="utf-8"), re.M)
        ]
        if elsewhere:
            violations.append(f"{name} is also declared in {', '.join(elsewhere)}")


def check_tone_layer(violations: list[str]) -> None:
    """Rule 6: one verdict table, one resolver, and no mapper that nobody reads."""
    sources = {p.relative_to(UI).as_posix(): p.read_text(encoding="utf-8") for p in ui_files()}
    for mapper in TONE_MAPPERS:
        declarations = [
            rel
            for rel, text in sources.items()
            if re.search(rf"^(internal |private )?fun (ColorScheme\.)?{mapper}\(", text, re.M)
        ]
        if len(declarations) != 1:
            violations.append(
                f"{mapper} is declared {len(declarations)} times: {', '.join(declarations)}"
            )
        uses = sum(
            len(re.findall(rf"(?<![\w.]){mapper}\(", strip_noise(text)))
            for text in sources.values()
        )
        if uses < 2:
            violations.append(f"{mapper} is declared but never called (uses={uses})")


def _matches(path: Path, pattern: re.Pattern) -> list[tuple[str, int]]:
    text = strip_noise(path.read_text(encoding="utf-8"))
    return [
        (match.group(1), text.count("\n", 0, match.start()) + 1)
        for match in pattern.finditer(text)
    ]


def main() -> int:
    violations: list[str] = []
    ladders = check_scales(violations)
    check_shape(ladders, violations)
    check_space(ladders, violations)
    check_alpha(ladders, violations)
    check_kit_ownership(violations)
    check_delegations(violations)
    check_kit_is_single(violations)
    check_tone_layer(violations)

    print("=== MARBLE DESIGN SYSTEM CONFORMANCE (V281) ===")
    print("shape ladder  :", ", ".join(str(int(v)) for v in ladders["MarbleRadius"]))
    print("space ladder  :", ", ".join(str(int(v)) for v in ladders["MarbleSpace"]))
    print("elevation     :", ", ".join(str(int(v)) for v in ladders["MarbleElevation"]))
    print("wash scale    :", ", ".join(str(v) for v in ladders["MarbleWash"]))
    print("veil scale    :", ", ".join(str(v) for v in ladders["MarbleVeil"]))
    print(f"files scanned : {len(ui_files())}")
    print(f"kit components: {len(KIT_COMPONENTS)}")

    if violations:
        print(f"\nviolations={len(violations)}")
        for item in violations:
            print(f" - {item}")
        return 1
    print("\nviolations=0")
    print("Every screen is drawn from one shape, space, alpha, kit and tone language.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
