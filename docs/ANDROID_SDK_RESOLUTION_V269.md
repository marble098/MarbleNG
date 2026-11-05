# MARBLE_ANDROID_SDK_RESOLUTION_V269 — resolve the pinned SDK, not the runner's luck

Release run **#378** failed before native preparation or Gradle: the Android SDK setup step asked
SDK Manager for `platforms;android-37`, and the repository replied `Failed to find package`. The
same release lane had previously succeeded, so this is a brittle boundary between a moving hosted
image and Google's changing SDK package catalog—not an application or signing failure. The step
also hard-coded NDK `28.2.13676358` even though `app/build.gradle.kts` is the source of truth.

## What changed

The release installer now reads both `compileSdk` and `ndkVersion` from `app/build.gradle.kts`.
When the exact platform is already present, it reuses it only if `android.jar` exists. Otherwise it
asks SDK Manager to search through channel 3 as well as stable packages; this accommodates a
platform that is still preview-channel-only or in transition without changing the app's API pin.
Package resolution gets up to three attempts, clearing SDK Manager's stale metadata cache and
backing off between attempts. The build-tools and NDK package IDs remain exact pins. After SDK
Manager returns, the step verifies the compile platform's `android.jar` before proceeding and
prints the runner's installed platform directories if all attempts fail.

The NDK environment variables are now derived from the same Gradle pin, so a future NDK bump cannot
silently leave the release environment pointing at the old hard-coded version. The expected
`yes`-pipe SIGPIPE from SDK license acceptance is suppressed, removing the misleading `Broken
pipe` warning from the Actions log.

The release integrity preflight asserts the dynamic pins, preview-inclusive SDK resolution, retry
and cache-refresh path, and platform validation. This keeps the failure at the SDK boundary with a
useful diagnostic instead of allowing SDK package churn to produce a later, opaque Gradle failure.

## Verification

Run `python3 scripts/system-integrity-check.py` locally or through the `Marble system integrity
preflight` step. The source verification workflow runs that check for pull requests.
