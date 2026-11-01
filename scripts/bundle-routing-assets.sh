#!/usr/bin/env bash
# MARBLE_ROUTING_ASSET_PIN_V257 — bundle the Iran routing data from ONE commit, not from a branch.
#
# The failure this file exists for: on 2026-11-01 the release build died at "Bundle verified Iran
# routing assets" with a bare `exit 1` and no message. The data source (`Chocolate4U/
# Iran-v2ray-rules`) publishes by *force-pushing* a single orphan commit onto its `release` branch,
# so that branch is not a moving reference — it is a new object graph every publish, and
# `raw.githubusercontent.com/OWNER/REPO/release/geoip.dat` is cached under the *branch* path for
# minutes on top of that. The old step fetched `geoip.dat` (16.5 MB) and then `geoip.dat.sha256sum`
# (84 bytes) a second later, by branch path. When a publish landed between the two requests the
# binary came from one snapshot and the digest from the next, the comparison failed, and the step
# said only "exit 1". The upstream commit `202611011127` was published at 11:29:10Z; our build was
# fetching at 11:29:10Z–11:29:33Z. Nineteen consecutive builds before it were green.
#
# The fix is not a longer timeout and not a retry of the same torn read: resolve the branch tip to a
# commit SHA once, then fetch every file *by that SHA*. A commit path is immutable — a publish that
# lands mid-flight cannot change the bytes we are about to compare, and a force-push cannot either.
# If the snapshot still fails verification (a truncated download, an upstream that is itself
# inconsistent, a CDN that served half a file), the whole snapshot is retried with a freshly
# resolved SHA, into a staging directory, so a failed attempt never leaves a bad `.dat` in the tree.
#
#   bash scripts/bundle-routing-assets.sh
#
# Environment (all optional; the defaults are production):
#   GITHUB_TOKEN                  raises the API rate limit from 60/h to 5000/h
#   MARBLE_ROUTING_REPO           Chocolate4U/Iran-v2ray-rules
#   MARBLE_ROUTING_REF            release
#   MARBLE_ROUTING_DEST           app/src/main/assets/xray
#   MARBLE_ROUTING_SHA            skip the API and use this commit (the fixture test does)
#   MARBLE_ROUTING_ATTEMPTS       3
#   MARBLE_ROUTING_BACKOFF_SECONDS 5
#   MARBLE_ROUTING_RAW_BASE       https://raw.githubusercontent.com
#   MARBLE_ROUTING_CDN_BASE       https://cdn.jsdelivr.net/gh
#   MARBLE_ROUTING_API_BASE       https://api.github.com

set -euo pipefail

REPO="${MARBLE_ROUTING_REPO:-Chocolate4U/Iran-v2ray-rules}"
REF="${MARBLE_ROUTING_REF:-release}"
DEST="${MARBLE_ROUTING_DEST:-app/src/main/assets/xray}"
RAW_BASE="${MARBLE_ROUTING_RAW_BASE:-https://raw.githubusercontent.com}"
CDN_BASE="${MARBLE_ROUTING_CDN_BASE:-https://cdn.jsdelivr.net/gh}"
API_BASE="${MARBLE_ROUTING_API_BASE:-https://api.github.com}"
ATTEMPTS="${MARBLE_ROUTING_ATTEMPTS:-3}"
BACKOFF="${MARBLE_ROUTING_BACKOFF_SECONDS:-5}"
PINNED_SHA="${MARBLE_ROUTING_SHA:-}"

# The two files the Xray router actually reads, plus the digests that prove they are the published
# pair. Order matters only for the log: geoip is the big one and the likeliest to straddle a publish.
ASSETS=(geoip.dat geosite.dat)

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

say() { printf '%s\n' "$*"; }
warn() { printf '[WARN] %s\n' "$*" >&2; }
fail() { printf '[ERR] %s\n' "$*" >&2; }

# --- resolve the branch tip to an immutable commit ------------------------------------------------

resolve_sha() {
    local body="" sha=""
    local -a args=(
        --fail --silent --show-error --location
        --retry 3 --retry-delay 2 --retry-all-errors
        --connect-timeout 15 --max-time 60
    )
    if [ -n "${GITHUB_TOKEN:-}" ]; then
        args+=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
    fi
    args+=(-H "Accept: application/vnd.github+json" -H "User-Agent: marble-ng-build")

    if ! body="$(curl "${args[@]}" "$API_BASE/repos/$REPO/branches/$REF")"; then
        warn "could not read $API_BASE/repos/$REPO/branches/$REF"
        return 1
    fi

    if command -v jq >/dev/null 2>&1; then
        sha="$(printf '%s' "$body" | jq -r '.commit.sha // empty')"
    elif command -v python3 >/dev/null 2>&1; then
        sha="$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("commit",{}).get("sha",""))')"
    fi

    if [[ ! "$sha" =~ ^[0-9a-f]{40}$ ]]; then
        warn "branch $REF did not resolve to a 40-character commit (got '${sha:-}')"
        return 1
    fi
    printf '%s' "$sha"
}

