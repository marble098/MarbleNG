#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MarbleNG Native Core Builder
#
# Builds and prepares:
#
#   Xray-core:
#     - arm64-v8a
#     - armeabi-v7a
#     - x86_64
#     - x86
#
#   HEV SOCKS5 Tunnel:
#     - arm64-v8a
#     - armeabi-v7a
#     - x86_64
#     - x86
#
#   MarbleNG JNI bridge
#
#   Assets:
#     - geoip.dat
#     - geosite.dat
#     - core-lock.json
#
# Important design:
#
#   1. Never downgrade Xray go.mod.
#   2. GOTOOLCHAIN=auto lets Xray select its required Go toolchain.
#   3. Xray is built using Android NDK clang + CGO.
#   4. -checklinkname=0 is used for current Xray dependencies.
#   5. Xray binaries are built into an isolated staging directory.
#   6. HEV/JNI ndk-build runs BEFORE Xray is copied into jniLibs.
#   7. Final verification guarantees every requested native file exists.
#
# ==============================================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

LOCK="$ROOT/core-lock.json"

CORE="$ROOT/.cores"
XRAY_SRC="$CORE/xray"
XRAY_STAGE="$CORE/xray-android"

JNILIBS="$ROOT/app/src/main/jniLibs"

JNI_ROOT="$ROOT/app/src/main/jni"
HEVDST="$JNI_ROOT/hev"

ASSETS_ROOT="$ROOT/app/src/main/assets"
XRAY_ASSETS="$ASSETS_ROOT/xray"

export GOTOOLCHAIN=auto


# ==============================================================================
# Logging helpers
# ==============================================================================

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
    command -v "$1" >/dev/null 2>&1 || {
        die "Missing required command: $1"
    }
}


# ==============================================================================
# Failure diagnostics
# ==============================================================================

failure_diagnostics() {
    local status=$?

    if (( status != 0 )); then
        echo
        echo "================================================================"
        echo " MarbleNG native preparation FAILED"
        echo "================================================================"
        echo

        echo "Exit code:"
        echo "  $status"
        echo

        echo "Go:"
        go version 2>/dev/null || true
        echo

        echo "GOTOOLCHAIN:"
        echo "  ${GOTOOLCHAIN:-unset}"
        echo

        echo "ANDROID_NDK_HOME:"
        echo "  ${ANDROID_NDK_HOME:-unset}"
        echo

        echo "ANDROID_NDK_ROOT:"
        echo "  ${ANDROID_NDK_ROOT:-unset}"
        echo

        echo "Existing staged Xray files:"
        if [[ -d "$XRAY_STAGE" ]]; then
            find "$XRAY_STAGE" \
                -maxdepth 3 \
                -type f \
                -printf '%p %s bytes\n' \
                2>/dev/null || true
        else
            echo "  staging directory does not exist"
        fi

        echo
        echo "Existing jniLibs:"
        if [[ -d "$JNILIBS" ]]; then
            find "$JNILIBS" \
                -maxdepth 3 \
                -type f \
                -printf '%p %s bytes\n' \
                2>/dev/null || true
        else
            echo "  jniLibs directory does not exist"
        fi

        echo
        echo "================================================================"
    fi

    exit "$status"
}

trap failure_diagnostics EXIT


# ==============================================================================
# Required commands
# ==============================================================================

log "Checking build environment"

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
    sha256sum \
    wc
do
    require_command "$cmd"
done

ok "Required command-line tools are available"


# ==============================================================================
# Check core-lock.json
# ==============================================================================

[[ -f "$LOCK" ]] || {
    die "Missing core-lock.json: $LOCK"
}

if ! jq -e . "$LOCK" >/dev/null 2>&1; then
    die "core-lock.json is not valid JSON"
fi

XRAY_TAG="$(jq -r '.xray.tag // empty' "$LOCK")"
HEV_TAG="$(jq -r '.hev.tag // empty' "$LOCK")"

[[ -n "$XRAY_TAG" ]] || {
    die "Missing .xray.tag in core-lock.json"
}

[[ -n "$HEV_TAG" ]] || {
    die "Missing .hev.tag in core-lock.json"
}

log "Locked native versions"

