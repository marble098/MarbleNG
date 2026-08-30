#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MarbleNG Core Lock Updater
#
# Resolves the newest upstream tags for every pinned runtime core and writes
# them back into core-lock.json.  The build workflow reads core-lock.json to
# decide which Xray and HEV source to compile into the APK.
#
# Channels
# --------
#   xray.channel = "prerelease"  → latest Xray pre-release (beta) tag
#   xray.channel = "latest-release" → latest Xray stable tag
#   hev.channel  = "prerelease"  → latest HEV pre-release (beta) tag
#   hev.channel  = "latest-release" → latest HEV stable tag
#
# When a channel entry is missing or unrecognised the script falls back to
# "latest-release" for safety.
# ==============================================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCK="$ROOT/core-lock.json"

need() { command -v "$1" >/dev/null || { echo "missing: $1" >&2; exit 1; }; }
need curl
need jq
need grep

[[ -f "$LOCK" ]] || { echo "core-lock.json not found: $LOCK" >&2; exit 1; }

# Authenticate API requests in CI so shared runner IP rate limits cannot turn a
# public upstream lookup into HTTP 403. Local callers may still run without a
# token and use GitHub's unauthenticated quota.
GITHUB_API_TOKEN="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
GITHUB_API_HEADERS=(-H "Accept: application/vnd.github+json")
if [[ -n "$GITHUB_API_TOKEN" ]]; then
    GITHUB_API_HEADERS+=(
        -H "Authorization: Bearer $GITHUB_API_TOKEN"
        -H "X-GitHub-Api-Version: 2022-11-28"
    )
fi

# ------------------------------------------------------------------------------
# resolve_latest_tag  <owner/repo>  <channel>
#
# Prints the tag_name of the newest matching release.
#   channel = "prerelease"     → first non-draft pre-release
#   channel = "latest-release" → first non-draft non-pre-release (stable)
# ------------------------------------------------------------------------------
resolve_latest_tag() {
    local repo="$1" channel="$2"
    local api="https://api.github.com/repos/${repo}/releases?per_page=30"

    local json
    json="$(curl -fsSL --retry 4 --retry-delay 3 \
        "${GITHUB_API_HEADERS[@]}" \
        "$api")" || {
        echo ""
        return
    }

    case "$channel" in
        prerelease)
            jq -r '[.[] | select(.draft == false and .prerelease == true)][0].tag_name // empty' <<< "$json"
            ;;
        *)
            jq -r '[.[] | select(.draft == false and .prerelease == false)][0].tag_name // empty' <<< "$json"
            ;;
    esac
}

# ------------------------------------------------------------------------------
# Read channels from core-lock.json (fall back to stable if absent)
# ------------------------------------------------------------------------------
xray_repo="$(jq -r '.xray.repo // "XTLS/Xray-core"' "$LOCK")"
hev_repo="$(jq -r '.hev.repo // "heiher/hev-socks5-tunnel"' "$LOCK")"
xray_channel="$(jq -r '.xray.channel // "prerelease"' "$LOCK")"
hev_channel="$(jq -r '.hev.channel // "latest-release"' "$LOCK")"

echo "Resolving upstream cores …"
echo "  Xray  repo=$xray_repo  channel=$xray_channel"
echo "  HEV   repo=$hev_repo   channel=$hev_channel"

xray_tag="$(resolve_latest_tag "$xray_repo" "$xray_channel")"
hev_tag="$(resolve_latest_tag "$hev_repo" "$hev_channel")"

if [[ -z "$xray_tag" ]]; then
    echo "::error::Could not resolve Xray tag from $xray_repo (channel=$xray_channel)" >&2
    exit 1
fi
if [[ -z "$hev_tag" ]]; then
    echo "::error::Could not resolve HEV tag from $hev_repo (channel=$hev_channel)" >&2
    exit 1
fi

# Preserve the channel settings; update only tags and the timestamp.
jq \
    --arg x "$xray_tag" \
    --arg h "$hev_tag" \
    --arg d "$(date -u +%F)" \
    '.xray.tag = $x | .hev.tag = $h | .updated = $d' \
    "$LOCK" > "$LOCK.tmp" && mv -f "$LOCK.tmp" "$LOCK"

echo ""
echo "Resolved:"
echo "  Xray = $xray_tag  ($xray_channel)"
echo "  HEV  = $hev_tag  ($hev_channel)"
