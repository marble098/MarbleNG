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
#   xray.channel    = "prerelease"     → latest Xray pre-release (beta) tag
#   xray.channel    = "latest-release" → latest Xray stable tag
#   hev.channel     = "prerelease"     → latest HEV pre-release (beta) tag
#   hev.channel     = "latest-release" → latest HEV stable tag
#   singbox.channel = "beta"           → newest sing-box extended tag, whether
#                                        it is marked pre-release or not. The
#                                        extended fork publishes its newest
#                                        work as ordinary releases, so a strict
#                                        "prerelease" filter would pin an
#                                        abandoned March rc forever; "beta"
#                                        means "the newest build of the fork".
#   singbox.channel = "latest-release" → newest sing-box extended stable tag
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
#   channel = "beta"           → first non-draft release of any kind
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
        beta)
            jq -r '[.[] | select(.draft == false)][0].tag_name // empty' <<< "$json"
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
singbox_repo="$(jq -r '.singbox.repo // "shtorm-7/sing-box-extended"' "$LOCK")"
xray_channel="$(jq -r '.xray.channel // "prerelease"' "$LOCK")"
hev_channel="$(jq -r '.hev.channel // "latest-release"' "$LOCK")"
singbox_channel="$(jq -r '.singbox.channel // "beta"' "$LOCK")"

echo "Resolving upstream cores …"
echo "  Xray     repo=$xray_repo     channel=$xray_channel"
echo "  HEV      repo=$hev_repo      channel=$hev_channel"
echo "  sing-box repo=$singbox_repo  channel=$singbox_channel"

xray_tag="$(resolve_latest_tag "$xray_repo" "$xray_channel")"
hev_tag="$(resolve_latest_tag "$hev_repo" "$hev_channel")"
singbox_tag="$(resolve_latest_tag "$singbox_repo" "$singbox_channel")"

if [[ -z "$xray_tag" ]]; then
    echo "::error::Could not resolve Xray tag from $xray_repo (channel=$xray_channel)" >&2
    exit 1
fi
if [[ -z "$hev_tag" ]]; then
    echo "::error::Could not resolve HEV tag from $hev_repo (channel=$hev_channel)" >&2
    exit 1
fi
if [[ -z "$singbox_tag" ]]; then
    echo "::error::Could not resolve sing-box tag from $singbox_repo (channel=$singbox_channel)" >&2
    exit 1
fi

# ------------------------------------------------------------------------------
# MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — what the sing-box pin has to carry
#
# The Android core is compiled by scripts/prepare-native.sh from the pinned
# source (with the upstream nil-interface-monitor crash fix backported by
# scripts/inject-singbox-android-fix.py), so the integrity anchor is the COMMIT
# the tag resolves to, not an archive digest. A tag can be re-pointed; a commit
# cannot.
#
# One digest is still recorded: the linux-amd64 release archive that
# scripts/prepare-native-test-cores.sh installs for the host-side acceptance
# tests. That binary runs on a Linux runner, where a real interface monitor
# exists, so it needs no patch — it only has to accept the configs the pinned
# core is asked to accept.
# ------------------------------------------------------------------------------
resolve_singbox_commit() {
    local repo="$1" tag="$2" ref object_type object_sha

    ref="$(curl -fsSL --retry 4 "${GITHUB_API_HEADERS[@]}" \
        "https://api.github.com/repos/${repo}/git/ref/tags/${tag}")" || {
        return 0
    }

    object_type="$(jq -r '.object.type // empty' <<< "$ref")"
    object_sha="$(jq -r '.object.sha // empty' <<< "$ref")"

    # An annotated tag points at a tag object; dereference it to the commit.
    if [[ "$object_type" == "tag" && -n "$object_sha" ]]; then
        object_sha="$(curl -fsSL --retry 4 "${GITHUB_API_HEADERS[@]}" \
            "https://api.github.com/repos/${repo}/git/tags/${object_sha}" |
            jq -r '.object.sha // empty')"
    fi

    # Every refusal path prints nothing and succeeds: the caller turns an empty
    # answer into one explicit ::error:: instead of dying inside a substitution.
    [[ "$object_type" == "commit" || "$object_type" == "tag" ]] || return 0

    [[ "$object_sha" =~ ^[0-9a-f]{40}$ ]] || return 0

    printf '%s\n' "$object_sha"
}

singbox_commit="$(resolve_singbox_commit "$singbox_repo" "$singbox_tag")"

if [[ -z "$singbox_commit" ]]; then
    echo "::error::Could not resolve the sing-box commit for $singbox_tag" >&2
    exit 1
fi

singbox_digests="$(curl -fsSL --retry 4 "${GITHUB_API_HEADERS[@]}" \
    "https://api.github.com/repos/${singbox_repo}/releases/tags/${singbox_tag}" | jq -ce '
    [.assets[] | select(.name | test("-linux-amd64\\.tar\\.gz$"))
      | select(.digest | startswith("sha256:")) | {key:.name, value:(.digest | sub("^sha256:"; ""))}]
    | from_entries | select(length == 1)')"

if [[ -z "$singbox_digests" ]]; then
    echo "::error::No linux-amd64 host-test archive digest for sing-box $singbox_tag" >&2
    exit 1
fi

# Preserve the channel settings and MarbleNG's own patch level; update the tags,
# the resolved source commit and the timestamp.
jq \
    --arg x "$xray_tag" \
    --arg h "$hev_tag" \
    --arg s "$singbox_tag" \
    --arg c "$singbox_commit" \
    --argjson sha "$singbox_digests" \
    --arg d "$(date -u +%F)" \
    '.xray.tag = $x | .hev.tag = $h | .singbox.tag = $s | .singbox.commit = $c
     | .singbox.sha256 = $sha | .updated = $d' \
    "$LOCK" > "$LOCK.tmp" && mv -f "$LOCK.tmp" "$LOCK"

echo ""
echo "Resolved:"
echo "  Xray     = $xray_tag     ($xray_channel)"
echo "  HEV      = $hev_tag      ($hev_channel)"
echo "  sing-box = $singbox_tag  ($singbox_channel)"
echo "             source commit $singbox_commit (compiled locally, crash fix backported)"
echo ""
echo "NOTE: a new sing-box tag does not carry MarbleNG's backport. prepare-native.sh"
echo "      re-applies scripts/inject-singbox-android-fix.py and fails loudly if the"
echo "      anchors moved; read the pinned core again before forcing the build."
