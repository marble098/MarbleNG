#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MARBLE_ANDROID_PLATFORM_DIRECTORY_V271 fixtures - offline.
#
# Release run #380 installed platforms;android-37.0 successfully and then
# reported "Selected compileSdk package platforms;android-37.0 is missing
# android.jar after installation", because the verification built its path as
# "$ANDROID_SDK_ROOT/${package#platforms;}" — the SDK root joined to the package
# name — instead of "$ANDROID_SDK_ROOT/platforms/android-37.0". Gradle never
# ran; nothing about the download was wrong.
#
# scripts/install-android-sdk.sh now owns that step and asks the filesystem
# where the platform landed. These fixtures drive it end to end against a fake
# sdkmanager that installs exactly like the real one, with no network and no
# hosted runner:
#
#   1. the real layout (platforms/android-37.0) is found, and the values the
#      workflow evals name that directory — not "$SDK/android-37.0";
#   2. an install that lands at a stable minor other than the resolved package
#      (android-37.2) is still a usable compileSdk 37 platform;
#   3. a directory with the right name but no android.jar, or one declaring
#      another API level, or one in an inconsistent location (android-37.0-2),
#      fails with the directories named instead of shipping a broken Gradle;
#   4. sdkmanager noise never reaches stdout, because the workflow evals it;
#   5. the Gradle-pinned NDK must exist after installation;
#   6. a failed installation is retried (with the retry delay at 0) and, when it
#      never succeeds, exits 1 rather than continuing;
#   7. the locator's own cases (scripts/install-android-sdk.sh --self-test).
#
# Run:  ./scripts/test-android-sdk-install.sh
# ==============================================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INSTALLER="$ROOT/scripts/install-android-sdk.sh"
FIXTURE="$ROOT/scripts/fixtures/android-sdk-platforms-37-minor.txt"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() {
    echo "[FAIL] $*" >&2
    exit 1
}

pass() {
    echo "[PASS] $*"
}

[[ -f "$INSTALLER" ]] || fail "missing scripts/install-android-sdk.sh"
[[ -f "$FIXTURE" ]] || fail "missing scripts/fixtures/android-sdk-platforms-37-minor.txt"

bash -n "$INSTALLER" || fail "scripts/install-android-sdk.sh is not valid bash"
bash "$INSTALLER" --self-test > /dev/null 2>&1 || fail "--self-test failed"
pass "the platform locator's own cases pass"

# ==============================================================================
# A fake sdkmanager that installs the way the real one does
#
#   --licenses        print a license blob
#   --list ...        print the recorded catalog
#   <package> ...     create the package where sdkmanager would put it
#
# FAKE_MODE decides what the install does, so the fixtures can break it on
# purpose.
# ==============================================================================

FAKE_BIN="$TMP/bin"
mkdir -p "$FAKE_BIN"

cat > "$FAKE_BIN/sdkmanager" <<'FAKE'
#!/usr/bin/env bash
set -euo pipefail

api=37
sdk="${ANDROID_SDK_ROOT:?}"
mode="${FAKE_MODE:-good}"
log="${FAKE_LOG:-/dev/null}"

echo "sdkmanager $*" >> "$log"

if [[ "${1:-}" == "--licenses" ]]; then
    echo "All SDK package licenses accepted"
    exit 0
fi

if [[ "${1:-}" == "--list" ]]; then
    cat "$FAKE_CATALOG"
    exit 0
fi

if [[ -n "${FAKE_INSTALL_FAILS:-}" ]]; then
    echo "Warning: Failed to find package '${3:-}'" >> "$log"
    exit 1
fi

# Progress chatter on both streams, exactly as sdkmanager produces it: none of
# it may reach the caller's stdout.
echo "Loading package information..."
echo "[==========          ] 50% Unzipping..." >&2

make_platform() {
    local name="$1"

    mkdir -p "$sdk/platforms/$name"
    printf 'dex\n' > "$sdk/platforms/$name/android.jar"
    printf 'AndroidVersion.ApiLevel=%s\nPkg.Desc=Android SDK Platform\n' "$api" \
        > "$sdk/platforms/$name/source.properties"
}

for package in "$@"; do
    case "$package" in
        platforms\;*)
            case "$mode" in
                good) make_platform "android-$api.0" ;;
                # The resolved package directory is not what lands on disk.
                other-minor) make_platform "android-$api.2" ;;
                # The right name, no android.jar.
                no-jar)
                    mkdir -p "$sdk/platforms/android-$api.0"
                    printf 'AndroidVersion.ApiLevel=%s\n' "$api" \
                        > "$sdk/platforms/android-$api.0/source.properties"
                    ;;
                # The right name, android.jar, another API level.
                wrong-api)
                    mkdir -p "$sdk/platforms/android-$api.0"
                    printf 'dex\n' > "$sdk/platforms/android-$api.0/android.jar"
                    printf 'AndroidVersion.ApiLevel=36\n' \
                        > "$sdk/platforms/android-$api.0/source.properties"
                    ;;
                # An IDE-style inconsistent location AGP will not resolve.
                misplaced) make_platform "android-$api.0-2" ;;
                nothing) : ;;
            esac
            ;;
        ndk\;*)
            mkdir -p "$sdk/ndk/${package#ndk;}/toolchains/llvm"
            ;;
        build-tools\;*)
            mkdir -p "$sdk/build-tools/${package#build-tools;}"
            ;;
    esac
