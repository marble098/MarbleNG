# APK install gate — proving "installable over the previous release" on the artifact

**Marker:** `MARBLE_RELEASE_INSTALL_GATE_V217`
**Symptom being prevented:** a release that builds green, uploads green and publishes
green — and then refuses to install on every phone that already runs the previous
release. The installer answers in one line — **"App not installed"** (some OEM skins
word it **"App not updated"**) — with no diagnostics on either side.

## What Android actually checks at update time

When the package installer is asked to *replace* an existing app, exactly four
properties decide success before anything else:

1. **The package name** must be identical (`com.marbleng.app`).
2. **The versionCode must be strictly newer** than the installed copy. This build's
   versionCode is a UTC timestamp, so every release is newer than the last by
   construction — *unless* the value silently fails to reach Gradle. The old
   `app/build.gradle.kts` fallback (`?: 10000`) would have packaged a versionCode
   ten orders below any timestamp-coded release: a silent downgrade no phone would
   ever accept.
3. **The signing certificate must be byte-identical** to the one that signed the
   installed copy. `apksigner verify` proves a signature is *well-formed*; it says
   nothing about *whose* key produced it. If the `ANDROID_KEYSTORE_B64` secret is
   ever regenerated, every subsequent APK is signed by a stranger as far as every
   installed copy is concerned, and every update fails forever.
4. **The native payload must satisfy the extraction contract** pinned by
   `docs/APK_INSTALL_CONTRACT_V195.md`: the Go cores are executables launched from
   `applicationInfo.nativeLibraryDir`, so `android:extractNativeLibs` must be true
   in the *packaged* manifest while the `lib/*` entries stay DEFLATED
   (`useLegacyPackaging = true`). AGP injects this; the merged-manifest
   normalization guards it — but until V217 nothing looked at the finished APK to
   confirm either one actually happened.

Before V217 the pipeline verified none of these on the artifact. The pre-release
toolchain (AGP `9.5.0-alpha0x`, Gradle milestone) makes that exposure real: a
packaging-behavior change in an alpha can flip any of them between two builds of
the same tree.

## The fix

### 1. `build.yml` — "Verify the APK install contract"

Runs after `Collect and verify APKs`, before any artifact upload. For **every**
APK in `dist/`, using the build-tools already installed by the job:

- `aapt2 dump badging` — the packaged `package` name, `versionCode` and
  `versionName` must equal exactly what this build computed; the packaged
  `native-code` must equal the split's own ABI (all four for `universal`).
- `aapt2 dump xmltree --file AndroidManifest.xml` — `android:extractNativeLibs`
  must not be false. (Absent is acceptable: the platform default is true, which
  V195 also allows.)
- `unzip -v` — every `lib/*` entry must be DEFLATED. A `Stored` entry means
  `useLegacyPackaging` silently stopped applying — the exact class of quiet
  packaging flip this gate exists to catch.
- `apksigner verify --print-certs` — all APKs of one release must share one
  certificate.

Then, against the **newest published release** (what phones actually carry), if
one exists:

- its `versionCode` is read from its own APK and this build's must be **strictly
  newer**;
- its signer certificate must equal this build's — **signing continuity**. This is
  the guard that turns "someone regenerated the keystore secret" from a mystery
  on users' phones into a red build with the remedy spelled out.

An intentional rotation has an escape hatch: commit a `release-signing.lock`
containing `{"signingCertSha256": "<64 hex chars>"}` and the gate compares against
that pin instead of the published baseline. Users must still uninstall/reinstall
once — Android offers no way around that — but the pipeline stops lying about it.

The verified certificate digest is exported as `RELEASE_CERT_SHA256` and printed
in the release notes ("Build identity"), so the signing identity of any release
can be compared with any other without downloading a single asset.

### 2. `app/build.gradle.kts` — version properties fail loudly

With signing configured (the CI release context) a missing or non-numeric
`VERSION_CODE`, or a missing `VERSION_NAME`, now fails the build at configuration
time instead of silently packaging the `10000` / `1.0.2` fallbacks. Local trees
without `signing.properties` keep the developer fallbacks.

### 3. `scripts/system-integrity-check.py` pins the gate

The central gate asserts the workflow step, its hard-failure messages and the
`release-signing.lock` handling exist, so the install gate cannot be quietly
removed.

## How to verify a future change

- `python3 scripts/system-integrity-check.py` — the source invariants.
- Every release build runs the gate on all five APKs; a red
  `Verify the APK install contract` step names the exact property that would have
  shipped broken.
- On-device: install the new release APK over the previous published one — the
  install must complete, and Settings › Apps must show the new version.
