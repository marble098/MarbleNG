# MARBLE_RUNNER_DISK_HEADROOM_V268 — the release build takes the runner's disk back

Release run **#377** ("Build signed Android APKs", `main`, 2026-11-04, 3m 18s) did not die
compiling, linking, shrinking, signing or packaging anything. The four ABIs built, the routing
assets verified, the signer validated, Gradle configured, and then:

```
> Task :app:stripReleaseDebugSymbols FAILED

* What went wrong:
Execution failed for task ':app:stripReleaseDebugSymbols' (registered by plugin 'com.android.internal.application').
> A failure occurred while executing com.android.build.gradle.internal.tasks.StripDebugSymbolsDelegate
   > There were multiple failures while executing work items
      > A failure occurred while executing com.android.build.gradle.internal.tasks.StripDebugSymbolsRunnable
         > .../merged_native_libs/release/mergeReleaseNativeLibs/out/lib/x86_64/libsingbox.so ->
           .../stripped_native_libs/release/stripReleaseDebugSymbols/out/lib/x86_64/libsingbox.so: No space left on device
      > .../lib/x86_64/libxray.so -> .../lib/x86_64/libxray.so: No space left on device
```

The same workflow, on the same commit (`fbc8a56`), had gone green 17 minutes earlier — run #376,
8m 48s, every step from `Set up job` to `Publish GitHub Release` — and run #377 then died 21
seconds into the Gradle invocation. The run before that, #375, is a different failure with its own
chapter (`docs/LINT_FATAL_LOOPBACK_V267.md`, the missing `includeSubdomains` attribute). A build
that passes and fails on one commit within the quarter hour is sitting on a limit that is not its
own.

## What was actually wrong

`stripReleaseDebugSymbols` is the **second** full copy of the merged native libraries: `jniLibs`
→ `merged_native_libs` → `stripped_native_libs` → five APKs. Around 500 MB of `.so` files cross
that path for four ABIs, so the task is simply where a filesystem that is already at its limit
refuses the next write. Nothing about the copy is wrong, and nothing about the build's own
appetite is unusual.

The runner was full, and the build is not what filled it. `ubuntu-latest` is now the Ubuntu 26.04
image (image version `20261026.180.1`), and its own published manifest lists, on the same
filesystem the job writes to:

- **three NDKs** — `27.3.13750724` (the image's default), `29.0.14206865` and `30.0.16248370`.
  MarbleNG uses exactly one NDK and it is not one of those three: `app/build.gradle.kts` names
  `ndkVersion = "28.2.13676358"`, which the workflow installs itself. Three unpacked NDKs are the
  single largest thing on the image;
- **eighteen Android platforms**, four of them `37.2` pre-release revisions, while the build
  compiles against one;
- eleven .NET SDKs, Homebrew (`/home/linuxbrew`), vcpkg, GHC/GHCup/Stack/Cabal, Swift 6.4,
  Chrome, Chromium, Edge, Firefox, PowerShell's `Az` module, the Google Cloud and Azure CLIs,
  Bazel, Kind, Minikube, MySQL and PostgreSQL;
- four JVMs, three Go toolchains, five CPythons, three PyPys, four Rubies and two Node.js
  releases under `/opt/hostedtoolcache` — while this job installs its own JDK (`setup-java`), its
  own Go (`setup-go`, resolved from the pinned Xray's `go.mod`) and uses the distro `python3`.

An Android release build cannot reach any of it, so the job now takes that space back before the
first toolchain is installed on it.

## What now runs instead

**`Reclaim runner disk headroom`** — a new step between the integrity preflight and `Set up
JDK 17`, running `scripts/reclaim-runner-disk.sh`. It deletes the ballast above, then proves the
filesystem still has headroom. Its rules:

1. Every candidate is an absolute path listed once in that script, removed only if it exists; a
   smaller image is a no-op, not a red build.
2. **The two Android pins the build uses are read from `app/build.gradle.kts`**, never hard-coded:
   the platform `compileSdk` compiles against and the NDK `ndkVersion` names are spared, so a pin
   bump cannot make the reclaimer delete the SDK the build is about to use.
3. Nothing inside the repository, inside the workspace, or inside the Android SDK's
   `cmdline-tools`, `platform-tools`, `build-tools`, `licenses` or `extras` is ever a candidate —
   and a candidate that resolves to one of those is refused before anything is deleted.
4. The twenty-two command-line tools the rest of the job calls are re-verified *after* the
   deletions, so a reclaimed byte can never cost the build a tool.
5. If the filesystem still misses the floor (12 GiB, warning line 20 GiB), the script prints `df`,
   lists the largest directories and fails **there**, in one line — instead of four minutes later
   inside a Gradle work item.

**`Release native scratch before the Gradle phase`** — once `jniLibs` holds the finished libraries
and `app/src/main/assets` the verified routing data, the three pinned core source trees, their
staging copies and the twelve Go build caches behind them (four ABIs × Xray, sing-box and
tun2socks) are dead weight for the one phase that needs the room. `GOCACHE` is per-runner and is
never restored, so dropping it costs nothing across runs; the module cache and `.bootstrap` stay,
because `setup-go`'s post step archives the one and hashes the other's `go.sum`.

**`Install Android SDK and NDK`** now installs the platform `compileSdk` names, read from
`app/build.gradle.kts`. It used to install a hard-coded `platforms;android-36` while `compileSdk`
had already moved to 37 — which only worked because the image happened to ship `android-37`. With
the reclaimer deleting every platform the build does not name, the build has to install its own,
and `ANDROID_NDK_LATEST_HOME` follows the pinned NDK instead of pointing at a deleted one.

**`Diagnose a failed release build`** (`if: failure()`) — run #377's only surviving evidence was
its annotation text; the step log host was unreachable from every diagnostic environment, and the
cause was buried in a work-item line. So on any failure of this job, the free space, `df -h` and
the largest directories under the runner home and the checkout now go into the failed job's
summary, and one annotation says whether the disk was the cause (`< 4 GiB` free) or not.

## Why this recurs, and what now stops it

The failure lives only in the release lane, and it is a property of the *runner image*, which
moves without a commit in this repository — the Ubuntu 26 migration is exactly such a move, and
it is why the same commit went green and red within an hour. Three guards pin the response:

- `scripts/test-reclaim-runner-disk.sh` (offline, in source verification as **`Runner disk
  headroom fixtures`**) builds a fake Ubuntu 26.04 image tree in a temporary directory, runs the
  real reclaimer against it with `MARBLE_DISK_ROOT`, and asserts: the ballast is gone;
  `ndk/<ndkVersion>` and `platforms/android-<compileSdk>` survive, as do `build-tools`,
  `platform-tools`, `cmdline-tools` and `licenses`; a second run reports zero removals; a
  filesystem below the floor exits 1 naming the floor and the failure it prevents; and every
  deletion guard refuses — the filesystem root, a top-level path, the workspace, the repository
  and the SDK components. The pins are read from `app/build.gradle.kts` by both the script and the
  fixture, so the two cannot drift.
- `scripts/system-integrity-check.py` (release preflight and source verification) requires the
  reclaim step to exist and to run **before** `Set up JDK 17` and before `Install Android SDK and
  NDK` — reclaiming after the toolchains are installed would delete the NDK the job just
  downloaded.
- The reclaimer's own floor is the last guard: an image that outgrows the reclaim list fails at
  minute one with the largest directories named, instead of at minute four inside AGP.