echo "Xray : $XRAY_TAG"
echo "HEV  : $HEV_TAG"


# ==============================================================================
# Android NDK
# ==============================================================================

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"

[[ -n "$NDK" ]] || {
    die "ANDROID_NDK_HOME or ANDROID_NDK_ROOT is not set"
}

[[ -d "$NDK" ]] || {
    die "Android NDK directory does not exist: $NDK"
}

[[ -x "$NDK/ndk-build" ]] || {
    die "ndk-build not found: $NDK/ndk-build"
}

ok "Android NDK: $NDK"


# ==============================================================================
# Find NDK LLVM toolchain
# ==============================================================================

NDK_TOOLCHAIN_ROOT="$NDK/toolchains/llvm/prebuilt"

[[ -d "$NDK_TOOLCHAIN_ROOT" ]] || {
    die "Android NDK LLVM toolchain missing: $NDK_TOOLCHAIN_ROOT"
}

# GitHub Actions normally provides linux-x86_64.
if [[ -d "$NDK_TOOLCHAIN_ROOT/linux-x86_64" ]]; then
    NDK_HOST_DIR="$NDK_TOOLCHAIN_ROOT/linux-x86_64"
else
    NDK_HOST_DIR="$(
        find "$NDK_TOOLCHAIN_ROOT" \
            -mindepth 1 \
            -maxdepth 1 \
            -type d \
            | head -n 1
    )"
fi

[[ -n "$NDK_HOST_DIR" ]] || {
    die "Could not locate NDK host LLVM toolchain"
}

[[ -d "$NDK_HOST_DIR" ]] || {
    die "Invalid NDK host toolchain: $NDK_HOST_DIR"
}

NDK_BIN="$NDK_HOST_DIR/bin"

[[ -d "$NDK_BIN" ]] || {
    die "NDK compiler directory missing: $NDK_BIN"
}

ok "NDK LLVM toolchain: $NDK_BIN"


# ==============================================================================
# Android API
# ==============================================================================

# API 24 gives broad Android compatibility while providing the current
# NDK compiler targets required for the native build.
ANDROID_NATIVE_API="${ANDROID_NATIVE_API:-24}"

[[ "$ANDROID_NATIVE_API" =~ ^[0-9]+$ ]] || {
    die "ANDROID_NATIVE_API must be numeric"
}

echo "Android native API: $ANDROID_NATIVE_API"


# ==============================================================================
# Clean previous native build
# ==============================================================================

log "Cleaning previous native build"

rm -rf \
    "$CORE" \
    "$JNILIBS" \
    "$HEVDST"

mkdir -p \
    "$CORE" \
    "$XRAY_STAGE" \
    "$JNILIBS" \
    "$XRAY_ASSETS"

ok "Native workspace cleaned"


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

[[ -d "$XRAY_SRC/.git" ]] || {
    die "Xray source clone failed"
}

[[ -f "$XRAY_SRC/go.mod" ]] || {
    die "Xray go.mod missing after clone"
}


# ==============================================================================
# Determine Xray Go requirement
# ==============================================================================

XRAY_GO_REQUIRED="$(
    awk '
        /^go[[:space:]]+/ {
            print $2
            exit
        }
    ' "$XRAY_SRC/go.mod"
)"

[[ -n "$XRAY_GO_REQUIRED" ]] || {
    die "Could not determine Xray Go requirement"
}

echo
echo "------------------------------------------------------------"
echo "Xray tag            : $XRAY_TAG"
echo "Xray requires Go    : $XRAY_GO_REQUIRED"
echo "Runner bootstrap Go : $(go version)"
echo "GOTOOLCHAIN          : ${GOTOOLCHAIN}"
echo "------------------------------------------------------------"
echo


# ==============================================================================
# Ensure upstream go.mod was NOT modified
# ==============================================================================

if git -C "$XRAY_SRC" diff --quiet -- go.mod go.sum; then
    ok "Xray Go module files preserved unchanged"
else
    die "Xray go.mod/go.sum were unexpectedly modified"
fi


# ==============================================================================
# Prepare Xray dependencies
# ==============================================================================

log "Preparing Xray Go dependencies"

(
    cd "$XRAY_SRC"

    echo "Effective Go toolchain:"
    go version

    echo
    echo "Downloading modules..."

    go mod download
)

