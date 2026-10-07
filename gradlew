#!/usr/bin/env bash
set -euo pipefail
# MARBLE_TOOLCHAIN_AUTOPILOT_V211 — the distribution comes from the same file the build reads.
D="$(cd "$(dirname "$0")" && pwd)"
V="$(sed -n 's/^gradle=//p' "$D/gradle/toolchain.properties" 2>/dev/null | tail -n 1)"
V="${V:-9.5.1}"
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
D="$D/.gradle-dist"; B="$D/gradle-$V/bin/gradle"
if [[ ! -x "$B" ]]; then command -v curl >/dev/null && command -v unzip >/dev/null || { echo 'Install curl and unzip.' >&2; exit 1; }; mkdir -p "$D"; curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$V-bin.zip" -o "$D/gradle.zip"; unzip -q "$D/gradle.zip" -d "$D"; fi
exec "$B" "$@"
