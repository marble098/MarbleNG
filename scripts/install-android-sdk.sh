#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# MARBLE_ANDROID_PLATFORM_DIRECTORY_V271 - install the pinned Android SDK
# packages, then find the platform that actually landed on disk.
#
# Release run #380 (2026-11-05, main) failed in "Install Android SDK and NDK",
# two minutes in, three steps before Gradle:
#
#   Resolved compileSdk 37 to platforms;android-37.0 (stable candidates: ...)
#   Installing build-tools;36.0.0 ndk;28.2.13676358 platforms;android-37.0
#   Error: Selected compileSdk package platforms;android-37.0 is missing
#   android.jar after installation.
#
# The installation had succeeded. The verification read the wrong path:
#
#   platform_dir="$ANDROID_SDK_ROOT/${platform_package#platforms;}"
#
# strips the `platforms;` prefix and joins the remainder straight onto the SDK
# root, so it looked for `$ANDROID_SDK_ROOT/android-37.0`. sdkmanager installs
# platforms under `$ANDROID_SDK_ROOT/platforms/android-37.0`, so `android.jar`
# could never be found - for android-37 as much as for android-37.0, on any
# image, whether or not the download worked. V270 taught the workflow which
# package ID the catalog publishes; it kept a path that never contained
# `platforms`.
#
# The lesson is not "add /platforms". A path assembled from a package ID is a
# guess about where an installer put a file, and this repository has already
# been beaten once by that shape of guess (V270: the package ID itself was
# assumed). So the check now asks the filesystem:
#
#   1. a candidate directory must contain a non-empty `android.jar`;
#   2. that directory's own `source.properties` must declare the API level
#      `compileSdk` names (this also rejects a half-written or stale
#      `android-37.0` left by an interrupted install);
#   3. only names Gradle can consume are considered - `android-<api>` or
#      `android-<api>.<minor>` - because ANDROID_PLATFORM is handed to
#      app/build.gradle.kts, which rejects anything else;
#   4. the exact directory the resolved package ID implies wins, then the
#      highest installed stable minor of the same API (an install that lands
#      in `android-37.2` is a usable API 37 platform), then the legacy
#      unversioned name.
#
# A directory in an inconsistent location (`android-37.0-2`, as some IDEs
# produce) is not silently accepted: AGP looks for `platforms/android-37.0` and
# would fail later with "Failed to find target with hash string". It is named in
# the failure instead.
#
# Modes:
#
#   --exec       resolve, install and verify; print shell assignments on stdout
#                (the only thing on stdout - sdkmanager is redirected to stderr,
#                because the caller evals this). This is what .github/workflows/
#                build.yml runs.
#
#   --locate     verify only (no sdkmanager): print the located platform
#                directory and its API evidence. Used by the offline fixtures.
#
#   --self-test  prove the locator against synthetic SDK trees, in place.
#
# Environment:
#
#   ANDROID_SDK_ROOT / ANDROID_HOME   the SDK root              (required)
#   MARBLE_SDKMANAGER                 sdkmanager command       (default: sdkmanager)
#   MARBLE_GRADLE_FILE                file with the pins        (default: app/build.gradle.kts)
#   MARBLE_BUILD_TOOLS                build-tools package      (default: build-tools;36.0.0)
#   MARBLE_SDK_RETRY_SLEEP            seconds per retry unit   (default: 10)
#   MARBLE_PLATFORM_PACKAGE           package ID for --locate   (default: none)
#
# ==============================================================================

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SDKMANAGER="${MARBLE_SDKMANAGER:-sdkmanager}"
GRADLE_FILE="${MARBLE_GRADLE_FILE:-$ROOT/app/build.gradle.kts}"
BUILD_TOOLS="${MARBLE_BUILD_TOOLS:-build-tools;36.0.0}"
RETRY_SLEEP="${MARBLE_SDK_RETRY_SLEEP:-10}"
RESOLVER="$ROOT/scripts/resolve-android-platform.py"

log() { printf '  %s\n' "$*" >&2; }
ok() { printf '[OK] %s\n' "$*" >&2; }
warn() { printf '[WARN] %s\n' "$*" >&2; }

die() {
    printf '[FAIL] %s\n' "$*" >&2
    exit 1
}

# Multi-line failure text still has to arrive as one annotation.
annotate_error() {
    local title="$1"
    local body="$2"

    body="${body//%/%25}"
    body="${body//$'\r'/ }"
    body="${body//$'\n'/%0A}"

    printf '::error title=%s::%s\n' "$title" "$body" >&2
}

