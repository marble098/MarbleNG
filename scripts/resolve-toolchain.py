#!/usr/bin/env python3
"""MARBLE_TOOLCHAIN_AUTOPILOT_V211 — resolve the newest published versions of everything
this build is made of.

Why a script and not a Gradle plugin: the answer to "what is the newest Kotlin beta" lives in
four different public repositories (Maven Central, Google's Maven, the Gradle Plugin Portal and
services.gradle.org), and each of them publishes `maven-metadata.xml` in exactly the same shape.
One resolver can therefore answer for the JDK toolchain, the Android Gradle Plugin, the Kotlin
compiler, every androidx artifact, the two Java libraries and the test dependencies at once —
and the same file is what the scheduled updater commits and what the release workflow prints.

Version comparison follows Maven's ordering rules, because that is the ordering the repositories
themselves publish in:

    ... < 1.0-alpha1 < 1.0-beta1 < 1.0-milestone1 < 1.0-rc1 < 1.0 < 1.0-sp

with numeric segments compared numerically, and a package's own "newest" line expressed as a
*compatibility line* (`--line`): the artifact's major.minor for androidx-style versioning, the
major alone for Gradle/AGP/Kotlin. A candidate outside the line is not a bug fix, it is a
migration, so it is reported separately (`newerMajor`) instead of being applied automatically.

Usage:
    scripts/resolve-toolchain.py                      # report, newest-in-line + newest-any
    scripts/resolve-toolchain.py --write              # write gradle/toolchain.properties
    scripts/resolve-toolchain.py --json report.json   # machine-readable
    scripts/resolve-toolchain.py --offline            # report the pins, touch no network
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LOCK = ROOT / "gradle" / "toolchain.properties"

GOOGLE = "https://dl.google.com/dl/android/maven2"
CENTRAL = "https://repo1.maven.org/maven2"
PLUGINS = "https://plugins.gradle.org/m2"
GRADLE_SERVICES = "https://services.gradle.org/versions/all"

# The build's own vocabulary. `key` is the name this repository uses for the component
# (`gradle/toolchain.properties`, `settings.gradle.kts`, `app/build.gradle.kts`); `line` is how
# far the autopilot may move it on its own.
#
#   line="major"  — Gradle, AGP, Kotlin: every 9.x / 2.x is the same compatibility contract.
#   line="minor"  — androidx: 1.19 → 1.20 is a new feature line with new APIs and sometimes a new
#                   compileSdk, so the automatic move stays inside 1.19 and the crossing is
#                   reported to a human instead of being taken silently.
#   line="year"   — the Compose BOM, which is date-versioned: 2026.09.00-alpha01 is a newer BOM
#                   than 2026.08.00 whatever its qualifier says.
COMPONENTS: "list[dict]" = [
    # ── the toolchain ────────────────────────────────────────────────────────────────────────
    {"key": "gradle", "kind": "gradle", "line": "major"},
    {"key": "agp", "kind": "maven", "repo": GOOGLE, "group": "com.android.tools.build",
     "artifact": "gradle", "line": "major"},
    {"key": "kotlin", "kind": "maven", "repo": CENTRAL, "group": "org.jetbrains.kotlin",
     "artifact": "kotlin-gradle-plugin", "line": "major"},
    # ── the app's libraries ──────────────────────────────────────────────────────────────────
    {"key": "composeBom", "kind": "maven", "repo": GOOGLE, "group": "androidx.compose",
     "artifact": "compose-bom", "line": "year"},
    {"key": "coreKtx", "kind": "maven", "repo": GOOGLE, "group": "androidx.core",
     "artifact": "core-ktx", "line": "minor"},
    {"key": "activityCompose", "kind": "maven", "repo": GOOGLE, "group": "androidx.activity",
     "artifact": "activity-compose", "line": "minor"},
    {"key": "lifecycleRuntimeKtx", "kind": "maven", "repo": GOOGLE,
     "group": "androidx.lifecycle", "artifact": "lifecycle-runtime-ktx", "line": "minor"},
    {"key": "jsch", "kind": "maven", "repo": CENTRAL, "group": "com.github.mwiede",
     "artifact": "jsch", "line": "major"},
    {"key": "zxing", "kind": "maven", "repo": CENTRAL, "group": "com.google.zxing",
     "artifact": "core", "line": "major"},
    {"key": "orgJson", "kind": "maven", "repo": CENTRAL, "group": "org.json",
     "artifact": "json", "line": "major"},
    {"key": "junit", "kind": "maven", "repo": CENTRAL, "group": "junit",
     "artifact": "junit", "line": "major"},
]

QUALIFIERS = {
    "dev": -6,
    "snapshot": -5,
    "alpha": -4,
    "a": -4,
    "eap": -3,
    "beta": -3,
    "b": -3,
    "milestone": -2,
    "m": -2,
    "rc": -1,
    "cr": -1,
    "": 0,
    "sp": 1,
}

TOKEN_SPLIT = re.compile(r"[.\-_+]")


def _tokens(version: str) -> "list":
    """Split a version into comparable tokens, Maven's way."""
    version = version.strip()
    # A leading `v` (GitHub tags) is not part of the version.
    if version[:1] in ("v", "V") and version[1:2].isdigit():
        version = version[1:]
    out: "list" = []
    for raw in TOKEN_SPLIT.split(version):
        if raw == "":
            continue
        # `beta01`, `rc1`, `alpha2`: the digits belong to the qualifier's own counter, so the
        # qualifier and the number are two tokens that both have to be compared in order.
        match = re.match(r"^([A-Za-z]+)(\d*)$", raw)
        if match:
            name = match.group(1).lower()
            out.append(("q", QUALIFIERS.get(name, None), name))
            if match.group(2):
                out.append(("n", int(match.group(2)), ""))
            continue
        if raw.isdigit():
            out.append(("n", int(raw), ""))
            continue
        match = re.match(r"^(\d+)([A-Za-z]+)(\d*)$", raw)
        if match:
            out.append(("n", int(match.group(1)), ""))
            name = match.group(2).lower()
            out.append(("q", QUALIFIERS.get(name, None), name))
            if match.group(3):
                out.append(("n", int(match.group(3)), ""))
            continue
        out.append(("q", None, raw))
    return out


