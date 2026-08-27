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
# Design rules:
#
#   1. Never downgrade or edit upstream Xray go.mod/go.sum.
#   2. GOTOOLCHAIN=auto lets Xray select its required Go toolchain.
#   3. Xray is built using Android NDK clang + CGO.
#   4. -checklinkname=0 is used for current Xray dependencies.
#   5. Xray binaries are built into an isolated staging directory.
#   6. HEV/JNI ndk-build runs BEFORE Xray is copied into jniLibs.
#   7. HEV's upstream JNI_OnLoad glue is disabled intentionally.
#   8. MarbleNG uses its own JNI symbol bridge.
#   9. Verification avoids grep -q pipelines under pipefail.
#  10. Final verification guarantees every requested native file exists.
#
# ==============================================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

LOCK="$ROOT/core-lock.json"

CORE="$ROOT/.cores"
XRAY_SRC="$CORE/xray"
XRAY_STAGE="$CORE/xray-android"
RANK_HELPER_SOURCE="$ROOT/native/rankhelper/main.go"

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
# Resilient HTTPS download helper
#
# GitHub release/CDN transfers can occasionally terminate with curl error 56
# (receive failure / connection reset).  Download to a temporary file so a
# failed transfer can never leave a truncated file at the final destination.
# --retry-all-errors makes receive-side failures retryable as well.
# HTTP/1.1 avoids a class of flaky HTTP/2 stream/proxy resets seen on CI paths.
# ==============================================================================