ok "Xray dependencies prepared"


# ==============================================================================
# Verify go.mod still untouched
# ==============================================================================

if git -C "$XRAY_SRC" diff --quiet -- go.mod go.sum; then
    ok "Xray module files remain pristine"
else
    echo
    git -C "$XRAY_SRC" diff -- go.mod go.sum || true
    die "Dependency preparation modified upstream module files"
fi


# ==============================================================================
# 2/4 - Xray Android binaries
# ==============================================================================

log "[2/4] Building Xray Android binaries"


# ------------------------------------------------------------------------------
# Build helper
# ------------------------------------------------------------------------------

build_xray() {
    local abi="$1"
    local goarch="$2"
    local cc_name="$3"
    local goarm="${4:-}"

    local out_dir="$XRAY_STAGE/$abi"
    local output="$out_dir/libxray.so"
    local cc="$NDK_BIN/$cc_name"

    mkdir -p "$out_dir"

    [[ -x "$cc" ]] || {
        die "Android compiler for $abi does not exist: $cc"
    }

    local commit_id

    commit_id="$(
        git -C "$XRAY_SRC" \
            rev-parse \
            --short=12 \
            HEAD
    )"

    echo
    echo "================================================================"
    echo " Building Xray for Android"
    echo "================================================================"
    echo "ABI          : $abi"
    echo "GOOS         : android"
    echo "GOARCH       : $goarch"

    if [[ -n "$goarm" ]]; then
        echo "GOARM        : $goarm"
    fi

    echo "Android API  : $ANDROID_NATIVE_API"
    echo "CGO          : enabled"
    echo "Compiler     : $cc"
    echo "Xray commit  : $commit_id"

    echo -n "Go toolchain : "
    (
        cd "$XRAY_SRC"
        go version
    )

    echo "Stage output : $output"
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
                    -buildmode=pie \
                    -trimpath \
                    -buildvcs=false \
                    -gcflags="all=-l=4" \
                    -ldflags="-X github.com/xtls/xray-core/core.build=${commit_id} -s -w -buildid= -checklinkname=0" \
                    -o "$output" \
                    ./main

        else

            env \
                GOTOOLCHAIN=auto \
                GOOS=android \
                GOARCH="$goarch" \
                CGO_ENABLED=1 \
                CC="$cc" \
                go build \
                    -buildmode=pie \
                    -trimpath \
                    -buildvcs=false \
                    -gcflags="all=-l=4" \
                    -ldflags="-X github.com/xtls/xray-core/core.build=${commit_id} -s -w -buildid= -checklinkname=0" \
                    -o "$output" \
                    ./main

        fi
    )

    [[ -s "$output" ]] || {
        die "Xray Android build produced no file for $abi"
    }

    chmod 755 "$output"

    local size hash

    size="$(
        wc -c < "$output" |
        tr -d ' '
    )"

    hash="$(
        sha256sum "$output" |
        awk '{print $1}'
    )"

    ok "Xray staged: $abi"
    echo "     size   : $size bytes"
    echo "     sha256 : $hash"
}


# ==============================================================================
# Xray ARM64
# ==============================================================================

build_xray \
    "arm64-v8a" \
    "arm64" \
    "aarch64-linux-android${ANDROID_NATIVE_API}-clang"


# ==============================================================================
# Xray ARMv7
# ==============================================================================

build_xray \
    "armeabi-v7a" \
    "arm" \
    "armv7a-linux-androideabi${ANDROID_NATIVE_API}-clang" \
    "7"


# ==============================================================================
# Xray x86_64
# ==============================================================================

build_xray \
    "x86_64" \
    "amd64" \
    "x86_64-linux-android${ANDROID_NATIVE_API}-clang"


# ==============================================================================
# Xray x86
# ==============================================================================

build_xray \
    "x86" \
    "386" \
    "i686-linux-android${ANDROID_NATIVE_API}-clang"


# ==============================================================================
# Verify staging BEFORE HEV build
# ==============================================================================

log "Verifying staged Xray binaries"

for abi in \
    arm64-v8a \
    armeabi-v7a \
    x86_64 \
    x86