# A token that stands for "the version just ended here": 1.0 == 1.0.0, and 1.0-rc1 < 1.0.
_RELEASE = ("q", 0, "")


def _cmp_token(left, right) -> int:
    """Maven's token order: numbers beat qualifiers, a release beats rc, unknown beats release."""
    lk, lv, ls = left
    rk, rv, rs = right
    if lk == "n" and rk == "n":
        return (lv > rv) - (lv < rv)
    if lk == "n" and rk == "q":
        # A number beats a qualifier — except zero, which *is* the release marker, so 1.0.0-beta00
        # still sorts below 1.0.0 and 1.0.0 sorts exactly level with 1.0.
        if lv == 0:
            return _cmp_token(_RELEASE, right)
        return 1
    if lk == "q" and rk == "n":
        return -_cmp_token(right, left)
    # Both qualifiers. A known qualifier outranks an unknown one; unknown ones compare by name and
    # sort *after* a release, which is Maven's rule for "1.0-abc" > "1.0".
    lrank = lv if lv is not None else 2
    rrank = rv if rv is not None else 2
    if lrank != rrank:
        return (lrank > rrank) - (lrank < rrank)
    return (ls > rs) - (ls < rs)


def compare(left: str, right: str) -> int:
    a, b = _tokens(left), _tokens(right)
    for i in range(max(len(a), len(b))):
        result = _cmp_token(
            a[i] if i < len(a) else _RELEASE,
            b[i] if i < len(b) else _RELEASE,
        )
        if result:
            return result
    return 0


def _numeric_head(version: str) -> "list[int]":
    head = re.match(r"^\d+(\.\d+)*", version.strip().lstrip("vV"))
    if not head:
        return []
    return [int(part) for part in head.group(0).split(".")]


def within_line(candidate: str, pin: str, line: str) -> bool:
    """May the autopilot move `pin` to `candidate` without a human deciding?"""
    if not pin or compare(candidate, pin) <= 0:
        return False
    candidate_head, pin_head = _numeric_head(candidate), _numeric_head(pin)
    if not candidate_head or not pin_head:
        return False
    if line == "major":
        return candidate_head[0] == pin_head[0]
    if line == "minor":
        return (candidate_head + [0])[:2] == (pin_head + [0])[:2]
    if line == "year":
        return (candidate_head + [0])[:1] == (pin_head + [0])[:1]
    return False


def http_text(url: str, timeout: float = 25.0) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": "MarbleNG-toolchain-autopilot"})
    with urllib.request.urlopen(request, timeout=timeout) as response:  # nosec - public metadata
        return response.read().decode("utf-8", "replace")


def maven_versions(repo: str, group: str, artifact: str) -> "list[str]":
    path = group.replace(".", "/")
    text = http_text(f"{repo}/{path}/{artifact}/maven-metadata.xml")
    return re.findall(r"<version>([^<]+)</version>", text)


def gradle_versions() -> "list[str]":
    data = json.loads(http_text(GRADLE_SERVICES))
    # Nightly snapshots are build artifacts of a branch, not releases: the autopilot tracks
    # published versions only.
    return [entry["version"] for entry in data if not entry.get("snapshot")]


def load_pins() -> "dict[str,str]":
    pins: "dict[str,str]" = {}
    if not LOCK.is_file():
        return pins
    for line in LOCK.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        pins[key.strip()] = value.strip()
    return pins