download_file() {
    local url="$1"
    local output="$2"
    local max_time="$3"
    shift 3

    local tmp="${output}.part"
    local status=0

    rm -f "$tmp"

    log "Downloading: $url"

    curl \
        --fail \
        --show-error \
        --location \
        --http1.1 \
        --retry 8 \
        --retry-all-errors \
        --retry-delay 2 \
        --retry-max-time "$max_time" \
        --connect-timeout 20 \
        --max-time "$max_time" \
        --speed-time 30 \
        --speed-limit 1024 \
        "$@" \
        -o "$tmp" \
        "$url" || status=$?

    if (( status != 0 )); then
        rm -f "$tmp"
        warn "Download failed with curl exit code $status: $url"
        return "$status"
    fi

    if [[ ! -s "$tmp" ]]; then
        rm -f "$tmp"
        warn "Download produced an empty file: $url"
        return 1
    fi

    mv -f "$tmp" "$output"
    ok "Downloaded: $output"
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
    wc \
    tr
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

# GitHub Actions Linux runners normally provide linux-x86_64.
if [[ -d "$NDK_TOOLCHAIN_ROOT/linux-x86_64" ]]; then

    NDK_HOST_DIR="$NDK_TOOLCHAIN_ROOT/linux-x86_64"

else

    # Do NOT use:
    #
    #   find ... | head -n 1
    #
    # under pipefail because head may close the pipe early and make find
    # terminate with SIGPIPE.
    NDK_HOST_DIR="$(
        find "$NDK_TOOLCHAIN_ROOT" \
            -mindepth 1 \
            -maxdepth 1 \
            -type d \
            -print \
            -quit
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

LLVM_NM="$NDK_BIN/llvm-nm"
LLVM_READELF="$NDK_BIN/llvm-readelf"

[[ -x "$LLVM_NM" ]] || {
    die "NDK llvm-nm not found: $LLVM_NM"
}

[[ -x "$LLVM_READELF" ]] || {
    die "NDK llvm-readelf not found: $LLVM_READELF"
}

ok "NDK LLVM toolchain: $NDK_BIN"


# ==============================================================================
# Android API
# ==============================================================================

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

[[ -s "$RANK_HELPER_SOURCE" ]] || {
    die "Missing MarbleNG Rank helper source: $RANK_HELPER_SOURCE"
}

mkdir -p "$XRAY_SRC/marble-rank-helper"
cp -f "$RANK_HELPER_SOURCE" "$XRAY_SRC/marble-rank-helper/main.go"

[[ -s "$XRAY_SRC/marble-rank-helper/main.go" ]] || {
    die "Could not stage MarbleNG Rank helper into pinned Xray module"
}

ok "PattNG-style Rank helper staged inside pinned Xray source"


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
# Ensure upstream go.mod/go.sum were NOT modified
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
# Verify go.mod/go.sum still untouched
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


# ==============================================================================
# Xray build helper
# ==============================================================================

build_xray() {
    local abi="$1"
    local goarch="$2"
    local cc_name="$3"
    local goarm="${4:-}"

    local out_dir="$XRAY_STAGE/$abi"
    local output="$out_dir/libxray.so"
    local rank_output="$out_dir/libmarblerank.so"
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

    if [[ -n "$goarm" ]]; then

        (
            cd "$XRAY_SRC"

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
        )

    else

        (
            cd "$XRAY_SRC"

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
        )

    fi

    # PattNG-style in-process Rank helper. One helper process serves the entire batch.
    if [[ -n "$goarm" ]]; then
        (
            cd "$XRAY_SRC"
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
                    -o "$rank_output" \
                    ./marble-rank-helper
        )
    else
        (
            cd "$XRAY_SRC"
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
                    -o "$rank_output" \
                    ./marble-rank-helper
        )
    fi

    [[ -s "$rank_output" ]] || {
        die "MarbleNG Rank helper build produced no file for $abi"
    }
    chmod 755 "$rank_output"

    [[ -s "$output" ]] || {
        die "Xray Android build produced no file for $abi"
    }

    chmod 755 "$output"

    local size
    local hash

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

    local rank_size
    local rank_hash
    rank_size="$(wc -c < "$rank_output" | tr -d ' ')"
    rank_hash="$(sha256sum "$rank_output" | awk '{print $1}')"
    ok "Rank helper staged: $abi"
    echo "     size   : $rank_size bytes"
    echo "     sha256 : $rank_hash"
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

    rank_file="$XRAY_STAGE/$abi/libmarblerank.so"
    [[ -s "$rank_file" ]] || {
        die "Staged Rank helper missing for $abi: $rank_file"
    }

    ok "$abi / Xray staging"
    ok "$abi / PattNG-style Rank helper staging"
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
# Disable HEV upstream JNI_OnLoad
#
# HEV's Android source contains its own JNI glue in:
#
#   src/hev-jni.c
#
# It defines JNI_OnLoad() for HEV's own Java package.
#
# MarbleNG does NOT use that Java integration. MarbleNG uses:
#
#   app/src/main/jni/marbleng_jni.c
#
# and directly invokes HEV's public C API.
#
# Therefore the upstream HEV JNI source is renamed so recursive *.c source
# discovery ignores it.
#
# MarbleNG JNI methods remain ordinary exported JNI symbols and do NOT need
# JNI_OnLoad.
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

ok "Disabled upstream HEV JNI_OnLoad glue; MarbleNG JNI bridge remains active."


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
# Native verification helpers
#
# IMPORTANT:
#
# Do NOT use:
#
#   readelf | awk | grep -q
#
# with:
#
#   set -o pipefail
#
# grep -q exits as soon as the match is found. The upstream process can then
# receive SIGPIPE and the pipeline may return a failure even though the symbol
# exists.
#
# The helpers below let awk consume the complete stream before returning.
# ==============================================================================

symbol_exists() {
    local library="$1"
    local wanted="$2"

    "$LLVM_NM" \
        -D \
        --defined-only \
        "$library" |
    awk \
        -v wanted="$wanted" \
        '
        $NF == wanted {
            found = 1
        }

        END {
            exit(found ? 0 : 1)
        }
        '
}

dependency_exists() {
    local library="$1"
    local wanted="$2"

    "$LLVM_READELF" \
        -d \
        "$library" |
    awk \
        -v wanted="$wanted" \
        '
        /NEEDED/ && index($0, "[" wanted "]") {
            found = 1
        }

        END {
            exit(found ? 0 : 1)
        }
        '
}


# ==============================================================================
# Verify HEV / MarbleNG JNI ownership
# ==============================================================================

log "Verifying native JNI ownership"

for abi in \
    arm64-v8a \
    armeabi-v7a \
    x86_64 \
    x86
do

    echo
    echo "------------------------------------------------------------"
    echo "Verifying JNI ABI: $abi"
    echo "------------------------------------------------------------"

    HEV_LIB="$JNILIBS/$abi/libhev-socks5-tunnel.so"
    BRIDGE_LIB="$JNILIBS/$abi/libmarbleng.so"

    [[ -s "$HEV_LIB" ]] || {
        die "HEV library missing before JNI verification: $HEV_LIB"
    }

    [[ -s "$BRIDGE_LIB" ]] || {
        die "MarbleNG JNI library missing before JNI verification: $BRIDGE_LIB"
    }


    # --------------------------------------------------------------------------
    # HEV must no longer export JNI_OnLoad.
    # --------------------------------------------------------------------------

    if symbol_exists \
        "$HEV_LIB" \
        "JNI_OnLoad"
    then

        echo
        echo "HEV exported symbols:"
        "$LLVM_NM" \
            -D \
            --defined-only \
            "$HEV_LIB" || true

        die "HEV upstream JNI_OnLoad is still exported for $abi"
    fi

    ok "$abi / HEV has no JNI_OnLoad"


    # --------------------------------------------------------------------------
    # MarbleNG bridge intentionally uses exported Java_* JNI symbols and does
    # not require JNI_OnLoad.
    # --------------------------------------------------------------------------

    if symbol_exists \
        "$BRIDGE_LIB" \
        "JNI_OnLoad"
    then

        echo
        echo "MarbleNG bridge exported symbols:"
        "$LLVM_NM" \
            -D \
            --defined-only \
            "$BRIDGE_LIB" || true

        die "Unexpected JNI_OnLoad exported by MarbleNG bridge for $abi"
    fi

    ok "$abi / MarbleNG bridge has no JNI_OnLoad"


    # --------------------------------------------------------------------------
    # Required MarbleNG JNI entry points
    # --------------------------------------------------------------------------

    REQUIRED_BRIDGE_SYMBOLS=(
        "Java_com_marbleng_app_nativebridge_HevTunnel_run"
        "Java_com_marbleng_app_nativebridge_HevTunnel_quit"
        "Java_com_marbleng_app_nativebridge_HevTunnel_stats"
    )

    for symbol in "${REQUIRED_BRIDGE_SYMBOLS[@]}"; do

        if ! symbol_exists \
            "$BRIDGE_LIB" \
            "$symbol"
        then

            echo
            echo "Available MarbleNG dynamic symbols:"

            "$LLVM_NM" \
                -D \
                --defined-only \
                "$BRIDGE_LIB" || true

            die "Required MarbleNG JNI symbol missing for $abi: $symbol"
        fi

        ok "$abi / bridge symbol: $symbol"

    done


    # --------------------------------------------------------------------------
    # Required HEV C API
    # --------------------------------------------------------------------------

    REQUIRED_HEV_SYMBOLS=(
        "hev_socks5_tunnel_main_from_str"
        "hev_socks5_tunnel_quit"
        "hev_socks5_tunnel_stats"
    )

    for symbol in "${REQUIRED_HEV_SYMBOLS[@]}"; do

        if ! symbol_exists \
            "$HEV_LIB" \
            "$symbol"
        then

            echo
            echo "Available HEV dynamic symbols:"

            "$LLVM_NM" \
                -D \
                --defined-only \
                "$HEV_LIB" || true

            die "Required HEV C API symbol missing for $abi: $symbol"
        fi

        ok "$abi / HEV symbol: $symbol"

    done


    # --------------------------------------------------------------------------
    # Verify libmarbleng.so is dynamically linked against HEV.
    #
    # This catches a build that happens to produce both libraries but where the
    # bridge is not actually connected to HEV.
    # --------------------------------------------------------------------------

    if ! dependency_exists \
        "$BRIDGE_LIB" \
        "libhev-socks5-tunnel.so"
    then

        echo
        echo "Dynamic dependencies of libmarbleng.so:"

        "$LLVM_READELF" \
            -d \
            "$BRIDGE_LIB" || true

        die "MarbleNG JNI bridge is not linked to libhev-socks5-tunnel.so for $abi"
    fi

    ok "$abi / libmarbleng.so -> libhev-socks5-tunnel.so"

    echo
    ok "$abi / JNI ownership fully verified"

done

echo
ok "HEV JNI_OnLoad collision eliminated for all Android ABIs"


# ==============================================================================
# Important
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
    rank_src="$XRAY_STAGE/$abi/libmarblerank.so"
    dst_dir="$JNILIBS/$abi"
    dst="$dst_dir/libxray.so"
    rank_dst="$dst_dir/libmarblerank.so"

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

    [[ -s "$rank_src" ]] || {
        die "Staged Rank helper disappeared for $abi: $rank_src"
    }
    cp -f "$rank_src" "$rank_dst"
    chmod 755 "$rank_dst"
    [[ -s "$rank_dst" ]] || {
        die "Could not install Rank helper into jniLibs for $abi"
    }
    rank_src_hash="$(sha256sum "$rank_src" | awk '{print $1}')"
    rank_dst_hash="$(sha256sum "$rank_dst" | awk '{print $1}')"
    [[ "$rank_src_hash" == "$rank_dst_hash" ]] || {
        die "Rank helper copy checksum mismatch for $abi"
    }

    ok "Installed Xray -> $abi"
    ok "Installed Rank helper -> $abi"

done

ok "All Xray and PattNG-style Rank helper binaries safely installed after ndk-build"


# ==============================================================================
# 4/4 - Xray assets
# ==============================================================================

log "[4/4] Downloading Xray geo assets for $XRAY_TAG"

XRAY_RELEASE_JSON="$CORE/xray-release.json"
XRAY_ZIP="$CORE/xray-release.zip"

download_file \
    "https://api.github.com/repos/XTLS/Xray-core/releases/tags/$XRAY_TAG" \
    "$XRAY_RELEASE_JSON" \
    180 \
    --silent \
    -H "Accept: application/vnd.github+json" \
    -H "X-GitHub-Api-Version: 2022-11-28" || {
        die "Xray release metadata download failed after retries"
    }

[[ -s "$XRAY_RELEASE_JSON" ]] || {
    die "Xray release metadata download failed"
}

if ! jq -e . "$XRAY_RELEASE_JSON" >/dev/null 2>&1; then
    die "GitHub returned invalid Xray release metadata"
fi


# ==============================================================================
# Locate Xray release ZIP
#
# Avoid:
#
#   jq ... | head -n 1
#
# under pipefail. jq's first() performs the selection internally instead.
# ==============================================================================

XRAY_ASSET_URL="$(
    jq -r '
        first(
            .assets[]
            | select(.name == "Xray-linux-64.zip")
            | .browser_download_url
        ) // empty
    ' "$XRAY_RELEASE_JSON"
)"

[[ -n "$XRAY_ASSET_URL" ]] || {
    die "Xray-linux-64.zip is unavailable for $XRAY_TAG"
}

echo
echo "Xray release asset:"
echo "  $XRAY_ASSET_URL"
echo


# ==============================================================================
# Download release ZIP
# ==============================================================================

download_file \
    "$XRAY_ASSET_URL" \
    "$XRAY_ZIP" \
    600 || {
        die "Xray release ZIP download failed after retries"
    }

[[ -s "$XRAY_ZIP" ]] || {
    die "Downloaded Xray release ZIP is empty"
}


# ==============================================================================
# Validate ZIP
# ==============================================================================

log "Validating Xray release archive"

if ! unzip -t "$XRAY_ZIP" >/dev/null 2>&1; then
    warn "Downloaded Xray ZIP failed validation; deleting it and retrying once"
    rm -f "$XRAY_ZIP" "${XRAY_ZIP}.part"

    download_file \
        "$XRAY_ASSET_URL" \
        "$XRAY_ZIP" \
        600 || {
            die "Xray release ZIP re-download failed"
        }
fi

unzip -t "$XRAY_ZIP" >/dev/null 2>&1 || {
    die "Downloaded Xray ZIP failed integrity validation after re-download"
}

ok "Xray release ZIP validated"


# ==============================================================================
# ZIP member finder
#
# This avoids:
#
#   unzip -l file.zip | grep -q ...
#
# which has the same early-exit/SIGPIPE problem under pipefail.
#
# It also supports a future release where the files might be stored in a
# subdirectory rather than directly at ZIP root.
# ==============================================================================

zip_member_by_basename() {
    local zip_file="$1"
    local wanted_basename="$2"

    unzip -Z1 "$zip_file" |
    awk \
        -v wanted="$wanted_basename" \
        '
        {
            path = $0
            base = path
            sub(/^.*\//, "", base)

            if (!found && base == wanted) {
                found = path
            }
        }

        END {
            if (!found) {
                exit 1
            }

            print found
        }
        '
}


# ==============================================================================
# Locate required geo assets
# ==============================================================================

GEOIP_MEMBER="$(
    zip_member_by_basename \
        "$XRAY_ZIP" \
        "geoip.dat"
)" || {
    die "geoip.dat missing from Xray release ZIP"
}

