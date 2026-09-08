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
#   sing-box extended (second engine, upstream Android release binaries):
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

# The last milestone printed by log(). failure_diagnostics() reports it, so a
# dead build says what it was doing, not just that it died.
MILESTONE=""

# Log file of the native compile that is running (or ran last), written by
# build_xray()/build_singbox(). A compiler writes its real reason into its
# output, and failure_diagnostics() replays the tail into the step summary and
# the run annotation - the step log itself is not always reachable when a
# release build dies (its log host has been unreachable from every diagnostic
# environment so far), so the reason has to travel in the annotation.
LAST_BUILD_LOG=""

log() {
    MILESTONE="$*"
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

# Emit a GitHub Actions annotation. Annotation commands are single-line: a
# newline ends the command and truncates the message, so multi-line bodies are
# %0A-encoded (and percent signs escaped first, or %0A would decode as text).
annotate_error() {
    local title="$1"
    local body="$2"

    body="${body//%/%25}"
    body="${body//$'\r'/ }"
    body="${body//$'\n'/%0A}"

    printf '::error title=%s::%s\n' "$title" "$body"
}

# Replay the tail of a failed command's log so the reason survives the loss of
# the step log. Kept short: annotations and step summaries are read by humans.
tail_of_log() {
    local file="$1"
    local lines="${2:-40}"

    if [[ -n "$file" && -s "$file" ]]; then
        echo "----- last $lines lines of $file -----"
        tail -n "$lines" -- "$file"
        echo "----- end -----"
    else
        echo "(no captured log)"
    fi
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || {
        die "Missing required command: $1"
    }
}

# ==============================================================================
# Transient-failure retry helper
#
# GitHub-hosted runners share egress bandwidth: git clones, `go mod download`
# and GOTOOLCHAIN toolchain fetches occasionally die mid-transfer (curl 56,
# "unexpected EOF", a reset connection). One attempt turns a one-in-N infra
# hiccup into a red release build; a few attempts with backoff make it a
# non-event. A genuinely broken command still fails, on the last attempt.
#
# Usage: retry <max_attempts> <base_delay_seconds> <function> [args...]
# The command is a function (not a raw argv) so it can clean up after itself
# between attempts - e.g. remove a half-cloned directory that would make the
# next `git clone` refuse to run at all.
# ==============================================================================

retry() {
    local max_attempts="$1"
    local delay="$2"
    shift 2

    local attempt=1 status=0

    while :; do
        status=0
        "$@" || status=$?

        if (( status == 0 )); then
            return 0
        fi

        if (( attempt >= max_attempts )); then
            warn "$* failed on attempt $attempt of $max_attempts (exit $status); giving up"
            return "$status"
        fi

        warn "$* attempt $attempt of $max_attempts failed (exit $status); retrying in ${delay}s"
        sleep "$delay"
        delay=$(( delay * 2 ))
        attempt=$(( attempt + 1 ))
    done
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
        local report
        report="$(mktemp "${RUNNER_TEMP:-/tmp}/marbleng-native-failure.XXXXXX")" || report=""

        {
            echo "Last milestone:"
            echo "  ${MILESTONE:-<none>}"

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
            echo "Disk (workspace):"
            df -h "$ROOT" 2>/dev/null || true

            echo
            echo "Memory:"
            free -m 2>/dev/null || true

            echo
            echo "Last native compile log:"
            echo
            tail_of_log "$LAST_BUILD_LOG" 60

            echo
            echo "================================================================"
        } | tee "${report:-/dev/null}"

        # ======================================================================
        # Make the failure readable without the step log.
        #
        # A dead release build must name its cause in the run itself: the step
        # summary carries the full diagnostics, and one ::error:: annotation
        # points at it. (Percent signs are not legal unencoded in annotation
        # parameters; milestone text is ours but escape anyway.)
        # ======================================================================

        if [[ "${GITHUB_ACTIONS:-}" == "true" && -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
            {
                echo ""
                echo "## [FAIL] MarbleNG native preparation"
                echo ""
                echo "- Last milestone: \`${MILESTONE:-<none>}\`"
                echo "- Exit code: \`${status}\`"
                echo ""
                echo '```'
                if [[ -n "$report" && -s "$report" ]]; then
                    cat "$report"
                fi
                echo '```'
            } >> "${GITHUB_STEP_SUMMARY}"

            local annotation_title
            annotation_title="$(printf '%s' "${MILESTONE:-native preparation}" | sed 's/%/%25/g;s/\r/ /g;s/\n/ /g')"

            # The step log is not always reachable (a dead release build has
            # already lost it twice), so the annotation carries the compiler's
            # own last words, not just a pointer to the summary.
            local annotation_body
            annotation_body="$(
                printf 'prepare-native.sh failed at "%s" (exit %s)\n' \
                    "${MILESTONE:-native preparation}" "$status"

                if [[ -n "$LAST_BUILD_LOG" && -s "$LAST_BUILD_LOG" ]]; then
                    printf '\nCompiler output (%s):\n' "$(basename "$LAST_BUILD_LOG")"
                    tail_of_log "$LAST_BUILD_LOG" 30
                fi
            )"

            annotate_error "MarbleNG-native-ABIs" "$annotation_body"
        fi

        [[ -n "$report" ]] && rm -f "$report"
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
    tar \
    sed \
    awk \
    grep \
    find \
    sha256sum \
    wc \
    tr \
    python3
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
SINGBOX_TAG="$(jq -r '.singbox.tag // empty' "$LOCK")"

[[ -n "$XRAY_TAG" ]] || {
    die "Missing .xray.tag in core-lock.json"
}

[[ -n "$HEV_TAG" ]] || {
    die "Missing .hev.tag in core-lock.json"
}

[[ -n "$SINGBOX_TAG" ]] || {
    die "Missing .singbox.tag in core-lock.json"
}

log "Locked native versions"

echo "Xray    : $XRAY_TAG"
echo "HEV     : $HEV_TAG"
echo "sing-box: $SINGBOX_TAG"


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

log "[1/5] Cloning Xray source $XRAY_TAG"

# Retried: a mid-transfer reset used to kill the whole build at step one. The
# clone target is removed before every attempt because `git clone` refuses to
# run into a non-empty (half-finished) directory.
clone_xray_source() {
    rm -rf "$XRAY_SRC"
    git clone \
        --quiet \
        --depth 1 \
        --branch "$XRAY_TAG" \
        https://github.com/XTLS/Xray-core.git \
        "$XRAY_SRC"
}

retry 3 5 clone_xray_source || {
    die "Xray source clone failed after retries: $XRAY_TAG"
}

[[ -d "$XRAY_SRC/.git" ]] || {
    die "Xray source clone failed"
}

[[ -f "$XRAY_SRC/go.mod" ]] || {
    die "Xray go.mod missing after clone"
}

[[ -s "$RANK_HELPER_SOURCE" ]] || {
    die "Missing MarbleNG Rank command source: $RANK_HELPER_SOURCE"
}

cp -f "$RANK_HELPER_SOURCE" "$XRAY_SRC/main/marble_rank.go"

[[ -s "$XRAY_SRC/main/marble_rank.go" ]] || {
    die "Could not stage MarbleNG Rank command into pinned Xray main package"
}

# MARBLE_REALTIME_ENGINE_V70 — inject passive TCP_INFO before compiling pinned Xray.
python3 "$ROOT/scripts/inject-xray-realtime.py" "$XRAY_SRC"
grep -F 'marbleTrackSocket(fd, network, address)' "$XRAY_SRC/transport/internet/sockopt_linux.go" >/dev/null || die "Realtime Xray socket hook missing"
grep -F 'MARBLE_REALTIME_ENGINE_V70' "$XRAY_SRC/transport/internet/marble_telemetry_linux.go" >/dev/null || die "Realtime Xray telemetry source missing"

python3 - "$XRAY_SRC/main/main.go" <<'PYRANKMAIN'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
old = "\t\t\tcmdRun,\n\t\t\tcmdVersion,\n"
new = "\t\t\tcmdRun,\n\t\t\tcmdVersion,\n\t\t\tcmdMarbleRank,\n"
if text.count(old) != 1:
    raise SystemExit("Pinned Xray main.go command anchor changed")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
PYRANKMAIN

grep -F 'cmdMarbleRank,' "$XRAY_SRC/main/main.go" >/dev/null || {
    die "MarbleNG Rank command registration failed"
}

grep -F 'UsageLine: "{{.Exec}} marble-rank [batch.json]"'     "$XRAY_SRC/main/marble_rank.go" >/dev/null || {
    die "Rank UsageLine is incompatible with Xray base.Command.Name()"
}

cat > "$XRAY_SRC/main/marble_rank_test.go" <<'GORANKTEST'
package main

import "testing"

func TestMarbleRankCommandRegistration(t *testing.T) {
    if got := cmdMarbleRank.Name(); got != "marble-rank" {
        t.Fatalf("Rank command name = %q, want marble-rank", got)
    }
    if !cmdMarbleRank.CustomFlags {
        t.Fatal("Rank command must use CustomFlags")
    }
}
GORANKTEST

ok "Rank integrated into the single Xray binary"


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
)

XRAY_MODULE_LOG="$CORE/xray-module-download.log"

# Retried and logged: a module-graph fetch that dies mid-transfer used to kill
# the build with a bare non-zero exit and no message. The log file keeps the
# last attempt's output for the failure diagnostics.
xray_download_modules() {
    (
        cd "$XRAY_SRC"
        env GOTOOLCHAIN=auto go mod download
    ) 2>&1 | tee "$XRAY_MODULE_LOG"
}

log "Downloading Xray Go modules"

retry 4 5 xray_download_modules || {
    die "Xray go mod download failed after retries: last output in $XRAY_MODULE_LOG"
}

ok "Xray dependencies prepared"

log "Testing integrated Xray Rank command registration"

XRAY_RANK_TEST_LOG="$CORE/xray-rank-test.log"

(
    cd "$XRAY_SRC"
    env GOTOOLCHAIN=auto go test ./main
) 2>&1 | tee "$XRAY_RANK_TEST_LOG" || {
    die "Xray Rank registration test failed: last output in $XRAY_RANK_TEST_LOG"
}

ok "Integrated Rank command registration test passed"


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

log "[2/5] Building Xray Android binaries"


# ==============================================================================
# Xray build helper
# ==============================================================================

build_xray() {
    local abi="$1"
    local goarch="$2"
    local cc_name="$3"
    local goarm="${4:-}"

    MILESTONE="Building Xray for $abi"

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

    # Captured, not just streamed: the compile's own error text is the only
    # first-hand account of why it died, and it has to survive the loss of the
    # step log (see failure_diagnostics()).
    local build_log="$CORE/xray-build-$abi.log"

    LAST_BUILD_LOG="$build_log"

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
        ) 2>&1 | tee "$build_log"

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
        ) 2>&1 | tee "$build_log"

    fi

    [[ -s "$output" ]] || {
        die "Xray Android build produced no file for $abi"
    }

    chmod 755 "$output"

    grep -a -F 'marble-rank' "$output" >/dev/null || {
        die "Integrated Rank command missing from Xray binary for $abi"
    }
    grep -a -F 'MARBLE_RANK ' "$output" >/dev/null || {
        die "Integrated Rank event protocol missing from Xray binary for $abi"
    }

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

    ok "$abi / Xray staging + integrated Rank"
done


# ==============================================================================
# 3/4 - HEV SOCKS5 Tunnel
# ==============================================================================

log "[3/5] Cloning HEV SOCKS5 Tunnel $HEV_TAG"

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

    ok "Installed single Xray + Rank -> $abi"

done

ok "Single Xray binary per ABI safely installed after ndk-build"


# ==============================================================================
# Single-core APK size guard
# ==============================================================================

duplicate_rank="$(
    find "$JNILIBS" -type f -name 'libmarblerank.so' -print -quit 2>/dev/null || true
)"
[[ -z "$duplicate_rank" ]] || {
    die "Duplicate Rank/Xray payload detected: $duplicate_rank"
}