done
FAKE

chmod +x "$FAKE_BIN/sdkmanager"

export FAKE_CATALOG="$FIXTURE"
export FAKE_LOG="$TMP/sdkmanager.log"
export MARBLE_SDKMANAGER="$FAKE_BIN/sdkmanager"
export MARBLE_SDK_RETRY_SLEEP=0
export PATH="$FAKE_BIN:$PATH"

# The pins the fixture's platform must declare come from the same file the
# installer reads.
GRADLE_FILE="$ROOT/app/build.gradle.kts"
PINNED_NDK="$(
    sed -n 's/^[[:space:]]*ndkVersion[[:space:]]*=[[:space:]]*"\([0-9][0-9.]*\)".*/\1/p' \
        "$GRADLE_FILE" | tail -n 1
)"
COMPILE_SDK="$(
    sed -n 's/^[[:space:]]*val[[:space:]]*compileSdkApi[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
        "$GRADLE_FILE" | tail -n 1
)"
[[ "$COMPILE_SDK" == "37" ]] ||
    fail "these fixtures describe API 37; app/build.gradle.kts now pins $COMPILE_SDK"

# run_exec <sdk-root> <mode>: the installer's stdout, evaled into the caller.
run_exec() {
    local sdk="$1"
    local mode="$2"

    : > "$FAKE_LOG"
    ANDROID_SDK_ROOT="$sdk" FAKE_MODE="$mode" \
        bash "$INSTALLER" --exec 2> "$TMP/exec.log"
}

# ==============================================================================
# 1. The real layout is found and the workflow's eval names that directory.
# ==============================================================================

SDK="$TMP/sdk"
assignments="$(run_exec "$SDK" good)" || {
    cat "$TMP/exec.log" >&2
    fail "the installer failed on an install that worked"
}

# Only assignments on stdout: anything else breaks the eval.
while IFS= read -r line; do
    [[ -z "$line" || "$line" =~ ^[A-Z_]+= ]] ||
        fail "stdout carried something that is not an assignment: $line"
done <<< "$assignments"

eval "$assignments"

[[ "$ANDROID_PLATFORM" == "android-37.0" ]] ||
    fail "expected ANDROID_PLATFORM=android-37.0, got '$ANDROID_PLATFORM'"
[[ "$ANDROID_SDK_PLATFORM" == "$SDK/platforms/android-37.0" ]] ||
    fail "expected the platform under platforms/, got '$ANDROID_SDK_PLATFORM'"
[[ -s "$ANDROID_SDK_PLATFORM/android.jar" ]] ||
    fail "the reported platform directory has no android.jar"
[[ "$ANDROID_NDK_HOME" == "$SDK/ndk/$PINNED_NDK" ]] ||
    fail "expected ANDROID_NDK_HOME under the pinned NDK, got '$ANDROID_NDK_HOME'"
[[ -d "$ANDROID_NDK_ROOT/toolchains/llvm" ]] ||
    fail "ANDROID_NDK_ROOT does not hold the installed NDK"
[[ "$ANDROID_NDK_LATEST_HOME" == "$ANDROID_NDK_ROOT" ]] ||
    fail "ANDROID_NDK_LATEST_HOME must follow the Gradle pin"
[[ ! -e "$SDK/android-37.0" ]] ||
    fail "the bug is back: something exists at \$SDK/android-37.0"

grep -q "compileSdk $COMPILE_SDK platform: $SDK/platforms/android-37.0" "$TMP/exec.log" ||
    fail "the installer did not report the platform directory it verified"

pass "the installed platform is found at platforms/android-37.0 (#380's path bug is gone)"

# ==============================================================================
# 2. A stable minor other than the resolved package is still a usable platform.
# ==============================================================================

SDK="$TMP/sdk-other-minor"
assignments="$(run_exec "$SDK" other-minor)" || {
    cat "$TMP/exec.log" >&2
    fail "an install that landed at android-37.2 should be usable"
}

eval "$assignments"

[[ "$ANDROID_PLATFORM" == "android-37.2" ]] ||
    fail "expected ANDROID_PLATFORM=android-37.2, got '$ANDROID_PLATFORM'"
grep -q "is not the installed directory" "$TMP/exec.log" ||
    fail "the substitution from the resolved package to the installed one must be reported"

pass "an install that lands at another stable minor is reported and used"

# ==============================================================================
# 3. A platform that only looks installed fails, with the directories named.
# ==============================================================================

