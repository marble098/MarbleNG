#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MARBLE_RUNNER_DISK_HEADROOM_V268 fixtures - offline.
#
# scripts/reclaim-runner-disk.sh deletes preinstalled trees on a GitHub runner
# with `rm -rf`. That is the one kind of script that must be proven before it
# runs beside a release build, and a runner is not available to a pull request,
# so these fixtures build a fake ubuntu-latest image tree in a temporary
# directory, point the reclaimer at it with MARBLE_DISK_ROOT, and assert:
#
#   1. the ballast is gone;
#   2. the two Android pins the build compiles with survive - the platform its
#      compileSdk names and the NDK its ndkVersion names, both read from
#      app/build.gradle.kts by the reclaimer itself, so a pin bump cannot make
#      this script delete the SDK the build needs;
#   3. a second run is a no-op, so a smaller image is not an error;
#   4. a filesystem below the floor fails with the floor named, instead of
#      letting stripReleaseDebugSymbols die on "No space left on device";
#   5. every deletion guard refuses: the filesystem root, a top-level path, the
#      workspace, the repository, and the Android SDK components the build
#      needs.
#
# Run:  ./scripts/test-reclaim-runner-disk.sh
# ==============================================================================

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

RECLAIM="$ROOT/scripts/reclaim-runner-disk.sh"

[[ -x "$RECLAIM" || -f "$RECLAIM" ]] || {
    echo "[FAIL] missing scripts/reclaim-runner-disk.sh" >&2
    exit 1
}

TMP="$(mktemp -d)"

cleanup() {
    rm -rf "$TMP"
}

trap cleanup EXIT

fail() {
    echo "[FAIL] $*" >&2
    exit 1
}

pass() {
    echo "[PASS] $*"
}

expect_gone() {
    [[ ! -e "$1" ]] || fail "expected $1 to be removed"
}

expect_kept() {
    [[ -e "$1" ]] || fail "expected $1 to survive the reclaim"
}

# The pins the reclaimer must spare, read from the same file it reads them from.
GRADLE_FILE="$ROOT/app/build.gradle.kts"

COMPILE_SDK="$(
    sed -n 's/^[[:space:]]*compileSdk[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
        "$GRADLE_FILE" | tail -n 1
)"

PINNED_NDK="$(
    sed -n 's/^[[:space:]]*ndkVersion[[:space:]]*=[[:space:]]*"\([0-9][0-9.]*\)".*/\1/p' \
        "$GRADLE_FILE" | tail -n 1
)"

[[ -n "$COMPILE_SDK" ]] || fail "could not read compileSdk from app/build.gradle.kts"
[[ -n "$PINNED_NDK" ]] || fail "could not read ndkVersion from app/build.gradle.kts"

# ==============================================================================
# A fake ubuntu-latest image tree (Ubuntu 26.04, image 20261026.180.1)
# ==============================================================================

IMAGE="$TMP/image"
WORKSPACE="$IMAGE/home/runner/work/MarbleNG/MarbleNG"

make_fake_image() {
    local image="$1"

    rm -rf "$image"

    # Preinstalled ballast the release build cannot reach.
    mkdir -p \
        "$image/usr/share/dotnet/sdk/10.0.403" \
        "$image/usr/share/swift/usr/lib" \
        "$image/usr/lib/swift/linux" \
        "$image/opt/ghc/9.14.1" \
        "$image/usr/local/.ghcup/ghc" \
        "$image/usr/local/share/vcpkg/installed" \
        "$image/home/linuxbrew/.linuxbrew/Cellar" \
        "$image/usr/lib/google-cloud-sdk/lib" \
        "$image/opt/az/bin" \
        "$image/usr/local/share/powershell/Modules/Az" \
        "$image/opt/google/chrome" \
        "$image/usr/lib/chromium" \
        "$image/usr/lib/firefox" \
        "$image/opt/hostedtoolcache/Go/1.26.10/x64" \
        "$image/opt/hostedtoolcache/Python/3.14.9/x64" \
        "$image/opt/hostedtoolcache/PyPy/3.11.17/x64" \
        "$image/opt/hostedtoolcache/Ruby/4.0.9/x64" \
        "$image/opt/hostedtoolcache/node/24.22.1/x64" \
        "$image/opt/hostedtoolcache/Java_17_X64/17.0.21" \
        "$image/opt/hostedtoolcache/Java_25_X64/25.0.5" \
        "$image/opt/hostedtoolcache/CodeQL/2.27.3" \
        "$image/usr/lib/jvm/java-15-openjdk"

    # Android SDK: three NDKs that are not the pinned one, the pinned one, a
    # pile of platforms that are not the compileSdk one, the compileSdk one,
    # and the components that must never be touched.
    mkdir -p \
        "$image/usr/local/lib/android/sdk/ndk/27.3.13750724/toolchains" \
        "$image/usr/local/lib/android/sdk/ndk/29.0.14206865/toolchains" \
        "$image/usr/local/lib/android/sdk/ndk/30.0.16248370/toolchains" \
        "$image/usr/local/lib/android/sdk/ndk/$PINNED_NDK/toolchains" \
        "$image/usr/local/lib/android/sdk/platforms/android-34" \
        "$image/usr/local/lib/android/sdk/platforms/android-36" \
        "$image/usr/local/lib/android/sdk/platforms/android-37.2-beta3" \
        "$image/usr/local/lib/android/sdk/platforms/android-$COMPILE_SDK" \
        "$image/usr/local/lib/android/sdk/build-tools/36.0.0" \
        "$image/usr/local/lib/android/sdk/build-tools/37.0.0" \
        "$image/usr/local/lib/android/sdk/platform-tools" \
        "$image/usr/local/lib/android/sdk/cmdline-tools/latest/bin" \
        "$image/usr/local/lib/android/sdk/licenses" \
        "$image/usr/local/lib/android/sdk/system-images/android-34/google_apis/x86_64"

    # The checkout itself.
    mkdir -p "$WORKSPACE"
    echo "checkout" > "$WORKSPACE/core-lock.json"

    # A few bytes in each tree, so a survivor is visible as a survivor.
    find "$image" -type d -empty -exec sh -c 'echo ballast > "$1/payload"' _ {} \;
}

