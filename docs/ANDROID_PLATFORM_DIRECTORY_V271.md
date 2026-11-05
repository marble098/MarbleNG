# MARBLE_ANDROID_PLATFORM_DIRECTORY_V271 — ask the filesystem where the platform landed

Release run **#380** (2026-11-05, `main`) failed in `Install Android SDK and NDK`, three steps
before Gradle, with:

```
Resolved compileSdk 37 to platforms;android-37.0 (stable candidates: platforms;android-37.0, ...)
Installing build-tools;36.0.0 ndk;28.2.13676358 platforms;android-37.0
Error: Selected compileSdk package platforms;android-37.0 is missing android.jar after installation.
```

The installation had succeeded. The verification read the wrong path:

```bash
platform_dir="$ANDROID_SDK_ROOT/${platform_package#platforms;}"   # -> $SDK/android-37.0
```

`sdkmanager` writes platforms to `$ANDROID_SDK_ROOT/platforms/android-37.0`. The check stripped
`platforms;` from the package ID and joined the remainder to the SDK root, so it looked one
directory too high and could never find `android.jar` — for `android-37` as much as for
`android-37.0`, on any image, whether or not the download worked. V270 taught the workflow *which
package ID the catalog publishes*; it kept a path that never contained `platforms`.

## Resolution

The SDK step is now one script, `scripts/install-android-sdk.sh`, that every job which compiles the
app runs (`build.yml`, `verify.yml`, `marble-cloud-gate.yml`, `update-toolchain.yml`). It reads the
`compileSdk` and `ndkVersion` pins from `app/build.gradle.kts`, resolves the platform package from
`sdkmanager --list` (V270), installs it with the pinned NDK and build-tools, and then asks the
filesystem where the platform actually landed. A candidate directory is accepted only when:

1. it contains a non-empty `android.jar`;
2. its own `source.properties` declares the API level `compileSdk` names — which also rejects a
   stale or half-written directory left by an interrupted install. That file states the level as a
   **decimal** for a minor-versioned platform (`AndroidVersion.ApiLevel=37.0`, as Google publishes
   it) and as an integer for the legacy name (`=37`); both mean API 37, and `36.1` does not;
3. its name is one Gradle can consume, `android-<api>` or `android-<api>.<minor>`, because
   `ANDROID_PLATFORM` is handed to `app/build.gradle.kts`, which rejects anything else;
4. it is reachable: the exact directory the resolved package ID implies first, then the highest
   installed stable minor of the same API (an install that lands in `android-37.2` is a usable API
   37 platform, and the substitution is reported), then the legacy unversioned name.

A directory holding `android.jar` at an inconsistent location (`android-37.0-2`, which some IDEs
produce) is refused with its reason: AGP only resolves `platforms/android-<api>[.<minor>]`, so
accepting it would move the failure to `Failed to find target with hash string 'android-37'`. The
failure path lists every platform directory present, each one's declared API and whether it has an
`android.jar`, plus the catalog package IDs — the two things #380's one-line error omitted.

The directory that passes is what the job exports: `ANDROID_SDK_PLATFORM` names it, and
`ANDROID_PLATFORM` carries its `android-<api>[.<minor>]` name, the only value `app/build.gradle.kts`
accepts. `ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT` and `ANDROID_NDK_LATEST_HOME` follow the Gradle
pin. The script prints shell assignments on stdout (that is the caller's `eval` contract) and sends
everything else — sdkmanager's own progress included — to stderr.

The rule this restores is the one V270 taught: a path assembled from an identifier is a guess about
where an installer put a file. The catalog is the authority on package IDs; the filesystem is the
authority on paths.

## The second failure, which the first fixtures missed

The first push of this change failed its own verification on the hosted runner, in exactly the step
it was written for:

```
Installed platforms;android-37.0, but no usable Android platform exists for compileSdk 37 on this machine.
```

Nothing was wrong with the install, the path or the decimal — the *parser* was. `source.properties`
for `platforms;android-37.0` declares `AndroidVersion.ApiLevel=37.0`, and the pattern accepted
integers only, so it read nothing, every directory was rejected, and the failure path reported an
empty SDK. The offline fixtures could not have caught it: they wrote `AndroidVersion.ApiLevel=37`,
because that is what the integer-era file looked like. A fixture is worth exactly what its model of
the real artefact is worth, and this one modelled the file wrong.

Both halves are now pinned to the published file: the parser accepts `<api>` or `<api>.<minor>` and
compares the major (`declared_api_matches`), and every fixture — the end-to-end fake `sdkmanager`
and the locator's own cases — writes `AndroidVersion.ApiLevel=37.0`, the decimal, as the real
package does. There is also a case that `36.1` is not API 37.

## Verification

`bash scripts/test-android-sdk-install.sh` drives the installer end to end against a fake
`sdkmanager` that installs exactly like the real one, with no network and no hosted runner:

- the real layout is found and the exported values name `platforms/android-37.0`, never
  `$SDK/android-37.0`;
- an install that lands at another stable minor (`android-37.2`) is used and reported;
- a directory with the right name but no `android.jar`, one whose `source.properties` declares API
  36, and one in an inconsistent location all exit 1 with the directories named and no assignments
  printed;
- sdkmanager's chatter never reaches stdout;
- a missing pinned NDK fails the step after a successful platform install;
- a failing `sdkmanager` is retried three times and then reported.

`bash scripts/install-android-sdk.sh --self-test` covers the locator alone (ten cases, including
the published decimal `ApiLevel`, a decimal that is another API, preview and legacy names). Source verification runs both on every pull request, and
`scripts/system-integrity-check.py` asserts that `build.yml` delegates to the script, that no
workflow builds a platform path from a package ID, and that the fixtures and this chapter exist.
