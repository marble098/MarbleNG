#!/usr/bin/env bash
set -euo pipefail

# MARBLE_LATEST_VERSION_RULE_V235 — the one update rule, pinned offline.
#
# Runs scripts/update-core-lock.sh --resolve-only against recorded release
# lists (scripts/testdata/core-lock-rule/) and asserts the winner of each
# branch of the rule. No network: the fixtures are the whole world.
#
#   Xray      stable published AFTER a beta   → the stable wins (the V235 case:
#             the old `prerelease` channel pinned v26.9.30-beta forever while
#             stable v26.10.21 sat newer beside it)
#   HEV       beta published after a stable   → the beta wins (same rule, other side)
#   sing-box  a draft newer than everything   → ignored; newest *published* wins
#   tun2socks two releases, same instant      → the higher version breaks the tie
#             (v2.7.0 over v2.7.0-beta)

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

lock_before="$(sha256sum core-lock.json)"
out="$(MARBLE_RELEASES_FIXTURE_DIR="$ROOT/scripts/testdata/core-lock-rule" \
    ./scripts/update-core-lock.sh --resolve-only)"

echo "$out"

expect() {
    local key="$1" tag="$2" at="$3" pre="$4"
    local line="RESOLVED"$'\t'"$key"$'\t'"$tag"$'\t'"$at"$'\t'"$pre"
    if grep -qF "$line" <<< "$out"; then
        echo "[PASS] $key → $tag ($at, prerelease=$pre)"
    else
        echo "[FAIL] $key: expected '$line'" >&2
        exit 1
    fi
}

# A stable published after the beta is the update — date decides, label does not.
expect xray      "v26.10.21"            "2026-10-21T12:07:11Z" "false"
# A beta published after the stable is the update — same rule, other side.
expect hev       "2.20.0-beta1"         "2026-10-26T09:00:00Z" "true"
# A draft is not available to anyone, even when it is the newest thing in the list.
expect singbox   "v1.14.3-extended-2.7.3" "2026-10-19T16:41:59Z" "false"
# Same instant: the higher version wins the tie — the release over its own beta.
expect tun2socks "v2.7.0"               "2026-07-12T09:48:53Z" "false"

# --resolve-only must not have written anything.
if [[ "$(sha256sum core-lock.json)" != "$lock_before" ]]; then
    echo "[FAIL] --resolve-only modified core-lock.json" >&2
    exit 1
fi

# The backwards walk is allowed (the newest available is the newest available) but never
# silent: pin a version from the future in a scratch tree and demand the ::warning::.
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT
mkdir -p "$scratch/scripts"
cp scripts/update-core-lock.sh "$scratch/scripts/"
jq '.xray.tag = "v99.0.0"' core-lock.json > "$scratch/core-lock.json"
warned="$(MARBLE_RELEASES_FIXTURE_DIR="$ROOT/scripts/testdata/core-lock-rule" \
    bash "$scratch/scripts/update-core-lock.sh" --resolve-only 2>&1)"
if grep -q "::warning::Xray winner v26.10.21 sorts below the current pin v99.0.0" <<< "$warned"; then
    echo "[PASS] a backwards walk warns loudly instead of moving silently"
else
    echo "[FAIL] expected the downgrade ::warning:: for a v99.0.0 pin" >&2
    exit 1
fi

echo "[OK] MARBLE_LATEST_VERSION_RULE_V235 — newest published wins on every branch."
