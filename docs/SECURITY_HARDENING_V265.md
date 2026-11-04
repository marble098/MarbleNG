# V265 — Security & lifecycle hardening from the 2026-11-04 audit

MARBLE_LOCALHOST_CLEARTEXT_V265 · MARBLE_WIFI_LOCK_PERMISSION_V265 ·
MARBLE_STORE_RACE_V265 · MARBLE_SERVICE_POOL_LIFECYCLE_V265

Four findings from the static audit of commit 4d87bbd, fixed with regression tests. The bigger
architectural items from the audit (Room migration, encrypted backups, on-device VPN test
matrix, stable/canary toolchain split) are deliberately **not** in this pass — they are stage-2/3
work and need their own review.

## 1. P0 — loopback cleartext for the local core controllers

`network_security_config.xml` had only `<base-config cleartextTrafficPermitted="false" />`. On
Android 16 and older that base config is also applied to `127.0.0.1`, so the sing-box controller
API (`http://127.0.0.1:<port>/...` in `SingBoxProcessSession.kt`) could fail with
`CleartextTrafficNotPermitted` on every API level MarbleNG supports (minSdk 26) — breaking the
real-delay test, the controller readiness check and dependent server tests. Android 17 permits
loopback implicitly. The fix adds a single `domain-config` permitting cleartext for `localhost`,
`127.0.0.1` and `::1` **only**; global cleartext stays denied. Pinned by
`SecurityHardeningV265Test.networkSecurityConfigAllowsCleartextOnlyForLoopback`.

## 2. Wi-Fi lock permission

`MarbleVpnService` takes a `WIFI_MODE_FULL_LOW_LATENCY` WifiLock while a session is live, but
`WAKE_LOCK` was never declared — `acquire()` can throw a permission error (currently swallowed
by `runCatching`). Declared explicitly in `AndroidManifest.xml`; pinned by
`SecurityHardeningV265Test.mergedManifestDeclaresWakeLock`.

## 3. P1 — lost updates in the parallel server scanners

`AppStore.saveServerLocation` and `AppStore.saveIpFamilyScan` are load-all → put-one → save-all,
and the scanners call them from a fixed thread pool: two interleaved saves lose one record
(a scanned server's country silently vanishes and is re-scanned). Both read-modify-writes are
now one critical section on a dedicated lock. Pinned by `AppStoreRaceV265Test` (240 keys from
8 threads; every write must survive).

## 4. VPN service pool lifecycle

`routeProbeRacePool` (lazy) was never shut down in `onDestroy`, so its daemon threads could
outlive the service instance and probe into the next session. `onDestroy` now calls
`shutdownNow()` when the lazy was ever initialized (`::routeProbeRacePool.isInitialized`).

## Not done here (tracked as follow-ups)

- Room migration for high-churn server data (architectural, stage 3).
- Keystore-backed encryption of profiles and AES-GCM-encrypted backups (security stage 2).
- On-device (androidTest) VPN matrix: connect/disconnect ×100, network change mid-session,
  DNS/IPv6 leak checks (quality stage 2).
- verify.yml on push to main + PR-gated bot pushes; stable/canary toolchain split.