# --- one file, primary then mirror, into the given directory --------------------------------------

fetch_one() {
    local output="$1"
    shift
    local tmp="$output.part"
    local url
    rm -f "$tmp"
    for url in "$@"; do
        if curl --fail --location --silent --show-error \
            --retry 4 --retry-delay 2 --retry-all-errors \
            --connect-timeout 15 --max-time 240 \
            --output "$tmp" "$url"; then
            if [ -s "$tmp" ]; then
                mv -f "$tmp" "$output"
                return 0
            fi
            warn "empty body from $url"
        else
            warn "download failed: $url"
        fi
        rm -f "$tmp"
    done
    return 1
}

# --- verification ---------------------------------------------------------------------------------

digest_of() { awk 'NF {print $1; exit}' "$1" | tr 'A-F' 'a-f'; }

# Fetches the four files of one snapshot and proves each `.dat` is the one its own `.sha256sum`
# names. Nothing is written outside $1, so a torn or half-broken snapshot can be thrown away whole.
stage_snapshot() {
    local sha="$1" dir="$2" name
    mkdir -p "$dir"
    for name in "${ASSETS[@]}"; do
        fetch_one "$dir/$name" \
            "$RAW_BASE/$REPO/$sha/$name" \
            "$CDN_BASE/$REPO@$sha/$name" || return 1
        fetch_one "$dir/$name.sha256sum" \
            "$RAW_BASE/$REPO/$sha/$name.sha256sum" \
            "$CDN_BASE/$REPO@$sha/$name.sha256sum" || return 1
    done
    verify_snapshot "$dir"
}

verify_snapshot() {
    local dir="$1" name expected actual size bad=0
    for name in "${ASSETS[@]}"; do
        if [ ! -s "$dir/$name" ] || [ ! -s "$dir/$name.sha256sum" ]; then
            warn "snapshot is missing $name or its digest"
            bad=1
            continue
        fi
        expected="$(digest_of "$dir/$name.sha256sum")"
        actual="$(sha256sum "$dir/$name" | awk '{print $1}')"
        size="$(stat -c '%s' "$dir/$name")"
        if [ -z "$expected" ]; then
            warn "$name.sha256sum carries no digest"
            bad=1
        elif [ "$actual" != "$expected" ]; then
            # The message the old step never printed. This line in a future failure means the
            # upstream pair itself is inconsistent, not that we read it at a bad moment.
            warn "$name: expected $expected, got $actual"
            bad=1
        elif [ "$size" -le 1024 ]; then
            warn "$name is only ${size}B; that is not a routable dataset"
            bad=1
        fi
    done
    [ "$bad" -eq 0 ]
}

# --- the loop -------------------------------------------------------------------------------------

mkdir -p "$DEST"

sha=""
verified=0
attempt=0
while [ "$attempt" -lt "$ATTEMPTS" ]; do
    attempt=$((attempt + 1))
    stage="$WORK/snapshot-$attempt"

    if [ -n "$PINNED_SHA" ]; then
        sha="$PINNED_SHA"
    elif ! sha="$(resolve_sha)"; then
        sha=""
    fi

    if [ -z "$sha" ]; then
        warn "attempt $attempt/$ATTEMPTS: no commit to pin"
    else
        say "Snapshot $REPO@$sha (attempt $attempt/$ATTEMPTS)"
        if stage_snapshot "$sha" "$stage"; then
            verified=1
            break
        fi
        warn "attempt $attempt/$ATTEMPTS: snapshot $sha did not verify"
    fi

    if [ "$attempt" -lt "$ATTEMPTS" ]; then
        say "Retrying with a freshly resolved commit in ${BACKOFF}s"
        sleep "$BACKOFF"
    fi
done

if [ "$verified" -ne 1 ]; then
    fail "no verified routing snapshot after $ATTEMPTS attempt(s); nothing was installed"
    exit 1
fi

for name in "${ASSETS[@]}"; do
    install -m 0644 "$stage/$name" "$DEST/$name"
    say "[OK] $name $(sha256sum "$DEST/$name" | awk '{print $1}')"
done
say "Pinned snapshot: $REPO@$sha"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
        echo "### Iran routing assets"
        echo
        echo "Pinned to \`$REPO@$sha\` (branch \`$REF\` resolved once, then fetched by commit)."
        echo
        echo "| file | sha256 |"
        echo "| --- | --- |"
        for name in "${ASSETS[@]}"; do
            echo "| $name | \`$(sha256sum "$DEST/$name" | awk '{print $1}')\` |"
        done
    } >> "$GITHUB_STEP_SUMMARY"
fi