# ==============================================================================
# The SDK root
# ==============================================================================

sdk_root() {
    local root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

    [[ -n "$root" ]] || {
        die "Neither ANDROID_SDK_ROOT nor ANDROID_HOME is set; the Android SDK location is unknown."
    }

    [[ -d "$root" ]] || {
        die "The Android SDK root does not exist: $root"
    }

    printf '%s\n' "$root"
}

# ==============================================================================
# What a platform directory says about itself
# ==============================================================================

# The API level a platform directory declares in its own source.properties.
# Empty (and non-zero) when the file is absent, unreadable or says nothing.
declared_api() {
    local dir="$1"
    local file="$dir/source.properties"
    local api

    [[ -s "$file" ]] || return 1

    api="$(
        tr -d '\r' < "$file" |
            sed -n 's/^AndroidVersion\.ApiLevel[[:space:]]*=[[:space:]]*\([0-9][0-9]*\)[[:space:]]*$/\1/p' |
            tail -n 1
    )"

    [[ -n "$api" ]] || return 1

    printf '%s\n' "$api"
}

# The numeric minor in `android-<api>.<minor>`, 1000 for the legacy unversioned
# name (last resort: a versioned stable package is published for every API that
# has one), or nothing when the name is not this API's platform at all.
name_rank() {
    local name="$1"
    local api="$2"

    if [[ "$name" =~ ^android-${api}\.([0-9]+)$ ]]; then
        printf '%s\n' "${BASH_REMATCH[1]}"
        return 0
    fi

    if [[ "$name" == "android-${api}" ]]; then
        printf '1000\n'
        return 0
    fi

    return 1
}

# locate_platform <api> <package-id>  ->  prints the platform directory
#
# Success means: a directory named the way Gradle needs it, holding a non-empty
# android.jar, whose source.properties declares this API. Anything less is a
# failure with the directories that were rejected and why.
locate_platform() {
    local api="$1"
    local package="$2"
    local platforms expected dir name declared rank chosen

    platforms="$(sdk_root)/platforms"

    [[ -d "$platforms" ]] || return 1

    expected="$platforms/${package#platforms;}"

    if [[ -n "$package" && -d "$expected" && -s "$expected/android.jar" ]] &&
        [[ "$(declared_api "$expected" || true)" == "$api" ]]; then
        printf '%s\n' "$expected"
        return 0
    fi

    shopt -s nullglob
    local -a candidates=()

    for dir in "$platforms"/*; do
        [[ -d "$dir" ]] || continue
        [[ -s "$dir/android.jar" ]] || continue

        name="$(basename -- "$dir")"

        # Gradle only accepts android-<api>[.<minor>] on ANDROID_PLATFORM; a
        # directory it cannot name is not a candidate, however full it is.
        rank="$(name_rank "$name" "$api")" || continue

        declared="$(declared_api "$dir" || true)"

        # Accept "37" and "37.0" from source.properties: the minor is carried by
        # the directory name, the file states the API level.
        [[ "$declared" == "$api" || "$declared" == "$api".* ]] || continue

        candidates+=( "${rank}|${dir}" )
    done

    shopt -u nullglob

    (( ${#candidates[@]} > 0 )) || return 1

    # Highest stable minor first, then lexical (so android-37.0 beats a
    # hypothetical longer alias of the same minor).
    chosen="$(printf '%s\n' "${candidates[@]}" | sort -t'|' -k1,1nr -k2,2 | head -n 1)"
    printf '%s\n' "${chosen#*|}"
    return 0
}

# Every platform directory the SDK has, with what it says about itself. The
# failure path prints this, because "missing android.jar" without the directory
# listing is what made #380 unreadable.
report_platform_directories() {
    local platforms="$1"
    local dir name declared

    echo "Platform directories on this machine:" >&2

    if [[ ! -d "$platforms" ]]; then
        echo "  (no $platforms directory)" >&2
        return 0
    fi

    shopt -s nullglob
    for dir in "$platforms"/*; do
        [[ -d "$dir" ]] || continue
        name="$(basename -- "$dir")"
        declared="$(declared_api "$dir" || true)"
        printf '  %-28s declares API %-6s android.jar %s\n' \
            "$name" \
            "${declared:-?}" \
            "$([[ -s "$dir/android.jar" ]] && echo present || echo MISSING)" >&2
    done
    shopt -u nullglob
}

missing_platform_failure() {
    local api="$1"
    local package="$2"
    local platforms="$3"
    local sdk_list_file="$4"
    local message

    annotate_error "MarbleNG-android-platform" "$(
        printf 'Installed %s, but no usable Android platform exists for compileSdk %s on this machine.\n' "$package" "$api"
        printf 'A usable platform is a directory named android-%s[.<minor>] that contains android.jar and whose source.properties declares API %s.\n' "$api" "$api"
    )"

    echo "SDK Manager catalog platform packages (last listing):" >&2
    if [[ -s "$sdk_list_file" ]]; then
        grep -E '^[[:space:]]*platforms;android-[^[:space:]|]+[[:space:]]*\|' "$sdk_list_file" |
            sed 's/^[[:space:]]*/  /' >&2 || echo "  (none listed)" >&2
    else
        echo "  (sdkmanager --list produced no catalog output)" >&2
    fi

    report_platform_directories "$platforms"

    echo >&2
    echo "A directory holding android.jar at an unexpected path (for example" >&2
    echo "android-${api}.0-2) cannot be used: AGP looks for platforms/android-${api}.0." >&2
    echo "Remove it and reinstall the package, or rename it to its package name." >&2

    message="No usable Android platform for compileSdk ${api} after installing ${package}."
    die "$message"
}

