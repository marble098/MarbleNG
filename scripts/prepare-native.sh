#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MarbleNG Native Core Builder
#
# Builds:
#   - Xray-core for Android:
#       arm64-v8a
#       armeabi-v7a
#       x86_64
#       x86
#
#   - hev-socks5-tunnel Android native libraries
#   - MarbleNG JNI bridge
#   - Xray geoip.dat / geosite.dat assets
#
# Important:
#   NEVER downgrade Xray's go.mod.
#   Xray decides its required Go version itself.
# ==============================================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

LOCK="$ROOT/core-lock.json"
CORE="$ROOT/.cores"
XRAY_SRC="$CORE/xray"

JNILIBS="$ROOT/app/src/main/jniLibs"
JNI_ROOT="$ROOT/app/src/main/jni"
HEVDST="$JNI_ROOT/hev"

ASSETS_ROOT="$ROOT/app/src/main/assets"
XRAY_ASSETS="$ASSETS_ROOT/xray"

# Allow modern Go versions to automatically select/download the toolchain
# required by Xray's own go.mod.
export GOTOOLCHAIN=auto

# ------------------------------------------------------------------------------
# Helpers
# ------------------------------------------------------------------------------

log() {
    printf '\n\033[1;36m[MarbleNG]\033[0m %s\n' "$*"
}

ok() {
    printf '\033[1;32m[OK]\033[0m %s\n' "$*"
}

warn() {
    printf '\033[1;33m[WARN]\033[0m %s\n' "$*"
}

die() {
    printf '\033[1;31m[FAIL]\033[0m %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || die "Missing required command: $1"
}

# ------------------------------------------------------------------------------
# Requirements
# ------------------------------------------------------------------------------

for cmd in \
    git \
    jq \
    go \
    curl \
    unzip \
    sed \
    awk \
    grep \
    find \
    sha256sum
do
    require_command "$cmd"
done

[[ -f "$LOCK" ]] || die "Missing core lock file: $LOCK"

# ------------------------------------------------------------------------------
# Android NDK
# ------------------------------------------------------------------------------

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

[[ -n "$NDK" ]] || \
    die "ANDROID_NDK_HOME or ANDROID_NDK_ROOT is not set."

[[ -x "$NDK/ndk-build" ]] || \
    die "ndk-build not found at: $NDK/ndk-build"

ok "Android NDK: $NDK"

# ------------------------------------------------------------------------------
# Read locked versions
# ------------------------------------------------------------------------------

XRAY_TAG="$(jq -r '.xray.tag // empty' "$LOCK")"
HEV_TAG="$(jq -r '.hev.tag // empty' "$LOCK")"

[[ -n "$XRAY_TAG" ]] || die "Missing .xray.tag in core-lock.json"
[[ -n "$HEV_TAG" ]] || die "Missing .hev.tag in core-lock.json"

log "Locked native versions"
echo "Xray : $XRAY_TAG"
echo "HEV  : $HEV_TAG"

# ------------------------------------------------------------------------------
# Clean old native build
# ------------------------------------------------------------------------------

log "Cleaning previous native build"

rm -rf \
    "$CORE" \
    "$JNILIBS" \
    "$HEVDST"

mkdir -p \
    "$CORE" \
    "$JNILIBS" \
    "$XRAY_ASSETS"

# ==============================================================================
# 1/4 - Xray source
# ==============================================================================

log "[1/4] Cloning Xray source $XRAY_TAG"

git clone \
    --quiet \
    --depth 1 \
    --branch "$XRAY_TAG" \
    https://github.com/XTLS/Xray-core.git \
    "$XRAY_SRC"

[[ -f "$XRAY_SRC/go.mod" ]] || \
    die "Xray go.mod was not found after cloning."

XRAY_GO_REQUIRED="$(
    awk '/^go[[:space:]]+/ { print $2; exit }' \
        "$XRAY_SRC/go.mod"
)"

[[ -n "$XRAY_GO_REQUIRED" ]] || \
    die "Could not determine Xray Go version requirement."

echo
echo "------------------------------------------------------------"
echo "Xray tag            : $XRAY_TAG"
echo "Xray requires Go    : $XRAY_GO_REQUIRED"
echo "Runner bootstrap Go : $(go version)"
echo "GOTOOLCHAIN          : ${GOTOOLCHAIN}"
echo "------------------------------------------------------------"
echo

# DO NOT run:
#
#   go mod edit -go=1.22
#   go mod edit -toolchain=none
#
# Xray's original go.mod must remain untouched.

if git -C "$XRAY_SRC" diff --quiet -- go.mod; then
    ok "Xray go.mod preserved unchanged"
else
    die "Xray go.mod was unexpectedly modified."
fi

