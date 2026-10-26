#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MarbleNG Core Lock Updater
#
# Resolves the newest upstream release of every pinned runtime core and writes
# it back into core-lock.json. The build workflow reads core-lock.json to
# decide which Xray, HEV, sing-box and tun2socks source to compile into the APK.
#
# MARBLE_LATEST_VERSION_RULE_V235 — there is exactly ONE update rule:
#
#     Upgrade to the newest published version that exists — beta, release, or
#     any other label upstream put on it.
#
# "Newest published" is a statement about time, not about a channel. For each
# core the updater:
#
#   1. lists the upstream GitHub releases and drops drafts (an unpublished
#      release is not available to anyone);
#   2. picks the maximum by (published_at, version) — the timestamp is the
#      primary key, the version only breaks a same-instant tie;
#   3. records the winner's tag, the commit the tag resolves to, its
#      published_at and its prerelease label as provenance. The label is
#      recorded, never consulted: if a stable release is published after a
#      beta, the stable is the update; if a beta is published after the
#      stable, the beta is. Whichever came last is the newest version
#      that exists.
#
# The pre-V235 `channel` vocabulary ("prerelease" / "beta" / "latest-release")
# is gone, and with it the defect it encoded: Xray was pinned to its
# `prerelease` channel, so when XTLS published stable v26.10.21 (2026-10-21)
# after beta v26.9.30 (2026-09-30), the updater kept "upgrading" to the older
# beta. A channel filter can never see a newer build that wears the wrong
# label; a timestamp has no such blind spot.
#
# Two safety properties complete the rule without adding a second one:
#
#   * If the winner's *version* sorts below the pin it replaces (upstream
#     deleted or unpublished its newest release), the move still happens —
#     the newest available is the newest available — but a ::warning:: is
#     printed so the bot's commit never quietly walks a core backwards.
#   * Nothing is written from a partial answer: a core whose tag, commit or
#     host-test digest cannot be resolved fails the whole run before
#     core-lock.json is touched.
#
# `published_at` falls back to `created_at` for a payload that omits it, and
# a release whose dates do not parse is ignored. GitHub serves releases
# newest-created first; up to three pages are read so a release that sat as
# a draft for months and was published today is still inside the window.
#
# Modes:
#   ./scripts/update-core-lock.sh                  resolve + write the lock
#   ./scripts/update-core-lock.sh --resolve-only   print the decision table, write nothing
#
# MARBLE_RELEASES_FIXTURE_DIR=<dir> makes the resolver read
# `<dir>/<owner>__<repo>.json` release lists instead of the GitHub API, so
# scripts/test-core-lock-rule.sh can pin the rule's branches deterministically
# in PR verification with no network involved.
# ==============================================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOCK="$ROOT/core-lock.json"

need() { command -v "$1" >/dev/null || { echo "missing: $1" >&2; exit 1; }; }
need curl
need jq
need grep

[[ -f "$LOCK" ]] || { echo "core-lock.json not found: $LOCK" >&2; exit 1; }

RESOLVE_ONLY=0
[[ "${1:-}" == "--resolve-only" ]] && RESOLVE_ONLY=1

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
# The one version order, shared by the picker (same-instant tie-break) and the
# downgrade warning. Maven-style, matching scripts/resolve-toolchain.py and
# the app's LatestReleaseRule: numbers compare numerically, a known
# pre-release qualifier sorts below the release it precedes (2.6.0-beta <
# 2.6.0), an unknown qualifier sorts above it (1.14.3-extended > 1.14.3), and
# an appended release marker makes "the version simply ended" explicit.
# ------------------------------------------------------------------------------
JQ_VKEY='
def vkey:
  (sub("^[vV]"; "") | split("+")[0])
  | [ scan("[0-9]+|[A-Za-z]+") ]
  | map(
      if test("^[0-9]+$") then [2, tonumber, ""]
      else (ascii_downcase) as $w
        | if ($w | test("^(dev|snapshot|alpha|a|eap|beta|b|pre|preview|milestone|m|rc|cr)$"))
          then [0, 0, $w] else [1, 0, $w] end
      end)
  + [[1, 0, ""]];
'

# ------------------------------------------------------------------------------
# fetch_releases <owner/repo> → one JSON array of release objects.
# ------------------------------------------------------------------------------
fetch_releases() {
    local repo="$1"

    if [[ -n "${MARBLE_RELEASES_FIXTURE_DIR:-}" ]]; then
        local fixture="${MARBLE_RELEASES_FIXTURE_DIR}/${repo//\//__}.json"
        [[ -f "$fixture" ]] || { echo "::error::missing release fixture: $fixture" >&2; return 1; }
        cat "$fixture"
        return
    fi

    # Up to three pages (≈300 releases): `published_at` is the primary key, but
    # the API orders by creation, so a long-drafted release published today can
    # sit behind newer-created entries.
    local pages=() page json count
    for page in 1 2 3; do
        json="$(curl -fsSL --retry 4 --retry-delay 3 \
            "${GITHUB_API_HEADERS[@]}" \
            "https://api.github.com/repos/${repo}/releases?per_page=100&page=${page}")" || return 1
        pages+=("$json")
        count="$(jq 'length' <<< "$json")"
        [[ "$count" -lt 100 ]] && break
    done
    printf '%s\n' "${pages[@]}" | jq -cs 'add // []'
}

