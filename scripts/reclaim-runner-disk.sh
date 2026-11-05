#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MARBLE_RUNNER_DISK_HEADROOM_V268 - the release job's disk reclaimer.
#
# Release run #377 ("Build signed Android APKs", main, 2026-11-04) did not die
# compiling, linking, shrinking or signing anything. Every native core built for
# all four ABIs, the routing assets verified, the signer validated, and then:
#
#   > Task :app:stripReleaseDebugSymbols FAILED
#   > A failure occurred while executing ...StripDebugSymbolsRunnable
#      > .../merged_native_libs/release/.../lib/x86_64/libsingbox.so ->
#        .../stripped_native_libs/release/.../lib/x86_64/libsingbox.so:
#        No space left on device
#
# stripReleaseDebugSymbols is the *second* full copy of the merged native
# libraries (~500 MB across four ABIs), so it is simply the place where a
# filesystem that is already at its limit refuses the next write. The copy is
# not the bug; the runner was full.
#
# The build is not what filled it either. The ubuntu-latest image (Ubuntu 26.04,
# image version 20261026.180.1) ships, on the same filesystem as the job:
#
#   - three NDKs (27.3.13750724, 29.0.14206865, 30.0.16248370) - the build uses
#     exactly one, and it is not one of those three;
#   - eighteen Android platforms, four of them 37.2 pre-release revisions;
#   - eleven .NET SDKs, Homebrew, vcpkg, GHC/GHCup/Stack/Cabal, Swift 6.4,
#     Chrome, Chromium, Edge, Firefox, PowerShell's Az module, the Google Cloud
#     and Azure CLIs, Bazel, Kind, Minikube, MySQL and PostgreSQL;
#   - four JVMs, three Go toolchains, five Pythons, three PyPys, four Rubies and
#     two Node.js releases under /opt/hostedtoolcache.
#
# None of that is reachable from an Android release build: the job installs its
# own JDK (setup-java), its own Go (setup-go) and its own NDK (sdkmanager), and
# the repository's Python is the distro one. So the job now takes that space
# back before the first toolchain is installed, and proves afterwards that the
# filesystem the build needs still has headroom.
#
# Design rules:
#
#   1. Every candidate is an absolute path listed in this file once, and is
#      removed only if it exists; a missing path is not an error, so a smaller
#      image is a no-op rather than a red build.
#   2. The two Android pins the build actually uses - the platform its
#      `compileSdk` compiles against and the NDK its `ndkVersion` names - are
#      read from app/build.gradle.kts, never hard-coded here, so this script
#      cannot delete the SDK the build is about to use.
#   3. Nothing inside the repository, inside the workspace, or inside the
#      Android SDK's cmdline-tools/platform-tools/build-tools/licenses/extras is
#      ever a candidate, and a candidate that resolves to one of those is
#      refused.
#   4. The command-line tools the rest of the job needs are re-verified after
#      the deletions, so a reclaimed byte can never cost the build a tool.
#   5. A filesystem that still misses the floor fails here, in one readable
#      line, with the largest directories named - instead of four minutes later
#      inside a Gradle work item whose log is unreachable.
#   6. MARBLE_DISK_ROOT lets the offline fixtures (scripts/
#      test-reclaim-runner-disk.sh) run this same code against a fake image
#      tree. On a real runner it is "/", which is also when the docker and apt
#      caches join in.
#
# Environment:
#
#   MARBLE_DISK_ROOT        image tree to clean              (default: /)
#   MARBLE_DISK_WORKSPACE   filesystem that must have room   (default: $PWD)
#   MARBLE_MIN_FREE_MIB     hard floor in MiB                (default: 12000)
#   MARBLE_WARN_FREE_MIB    warning floor in MiB             (default: 20000)
#   MARBLE_EXTRA_BALLAST    extra absolute paths, space separated
#
# ==============================================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

DISK_ROOT="${MARBLE_DISK_ROOT:-/}"
DISK_ROOT="${DISK_ROOT%/}"