do
    file="$XRAY_STAGE/$abi/libxray.so"

    [[ -s "$file" ]] || {
        die "Staged Xray missing for $abi: $file"
    }

    ok "$abi / Xray staging"
done


# ==============================================================================
# 3/4 - HEV SOCKS5 Tunnel
# ==============================================================================

log "[3/4] Cloning HEV SOCKS5 Tunnel $HEV_TAG"

git clone \
    --quiet \
    --depth 1 \
    --branch "$HEV_TAG" \
    --recursive \
    https://github.com/heiher/hev-socks5-tunnel.git \
    "$HEVDST"

[[ -d "$HEVDST" ]] || {
    die "HEV repository clone failed"
}

log "Ensuring HEV submodules are initialized"

git -C "$HEVDST" \
    submodule update \
    --init \
    --recursive \
    --depth 1

ok "HEV source ready"


# ==============================================================================
# MarbleNG HEV JNI_OnLoad collision fix v1
#
# HEV's Android library includes src/hev-jni.c by default. That file defines
# JNI_OnLoad() and attempts to register hev/htproxy/TProxyService.
#
# MarbleNG does not use HEV's bundled Java JNI API. It supplies
# app/src/main/jni/marbleng_jni.c and directly calls HEV's public C library API.
#
# Rename only that upstream JNI source before ndk-build. HEV's recursive *.c
# source discovery will then skip it while all tunnel/core C APIs remain.
# ==============================================================================

HEV_UPSTREAM_JNI="$HEVDST/src/hev-jni.c"
HEV_UPSTREAM_JNI_DISABLED="$HEVDST/src/hev-jni.c.marbleng-disabled"

[[ -f "$HEV_UPSTREAM_JNI" ]] || {
    die "Expected upstream HEV JNI source missing: $HEV_UPSTREAM_JNI"
}

grep -q 'JNI_OnLoad' "$HEV_UPSTREAM_JNI" || {
    die "HEV src/hev-jni.c no longer contains JNI_OnLoad; review upstream integration."
}

grep -q 'hev/htproxy' "$HEV_UPSTREAM_JNI" || {
    die "HEV JNI default package changed; review upstream integration."
}

mv \
    "$HEV_UPSTREAM_JNI" \
    "$HEV_UPSTREAM_JNI_DISABLED"

[[ ! -f "$HEV_UPSTREAM_JNI" ]] || {
    die "Could not disable upstream HEV JNI source."
}

[[ -f "$HEV_UPSTREAM_JNI_DISABLED" ]] || {
    die "Disabled HEV JNI source backup is missing."
}

ok "Disabled upstream HEV JNI_OnLoad glue; MarbleNG bridge will own JNI."


# ==============================================================================
# Check MarbleNG JNI makefiles
# ==============================================================================

ANDROID_MK="$JNI_ROOT/Android.mk"
APPLICATION_MK="$JNI_ROOT/Application.mk"

[[ -f "$ANDROID_MK" ]] || {
    die "Missing JNI Android.mk: $ANDROID_MK"
}

[[ -f "$APPLICATION_MK" ]] || {
    die "Missing JNI Application.mk: $APPLICATION_MK"
}


# ==============================================================================
# Determine CPU count
# ==============================================================================

CPU_COUNT="$(
    getconf _NPROCESSORS_ONLN 2>/dev/null ||
    nproc 2>/dev/null ||
    echo 4
)"

[[ "$CPU_COUNT" =~ ^[0-9]+$ ]] || {
    CPU_COUNT=4
}

if (( CPU_COUNT < 1 )); then
    CPU_COUNT=1
fi

echo "Native build workers: $CPU_COUNT"


# ==============================================================================
# Build HEV + MarbleNG JNI
# ==============================================================================

log "Building HEV + MarbleNG JNI bridge"

"$NDK/ndk-build" \
    -C "$ROOT/app/src/main" \
    NDK_PROJECT_PATH="$ROOT/app/src/main" \
    APP_BUILD_SCRIPT="$ANDROID_MK" \
    NDK_APPLICATION_MK="$APPLICATION_MK" \
    NDK_LIBS_OUT="$JNILIBS" \
    -j"$CPU_COUNT"

ok "HEV + MarbleNG JNI compilation completed"