run_reclaim() {
    MARBLE_DISK_ROOT="$IMAGE" \
    MARBLE_DISK_WORKSPACE="$WORKSPACE" \
        bash "$RECLAIM"
}

# ==============================================================================
# 1. The ballast goes, the pins stay.
# ==============================================================================

make_fake_image "$IMAGE"

echo "== case 1: preinstalled ballast is reclaimed, the two pins are spared =="

run_reclaim > "$TMP/case1.log" 2>&1 || {
    cat "$TMP/case1.log"
    fail "the reclaimer exited non-zero on a full fake image"
}

for gone in \
    usr/share/dotnet \
    usr/share/swift \
    usr/lib/swift \
    opt/ghc \
    usr/local/.ghcup \
    usr/local/share/vcpkg \
    home/linuxbrew \
    usr/lib/google-cloud-sdk \
    opt/az \
    usr/local/share/powershell/Modules \
    opt/google/chrome \
    usr/lib/chromium \
    usr/lib/firefox \
    usr/lib/jvm \
    opt/hostedtoolcache/Go \
    opt/hostedtoolcache/Python \
    opt/hostedtoolcache/PyPy \
    opt/hostedtoolcache/Ruby \
    opt/hostedtoolcache/node \
    opt/hostedtoolcache/Java_25_X64 \
    opt/hostedtoolcache/CodeQL \
    usr/local/lib/android/sdk/ndk/27.3.13750724 \
    usr/local/lib/android/sdk/ndk/29.0.14206865 \
    usr/local/lib/android/sdk/ndk/30.0.16248370 \
    usr/local/lib/android/sdk/platforms/android-34 \
    usr/local/lib/android/sdk/platforms/android-36 \
    usr/local/lib/android/sdk/platforms/android-37.2-beta3 \
    usr/local/lib/android/sdk/system-images
do
    expect_gone "$IMAGE/$gone"
done

for kept in \
    "usr/local/lib/android/sdk/ndk/$PINNED_NDK" \
    "usr/local/lib/android/sdk/platforms/android-$COMPILE_SDK" \
    usr/local/lib/android/sdk/build-tools/36.0.0 \
    usr/local/lib/android/sdk/build-tools/37.0.0 \
    usr/local/lib/android/sdk/platform-tools \
    usr/local/lib/android/sdk/cmdline-tools/latest/bin \
    usr/local/lib/android/sdk/licenses \
    opt/hostedtoolcache/Java_17_X64/17.0.21 \
    home/runner/work/MarbleNG/MarbleNG/core-lock.json
do
    expect_kept "$IMAGE/$kept"
done

grep -q "spared  /usr/local/lib/android/sdk/ndk/$PINNED_NDK (ndkVersion)" "$TMP/case1.log" ||
    fail "the pinned NDK was not reported as spared"

grep -q "spared  /usr/local/lib/android/sdk/platforms/android-$COMPILE_SDK (compileSdk)" "$TMP/case1.log" ||
    fail "the compileSdk platform was not reported as spared"

grep -q "required commands still resolve" "$TMP/case1.log" ||
    fail "the tool verification did not run"

grep -q "Runner disk headroom: " "$TMP/case1.log" ||
    fail "the reclaimer did not state the headroom it left"