GEOSITE_MEMBER="$(
    zip_member_by_basename \
        "$XRAY_ZIP" \
        "geosite.dat"
)" || {
    die "geosite.dat missing from Xray release ZIP"
}

echo "geoip.dat ZIP entry   : $GEOIP_MEMBER"
echo "geosite.dat ZIP entry : $GEOSITE_MEMBER"


# ==============================================================================
# Extract geoip.dat
# ==============================================================================

unzip -p \
    "$XRAY_ZIP" \
    "$GEOIP_MEMBER" \
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
    "$GEOSITE_MEMBER" \
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
    RANK_FILE="$JNILIBS/$abi/libmarblerank.so"
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
    # PattNG-style Rank helper
    # --------------------------------------------------------------------------

    if [[ -s "$RANK_FILE" ]]; then
        RANK_SIZE="$(wc -c < "$RANK_FILE" | tr -d ' ')"
        echo "[OK] $abi / Rank helper"
        echo "     $RANK_SIZE bytes"
    else
        echo "[FAIL] $abi / Rank helper missing:"
        echo "       $RANK_FILE"
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
    rank_staged="$XRAY_STAGE/$abi/libmarblerank.so"
    rank_final="$JNILIBS/$abi/libmarblerank.so"

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

    rank_staged_hash="$(sha256sum "$rank_staged" | awk '{print $1}')"
    rank_final_hash="$(sha256sum "$rank_final" | awk '{print $1}')"
    [[ "$rank_staged_hash" == "$rank_final_hash" ]] || {
        die "Final Rank helper checksum mismatch for $abi"
    }

    ok "$abi Xray checksum verified"
    ok "$abi Rank helper checksum verified"