# ==============================================================================
# The two pins, read from the file that owns them
# ==============================================================================

read_pins() {
    [[ -s "$GRADLE_FILE" ]] || die "Cannot read the Android pins: $GRADLE_FILE is missing"

    COMPILE_SDK="$(
        sed -n 's/^[[:space:]]*val[[:space:]]*compileSdkApi[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
            "$GRADLE_FILE" | tail -n 1
    )"
    PINNED_NDK="$(
        sed -n 's/^[[:space:]]*ndkVersion[[:space:]]*=[[:space:]]*"\([0-9][0-9.]*\)".*/\1/p' \
            "$GRADLE_FILE" | tail -n 1
    )"

    [[ "$COMPILE_SDK" =~ ^[0-9]+$ ]] ||
        die "Could not read compileSdk from $GRADLE_FILE"
    [[ "$PINNED_NDK" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] ||
        die "Could not read ndkVersion from $GRADLE_FILE"
}

# ==============================================================================
# --exec: resolve the package, install it, then find it
# ==============================================================================

exec_install() {
    local sdk platforms sdk_list_file resolver_log
    local platform_package="" platform_dir="" installed=false
    local attempt

    sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"

    [[ -n "$sdk" ]] || {
        die "Neither ANDROID_SDK_ROOT nor ANDROID_HOME is set; the Android SDK location is unknown."
    }

    # A fresh SDK root is not a failure: sdkmanager creates every directory it
    # needs below the root it is given, and `android-actions/setup-android` has
    # already created it on a hosted runner.
    if [[ ! -d "$sdk" ]]; then
        warn "The Android SDK root does not exist yet; creating $sdk"
        mkdir -p "$sdk"
    fi

    sdk="$(sdk_root)"
    platforms="$sdk/platforms"

    read_pins
    ok "Reading pins from $GRADLE_FILE: compileSdk $COMPILE_SDK, NDK $PINNED_NDK"

    sdk_list_file="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/android-sdk-list.txt"
    resolver_log="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/android-platform-resolver.log"

    # sdkmanager closes its input as soon as licenses are accepted. Keep that
    # expected SIGPIPE from `yes` out of the log.
    { yes 2>/dev/null | "$SDKMANAGER" --licenses >/dev/null; } || true

    # V270: the catalog is the authority on package IDs; V271: the filesystem is
    # the authority on paths. Both are asked on every attempt.
    for attempt in 1 2 3; do
        if ! "$SDKMANAGER" --list --channel=3 > "$sdk_list_file" 2>&1; then
            warn "SDK Manager could not list packages (attempt ${attempt}/3):"
            cat "$sdk_list_file" >&2
        elif platform_package="$(
            python3 "$RESOLVER" "$COMPILE_SDK" < "$sdk_list_file" 2> "$resolver_log"
        )"; then
            local -a packages=( "$BUILD_TOOLS" "ndk;${PINNED_NDK}" "$platform_package" )

            log "$(cat "$resolver_log")"
            log "Installing ${packages[*]}"

            # sdkmanager writes progress to stdout; stdout belongs to the
            # assignments this script prints, so everything it says goes to
            # stderr.
            if "$SDKMANAGER" --channel=3 "${packages[@]}" >&2; then
                installed=true
                break
            fi

            warn "SDK package installation failed (attempt ${attempt}/3)."
        else
            warn "$(cat "$resolver_log")"
        fi

        if (( attempt < 3 )); then
            warn "Refreshing SDK Manager metadata before retry ${attempt}/3."
            rm -rf "$HOME/.android/cache"
            sleep $(( attempt * RETRY_SLEEP ))
        fi
    done

    if [[ "$installed" != true ]]; then
        annotate_error "MarbleNG-android-sdk-install" "$(
            printf 'Could not install %s, NDK %s and the stable compileSdk %s platform after three attempts.\n' \
                "$BUILD_TOOLS" "$PINNED_NDK" "$COMPILE_SDK"
            printf 'The SDK Manager catalog packages and the platform directories present are listed in the step log.\n'
        )"
        report_platform_directories "$platforms"
        die "Android SDK package installation did not succeed."
    fi

    # The check #380 should have made: ask the filesystem, then let the answer
    # drive ANDROID_PLATFORM.
    if ! platform_dir="$(locate_platform "$COMPILE_SDK" "$platform_package")"; then
        missing_platform_failure "$COMPILE_SDK" "$platform_package" "$platforms" "$sdk_list_file"
    fi

    ok "compileSdk $COMPILE_SDK platform: $platform_dir (declares API $(declared_api "$platform_dir"))"

    if [[ "$(basename -- "$platform_dir")" != "${platform_package#platforms;}" ]]; then
        warn "The resolved package ${platform_package} is not the installed directory"
        warn "$(basename -- "$platform_dir"); using the installed platform instead."
    fi

    [[ -d "$sdk/ndk/$PINNED_NDK" ]] || {
        report_platform_directories "$platforms"
        die "The Gradle-pinned NDK $PINNED_NDK is not installed at $sdk/ndk/$PINNED_NDK after installation."
    }

    # stdout is the contract: shell assignments the caller evals. Nothing else
    # may be printed here.
    printf 'ANDROID_PLATFORM=%q\n' "$(basename -- "$platform_dir")"
    printf 'ANDROID_SDK_PLATFORM=%q\n' "$platform_dir"

    # The image may point these at an NDK the reclaimer deleted; always redirect
    # consumers to the exact Gradle-pinned version installed above.
    printf 'ANDROID_NDK_HOME=%q\n' "$sdk/ndk/$PINNED_NDK"
    printf 'ANDROID_NDK_ROOT=%q\n' "$sdk/ndk/$PINNED_NDK"
    printf 'ANDROID_NDK_LATEST_HOME=%q\n' "$sdk/ndk/$PINNED_NDK"
}