# Ask Go to resolve the module once before compiling all ABIs.
# With GOTOOLCHAIN=auto Go can select the version requested by go.mod.
log "Preparing Xray Go dependencies"

(
    cd "$XRAY_SRC"

    echo "Effective Go toolchain:"
    go version

    go mod download
)

ok "Xray dependencies prepared"

# ==============================================================================
# 2/4 - Xray Android binaries
# ==============================================================================

log "[2/4] Building Xray Android binaries using official Android build method"

# ------------------------------------------------------------------------------
# Locate Android NDK LLVM toolchain
# ------------------------------------------------------------------------------

NDK_TOOLCHAIN_ROOT="$NDK/toolchains/llvm/prebuilt"

[[ -d "$NDK_TOOLCHAIN_ROOT" ]] || \
    die "Android NDK LLVM toolchain directory not found: $NDK_TOOLCHAIN_ROOT"

NDK_HOST_DIR="$(
    find "$NDK_TOOLCHAIN_ROOT" \
        -mindepth 1 \
        -maxdepth 1 \
        -type d \
        | head -n 1
)"

[[ -n "$NDK_HOST_DIR" && -d "$NDK_HOST_DIR" ]] || \
    die "Could not locate Android NDK host toolchain."

NDK_BIN="$NDK_HOST_DIR/bin"

[[ -d "$NDK_BIN" ]] || \
    die "Android NDK compiler directory not found: $NDK_BIN"

echo
echo "Android NDK LLVM:"
echo "  $NDK_BIN"
echo

# Android API 24 matches the current official Xray Android release build.
ANDROID_NATIVE_API=24

# ------------------------------------------------------------------------------
# Xray Android builder
#
# IMPORTANT:
#
# Current Xray Android builds require the same important linker behavior used by
# Xray's official GitHub workflow:
#
#   CGO_ENABLED=1
#   Android NDK clang
#   -gcflags="all=-l=4"
#   -checklinkname=0
#
# Do NOT remove -checklinkname=0.
# ------------------------------------------------------------------------------

build_xray() {
    local abi="$1"
    local goarch="$2"
    local cc_name="$3"
    local goarm="${4:-}"

    local out_dir="$JNILIBS/$abi"
    local output="$out_dir/libxray.so"
    local cc="$NDK_BIN/$cc_name"

    mkdir -p "$out_dir"

    [[ -x "$cc" ]] || {
        warn "Android compiler not available for $abi:"
        warn "  $cc"
        return 1
    }

    local commit_id

    commit_id="$(
        git -C "$XRAY_SRC" \
            describe \
            --always \
            --dirty \
            2>/dev/null ||
        git -C "$XRAY_SRC" \
            rev-parse \
            --short=7 HEAD
    )"

    echo
    echo "================================================================"
    echo " Building Xray for Android"
    echo "================================================================"
    echo " ABI          : $abi"
    echo " GOOS         : android"
    echo " GOARCH       : $goarch"

    if [[ -n "$goarm" ]]; then
        echo " GOARM        : $goarm"
    fi

    echo " API          : $ANDROID_NATIVE_API"
    echo " CGO          : enabled"
    echo " Compiler     : $cc"
    echo " Xray commit  : $commit_id"
    echo " Go           : $(cd "$XRAY_SRC" && go version)"
    echo " Output       : $output"
    echo "================================================================"
    echo

    (
        cd "$XRAY_SRC"

        if [[ -n "$goarm" ]]; then

            env \
                GOTOOLCHAIN=auto \
                GOOS=android \
                GOARCH="$goarch" \
                GOARM="$goarm" \
                CGO_ENABLED=1 \
                CC="$cc" \
                go build \
                    -o "$output" \
                    -trimpath \
                    -buildvcs=false \
                    -gcflags="all=-l=4" \
                    -ldflags="-X github.com/xtls/xray-core/core.build=${commit_id} -s -w -buildid= -checklinkname=0" \
                    -v \
                    ./main

        else

            env \
                GOTOOLCHAIN=auto \
                GOOS=android \
                GOARCH="$goarch" \
                CGO_ENABLED=1 \
                CC="$cc" \
                go build \
                    -o "$output" \
                    -trimpath \
                    -buildvcs=false \
                    -gcflags="all=-l=4" \
                    -ldflags="-X github.com/xtls/xray-core/core.build=${commit_id} -s -w -buildid= -checklinkname=0" \
                    -v \
                    ./main

        fi
    )

    [[ -s "$output" ]] || \
        die "Xray Android build produced no binary for $abi"

    chmod 755 "$output"

    local size
    size="$(wc -c < "$output" | tr -d ' ')"

    ok "Xray Android $abi built successfully — $size bytes"
}

# ------------------------------------------------------------------------------
# Android ARM64
#
# This is an officially built Xray Android target.
# ------------------------------------------------------------------------------