WORKSPACE="${MARBLE_DISK_WORKSPACE:-$PWD}"

MIN_FREE_MIB="${MARBLE_MIN_FREE_MIB:-12000}"
WARN_FREE_MIB="${MARBLE_WARN_FREE_MIB:-20000}"

# ==============================================================================
# Logging helpers (the vocabulary of every other MarbleNG build script)
# ==============================================================================

log() {
    printf '\033[1;36m[MarbleNG]\033[0m %s\n' "$*"
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

# Annotation commands are single-line: a newline ends the command and truncates
# the message, so multi-line bodies are %0A-encoded (and percent signs escaped
# first, or %0A would decode as text). Same helper prepare-native.sh uses,
# because a dead release build has to name its cause in the run itself.
annotate_error() {
    local title="$1"
    local body="$2"

    body="${body//%/%25}"
    body="${body//$'\r'/ }"
    body="${body//$'\n'/%0A}"

    printf '::error title=%s::%s\n' "$title" "$body"
}

# ==============================================================================
# Inputs
# ==============================================================================

[[ -d "$WORKSPACE" ]] || {
    die "MARBLE_DISK_WORKSPACE does not exist: $WORKSPACE"
}

[[ "$MIN_FREE_MIB" =~ ^[0-9]+$ ]] || {
    die "MARBLE_MIN_FREE_MIB must be a number of MiB, got: $MIN_FREE_MIB"
}

[[ "$WARN_FREE_MIB" =~ ^[0-9]+$ ]] || {
    die "MARBLE_WARN_FREE_MIB must be a number of MiB, got: $WARN_FREE_MIB"
}

if [[ -n "$DISK_ROOT" ]]; then
    [[ -d "$DISK_ROOT" ]] || {
        die "MARBLE_DISK_ROOT does not exist: $DISK_ROOT"
    }
fi

# sudo, when the trees belong to root and we are not root. Absent sudo is not
# fatal: remove_tree() says which path it could not take and carries on.
SUDO=""
if [[ "$(id -u)" != "0" ]] && command -v sudo >/dev/null 2>&1; then
    SUDO="sudo"
fi

# ==============================================================================
# Free space of the filesystem the build writes to
# ==============================================================================

free_mib() {
    local kib

    kib="$(df -Pk -- "$WORKSPACE" 2>/dev/null | awk 'NR==2 {print $4}')"

    [[ -n "$kib" ]] || {
        die "could not read the free space of $WORKSPACE"
    }

    echo $(( kib / 1024 ))
}

report_disk() {
    local label="$1"

    echo
    echo "Disk ($label):"
    df -h -- "$WORKSPACE" 2>/dev/null || true
    df -Pm -- "$WORKSPACE" 2>/dev/null | awk 'NR==2 {printf "  available: %d MiB\n", $4}'
}

# The largest directories under the cleaned tree. Only ever called on the
# failure path, where a few seconds of `du` are cheaper than a mystery, and
# bounded so a pathological tree cannot outlive the step.
largest_directories() {
    local where="${1:-$DISK_ROOT}"
    local depth="${2:-2}"

    [[ -d "$where" ]] || return 0

    echo
    echo "Largest directories under ${where:-/}:"

    if command -v timeout >/dev/null 2>&1; then
        timeout 120 du -xk -d "$depth" -- "$where" 2>/dev/null |
            sort -rn |
            head -n 20 |
            awk '{printf "  %8.1f MiB  %s\n", $1 / 1024, $2}' || true
    else
        du -xk -d "$depth" -- "$where" 2>/dev/null |
            sort -rn |
            head -n 20 |
            awk '{printf "  %8.1f MiB  %s\n", $1 / 1024, $2}' || true
    fi
}

# ==============================================================================
# The Android pins this build actually uses
#
# Read from app/build.gradle.kts so the reclaimer can never drift from the
# build it feeds: a compileSdk bump moves the platform this script spares, and
# an ndkVersion bump moves the NDK it spares.
# ==============================================================================

GRADLE_FILE="$ROOT/app/build.gradle.kts"

[[ -s "$GRADLE_FILE" ]] || {
    die "Cannot read the Android pins: $GRADLE_FILE is missing"
}

COMPILE_SDK="$(
    sed -n 's/^[[:space:]]*val[[:space:]]*compileSdkApi[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
        "$GRADLE_FILE" | tail -n 1
)"