done


# ==============================================================================
# Final native JNI verification
#
# Recheck the installed libraries at the very end so a later build step cannot
# silently reintroduce a JNI/HEV problem.
# ==============================================================================

log "Running final HEV/JNI integrity verification"

for abi in \
    arm64-v8a \
    armeabi-v7a \
    x86_64 \
    x86
do

    HEV_LIB="$JNILIBS/$abi/libhev-socks5-tunnel.so"
    BRIDGE_LIB="$JNILIBS/$abi/libmarbleng.so"

    if symbol_exists "$HEV_LIB" "JNI_OnLoad"; then
        die "Final verification: HEV JNI_OnLoad collision detected for $abi"
    fi

    if symbol_exists "$BRIDGE_LIB" "JNI_OnLoad"; then
        die "Final verification: unexpected MarbleNG JNI_OnLoad for $abi"
    fi

    for symbol in \
        "Java_com_marbleng_app_nativebridge_HevTunnel_run" \
        "Java_com_marbleng_app_nativebridge_HevTunnel_quit" \
        "Java_com_marbleng_app_nativebridge_HevTunnel_stats"
    do

        symbol_exists "$BRIDGE_LIB" "$symbol" || {
            die "Final verification: MarbleNG JNI symbol missing for $abi: $symbol"
        }

    done

    for symbol in \
        "hev_socks5_tunnel_main_from_str" \
        "hev_socks5_tunnel_quit" \
        "hev_socks5_tunnel_stats"
    do

        symbol_exists "$HEV_LIB" "$symbol" || {
            die "Final verification: HEV API symbol missing for $abi: $symbol"
        }

    done

    dependency_exists \
        "$BRIDGE_LIB" \
        "libhev-socks5-tunnel.so" || {
            die "Final verification: libmarbleng.so is not linked to HEV for $abi"
        }

    ok "$abi final HEV/JNI integrity verified"

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

# Everything succeeded. Disable EXIT failure diagnostics.
trap - EXIT

exit 0