ok "APK size guard: no duplicate libmarblerank.so"


# ==============================================================================
# 4/4 - Xray assets
# ==============================================================================

log "[4/5] Downloading Xray geo assets for $XRAY_TAG"

XRAY_ZIP="$CORE/xray-release.zip"
XRAY_ASSET_NAME="Xray-linux-64.zip"

# The asset name is fixed by the Xray release contract, so querying the GitHub
# Releases API only to rediscover this URL adds an unnecessary rate-limited
# dependency. GitHub-hosted runners share unauthenticated API quotas and can
# receive HTTP 403 even while the public release asset itself is available.
# Use the stable release download URL directly instead.
[[ "$XRAY_TAG" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || {
    die "Unsafe Xray release tag in core-lock.json: $XRAY_TAG"
}

XRAY_ASSET_URL="https://github.com/XTLS/Xray-core/releases/download/${XRAY_TAG}/${XRAY_ASSET_NAME}"

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
# 5/5 - sing-box extended (second engine)
#
# MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 - this core is BUILT HERE. It used to be
# the upstream Android release artifact, and that artifact cannot survive
# MarbleNG's process model:
#
#   * MarbleNG spawns the core with ProcessBuilder, so the child has an app UID
#     and no PlatformInterface (only libbox/SFA injects one).
#   * sing-tun refuses netlink on GOOS=android, so route.NewNetworkManager
#     tolerates the refusal and leaves the interface monitor nil.
#   * protocol/direct/outbound.go then calls
#     InterfaceMonitor().MyInterfaces() from Outbound.Start(StartStatePostStart)
#     for every `direct` outbound - a method call on a nil interface:
#
#       panic: runtime error: invalid memory address or nil pointer dereference
#       [signal SIGSEGV: segmentation violation code=0x1 addr=0x30 pc=0x...]
#
#     Exit status 2, surfaced as `core-start: exited 2: ...`. Every MarbleNG
#     config carries a `direct` outbound (bypass routing, rule-set downloads,
#     the DNS bootstrap detour), so every sing-box start died: the session went
#     BLOCKED, failover replayed the crash once per node, and URL test / Real
#     delay answered reachable=0 for the whole subscription.
#
# No configuration avoids it (`action: "direct"` is parsed by 1.14 but the route
# router never selects it), so the binary itself has to carry the fix. Upstream
# agrees: SagerNet/sing-box#4498 is this exact trace from an unprivileged
# Android CLI, fixed one day later in 288411b0b9044c11a00a8ab478000e3ec1133101
# ("Fix crash when interface monitor is unavailable") - newer than the pinned
# release and not merged by the extended fork.
#
# scripts/inject-singbox-android-fix.py backports that commit onto the pinned
# source (anchor-guarded: a moved anchor fails this build instead of silently
# shipping a crashing core), adds the Go regression tests that reproduce the
# device condition on any host, and only then are the four ABIs compiled:
#
#   ABI           GOOS/GOARCH      NDK compiler
#   arm64-v8a     android/arm64    aarch64-linux-android<API>-clang
#   armeabi-v7a   android/arm(7)   armv7a-linux-androideabi<API>-clang
#   x86_64        android/amd64    x86_64-linux-android<API>-clang
#   x86           android/386      i686-linux-android<API>-clang
#
# Build settings are the pinned fork's own (.goreleaser.yaml build id `android`):
# CGO=1 against the NDK, the same feature tags, the same -checklinkname=0. The
# two deliberate differences are the backport and a `-marble.<patch>` version
# suffix, which makes the patched core identifiable from `sing-box version` and
# gives this script something to grep for in the finished binary.
#
# It is installed as libsingbox.so because Android only extracts files named
# lib*.so from jniLibs, and the app executes it with ProcessBuilder exactly
# like libxray.so.
# ==============================================================================

python3 "$ROOT/scripts/prepare-singbox-rules.py"
log "[5/5] Building sing-box extended $SINGBOX_TAG"

[[ "$SINGBOX_TAG" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || {
    die "Unsafe sing-box release tag in core-lock.json: $SINGBOX_TAG"
}

SINGBOX_REPO="$(jq -r '.singbox.repo // "shtorm-7/sing-box-extended"' "$LOCK")"

[[ "$SINGBOX_REPO" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || {
    die "Unsafe sing-box repository in core-lock.json: $SINGBOX_REPO"
}

# Source integrity moved from archive digests to the commit the tag resolves to:
# the payload is compiled here, so the pin has to be the source, not a tarball.
SINGBOX_COMMIT="$(jq -r '.singbox.commit // empty' "$LOCK")"

if [[ -n "$SINGBOX_COMMIT" ]]; then
    [[ "$SINGBOX_COMMIT" =~ ^[0-9a-f]{40}$ ]] || {
        die "Invalid .singbox.commit in core-lock.json: $SINGBOX_COMMIT"
    }
fi

SINGBOX_PATCH_LEVEL="$(jq -r '.singbox.patch // empty' "$LOCK")"

[[ -n "$SINGBOX_PATCH_LEVEL" ]] || {
    die "Missing .singbox.patch in core-lock.json"
}

[[ "$SINGBOX_PATCH_LEVEL" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || {
    die "Unsafe .singbox.patch in core-lock.json: $SINGBOX_PATCH_LEVEL"
}

SINGBOX_SRC="$CORE/singbox-src"
SINGBOX_STAGE="$CORE/singbox"

rm -rf "$SINGBOX_SRC" "$SINGBOX_STAGE"
mkdir -p "$SINGBOX_STAGE"

# Retried for the same reason as the Xray clone above.
clone_singbox_source() {
    rm -rf "$SINGBOX_SRC"
    git clone \
        --quiet \
        --depth 1 \
        --branch "$SINGBOX_TAG" \
        "https://github.com/${SINGBOX_REPO}.git" \
        "$SINGBOX_SRC"
}

retry 3 5 clone_singbox_source || {
    die "sing-box source clone failed after retries: ${SINGBOX_REPO} ${SINGBOX_TAG}"
}

[[ -f "$SINGBOX_SRC/go.mod" ]] || {
    die "sing-box go.mod missing after clone"
}

SINGBOX_HEAD="$(
    git -C "$SINGBOX_SRC" \
        rev-parse \
        HEAD
)"

if [[ -n "$SINGBOX_COMMIT" && "$SINGBOX_HEAD" != "$SINGBOX_COMMIT" ]]; then
    die "sing-box source is not the pinned commit: $SINGBOX_HEAD != $SINGBOX_COMMIT"
fi

echo "sing-box source : $SINGBOX_REPO $SINGBOX_TAG"
echo "sing-box commit : $SINGBOX_HEAD"

# ---------------------------------------------------------------------------
# The crash fix, and the proof that it is in the tree before anything compiles
# ---------------------------------------------------------------------------

python3 "$ROOT/scripts/inject-singbox-android-fix.py" "$SINGBOX_SRC"

grep -F 'MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157' \
    "$SINGBOX_SRC/protocol/direct/outbound.go" >/dev/null || {
        die "sing-box Android CLI crash fix is missing from protocol/direct/outbound.go"
    }

# The module graph is fetched explicitly (and retried) before the tests run,
# so a dead module transfer fails as "dependency download failed" instead of
# masquerading as "regression tests failed".
log "Downloading sing-box Go modules"

SINGBOX_MODULE_LOG="$CORE/singbox-module-download.log"

singbox_download_modules() {
    (
        cd "$SINGBOX_SRC"
        env GOTOOLCHAIN=auto go mod download
    ) 2>&1 | tee "$SINGBOX_MODULE_LOG"
}

retry 4 5 singbox_download_modules || {
    die "sing-box go mod download failed after retries: last output in $SINGBOX_MODULE_LOG"
}

ok "sing-box dependencies prepared"

log "Running the injected sing-box regression tests (nil interface monitor)"

# Host architecture, no CGO: these tests recreate the Android condition with a
# NetworkManager stub whose InterfaceMonitor() is nil, so they pin the guard on
# a Linux runner where a real monitor would otherwise hide the crash.
SINGBOX_TEST_LOG="$CORE/singbox-regression-test.log"

(
    cd "$SINGBOX_SRC"

    env \
        GOTOOLCHAIN=auto \
        CGO_ENABLED=0 \
        go test \
            ./protocol/direct \
            ./route
) 2>&1 | tee "$SINGBOX_TEST_LOG" || {
    echo
    echo "Last test output:"
    tail -n 30 "$SINGBOX_TEST_LOG" >&2 || true
    die "sing-box Android CLI crash regression tests failed"
}

ok "sing-box crash backport verified by go test"

# ---------------------------------------------------------------------------
# Resolve the Android package graph before a single ABI is compiled
#
# The regression tests above run on the host and with no build tags, so they
# cannot see what the release build sees: `go list -deps` with the real tags
# and the real GOOS/GOARCH can. A generated-but-absent embedded asset (the
# admin panel's `dist/`) or any other unresolvable import fails here in
# seconds with the compiler's own message, instead of ~4 minutes into the
# job after Xray, HEV and the JNI bridge have already been built.
# ---------------------------------------------------------------------------

log "Resolving the sing-box Android package graph"

SINGBOX_GRAPH_LOG="$CORE/singbox-package-graph.log"

singbox_resolve_graph() {
    local goarch="$1"
    local goarm="${2:-}"

    (
        cd "$SINGBOX_SRC"

        env \
            GOTOOLCHAIN=auto \
            GOOS=android \
            GOARCH="$goarch" \
            ${goarm:+GOARM="$goarm"} \
            CGO_ENABLED=1 \
            go list \
                -deps \
                -tags "$SINGBOX_TAGS" \
                ./cmd/sing-box \
                >/dev/null
    ) 2>&1 | tee "$SINGBOX_GRAPH_LOG"
}

singbox_resolve_graph "arm64" || {
    tail -n 20 "$SINGBOX_GRAPH_LOG" >&2 || true
    die "sing-box Android package graph does not resolve (arm64): see $SINGBOX_GRAPH_LOG"
}

singbox_resolve_graph "arm" "7" || {
    tail -n 20 "$SINGBOX_GRAPH_LOG" >&2 || true
    die "sing-box Android package graph does not resolve (arm): see $SINGBOX_GRAPH_LOG"
}

singbox_resolve_graph "amd64" || {
    tail -n 20 "$SINGBOX_GRAPH_LOG" >&2 || true
    die "sing-box Android package graph does not resolve (amd64): see $SINGBOX_GRAPH_LOG"
}

singbox_resolve_graph "386" || {
    tail -n 20 "$SINGBOX_GRAPH_LOG" >&2 || true
    die "sing-box Android package graph does not resolve (386): see $SINGBOX_GRAPH_LOG"
}

ok "sing-box Android package graph resolves for every ABI"

# ---------------------------------------------------------------------------
# sing-box build helper
# ---------------------------------------------------------------------------

# The pinned fork's own tag list (.goreleaser.yaml build id `android`).
# with_clash_api is load-bearing: the URL test reads the core's Clash
# controller. Changing this list changes which protocols the product supports.
#
# with_admin_panel is deliberately NOT in this list, and it can never be
# added back without also generating its assets. The fork's
# service/admin_panel/service.go carries
#
#     //go:embed dist
#     var distFS embed.FS
#
# but `service/admin_panel/dist` is neither committed nor committable: it is
# the Vite bundle the fork builds with `npm run build` + `go run
# ./cmd/internal/admin_panel_pack` in its own release pipeline, and the
# repository's .gitignore excludes `dist`. Upstream therefore only compiles
# because goreleaser runs after that step; a plain source clone always dies
# with
#
#     service/admin_panel/service.go:48:12: pattern dist: no matching files found
#
# which is exactly how the first source-built release on main stopped: every
# native build after PR #130 failed at "Building sing-box for arm64-v8a".
#
# MarbleNG is a client and never configures an admin-panel service, and the
# tag's absence is not silent: the fork's service_stub.go registers the type
# and answers "Admin panel is not included in this build, rebuild with -tags
# with_admin_panel" for anyone who does.
SINGBOX_TAGS="with_gvisor,with_quic,with_dhcp,with_wireguard,with_utls,with_acme,with_clash_api,with_tailscale,with_masque,with_mtproxy,with_trusttunnel,with_call,with_sudoku,with_manager,with_profiler,badlinkname,tfogo_checklinkname0"

SINGBOX_VERSION="${SINGBOX_TAG#v}"
SINGBOX_BUILD_VERSION="${SINGBOX_VERSION}-marble.${SINGBOX_PATCH_LEVEL}"

# Verify the binary really is the requested Android architecture.
singbox_assert_machine() {
    local file="$1" expected="$2"

    "$LLVM_READELF" -h "$file" 2>/dev/null |
        grep -q "Machine:.*$expected" || {
            die "sing-box binary is not $expected: $file"
        }
}

build_singbox() {
    local abi="$1"
    local goarch="$2"
    local cc_name="$3"
    local machine="$4"
    local goarm="${5:-}"

    MILESTONE="Building sing-box for $abi"

    local out_dir="$SINGBOX_STAGE/$abi"
    local output="$out_dir/libsingbox.so"
    local cc="$NDK_BIN/$cc_name"

    mkdir -p "$out_dir"

    [[ -x "$cc" ]] || {
        die "Android compiler for $abi does not exist: $cc"
    }

    echo
    echo "================================================================"
    echo " Building sing-box extended for Android"
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
    echo "Version      : $SINGBOX_BUILD_VERSION"
    echo "Crash fix    : MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157"
    echo "Stage output : $output"

    echo -n "Go toolchain : "

    (
        cd "$SINGBOX_SRC"
        env GOTOOLCHAIN=auto go version
    )

    echo "================================================================"
    echo

    # Captured, not just streamed: a compiler error here has to survive the
    # loss of the step log (see failure_diagnostics()).
    local build_log="$CORE/singbox-build-$abi.log"

    LAST_BUILD_LOG="$build_log"

    if [[ -n "$goarm" ]]; then

        (
            cd "$SINGBOX_SRC"

            env \
                GOTOOLCHAIN=auto \
                GOOS=android \
                GOARCH="$goarch" \
                GOARM="$goarm" \
                CGO_ENABLED=1 \
                CC="$cc" \
                CXX="$cc" \
                go build \
                    -buildmode=pie \
                    -trimpath \
                    -buildvcs=false \
                    -tags "$SINGBOX_TAGS" \
                    -ldflags "-X github.com/sagernet/sing-box/constant.Version=${SINGBOX_BUILD_VERSION} -s -w -buildid= -checklinkname=0" \
                    -o "$output" \
                    ./cmd/sing-box
        ) 2>&1 | tee "$build_log"

    else

        (
            cd "$SINGBOX_SRC"

            env \
                GOTOOLCHAIN=auto \
                GOOS=android \
                GOARCH="$goarch" \
                CGO_ENABLED=1 \
                CC="$cc" \
                CXX="$cc" \
                go build \
                    -buildmode=pie \
                    -trimpath \
                    -buildvcs=false \
                    -tags "$SINGBOX_TAGS" \
                    -ldflags "-X github.com/sagernet/sing-box/constant.Version=${SINGBOX_BUILD_VERSION} -s -w -buildid= -checklinkname=0" \
                    -o "$output" \
                    ./cmd/sing-box
        ) 2>&1 | tee "$build_log"

    fi

    [[ -s "$output" ]] || {
        die "sing-box Android build produced no file for $abi"
    }

    chmod 755 "$output"

    # A wrongly labelled or unpatched core must fail the build here, not the
    # user's first connection.
    singbox_assert_machine "$output" "$machine"

    grep -a -F "$SINGBOX_BUILD_VERSION" "$output" >/dev/null || {
        die "Patched sing-box version marker missing from the $abi binary"
    }

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

    ok "sing-box staged: $abi"
    echo "     size   : $size bytes"
    echo "     sha256 : $hash"

    mkdir -p "$JNILIBS/$abi"

    cp -f \
        "$output" \
        "$JNILIBS/$abi/libsingbox.so"

    chmod 755 "$JNILIBS/$abi/libsingbox.so"

    [[ -s "$JNILIBS/$abi/libsingbox.so" ]] || {
        die "Could not install sing-box into jniLibs for $abi"
    }

    ok "Installed sing-box extended $SINGBOX_BUILD_VERSION -> $abi"
}

build_singbox \
    "arm64-v8a" \
    "arm64" \
    "aarch64-linux-android${ANDROID_NATIVE_API}-clang" \
    "AArch64"

build_singbox \
    "armeabi-v7a" \
    "arm" \
    "armv7a-linux-androideabi${ANDROID_NATIVE_API}-clang" \
    "ARM" \
    "7"

build_singbox \
    "x86_64" \
    "amd64" \
    "x86_64-linux-android${ANDROID_NATIVE_API}-clang" \
    "Advanced Micro Devices X86-64"

build_singbox \
    "x86" \
    "386" \
    "i686-linux-android${ANDROID_NATIVE_API}-clang" \
    "Intel 80386"

ok "sing-box extended built and installed for every ABI"



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
    SINGBOX_FILE="$JNILIBS/$abi/libsingbox.so"


    # --------------------------------------------------------------------------
    # sing-box extended
    # --------------------------------------------------------------------------

    if [[ -s "$SINGBOX_FILE" ]]; then

        SINGBOX_SIZE="$(
            wc -c < "$SINGBOX_FILE" |
            tr -d ' '
        )"

        echo "[OK] $abi / sing-box extended"
        echo "     $SINGBOX_SIZE bytes"

    else

        echo "[FAIL] $abi / sing-box extended missing:"
        echo "       $SINGBOX_FILE"

        FAILED=1

    fi



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

    ok "$abi Xray + integrated Rank checksum verified"

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
    echo "sing-box extended:"
    sha256sum "$JNILIBS/$abi/libsingbox.so"

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
echo "sing-box extended"
echo "  Tag             : $SINGBOX_TAG"
echo "  Repository      : $SINGBOX_REPO"

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