# ==============================================================================
# Verify HEV JNI collision cannot regress
# ==============================================================================

log "Verifying native JNI ownership"

LLVM_READELF="$NDK_BIN/llvm-readelf"

[[ -x "$LLVM_READELF" ]] || {
    die "NDK llvm-readelf not found: $LLVM_READELF"
}

for abi in \
    arm64-v8a \
    armeabi-v7a \
    x86_64 \
    x86
do
    HEV_LIB="$JNILIBS/$abi/libhev-socks5-tunnel.so"
    BRIDGE_LIB="$JNILIBS/$abi/libmarbleng.so"

    [[ -s "$HEV_LIB" ]] || {
        die "HEV library missing before JNI verification: $HEV_LIB"
    }

    [[ -s "$BRIDGE_LIB" ]] || {
        die "MarbleNG JNI library missing before JNI verification: $BRIDGE_LIB"
    }

    if "$LLVM_READELF" -Ws "$HEV_LIB" |
        awk '{print $8}' |
        grep -Fxq 'JNI_OnLoad'
    then
        die "JNI_OnLoad collision still present in $HEV_LIB"
    fi

    if "$LLVM_READELF" -Ws "$BRIDGE_LIB" |
        awk '{print $8}' |
        grep -Fxq 'JNI_OnLoad'
    then
        die "Unexpected JNI_OnLoad exported by MarbleNG bridge: $BRIDGE_LIB"
    fi

    for symbol in \
        Java_com_marbleng_app_nativebridge_HevTunnel_run \
        Java_com_marbleng_app_nativebridge_HevTunnel_quit \
        Java_com_marbleng_app_nativebridge_HevTunnel_stats
    do
        if ! "$LLVM_READELF" -Ws "$BRIDGE_LIB" |
            awk '{print $8}' |
            grep -Fxq "$symbol"
        then
            die "Required MarbleNG JNI symbol missing for $abi: $symbol"
        fi
    done

    for symbol in \
        hev_socks5_tunnel_main_from_str \
        hev_socks5_tunnel_quit \
        hev_socks5_tunnel_stats
    do
        if ! "$LLVM_READELF" -Ws "$HEV_LIB" |
            awk '{print $8}' |
            grep -Fxq "$symbol"
        then
            die "Required HEV C API symbol missing for $abi: $symbol"
        fi
    done

    ok "$abi / JNI ownership verified"
done

ok "HEV JNI_OnLoad collision eliminated for all ABIs"


# ==============================================================================
# Important:
#
# ndk-build owns NDK_LIBS_OUT and may recreate/update jniLibs.
#
# Therefore Xray is copied AFTER ndk-build.
# ==============================================================================

log "Installing staged Xray binaries into Android jniLibs"

for abi in \
    arm64-v8a \
    armeabi-v7a \
    x86_64 \
    x86
do
    src="$XRAY_STAGE/$abi/libxray.so"
    dst_dir="$JNILIBS/$abi"
    dst="$dst_dir/libxray.so"

    [[ -s "$src" ]] || {
        die "Staged Xray binary disappeared for $abi: $src"
    }

    mkdir -p "$dst_dir"

    cp -f \
        "$src" \
        "$dst"

    chmod 755 "$dst"

    [[ -s "$dst" ]] || {
        die "Could not install Xray into jniLibs for $abi"
    }

    src_hash="$(
        sha256sum "$src" |
        awk '{print $1}'
    )"

    dst_hash="$(
        sha256sum "$dst" |
        awk '{print $1}'
    )"

    [[ "$src_hash" == "$dst_hash" ]] || {
        die "Xray copy checksum mismatch for $abi"
    }

    ok "Installed Xray -> $abi"
done

ok "All Xray binaries safely installed after ndk-build"


# ==============================================================================
# 4/4 - Xray assets
# ==============================================================================

log "[4/4] Downloading Xray geo assets for $XRAY_TAG"

XRAY_RELEASE_JSON="$CORE/xray-release.json"
XRAY_ZIP="$CORE/xray-release.zip"

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

[[ -s "$XRAY_RELEASE_JSON" ]] || {
    die "Xray release metadata download failed"
}

if ! jq -e . "$XRAY_RELEASE_JSON" >/dev/null 2>&1; then
    die "GitHub returned invalid Xray release metadata"