def newest(versions: "list[str]", pin: str, line: str) -> "tuple[str,str]":
    """Return (newest in line, newest overall)."""
    usable = [v for v in versions if re.match(r"^\d", v.lstrip("vV"))]
    usable = sorted(set(usable), key=_VersionKey())
    in_line = [v for v in usable if within_line(v, pin, line)]
    overall = usable[-1] if usable else pin
    # "Newest overall" only means something when it is newer than the pin *and* outside the line;
    # otherwise the in-line answer is the better one to report as the target.
    best_in_line = in_line[-1] if in_line else pin
    return best_in_line, overall


class _VersionKey:
    """Sort helper: `sorted(..., key=_VersionKey())`."""

    def __call__(self, version: str):
        return _Sortable(version)


class _Sortable:
    __slots__ = ("version",)

    def __init__(self, version: str) -> None:
        self.version = version

    def __lt__(self, other: "_Sortable") -> bool:
        return compare(self.version, other.version) < 0

    def __eq__(self, other: object) -> bool:
        return isinstance(other, _Sortable) and compare(self.version, other.version) == 0


def resolve(offline: bool = False) -> "list[dict]":
    pins = load_pins()
    report: "list[dict]" = []
    for component in COMPONENTS:
        key = component["key"]
        pin = pins.get(key, "")
        entry = {
            "key": key,
            "line": component["line"],
            "pin": pin,
            "target": pin,
            "newest": pin,
            "newerMajor": "",
            "error": "",
        }
        if offline:
            report.append(entry)
            continue
        try:
            if component["kind"] == "gradle":
                versions = gradle_versions()
            else:
                versions = maven_versions(component["repo"], component["group"], component["artifact"])
            target, overall = newest(versions, pin, component["line"])
            entry["target"] = target
            entry["newest"] = overall
            if overall != target and compare(overall, pin) > 0:
                entry["newerMajor"] = overall
        except (urllib.error.URLError, OSError, ValueError, KeyError) as failure:
            entry["error"] = f"{type(failure).__name__}: {failure}"
        report.append(entry)
    return report


def write_properties(report: "list[dict]") -> "list[str]":
    pins = load_pins()
    changed: "list[str]" = []
    for entry in report:
        if entry["error"] or not entry["target"]:
            continue
        if pins.get(entry["key"]) != entry["target"]:
            changed.append(f"{entry['key']} {pins.get(entry['key'], '(none)')} -> {entry['target']}")
            pins[entry["key"]] = entry["target"]
    header = (
        "# MARBLE_TOOLCHAIN_AUTOPILOT_V211 — the one file every build reads for versions.\n"
        "#\n"
        "# Written by `scripts/resolve-toolchain.py --write` (the scheduled toolchain autopilot)\n"
        "# and read by `settings.gradle.kts`, `gradlew` and `.github/workflows/*.yml`. Nothing else\n"
        "# in the tree names a version: a second copy is a version that drifts.\n"
        "#\n"
        "# `line` in the resolver decides how far the autopilot may move a value on its own:\n"
        "# Gradle/AGP/Kotlin move inside their major, androidx inside its minor, the Compose BOM\n"
        "# inside its year. Anything bigger is reported as `newerMajor` for a human to take.\n"
    )
    body = "\n".join(f"{key}={pins[key]}" for key in sorted(pins))
    LOCK.parent.mkdir(parents=True, exist_ok=True)
    LOCK.write_text(f"{header}\n{body}\n", encoding="utf-8")
    return changed


def main() -> int:
    parser = argparse.ArgumentParser(description="Resolve the newest pre-release toolchain versions.")
    parser.add_argument("--write", action="store_true", help="write gradle/toolchain.properties")
    parser.add_argument("--offline", action="store_true", help="report the pins, touch no network")
    parser.add_argument("--json", metavar="PATH", help="also write the report as JSON")
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()

    report = resolve(offline=args.offline)

    if args.json:
        Path(args.json).write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    if not args.quiet:
        width = max(len(entry["key"]) for entry in report)
        for entry in report:
            state = "latest" if entry["target"] == entry["pin"] else "update"
            note = ""
            if entry["error"]:
                state, note = "unreachable", entry["error"]
            elif entry["newerMajor"]:
                note = f"(newer line available: {entry['newerMajor']})"
            print(f"{entry['key']:<{width}}  {state:<11} {entry['pin']} -> {entry['target']} {note}".rstrip())

    if args.write:
        changed = write_properties(report)
        if changed:
            print("MARBLE_TOOLCHAIN_AUTOPILOT wrote gradle/toolchain.properties:")
            for line in changed:
                print(f"  {line}")
        else:
            print("MARBLE_TOOLCHAIN_AUTOPILOT: everything already at the newest in-line version.")

    # An unreachable repository is a report, never a failure: the pins in the tree are the build's
    # floor, and a network problem in CI must not turn into a red release.
    return 0


if __name__ == "__main__":
    sys.exit(main())
