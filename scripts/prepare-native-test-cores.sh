#!/usr/bin/env bash
# Install the EXACT pinned Linux cores for offline loopback acceptance tests. This is not the
# Android packaging path and never changes core-lock.json. No binary is committed to the repo.
#
# MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — the sing-box binary installed here is the upstream
# linux-amd64 release artifact, NOT the Android binary the APK ships. That is deliberate:
#   * the APK's core is compiled by prepare-native.sh from the pinned source with the upstream
#     nil-interface-monitor fix backported, because the android/* release build SIGSEGVs in the
#     `direct` outbound of every config MarbleNG emits;
#   * this host core runs on a Linux runner, where sing-tun's netlink monitor exists, so the crash
#     is unreachable and the release artifact is the right thing to test config acceptance against;
#   * the backport itself is pinned by `go test` (see scripts/inject-singbox-android-fix.py), which
#     reproduces the missing monitor with a stub on any host.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${MARBLE_NATIVE_TEST_DIR:-${RUNNER_TEMP:-$ROOT/.cores}/marble-native-tests}"
mkdir -p "$DEST"
for tool in gh jq tar unzip; do command -v "$tool" >/dev/null; done
TAG="$(jq -r '.singbox.tag' "$ROOT/core-lock.json")"
REPO="$(jq -r '.singbox.repo' "$ROOT/core-lock.json")"
[[ "$REPO" == "shtorm-7/sing-box-extended" && "$TAG" =~ ^v[0-9A-Za-z._-]+$ ]]
ASSET="sing-box-${TAG#v}-linux-amd64.tar.gz"
if [[ ! -x "$DEST/$TAG/sing-box" ]]; then
    mkdir -p "$DEST/$TAG"
    gh release download "$TAG" --repo "$REPO" --pattern "$ASSET" --dir "$DEST/$TAG" --clobber
    EXPECTED="$(jq -er --arg asset "$ASSET" '.singbox.sha256[$asset]' "$ROOT/core-lock.json")"
    printf '%s  %s\n' "$EXPECTED" "$DEST/$TAG/$ASSET" | sha256sum --check --status
    tar -xzf "$DEST/$TAG/$ASSET" --strip-components=1 -C "$DEST/$TAG"
    chmod +x "$DEST/$TAG/sing-box"
fi
export MARBLE_SINGBOX_BINARY="$DEST/$TAG/sing-box"
"$MARBLE_SINGBOX_BINARY" version | grep -F "${TAG#v}"

XRAY_TAG="$(jq -r '.xray.tag' "$ROOT/core-lock.json")"
# verify.yml has already built the patched pinned Xray source for its realtime smoke test.
if [[ ! -x "${MARBLE_XRAY_BINARY:-}" ]]; then
    mkdir -p "$DEST/xray-$XRAY_TAG"
    if [[ ! -x "$DEST/xray-$XRAY_TAG/xray" ]]; then
        gh release download "$XRAY_TAG" --repo "$(jq -r '.xray.repo' "$ROOT/core-lock.json")" \
            --pattern Xray-linux-64.zip --dir "$DEST/xray-$XRAY_TAG" --clobber
        unzip -oq "$DEST/xray-$XRAY_TAG/Xray-linux-64.zip" -d "$DEST/xray-$XRAY_TAG"
        chmod +x "$DEST/xray-$XRAY_TAG/xray"
    fi
    export MARBLE_XRAY_BINARY="$DEST/xray-$XRAY_TAG/xray"
fi
"$MARBLE_XRAY_BINARY" version

# MARBLE_TUN2SOCKS_CORE_V222 — the second datapath's host binary. The APK ships the c-shared
# library built from source (it must read a descriptor in this process), but the upstream
# linux-amd64 CLI is the same engine and is the right thing to run the routing acceptance
# tests against: it accepts the same flags and the same SOCKS5 upstream.
TUN2SOCKS_TAG="$(jq -r '.tun2socks.tag' "$ROOT/core-lock.json")"
TUN2SOCKS_REPO="$(jq -r '.tun2socks.repo' "$ROOT/core-lock.json")"
[[ "$TUN2SOCKS_REPO" == "xjasonlyu/tun2socks" && "$TUN2SOCKS_TAG" =~ ^v[0-9A-Za-z._-]+$ ]]
TUN2SOCKS_ASSET="tun2socks-linux-amd64.zip"
if [[ ! -x "$DEST/tun2socks-$TUN2SOCKS_TAG/tun2socks" ]]; then
    mkdir -p "$DEST/tun2socks-$TUN2SOCKS_TAG"
    gh release download "$TUN2SOCKS_TAG" --repo "$TUN2SOCKS_REPO" \
        --pattern "$TUN2SOCKS_ASSET" --dir "$DEST/tun2socks-$TUN2SOCKS_TAG" --clobber
    TUN2SOCKS_EXPECTED="$(jq -er --arg asset "$TUN2SOCKS_ASSET" '.tun2socks.sha256[$asset]' "$ROOT/core-lock.json")"
    printf '%s  %s\n' "$TUN2SOCKS_EXPECTED" "$DEST/tun2socks-$TUN2SOCKS_TAG/$TUN2SOCKS_ASSET" | sha256sum --check --status
    unzip -oq "$DEST/tun2socks-$TUN2SOCKS_TAG/$TUN2SOCKS_ASSET" -d "$DEST/tun2socks-$TUN2SOCKS_TAG"
    chmod +x "$DEST/tun2socks-$TUN2SOCKS_TAG/tun2socks"
fi
export MARBLE_TUN2SOCKS_BINARY="$DEST/tun2socks-$TUN2SOCKS_TAG/tun2socks"
"$MARBLE_TUN2SOCKS_BINARY" -version | grep -F "${TUN2SOCKS_TAG#v}"

python3 "$ROOT/scripts/prepare-singbox-rules.py"
if [[ -n "${GITHUB_ENV:-}" ]]; then
    printf 'MARBLE_SINGBOX_BINARY=%s\nMARBLE_XRAY_BINARY=%s\nMARBLE_TUN2SOCKS_BINARY=%s\nMARBLE_NATIVE_TESTS_REQUIRED=1\n' \
        "$MARBLE_SINGBOX_BINARY" "$MARBLE_XRAY_BINARY" "$MARBLE_TUN2SOCKS_BINARY" >> "$GITHUB_ENV"
fi
printf '\nFor local tests:\nexport MARBLE_SINGBOX_BINARY=%q\nexport MARBLE_XRAY_BINARY=%q\nexport MARBLE_TUN2SOCKS_BINARY=%q\n' \
    "$MARBLE_SINGBOX_BINARY" "$MARBLE_XRAY_BINARY" "$MARBLE_TUN2SOCKS_BINARY"
