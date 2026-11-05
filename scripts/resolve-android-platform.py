#!/usr/bin/env python3
"""Resolve the stable SDK platform package for an Android compileSdk API level."""

from __future__ import annotations

import re
import sys

PACKAGE_RE = re.compile(r"^\s*(platforms;android-([^\s|]+))\s*\|")


def listed_platforms(text: str) -> set[str]:
    """Return package IDs in sdkmanager --list's Installed/Available tables."""
    return {
        match.group(1)
        for line in text.splitlines()
        if (match := PACKAGE_RE.match(line))
    }


def stable_candidates(api: str, package_ids: set[str]) -> list[str]:
    """Prefer the .0 naming, then the highest numeric minor, then legacy API name."""
    prefix = f"platforms;android-{api}"
    minor_re = re.compile(rf"^{re.escape(prefix)}\.(\d+)$")
    minors: list[tuple[int, str]] = []
    for package_id in package_ids:
        match = minor_re.fullmatch(package_id)
        if match:
            minors.append((int(match.group(1)), package_id))

    candidates: list[str] = []
    zero = f"{prefix}.0"
    if zero in package_ids:
        candidates.append(zero)
    candidates.extend(
        package_id
        for _, package_id in sorted(minors, reverse=True)
        if package_id != zero
    )
    if prefix in package_ids:
        candidates.append(prefix)
    return candidates


def resolve(api: str, text: str) -> tuple[str | None, list[str], list[str]]:
    packages = listed_platforms(text)
    all_platforms = sorted(packages)
    candidates = stable_candidates(api, packages)
    return (candidates[0] if candidates else None, candidates, all_platforms)


def main() -> int:
    if len(sys.argv) != 2 or not re.fullmatch(r"\d+", sys.argv[1]):
        print(f"Usage: {sys.argv[0]} <compileSdk-api-number>", file=sys.stderr)
        return 2

    api = sys.argv[1]
    selected, candidates, all_platforms = resolve(api, sys.stdin.read())
    if selected:
        print(selected)
        print(
            f"Resolved compileSdk {api} to {selected} "
            f"(stable candidates: {', '.join(candidates)})",
            file=sys.stderr,
        )
        return 0

    print(
        f"No stable Android SDK platform package found for compileSdk {api}.",
        file=sys.stderr,
    )
    print(
        "Platforms listed by sdkmanager: "
        + (", ".join(all_platforms) if all_platforms else "(none)"),
        file=sys.stderr,
    )
    print(
        "Expected platforms;android-"
        f"{api}.0 or a numeric platforms;android-{api}.<minor> "
        "package (preview, beta and extension packages are ignored).",
        file=sys.stderr,
    )
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