# ==============================================================================
# --locate: no sdkmanager, just the filesystem question
# ==============================================================================

locate_only() {
    local package="${MARBLE_PLATFORM_PACKAGE:-}"
    local platform_dir

    read_pins

    platform_dir="$(locate_platform "$COMPILE_SDK" "$package")" || {
        report_platform_directories "$(sdk_root)/platforms"
        die "No usable Android platform for compileSdk $COMPILE_SDK."
    }

    printf 'ANDROID_PLATFORM=%q\n' "$(basename -- "$platform_dir")"
    printf 'ANDROID_SDK_PLATFORM=%q\n' "$platform_dir"
}

# ==============================================================================
# --self-test: the locator, against trees built here
# ==============================================================================

self_test() {
    local work platform_root failures=0

    work="$(mktemp -d)"
    # shellcheck disable=SC2064
    trap "rm -rf '$work'" EXIT

    platform_root="$work/sdk/platforms"

    # A platform directory that claims an API in its own source.properties.
    make_platform() {
        local dir="$1"
        local api="$2"
        local jar="${3:-yes}"

        mkdir -p "$dir"
        if [[ "$jar" == yes ]]; then
            printf 'dex\n' > "$dir/android.jar"
        fi
        printf 'AndroidVersion.ApiLevel=%s\nPkg.Desc=Android SDK Platform\n' "$api" \
            > "$dir/source.properties"
    }

    expect_selected() {
        local expected="$1"
        local got="$2"

        if [[ "$got" == "$expected" ]]; then
            ok "located $(basename -- "$expected")"
        else
            printf '[FAIL] expected %s, got %s\n' "$expected" "${got:-<nothing>}" >&2
            failures=$(( failures + 1 ))
        fi
    }

    expect_none() {
        local got="$1"

        if [[ -z "$got" ]]; then
            ok "refused to invent a platform"
        else
            printf '[FAIL] expected no platform, got %s\n' "$got" >&2
            failures=$(( failures + 1 ))
        fi
    }

    # 1. The package's own directory, exactly where sdkmanager puts it.
    rm -rf "$work/sdk"
    make_platform "$platform_root/android-37.0" 37
    ANDROID_SDK_ROOT="$work/sdk"
    expect_selected "$platform_root/android-37.0" \
        "$(locate_platform 37 "platforms;android-37.0" || true)"

    # 2. The bug itself: the SDK root joined to the package name is not a path.
    #    #380 looked here and reported "missing android.jar" for a good install.
    if [[ -e "$work/sdk/android-37.0" ]]; then
        printf '[FAIL] the fixture leaked a top-level android-37.0\n' >&2
        failures=$(( failures + 1 ))
    fi

    # 3. The resolved package directory is absent, but another stable minor of
    #    the same API is installed and declares it: usable, and preferred by
    #    the highest minor.
    rm -rf "$work/sdk"
    make_platform "$platform_root/android-37.1" 37
    make_platform "$platform_root/android-37.2" 37
    ANDROID_SDK_ROOT="$work/sdk"
    expect_selected "$platform_root/android-37.2" \
        "$(locate_platform 37 "platforms;android-37.0" || true)"

    # 4. A directory that carries the right name but declares another API is a
    #    stale or half-written install, not a platform.
    rm -rf "$work/sdk"
    make_platform "$platform_root/android-37.0" 36
    ANDROID_SDK_ROOT="$work/sdk"
    expect_none "$(locate_platform 37 "platforms;android-37.0" || true)"

    # 5. No android.jar is no platform, whatever source.properties claims.
    rm -rf "$work/sdk"
    make_platform "$platform_root/android-37.0" 37 no
    ANDROID_SDK_ROOT="$work/sdk"
    expect_none "$(locate_platform 37 "platforms;android-37.0" || true)"

    # 6. An inconsistent location (android-37.0-2) is named, never silently used:
    #    AGP only resolves platforms/android-<api>[.<minor>].
    rm -rf "$work/sdk"
    make_platform "$platform_root/android-37.0-2" 37
    ANDROID_SDK_ROOT="$work/sdk"
    expect_none "$(locate_platform 37 "platforms;android-37.0" || true)"

    # 7. Preview directories are not stable platforms.
    rm -rf "$work/sdk"
    make_platform "$platform_root/android-37.2-beta3" 37
    ANDROID_SDK_ROOT="$work/sdk"
    expect_none "$(locate_platform 37 "platforms;android-37.0" || true)"

    # 8. The legacy unversioned name is still a platform, ranked after a minor.
    rm -rf "$work/sdk"
    make_platform "$platform_root/android-37" 37
    ANDROID_SDK_ROOT="$work/sdk"
    expect_selected "$platform_root/android-37" \
        "$(locate_platform 37 "platforms;android-37" || true)"

    rm -rf "$work/sdk"
    make_platform "$platform_root/android-37" 37
    make_platform "$platform_root/android-37.0" 37
    ANDROID_SDK_ROOT="$work/sdk"
    expect_selected "$platform_root/android-37.0" \
        "$(locate_platform 37 "platforms;android-37.0" || true)"

    # 9. An empty SDK has no platform and says so rather than guessing.
    rm -rf "$work/sdk"
    mkdir -p "$work/sdk/platforms"
    ANDROID_SDK_ROOT="$work/sdk"
    expect_none "$(locate_platform 37 "platforms;android-37.0" || true)"

    if (( failures > 0 )); then
        printf '[FAIL] %d locator case(s) failed\n' "$failures" >&2
        exit 1
    fi

    ok "platform locator: 9 cases, reads source.properties, never assembles a path from a package ID"
}

# ==============================================================================
# Entry
# ==============================================================================

case "${1:-}" in
    --exec)
        exec_install
        ;;
    --locate)
        locate_only
        ;;
    --self-test)
        self_test
        ;;
    *)
        cat >&2 <<'USAGE'
Usage: scripts/install-android-sdk.sh --exec | --locate | --self-test

  --exec       resolve the stable SDK platform, install it with the NDK and
               build-tools, verify the install on disk, and print shell
               assignments (ANDROID_PLATFORM, ANDROID_SDK_PLATFORM,
               ANDROID_NDK_HOME, ANDROID_NDK_ROOT, ANDROID_NDK_LATEST_HOME)
               for the caller to eval.
  --locate     verify an already-installed SDK without calling sdkmanager.
  --self-test  prove the platform locator against synthetic SDK trees.
USAGE
        exit 2
        ;;
esac