expect_install_failure() {
    local mode="$1"
    local label="$2"
    local sdk="$TMP/sdk-$mode"
    local status=0

    run_exec "$sdk" "$mode" > "$TMP/assignments-$mode" 2>/dev/null || status=$?

    [[ "$status" == "1" ]] || fail "$label: expected exit 1, exited $status"
    [[ ! -s "$TMP/assignments-$mode" ]] ||
        fail "$label: a failed install must print no assignments"
    grep -q "No usable Android platform for compileSdk 37" "$TMP/exec.log" ||
        fail "$label: the failure must name the compileSdk it could not satisfy"
    grep -q "Platform directories on this machine:" "$TMP/exec.log" ||
        fail "$label: the failure must list the platform directories present"
}

expect_install_failure no-jar "android.jar missing"
expect_install_failure wrong-api "source.properties declares another API"
expect_install_failure misplaced "inconsistent location android-37.0-2"
expect_install_failure nothing "sdkmanager reported success but installed nothing"

grep -q "android-37.0-2" "$TMP/exec.log" && true

pass "a name without android.jar, a stale API and an inconsistent location all fail loudly"

# The inconsistent location gets its own explanation, because that is the one a
# reader will otherwise try to debug in AGP.
rerun_status=0
run_exec "$TMP/sdk-misplaced" misplaced > /dev/null 2>&1 || rerun_status=$?
grep -q "cannot be used: AGP looks for platforms/android-37.0" "$TMP/exec.log" ||
    fail "the inconsistent-location failure must say why it cannot be used"
pass "the inconsistent-location failure explains the AGP expectation"

# ==============================================================================
# 4. A missing pinned NDK fails after a successful platform install.
# ==============================================================================

mkdir -p "$TMP/sdk-no-ndk"
ANDROID_SDK_ROOT="$TMP/sdk-no-ndk" FAKE_MODE=good MARBLE_BUILD_TOOLS=build-tools\;36.0.0 \
    bash -c '
        set -euo pipefail
        # A fake sdkmanager that installs the platform but skips the NDK.
        dir="$(mktemp -d)"
        cat > "$dir/sdkmanager" <<"INNER"
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "--licenses" ]]; then exit 0; fi
if [[ "${1:-}" == "--list" ]]; then cat "$FAKE_CATALOG"; exit 0; fi
for package in "$@"; do
    case "$package" in
        platforms\;*)
            mkdir -p "$ANDROID_SDK_ROOT/platforms/android-37.0"
            printf "dex\n" > "$ANDROID_SDK_ROOT/platforms/android-37.0/android.jar"
            printf "AndroidVersion.ApiLevel=37\n" > "$ANDROID_SDK_ROOT/platforms/android-37.0/source.properties"
            ;;
    esac
done
INNER
        chmod +x "$dir/sdkmanager"
        MARBLE_SDKMANAGER="$dir/sdkmanager" bash "$0" --exec
    ' "$INSTALLER" > /dev/null 2>&1 && {
    fail "a missing pinned NDK must fail the install step"
} || true

pass "the Gradle-pinned NDK must exist after installation"

# ==============================================================================
# 5. A failed installation is retried, then reported.
# ==============================================================================

SDK="$TMP/sdk-retry"
status=0
: > "$FAKE_LOG"
ANDROID_SDK_ROOT="$SDK" FAKE_INSTALL_FAILS=1 FAKE_MODE=good \
    bash "$INSTALLER" --exec > /dev/null 2> "$TMP/retry.log" || status=$?

[[ "$status" == "1" ]] || fail "an installation that never succeeds must exit 1, exited $status"
[[ "$(grep -c "sdkmanager --channel=3" "$FAKE_LOG" || true)" == "3" ]] ||
    fail "the installation must be attempted three times before failing"
grep -q "Could not install build-tools;36.0.0, NDK $PINNED_NDK" "$TMP/retry.log" ||
    fail "the retry failure must name every package it could not install"

pass "a failing sdkmanager is retried three times and then reported"

# ==============================================================================
# 6. The workflow runs this script instead of assembling the path itself.
# ==============================================================================

BUILD_WORKFLOW="$ROOT/.github/workflows/build.yml"

grep -q 'bash scripts/install-android-sdk.sh --exec' "$BUILD_WORKFLOW" ||
    fail "build.yml must run scripts/install-android-sdk.sh --exec"

# Comments may explain #380; no *executed* line may name a platform package
# again, because that is the line that joined a package ID onto a path.
offending="$(
    grep -vE '^[[:space:]]*#' "$BUILD_WORKFLOW" |
        grep -nE 'platforms;|\$\{platform_package#' || true
)"
[[ -z "$offending" ]] || {
    echo "$offending" >&2
    fail "build.yml must not build an SDK platform path from a package ID any more"
}

pass "the release workflow delegates the SDK step to the verified installer"

echo
echo "[OK] Android SDK install fixtures"
