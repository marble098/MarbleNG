# MARBLE_ANDROID_SDK_MINOR_PLATFORM_V270 — resolve the platform package the catalog publishes

Release run **#379** failed in `Install Android SDK and NDK`, before native preparation or Gradle.
The workflow read `compileSdk = 37` correctly, but built the package ID by appending that integer:
`sdkmanager ... "platforms;android-37"`. SDK Manager reported `Failed to find package
'platforms;android-37'`; the failure is package-name resolution, not disk capacity or a Gradle
compile error. The previous V269 retries repeated the same nonexistent ID, so retrying could not
repair it.

## Resolution

The release workflow now reads `sdkmanager --list --channel=3` on each attempt and passes the live
catalog to `scripts/resolve-android-platform.py`. For API 37, it prefers the stable
`platforms;android-37.0` package, otherwise the highest numeric stable `37.x` package, and finally
the legacy unversioned package if that is the only stable name. Names containing preview/beta or
extension suffixes are excluded. If no stable package is listed, the resolver fails with the
compileSdk and all platform package IDs it saw; the workflow also prints any platform directories
actually installed on the runner. This replaces the old empty `find`-only diagnostic.

The resolved package is installed alongside the unchanged build-tools and Gradle-pinned NDK. The
workflow exports its selected directory as `ANDROID_PLATFORM`; `app/build.gradle.kts` derives the
API level supplied to AGP from that value, keeping Gradle aligned with the SDK resolution. The disk
reclaimer now preserves the legacy directory and stable numeric minor directories for the pinned
API, while still removing preview/beta and extension directories.

SDK Manager remains in use: the failure was a stale assumption about package naming, not a need to
migrate tools. Offline fixtures cover a catalog where `android-37` is absent but `android-37.0` is
present, stable-minor ordering, preview/extension exclusion, a useful missing-package diagnostic,
and preservation of a preinstalled `android-37.0` by the reclaimer.

## Verification

Run `bash scripts/test-android-sdk-platform-resolver.sh`,
`bash scripts/test-reclaim-runner-disk.sh`, and `python3 scripts/system-integrity-check.py`.
Source verification runs both fixture suites on every pull request.