# ------------------------------------------------------------------------------
# pick_latest <releases-json> →
#   winner-tag \t winner-published_at \t winner-prerelease \t
#   runner-tag \t runner-published_at \t runner-prerelease      (runner may be empty)
#
# THE rule: drafts out, then the maximum by (published_at, version). GitHub
# timestamps are UTC `…Z`, for which lexicographic order is chronological.
# ------------------------------------------------------------------------------
pick_latest() {
    jq -r "
        $JQ_VKEY
        [ .[]
          | select(.draft == false)
          | { tag: .tag_name,
              at: (.published_at // .created_at // \"\"),
              prerelease: (.prerelease // false) }
          | select(.tag != null and .tag != \"\" and .at != \"\") ]
        | sort_by([.at, (.tag | vkey)])
        | if length == 0 then empty
          else [ .[-1].tag, .[-1].at, .[-1].prerelease,
                 (.[-2].tag // \"\"), (.[-2].at // \"\"),
                 (if .[-2] then .[-2].prerelease else \"\" end) ]
          | @tsv end
    " <<< "$1"
}

# version_less <a> <b> → exit 0 iff version a sorts strictly below version b.
# (Not `jq -e … empty`: with no output at all jq exits non-zero, which would
# make "a is older" indistinguishable from "a is newer" — a warning that can
# never fire. Print the boolean and compare it.)
version_less() {
    [[ "$(jq -nr --arg a "$1" --arg b "$2" "$JQ_VKEY"'
        if ($a | vkey) < ($b | vkey) then "yes" else "no" end')" == "yes" ]]
}

kind_of() { [[ "$1" == "true" ]] && echo "prerelease" || echo "release"; }

# ------------------------------------------------------------------------------
# Resolve every core under the one rule.
# ------------------------------------------------------------------------------
declare -A TAG AT PRE PIN

resolve_core() {
    local name="$1" key="$2" default_repo="$3"
    local repo pin json picked
    repo="$(jq -r ".${key}.repo // \"${default_repo}\"" "$LOCK")"
    pin="$(jq -r ".${key}.tag // \"\"" "$LOCK")"

    json="$(fetch_releases "$repo")" || {
        echo "::error::Could not list releases of $repo" >&2; exit 1;
    }
    picked="$(pick_latest "$json")"
    if [[ -z "$picked" ]]; then
        echo "::error::No published release found for $name ($repo)" >&2; exit 1
    fi

    local tag at pre rtag rat rpre
    IFS=$'\t' read -r tag at pre rtag rat rpre <<< "$picked"

    TAG[$key]="$tag"; AT[$key]="$at"; PRE[$key]="$pre"; PIN[$key]="$pin"

    echo "  $name"
    echo "    repo    $repo"
    echo "    pinned  ${pin:-∅}"
    echo "    newest  $tag  ($(kind_of "$pre"), published $at)"
    if [[ -n "$rtag" ]]; then
        if [[ "$rat" == "$at" ]]; then
            echo "    runner  $rtag  ($(kind_of "$rpre"), published $rat) — same instant, lower version, so it loses"
        else
            echo "    runner  $rtag  ($(kind_of "$rpre"), published $rat) — older, so it loses"
        fi
    fi
    if [[ -n "$pin" && "$tag" != "$pin" ]] && version_less "$tag" "$pin"; then
        echo "::warning::$name winner $tag sorts below the current pin $pin by version;" \
             "upstream's newest *published* release moved backwards (deleted or unpublished?)." \
             "Taking it anyway: the rule is the newest available."
    fi
}

echo "MARBLE_LATEST_VERSION_RULE_V235 — newest published version wins, whatever its label."
echo "Resolving upstream cores …"
resolve_core "Xray"      xray      "XTLS/Xray-core"
resolve_core "HEV"       hev       "heiher/hev-socks5-tunnel"
resolve_core "sing-box"  singbox   "shtorm-7/sing-box-extended"
resolve_core "tun2socks" tun2socks "xjasonlyu/tun2socks"

if [[ "$RESOLVE_ONLY" -eq 1 ]]; then
    echo ""
    echo "--resolve-only: decision table above; core-lock.json untouched."
    # Machine-readable tail for scripts/test-core-lock-rule.sh.
    for key in xray hev singbox tun2socks; do
        printf 'RESOLVED\t%s\t%s\t%s\t%s\n' "$key" "${TAG[$key]}" "${AT[$key]}" "${PRE[$key]}"
    done
    exit 0
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
#
# MARBLE_LATEST_VERSION_RULE_V235 — the commit anchor now covers all four
# cores, not two: the updater moves every pin by the same rule, so every pin
# gets the same proof. update-cores.yml asserts each cloned HEAD equals it.
# ------------------------------------------------------------------------------
resolve_tag_commit() {
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

# Historical name, kept because system-integrity-check.py pins it and because the
# sing-box pin is still the one that resolves through it.
resolve_singbox_commit() { resolve_tag_commit "$@"; }

declare -A COMMIT REPO
for pair in "xray Xray" "hev HEV" "singbox sing-box" "tun2socks tun2socks"; do
    key="${pair%% *}"; name="${pair##* }"
    REPO[$key]="$(jq -r ".${key}.repo" "$LOCK")"
    COMMIT[$key]="$(resolve_tag_commit "${REPO[$key]}" "${TAG[$key]}")"
    if [[ -z "${COMMIT[$key]}" ]]; then
        echo "::error::Could not resolve the $name commit for ${TAG[$key]}" >&2
        exit 1
    fi
done

singbox_tag="${TAG[singbox]}"
tun2socks_tag="${TAG[tun2socks]}"
singbox_repo="${REPO[singbox]}"
tun2socks_repo="${REPO[tun2socks]}"

singbox_digests="$(curl -fsSL --retry 4 "${GITHUB_API_HEADERS[@]}" \
    "https://api.github.com/repos/${singbox_repo}/releases/tags/${singbox_tag}" | jq -ce '
    [.assets[] | select(.name | test("-linux-amd64\\.tar\\.gz$"))
      | select(.digest | startswith("sha256:")) | {key:.name, value:(.digest | sub("^sha256:"; ""))}]
    | from_entries | select(length == 1)')"

if [[ -z "$singbox_digests" ]]; then
    echo "::error::No linux-amd64 host-test archive digest for sing-box $singbox_tag" >&2
    exit 1
fi

tun2socks_digests="$(curl -fsSL --retry 4 "${GITHUB_API_HEADERS[@]}" \
    "https://api.github.com/repos/${tun2socks_repo}/releases/tags/${tun2socks_tag}" | jq -ce '
    [.assets[] | select(.name | test("-linux-amd64\\.zip$"))
      | select(.digest | startswith("sha256:")) | {key:.name, value:(.digest | sub("^sha256:"; ""))}]
    | from_entries | select(length == 1)')"

if [[ -z "$tun2socks_digests" ]]; then
    echo "::error::No linux-amd64 host-test archive digest for tun2socks $tun2socks_tag" >&2
    exit 1
fi

# Preserve MarbleNG's own fields (singbox.patch); write the winners with their
# provenance, drop the retired channel vocabulary wherever it still sits, and
# stamp the one rule the file now answers to.
jq \
    --arg xt "${TAG[xray]}"      --arg xc "${COMMIT[xray]}" \
    --arg xp "${AT[xray]}"       --argjson xpre "${PRE[xray]}" \
    --arg ht "${TAG[hev]}"       --arg hc "${COMMIT[hev]}" \
    --arg hp "${AT[hev]}"        --argjson hpre "${PRE[hev]}" \
    --arg st "${TAG[singbox]}"   --arg sc "${COMMIT[singbox]}" \
    --arg sp "${AT[singbox]}"    --argjson spre "${PRE[singbox]}" \
    --argjson sha "$singbox_digests" \
    --arg tt "${TAG[tun2socks]}" --arg tc "${COMMIT[tun2socks]}" \
    --arg tp "${AT[tun2socks]}"  --argjson tpre "${PRE[tun2socks]}" \
    --argjson t2sha "$tun2socks_digests" \
    --arg d "$(date -u +%F)" \
    '.rule = "latest-published"
     | .xray.tag = $xt | .xray.commit = $xc | .xray.publishedAt = $xp | .xray.prerelease = $xpre
     | .hev.tag = $ht | .hev.commit = $hc | .hev.publishedAt = $hp | .hev.prerelease = $hpre
     | .singbox.tag = $st | .singbox.commit = $sc | .singbox.publishedAt = $sp | .singbox.prerelease = $spre
     | .singbox.sha256 = $sha
     | .tun2socks.tag = $tt | .tun2socks.commit = $tc | .tun2socks.publishedAt = $tp | .tun2socks.prerelease = $tpre
     | .tun2socks.sha256 = $t2sha
     | del(.xray.channel, .hev.channel, .singbox.channel, .tun2socks.channel)
     | .updated = $d' \
    "$LOCK" > "$LOCK.tmp" && mv -f "$LOCK.tmp" "$LOCK"

echo ""
echo "Resolved — newest published, whatever the label:"
for pair in "xray Xray" "hev HEV" "singbox sing-box" "tun2socks tun2socks"; do
    key="${pair%% *}"; name="${pair##* }"
    echo "  $name = ${TAG[$key]} ($(kind_of "${PRE[$key]}"), published ${AT[$key]}, commit ${COMMIT[$key]})"
done
echo ""
echo "NOTE: a new sing-box tag does not carry MarbleNG's backport. prepare-native.sh"
echo "      re-applies scripts/inject-singbox-android-fix.py and fails loudly if the"
echo "      anchors moved; read the pinned core again before forcing the build."
