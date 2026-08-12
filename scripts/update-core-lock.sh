#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; LOCK="$ROOT/core-lock.json"
need(){ command -v "$1" >/dev/null || { echo "missing: $1" >&2; exit 1; };}; need curl;need jq
xray="$(curl -fsSL --retry 3 'https://api.github.com/repos/XTLS/Xray-core/releases?per_page=30' | jq -r '[.[]|select(.draft==false and .prerelease==true)][0].tag_name')"
hev="$(curl -fsSL --retry 3 'https://api.github.com/repos/heiher/hev-socks5-tunnel/releases?per_page=30' | jq -r '[.[]|select(.draft==false)][0].tag_name')"
[[ -n "$xray" && "$xray" != null && -n "$hev" && "$hev" != null ]]
jq --arg x "$xray" --arg h "$hev" --arg d "$(date -u +%F)" '.xray.tag=$x|.hev.tag=$h|.updated=$d' "$LOCK" > "$LOCK.tmp" && mv "$LOCK.tmp" "$LOCK"
printf 'Xray=%s\nHEV=%s\n' "$xray" "$hev"