pass "ballast reclaimed; ndk/$PINNED_NDK and platforms/android-$COMPILE_SDK spared"

# ==============================================================================
# 2. A second run is a no-op.
# ==============================================================================

echo "== case 2: a second run on the already-cleaned tree is a no-op =="

run_reclaim > "$TMP/case2.log" 2>&1 || {
    cat "$TMP/case2.log"
    fail "a second run must succeed when there is nothing left to reclaim"
}

grep -q "Removed 0 preinstalled tree(s)" "$TMP/case2.log" ||
    fail "a second run should report zero removals"

expect_kept "$IMAGE/usr/local/lib/android/sdk/ndk/$PINNED_NDK"
expect_kept "$IMAGE/usr/local/lib/android/sdk/platforms/android-$COMPILE_SDK"

pass "re-running is safe on a smaller image"

# ==============================================================================
# 3. A filesystem below the floor fails with the floor named.
# ==============================================================================

echo "== case 3: a filesystem below the floor fails loudly, before Gradle runs =="

make_fake_image "$IMAGE"

status=0
MARBLE_DISK_ROOT="$IMAGE" \
MARBLE_DISK_WORKSPACE="$WORKSPACE" \
MARBLE_MIN_FREE_MIB=999999999 \
    bash "$RECLAIM" > "$TMP/case3.log" 2>&1 || status=$?

[[ "$status" == "1" ]] || fail "an unreachable floor must exit 1, exited $status"

grep -q "No space left on device" "$TMP/case3.log" ||
    fail "the floor failure must name the failure it prevents"

grep -q "999999999 MiB" "$TMP/case3.log" ||
    fail "the floor failure must state the floor"

grep -q "Largest directories under" "$TMP/case3.log" ||
    fail "the floor failure must print what is using the filesystem"

pass "a full filesystem is reported before the build starts"

# ==============================================================================
# 4. Every deletion guard refuses.
# ==============================================================================

echo "== case 4: the deletion guards refuse =="

make_fake_image "$IMAGE"

expect_refusal() {
    local ballast="$1"
    local expected="$2"
    local refusal_status=0

    MARBLE_DISK_ROOT="$IMAGE" \
    MARBLE_DISK_WORKSPACE="$WORKSPACE" \
    MARBLE_EXTRA_BALLAST="$ballast" \
        bash "$RECLAIM" > "$TMP/refusal.log" 2>&1 || refusal_status=$?

    [[ "$refusal_status" == "1" ]] ||
        fail "candidate '$ballast' must be refused, exited $refusal_status"

    grep -q "$expected" "$TMP/refusal.log" || {
        cat "$TMP/refusal.log"
        fail "candidate '$ballast' was refused without saying why ('$expected')"
    }
}

expect_refusal "/" "refusing to delete the filesystem root"
expect_refusal "/usr" "refusing a top-level deletion target"
expect_refusal "/home/runner/work/MarbleNG/MarbleNG" "refusing to delete inside the workspace"
expect_refusal "/home/runner/work/MarbleNG/MarbleNG/core-lock.json" "refusing to delete inside the workspace"
expect_refusal "/usr/local/lib/android/sdk/cmdline-tools/latest" "refusing to delete an Android SDK component"
expect_refusal "/usr/local/lib/android/sdk/platform-tools" "refusing to delete an Android SDK component"

pass "root, top-level, workspace and SDK-component candidates are refused"

# The repository guard is the same code, but it can only fire when the cleaned
# tree contains the repository, so it is proven against a copy of the script
# living inside a fake checkout rather than against the real one.
echo "== case 5: the repository guard refuses =="

FAKE_REPO="$TMP/fake-repo"
mkdir -p "$FAKE_REPO/scripts" "$FAKE_REPO/app" "$FAKE_REPO/payload"
cp "$RECLAIM" "$FAKE_REPO/scripts/reclaim-runner-disk.sh"
cp "$GRADLE_FILE" "$FAKE_REPO/app/build.gradle.kts"

repo_status=0
MARBLE_DISK_ROOT="$TMP" \
MARBLE_DISK_WORKSPACE="$WORKSPACE" \
MARBLE_EXTRA_BALLAST="/fake-repo/payload" \
    bash "$FAKE_REPO/scripts/reclaim-runner-disk.sh" > "$TMP/repo.log" 2>&1 || repo_status=$?

[[ "$repo_status" == "1" ]] ||
    fail "a candidate inside the repository must be refused, exited $repo_status"

grep -q "refusing to delete inside the repository" "$TMP/repo.log" || {
    cat "$TMP/repo.log"
    fail "the repository guard did not say why"
}

expect_kept "$FAKE_REPO/payload"

pass "a candidate inside the repository is refused"

echo
echo "[OK] MARBLE_RUNNER_DISK_HEADROOM_V268 fixtures passed"