fi


# ==============================================================================
# Locate release ZIP
# ==============================================================================

XRAY_ASSET_URL="$(
    jq -r '
        .assets[]
        | select(.name == "Xray-linux-64.zip")
        | .browser_download_url
    ' "$XRAY_RELEASE_JSON" |
    head -n 1
)"

[[ -n "$XRAY_ASSET_URL" ]] || {
    die "Xray-linux-64.zip URL is empty"
}

[[ "$XRAY_ASSET_URL" != "null" ]] || {
    die "Xray-linux-64.zip is unavailable for $XRAY_TAG"
}

echo
echo "Xray release asset:"
echo "  $XRAY_ASSET_URL"
echo


# ==============================================================================
# Download release ZIP
# ==============================================================================

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

[[ -s "$XRAY_ZIP" ]] || {
    die "Downloaded Xray release ZIP is empty"
}


# ==============================================================================
# Validate ZIP
# ==============================================================================

log "Validating Xray release archive"

unzip -t "$XRAY_ZIP" >/dev/null || {
    die "Downloaded Xray ZIP failed integrity validation"
}

ok "Xray release ZIP validated"


# ==============================================================================
# Ensure required geo assets exist
# ==============================================================================

if ! unzip -l "$XRAY_ZIP" | grep -qE '(^|[[:space:]])geoip\.dat$'; then
    die "geoip.dat missing from Xray release ZIP"
fi

if ! unzip -l "$XRAY_ZIP" | grep -qE '(^|[[:space:]])geosite\.dat$'; then
    die "geosite.dat missing from Xray release ZIP"
fi


# ==============================================================================
# Extract geoip.dat
# ==============================================================================

unzip -p \
    "$XRAY_ZIP" \
    geoip.dat \
    > "$XRAY_ASSETS/geoip.dat"

[[ -s "$XRAY_ASSETS/geoip.dat" ]] || {
    die "geoip.dat extraction failed"
}

ok "geoip.dat extracted"


# ==============================================================================
# Extract geosite.dat
# ==============================================================================

unzip -p \
    "$XRAY_ZIP" \
    geosite.dat \
    > "$XRAY_ASSETS/geosite.dat"

[[ -s "$XRAY_ASSETS/geosite.dat" ]] || {
    die "geosite.dat extraction failed"
}

ok "geosite.dat extracted"


# ==============================================================================
# Copy core-lock.json into APK assets
# ==============================================================================

mkdir -p "$ASSETS_ROOT"

cp -f \
    "$LOCK" \
    "$ASSETS_ROOT/core-lock.json"

[[ -s "$ASSETS_ROOT/core-lock.json" ]] || {
    die "Could not copy core-lock.json into Android assets"
}

ok "core-lock.json installed into Android assets"


# ==============================================================================
# Final native verification
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
    echo "------------------------------------------------------------"
    echo "Checking ABI: $abi"
    echo "------------------------------------------------------------"

    XRAY_FILE="$JNILIBS/$abi/libxray.so"
    HEV_FILE="$JNILIBS/$abi/libhev-socks5-tunnel.so"
    BRIDGE_FILE="$JNILIBS/$abi/libmarbleng.so"

    # --------------------------------------------------------------------------
    # Xray
    # --------------------------------------------------------------------------

    if [[ -s "$XRAY_FILE" ]]; then
        XRAY_SIZE="$(
            wc -c < "$XRAY_FILE" |
            tr -d ' '
        )"

        echo "[OK] $abi / Xray"
        echo "     $XRAY_SIZE bytes"
    else
        echo "[FAIL] $abi / Xray missing:"
        echo "       $XRAY_FILE"
        FAILED=1
    fi

    # --------------------------------------------------------------------------
    # HEV
    # --------------------------------------------------------------------------

    if [[ -s "$HEV_FILE" ]]; then
        HEV_SIZE="$(
            wc -c < "$HEV_FILE" |
            tr -d ' '
        )"

        echo "[OK] $abi / HEV"
        echo "     $HEV_SIZE bytes"
    else
        echo "[FAIL] $abi / HEV missing:"
        echo "       $HEV_FILE"
        FAILED=1
    fi

    # --------------------------------------------------------------------------
    # MarbleNG JNI
    # --------------------------------------------------------------------------

    if [[ -s "$BRIDGE_FILE" ]]; then
        BRIDGE_SIZE="$(
            wc -c < "$BRIDGE_FILE" |
            tr -d ' '
        )"

        echo "[OK] $abi / MarbleNG JNI"
        echo "     $BRIDGE_SIZE bytes"
    else
        echo "[FAIL] $abi / MarbleNG JNI missing:"
        echo "       $BRIDGE_FILE"
        FAILED=1
    fi