build_xray \
    "arm64-v8a" \
    "arm64" \
    "aarch64-linux-android${ANDROID_NATIVE_API}-clang"

# ------------------------------------------------------------------------------
# Android ARMv7 / 32-bit
#
# Kept for MarbleNG compatibility.
# ------------------------------------------------------------------------------

build_xray \
    "armeabi-v7a" \
    "arm" \
    "armv7a-linux-androideabi${ANDROID_NATIVE_API}-clang" \
    "7"

# ------------------------------------------------------------------------------
# Android x86_64
#
# Go calls this architecture amd64.
# This is also an officially built Xray Android target.
# ------------------------------------------------------------------------------

build_xray \
    "x86_64" \
    "amd64" \
    "x86_64-linux-android${ANDROID_NATIVE_API}-clang"

# ------------------------------------------------------------------------------
# Android x86 / 32-bit
#
# Kept for MarbleNG compatibility.
# ------------------------------------------------------------------------------

build_xray \
    "x86" \
    "386" \
    "i686-linux-android${ANDROID_NATIVE_API}-clang"

# ------------------------------------------------------------------------------
# Verify Xray outputs
# ------------------------------------------------------------------------------

log "Verifying Xray Android binaries"

XRAY_ABIS=(
    "arm64-v8a"
    "armeabi-v7a"
    "x86_64"
    "x86"
)

for abi in "${XRAY_ABIS[@]}"; do

    binary="$JNILIBS/$abi/libxray.so"

    [[ -s "$binary" ]] || \
        die "Missing Xray binary after build: $binary"

    size="$(
        wc -c < "$binary" |
        tr -d ' '
    )"

    hash="$(
        sha256sum "$binary" |
        awk '{print $1}'
    )"

    echo
    echo "$abi"
    echo "  size   : $size"
    echo "  sha256 : $hash"
done

ok "All requested Xray Android binaries generated."
# ==============================================================================
# 3/4 - HEV SOCKS5 TUN
# ==============================================================================

log "[3/4] Cloning HEV SOCKS5 Tunnel $HEV_TAG"

git clone \
    --quiet \
    --depth 1 \
    --branch "$HEV_TAG" \
    --recursive \
    https://github.com/heiher/hev-socks5-tunnel.git \
    "$HEVDST"

[[ -d "$HEVDST" ]] || \
    die "HEV repository clone failed."

# In some git configurations shallow clone + --recursive can leave nested
# submodules incomplete. Explicitly ensure every required submodule exists.
git -C "$HEVDST" \
    submodule update \
    --init \
    --recursive \
    --depth 1

ok "HEV source ready"

# ------------------------------------------------------------------------------
# Check MarbleNG JNI build files
# ------------------------------------------------------------------------------

ANDROID_MK="$JNI_ROOT/Android.mk"
APPLICATION_MK="$JNI_ROOT/Application.mk"

[[ -f "$ANDROID_MK" ]] || \
    die "Missing JNI Android.mk: $ANDROID_MK"

[[ -f "$APPLICATION_MK" ]] || \
    die "Missing JNI Application.mk: $APPLICATION_MK"

log "Building HEV + MarbleNG JNI bridge"

CPU_COUNT="$(
    getconf _NPROCESSORS_ONLN 2>/dev/null ||
    nproc 2>/dev/null ||
    echo 4
)"

[[ "$CPU_COUNT" =~ ^[0-9]+$ ]] || CPU_COUNT=4

"$NDK/ndk-build" \
    -C "$ROOT/app/src/main" \
    NDK_PROJECT_PATH="$ROOT/app/src/main" \
    APP_BUILD_SCRIPT="$ANDROID_MK" \
    NDK_APPLICATION_MK="$APPLICATION_MK" \
    NDK_LIBS_OUT="$JNILIBS" \
    -j"$CPU_COUNT"

ok "HEV and JNI compilation completed"

# ==============================================================================
# 4/4 - Xray geo assets
# ==============================================================================

log "[4/4] Downloading Xray geo assets for $XRAY_TAG"

XRAY_RELEASE_JSON="$CORE/xray-release.json"
XRAY_ZIP="$CORE/xray.zip"

curl \
    --fail \
    --silent \
    --show-error \
    --location \
    --retry 5 \
    --retry-delay 2 \
    --connect-timeout 20 \
    --max-time 120 \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" \
    "https://api.github.com/repos/XTLS/Xray-core/releases/tags/$XRAY_TAG" \
    -o "$XRAY_RELEASE_JSON"

[[ -s "$XRAY_RELEASE_JSON" ]] || \
    die "Could not download Xray release metadata."