PINNED_NDK="$(
    sed -n 's/^[[:space:]]*ndkVersion[[:space:]]*=[[:space:]]*"\([0-9][0-9.]*\)".*/\1/p' \
        "$GRADLE_FILE" | tail -n 1
)"

[[ "$COMPILE_SDK" =~ ^[0-9]+$ ]] || {
    die "Could not read compileSdk from $GRADLE_FILE"
}

[[ "$PINNED_NDK" =~ ^[0-9]+(\.[0-9]+)+$ ]] || {
    die "Could not read ndkVersion from $GRADLE_FILE"
}

KEEP_PLATFORM="android-$COMPILE_SDK"

ok "Android pins spared: platforms/$KEEP_PLATFORM and ndk/$PINNED_NDK"

# ==============================================================================
# Deletion
# ==============================================================================

removed_count=0

# Refuse anything that is not an absolute, at-least-two-component path outside
# the repository and the workspace. The candidate list above is written by hand
# and reviewed, but a hand-written list is exactly the kind of thing a future
# edit gets wrong, and `rm -rf` has no undo.
assert_safe_target() {
    local target="$1"

    [[ "$target" == /* ]] || {
        die "refusing a relative deletion target: $target"
    }

    [[ "$target" != "/" ]] || {
        die "refusing to delete the filesystem root"
    }

    # At least two components: "/usr" alone is never a legitimate candidate.
    [[ "$target" =~ ^/[^/]+/[^/]+ ]] || {
        die "refusing a top-level deletion target: $target"
    }

    local resolved="$DISK_ROOT$target"

    [[ "$resolved" != "$ROOT" && "$resolved" != "$ROOT"/* ]] || {
        die "refusing to delete inside the repository: $target"
    }

    [[ "$resolved" != "$WORKSPACE" && "$resolved" != "$WORKSPACE"/* ]] || {
        die "refusing to delete inside the workspace: $target"
    }

    case "$resolved" in
        */android/sdk/cmdline-tools*|*/android/sdk/platform-tools*|\
        */android/sdk/build-tools*|*/android/sdk/licenses*|*/android/sdk/extras*)
            die "refusing to delete an Android SDK component the build needs: $target"
            ;;
    esac
}

remove_tree() {
    local target="$1"

    if [[ "$(id -u)" == "0" || -w "$(dirname -- "$target")" ]]; then
        rm -rf -- "$target"
    elif [[ -n "$SUDO" ]]; then
        "$SUDO" rm -rf -- "$target"
    else
        warn "cannot remove $target: not writable and sudo is unavailable"
        return 1
    fi
}

# reclaim <absolute path inside the image> <why it is dead weight>
reclaim() {
    local target="$1"
    local reason="$2"

    assert_safe_target "$target"

    local full="$DISK_ROOT$target"

    if [[ ! -e "$full" ]]; then
        echo "  absent  $target"
        return 0
    fi

    if remove_tree "$full"; then
        removed_count=$(( removed_count + 1 ))
        echo "  removed $target ($reason)"
    else
        echo "  kept    $target ($reason)"
    fi
}

# ==============================================================================
# The candidate list
# ==============================================================================

# A caller-supplied candidate is validated before anything at all is deleted,
# so a bad extra path fails on an untouched filesystem instead of after the
# ballast is already gone.
for extra in ${MARBLE_EXTRA_BALLAST:-}; do
    assert_safe_target "$extra"
done

log "Reclaiming runner disk headroom"

report_disk "before"

before_mib="$(free_mib)"

echo
echo "Preinstalled image ballast (unreachable from an Android release build):"

# --- Toolchains and language runtimes the job installs itself ---------------
reclaim /usr/share/dotnet                 "eleven .NET SDKs"
reclaim /usr/share/swift                  "Swift runtime"
reclaim /usr/lib/swift                    "Swift libraries"
reclaim /opt/ghc                          "GHC"
reclaim /usr/local/.ghcup                 "GHCup, Stack, Cabal"
reclaim /usr/local/share/vcpkg            "vcpkg package tree"
reclaim /home/linuxbrew                   "Homebrew"
reclaim /usr/share/miniconda              "Miniconda"
reclaim /usr/lib/jvm                      "distro JVMs; setup-java installs Temurin 17"
reclaim /opt/hostedtoolcache/Go           "Go toolchains; setup-go installs the pinned one"
reclaim /opt/hostedtoolcache/Python       "CPythons; the build uses the distro python3"
reclaim /opt/hostedtoolcache/PyPy         "PyPys"
reclaim /opt/hostedtoolcache/Ruby         "Rubies"
reclaim /opt/hostedtoolcache/node         "Node.js; actions use the runner's own runtime"
reclaim /opt/hostedtoolcache/Java_8_X64   "Temurin 8"
reclaim /opt/hostedtoolcache/Java_11_X64  "Temurin 11"
reclaim /opt/hostedtoolcache/Java_21_X64  "Temurin 21"
reclaim /opt/hostedtoolcache/Java_25_X64  "Temurin 25"
reclaim /opt/hostedtoolcache/CodeQL       "CodeQL bundle"

# --- CLIs, browsers and services no build step can reach -------------------
reclaim /usr/lib/google-cloud-sdk         "Google Cloud CLI"
reclaim /opt/az                           "Azure CLI"
reclaim /usr/local/share/powershell/Modules "PowerShell modules, including Az"
reclaim /opt/google/chrome                "Chrome"
reclaim /usr/lib/chromium                 "Chromium"
reclaim /usr/lib/firefox                  "Firefox"
reclaim /usr/local/share/chromedriver-linux64 "ChromeDriver"
reclaim /usr/local/share/edge_driver      "Edge WebDriver"
reclaim /usr/local/share/gecko_driver     "GeckoDriver"
reclaim /usr/share/java/selenium-server.jar "Selenium server"

# --- Android SDK: every NDK and platform except the two pins ---------------
#
# The image's NDKs are the single largest item on it and the build uses none of
# them: app/build.gradle.kts names ndkVersion "$PINNED_NDK", which the workflow
# installs with sdkmanager in the very next steps.
SDK="${DISK_ROOT}/usr/local/lib/android/sdk"

shopt -s nullglob

if [[ -d "$SDK/ndk" ]]; then
    for ndk_dir in "$SDK/ndk"/*; do
        [[ -d "$ndk_dir" ]] || continue
        if [[ "$(basename -- "$ndk_dir")" == "$PINNED_NDK" ]]; then
            echo "  spared  /usr/local/lib/android/sdk/ndk/$PINNED_NDK (ndkVersion)"
            continue
        fi
        reclaim "/usr/local/lib/android/sdk/ndk/$(basename -- "$ndk_dir")" "unused NDK"
    done
fi

if [[ -d "$SDK/system-images" ]]; then
    reclaim /usr/local/lib/android/sdk/system-images "emulator system images"
fi

if [[ -d "$SDK/platforms" ]]; then
    for platform_dir in "$SDK/platforms"/*; do
        [[ -d "$platform_dir" ]] || continue
        platform_name="$(basename -- "$platform_dir")"
        if [[ "$platform_name" =~ ^android-${COMPILE_SDK}(\.[0-9]+)?$ ]]; then
            echo "  spared  /usr/local/lib/android/sdk/platforms/$platform_name (compileSdk)"
            continue
        fi
        reclaim "/usr/local/lib/android/sdk/platforms/$(basename -- "$platform_dir")" "unused platform"
    done
fi

shopt -u nullglob

# --- Anything a caller adds (the fixtures use this to prove the guards) ----
for extra in ${MARBLE_EXTRA_BALLAST:-}; do
    reclaim "$extra" "caller-supplied"
done

# --- Package and container caches (real runner only) -----------------------
if [[ -z "$DISK_ROOT" ]]; then
    if command -v docker >/dev/null 2>&1; then
        echo "  pruning docker images, build cache and volumes"
        docker system prune --all --force --volumes >/dev/null 2>&1 ||
            warn "docker system prune failed; continuing"
    fi

    if command -v apt-get >/dev/null 2>&1; then
        echo "  clearing the apt cache"
        apt-get clean >/dev/null 2>&1 ||
            ${SUDO} apt-get clean >/dev/null 2>&1 ||
            warn "apt-get clean failed; continuing"
        if [[ -d /var/lib/apt/lists ]]; then
            rm -rf /var/lib/apt/lists 2>/dev/null ||
                ${SUDO} rm -rf /var/lib/apt/lists 2>/dev/null ||
                warn "could not clear /var/lib/apt/lists; continuing"
        fi
    fi
fi

# ==============================================================================
# The tools the rest of the job needs must still be there
#
# A reclaimed byte that costs the build a command is a worse failure than the
# one this script prevents, so the list prepare-native.sh and the release steps
# actually call is re-checked after the deletions.
# ==============================================================================

echo
echo "Verifying the tools the release build needs:"

REQUIRED_COMMANDS=(
    bash
    git
    jq
    python3
    curl
    unzip
    zip
    tar
    gzip
    sed
    awk
    grep
    find
    sort
    wc
    tr
    cut
    stat
    base64
    sha256sum
    df
    du
)

for cmd in "${REQUIRED_COMMANDS[@]}"; do
    command -v "$cmd" >/dev/null 2>&1 || {
        die "reclaiming disk headroom removed a command the build needs: $cmd"
    }
done

ok "all ${#REQUIRED_COMMANDS[@]} required commands still resolve"

# ==============================================================================
# Headroom, stated
# ==============================================================================

report_disk "after"

after_mib="$(free_mib)"

reclaimed=$(( after_mib - before_mib ))
if (( reclaimed < 0 )); then
    reclaimed=0
fi

echo
log "Removed $removed_count preinstalled tree(s); ${reclaimed} MiB more free than before"

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
        echo "### Runner disk headroom (MARBLE_RUNNER_DISK_HEADROOM_V268)"
        echo
        echo "- Removed preinstalled trees: \`${removed_count}\`"
        echo "- Free before: \`${before_mib} MiB\`"
        echo "- Free after: \`${after_mib} MiB\`"
        echo "- Reclaimed: \`${reclaimed} MiB\`"
        echo "- Floor: \`${MIN_FREE_MIB} MiB\` on \`${WORKSPACE}\`"
        echo
    } >> "$GITHUB_STEP_SUMMARY"
fi

if (( after_mib < MIN_FREE_MIB )); then

    largest_directories "${DISK_ROOT:-/}" 2

    message="$(
        printf 'Only %s MiB free on %s after reclaiming %s MiB; the release build needs at least %s MiB.\n' \
            "$after_mib" "$WORKSPACE" "$reclaimed" "$MIN_FREE_MIB"
        printf 'stripReleaseDebugSymbols copies ~500 MB of merged native libraries and dies with "No space left on device" when the filesystem is full.\n'
        printf 'The largest directories are listed in this step; add whatever the image now ships to scripts/reclaim-runner-disk.sh.\n'
    )"

    if [[ -n "${GITHUB_ACTIONS:-}" ]]; then
        annotate_error "MarbleNG-runner-disk-headroom" "$message"
    fi

    die "$message"
fi

if (( after_mib < WARN_FREE_MIB )); then
    warn "only ${after_mib} MiB free after reclaiming: below the ${WARN_FREE_MIB} MiB comfort line"
fi

ok "Runner disk headroom: ${after_mib} MiB free, floor is ${MIN_FREE_MIB} MiB"

exit 0