done


# ==============================================================================
# Asset verification
# ==============================================================================

echo
echo "------------------------------------------------------------"
echo "Checking APK assets"
echo "------------------------------------------------------------"

if [[ -s "$XRAY_ASSETS/geoip.dat" ]]; then
    ok "geoip.dat"
else
    echo "[FAIL] geoip.dat missing"
    FAILED=1
fi

if [[ -s "$XRAY_ASSETS/geosite.dat" ]]; then
    ok "geosite.dat"
else
    echo "[FAIL] geosite.dat missing"
    FAILED=1
fi

if [[ -s "$ASSETS_ROOT/core-lock.json" ]]; then
    ok "core-lock.json"
else
    echo "[FAIL] core-lock.json missing"
    FAILED=1
fi


# ==============================================================================
# Stop if verification failed
# ==============================================================================

if (( FAILED != 0 )); then
    echo
    die "Native build verification failed"
fi


# ==============================================================================
# Verify copied Xray binaries are still identical to staging
# ==============================================================================

log "Validating final Xray checksums"

for abi in \
    arm64-v8a \
    armeabi-v7a \
    x86_64 \
    x86
do
    staged="$XRAY_STAGE/$abi/libxray.so"
    final="$JNILIBS/$abi/libxray.so"

    staged_hash="$(
        sha256sum "$staged" |
        awk '{print $1}'
    )"

    final_hash="$(
        sha256sum "$final" |
        awk '{print $1}'
    )"

    if [[ "$staged_hash" != "$final_hash" ]]; then
        die "Final Xray checksum mismatch for $abi"
    fi

    ok "$abi Xray checksum verified"
done


# ==============================================================================
# Print final checksums
# ==============================================================================

log "Native core SHA-256 manifest"

echo

for abi in \
    arm64-v8a \
    armeabi-v7a \
    x86_64 \
    x86
do
    echo "============================================================"
    echo "$abi"
    echo "============================================================"

    echo
    echo "Xray:"
    sha256sum "$JNILIBS/$abi/libxray.so"

    echo
    echo "HEV:"
    sha256sum "$JNILIBS/$abi/libhev-socks5-tunnel.so"

    echo
    echo "MarbleNG JNI:"
    sha256sum "$JNILIBS/$abi/libmarbleng.so"

    echo
done


# ==============================================================================
# Asset checksums
# ==============================================================================

echo "============================================================"
echo "Assets"
echo "============================================================"

sha256sum \
    "$XRAY_ASSETS/geoip.dat" \
    "$XRAY_ASSETS/geosite.dat" \
    "$ASSETS_ROOT/core-lock.json"


# ==============================================================================
# Final information
# ==============================================================================

echo
echo "================================================================"
echo " MarbleNG native preparation completed successfully"
echo "================================================================"
echo

echo "Xray"
echo "  Tag             : $XRAY_TAG"
echo "  Go requirement  : $XRAY_GO_REQUIRED"

echo -n "  Effective Go    : "
(
    cd "$XRAY_SRC"
    go version
)

echo
echo "HEV"
echo "  Tag             : $HEV_TAG"

echo
echo "Android"
echo "  NDK             : $NDK"
echo "  API             : $ANDROID_NATIVE_API"

echo
echo "Native ABIs"
echo "  - arm64-v8a"
echo "  - armeabi-v7a"
echo "  - x86_64"
echo "  - x86"

echo
echo "Output directory:"
echo "  $JNILIBS"

echo
echo "Assets directory:"
echo "  $ASSETS_ROOT"

echo
ok "Native cores ready."

# Disable failure diagnostics because everything succeeded.
trap - EXIT

exit 0