XRAY_ASSET_URL="$(
    jq -r '
        .assets[]
        | select(.name == "Xray-linux-64.zip")
        | .browser_download_url
    ' "$XRAY_RELEASE_JSON" |
    head -n 1
)"

[[ -n "$XRAY_ASSET_URL" && "$XRAY_ASSET_URL" != "null" ]] || \
    die "Xray-linux-64.zip asset was not found for $XRAY_TAG"

echo "Downloading:"
echo "$XRAY_ASSET_URL"

curl \
    --fail \
    --show-error \
    --location \
    --retry 5 \
    --retry-delay 2 \
    --connect-timeout 20 \
    --max-time 300 \
    -o "$XRAY_ZIP" \
    "$XRAY_ASSET_URL"

[[ -s "$XRAY_ZIP" ]] || \
    die "Downloaded Xray archive is empty."

# ------------------------------------------------------------------------------
# Validate archive
# ------------------------------------------------------------------------------

unzip -t "$XRAY_ZIP" >/dev/null || \
    die "Downloaded Xray ZIP is invalid."

# ------------------------------------------------------------------------------
# Extract geo databases
# ------------------------------------------------------------------------------

unzip -p \
    "$XRAY_ZIP" \
    geoip.dat \
    > "$XRAY_ASSETS/geoip.dat"

unzip -p \
    "$XRAY_ZIP" \
    geosite.dat \
    > "$XRAY_ASSETS/geosite.dat"

[[ -s "$XRAY_ASSETS/geoip.dat" ]] || \
    die "geoip.dat extraction failed."

[[ -s "$XRAY_ASSETS/geosite.dat" ]] || \
    die "geosite.dat extraction failed."

ok "Xray geo databases installed"

# ------------------------------------------------------------------------------
# Copy native-core lock into APK assets
# ------------------------------------------------------------------------------

mkdir -p "$ASSETS_ROOT"

cp \
    "$LOCK" \
    "$ASSETS_ROOT/core-lock.json"

ok "core-lock.json copied to Android assets"

# ==============================================================================
# Final verification
# ==============================================================================

log "Running final native-core verification"

FAILED=0

for abi in \
    arm64-v8a \
    armeabi-v7a \
    x86_64 \
    x86
do
    echo
    echo "Checking ABI: $abi"

    XRAY_FILE="$JNILIBS/$abi/libxray.so"
    HEV_FILE="$JNILIBS/$abi/libhev-socks5-tunnel.so"
    BRIDGE_FILE="$JNILIBS/$abi/libmarbleng.so"

    if [[ -s "$XRAY_FILE" ]]; then
        ok "$abi / Xray"
    else
        echo "Missing: $XRAY_FILE" >&2
        FAILED=1
    fi

    if [[ -s "$HEV_FILE" ]]; then
        ok "$abi / HEV"
    else
        echo "Missing: $HEV_FILE" >&2
        FAILED=1
    fi

    if [[ -s "$BRIDGE_FILE" ]]; then
        ok "$abi / MarbleNG JNI"
    else
        echo "Missing: $BRIDGE_FILE" >&2
        FAILED=1
    fi
done

[[ -s "$XRAY_ASSETS/geoip.dat" ]] || FAILED=1
[[ -s "$XRAY_ASSETS/geosite.dat" ]] || FAILED=1
[[ -s "$ASSETS_ROOT/core-lock.json" ]] || FAILED=1

if (( FAILED != 0 )); then
    die "Native build verification failed."
fi

# ------------------------------------------------------------------------------
# Build information
# ------------------------------------------------------------------------------

echo
echo "================================================================"
echo " MarbleNG native preparation completed successfully"
echo "================================================================"
echo
echo "Xray tag:"
echo "  $XRAY_TAG"
echo
echo "Xray Go requirement:"
echo "  $XRAY_GO_REQUIRED"
echo
echo "Effective Go:"
(
    cd "$XRAY_SRC"
    go version
)
echo
echo "HEV tag:"
echo "  $HEV_TAG"
echo
echo "Android NDK:"
echo "  $NDK"
echo
echo "Architectures:"
echo "  - arm64-v8a"
echo "  - armeabi-v7a"
echo "  - x86_64"
echo "  - x86"
echo

for abi in \
    arm64-v8a \
    armeabi-v7a \
    x86_64 \
    x86
do
    echo "$abi:"
    echo "  Xray:"
    sha256sum "$JNILIBS/$abi/libxray.so"

    echo "  HEV:"
    sha256sum "$JNILIBS/$abi/libhev-socks5-tunnel.so"

    echo "  JNI:"
    sha256sum "$JNILIBS/$abi/libmarbleng.so"

    echo
done

echo "Assets:"
sha256sum \
    "$XRAY_ASSETS/geoip.dat" \
    "$XRAY_ASSETS/geosite.dat"

echo
ok "Native cores ready."
