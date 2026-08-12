#!/usr/bin/env bash
set -euo pipefail
V=9.5.1
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
D="$(cd "$(dirname "$0")" && pwd)/.gradle-dist"; B="$D/gradle-$V/bin/gradle"
if [[ ! -x "$B" ]]; then command -v curl >/dev/null && command -v unzip >/dev/null || { echo 'Install curl and unzip.' >&2; exit 1; }; mkdir -p "$D"; curl -fL --retry 3 "https://services.gradle.org/distributions/gradle-$V-bin.zip" -o "$D/gradle.zip"; unzip -q "$D/gradle.zip" -d "$D"; fi
exec "$B" "$@"
