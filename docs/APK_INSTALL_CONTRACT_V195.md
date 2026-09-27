# APK install contract — why installs used to fail instantly with "App not installed"

**Marker:** `MARBLE_APK_INSTALL_CONTRACT_V195`
**Symptom:** after downloading a release APK, tapping **Install** in the system install
dialog fails **immediately** (no progress bar) with **"App not installed"**.

## Root cause

The install dialog is only a confirmation step. Behind it the package installer
validates the native payload *before* writing anything to disk, and this APK was
carrying a native payload that violates that contract on two axes.

### 1. The Go cores were not 16 KB page aligned

`libmarbleng.so` and `libhev-socks5-tunnel.so` are NDK-built and already
16 KB-aware (`APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true`,
`-Wl,-z,max-page-size=16384`). `libxray.so` and `libsingbox.so` are **Go
executables** linked with `go build -buildmode=pie` and no linker page-size
flags, so their ELF `PT_LOAD` segments came out 4 KB aligned (`p_align
0x1000`).

Android 15+ devices with 16 KB memory pages — the entire current device
generation; Google made 16 KB support mandatory in **May 2026** — refuse to map
such libraries. The package installer runs the same validation through
`NativeLibraryHelper`:

```
D/NativeLibraryHelper: Library 'libxxx.so' is not page-aligned -
    will not be able to open it directly from apk.
W/NativeHelper: Failure copying native libraries [errorCode=-2]
→ INSTALL_FAILED_INVALID_APK: Failed to extract native libraries, res=-2
→ UI: "App not installed"
```

This is why the failure looked "new": the same APKs installed fine on older
4 KB-page devices, and failed on the phones users actually buy now.

### 2. The installer extraction contract was implicit and could silently flip

The Xray and sing-box cores are not JNI libraries — they are **executables
launched via `ProcessBuilder` from `applicationInfo.nativeLibraryDir`**
(`XrayManager`, `SingBoxManager`, `BugFinder`). They therefore *must* be
extracted at install time (`extractNativeLibs="true"`).

- AGP 8.3+/9.x **defaults `extractNativeLibs` to `"false"`** for `minSdk >= 23`
  (this app: 26) whenever it is not pinned.
- AGP 9 **rejects `android:extractNativeLibs` in the source
  `AndroidManifest.xml`**, so the attribute cannot be pinned there.
- `packaging.jniLibs.useLegacyPackaging = true` keeps the `.so` entries
  compressed and is the sanctioned AGP-9 control for the injection — but the
  resulting merged manifest was never verified. A merged manifest saying
  `extractNativeLibs="false"` together with compressed native entries is the
  canonical instant-install-failure combination (the installer cannot open
  compressed libs in place *and* is not allowed to extract them).

## The fix (belt and braces, both fail-loud)

1. **16 KB alignment everywhere.** Every `go build` in
   `scripts/prepare-native.sh` now passes
   `CGO_LDFLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"`,
   and the final verification stage runs `assert_elf_page_alignment` over
   **all four libraries of all four ABIs**: any `PT_LOAD` below 16384 bytes
   fails the build before an APK can be assembled.
2. **Extraction contract pinned and normalized.** `useLegacyPackaging = true`
   stays the primary control; additionally `marbleNormalizeExtractNativeLibs`
   in `app/build.gradle.kts` rewrites the **merged** manifest after every
   manifest-processing task and before every packaging task so
   `android:extractNativeLibs` can never end up `"false"` in a shipped APK.
   (Normalizing the merged manifest is legitimate — AGP 9 only forbids the
   attribute in the *source* manifest.)
3. **The cores are never stripped.** `**/libxray.so` joins
   `keepDebugSymbols` next to the other cores.
4. **Pinned by the central gate.** `scripts/system-integrity-check.py`
   asserts all of the above, so the contract cannot regress silently.

## Why not just `android:extractNativeLibs="true"` in the manifest?

AGP 9.0+ hard-fails `:packageRelease` when the attribute is present in the
source manifest ("Avoid setting android:extractNativeLibs explicitly … instead
set android.packagingOptions.jniLibs.useLegacyPackaging to true"). The
DSL flag + merged-manifest normalization achieves the same guarantee without
breaking the build.

## How to verify a future change

- `python3 scripts/system-integrity-check.py` — source invariants.
- Release CI builds every ABI; `scripts/prepare-native.sh` now hard-fails on
  the first misaligned library, before Gradle is even invoked.
- On-device: install the release APK on a 16 KB-page device (Pixel 8+ or a
  "16 KB page size" emulator image) — the install must complete and
  `Bug Finder` must report `libxray.so` and `libsingbox.so` present in
  `nativeLibraryDir`.
