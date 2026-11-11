# SOCKS hardening kill-switch fix, full-code audit and dead-code removal

## 1. The reported failure: "Kill switch is holding traffic. IllegalArgumentException: SOCKS hardening failed"

### Root cause (generator/verifier disagreement inside `XrayConfigHardener`)

`harden()` and its own final `verify()` disagreed about what a valid generated
config looks like:

* `harden()` binds the SOCKS inbound to `0.0.0.0` when the user enables
  **Allow LAN** (`settings.xrayAllowLan`), and emits a **second, HTTP inbound**
  when `settings.xrayHttpInboundPort` is a valid port.
* `verify()` unconditionally demanded **exactly one inbound, listening on
  `127.0.0.1`**.

So with either of those two user-facing settings on, `harden()` threw
`IllegalArgumentException("SOCKS hardening failed")` on the config it had just
built. `XrayManager.start` turned that into a failed start, and
`MarbleVpnService.failBeforeTunnel` — with a TUN already held — armed the kill
switch (`BLOCKED • Kill switch is holding traffic`). Xray never ran; no server
was ever contacted.

### Fixes applied (several layers)

1. **Parity (root cause).** `verify()` now accepts exactly the inbound set
   `harden()` emits for the given settings: the `socks-in` inbound (loopback, or
   wildcard when LAN access is opted into) plus the optional `http-in` inbound.
   The expected listen address/port set is derived from one shared pair of
   helpers (`expectedListen`, `expectedHttpPort`) used by both the generator
   and the verifier, so they cannot drift apart again.
2. **Diagnosability.** The refusal now names the expected and actual inbound
   sets instead of the opaque `SOCKS hardening failed`, and
   `MarbleVpnService.conciseFailure` maps any residual hardening refusal to a
   user-actionable sentence instead of leaking the raw exception.
3. **Second latent config refusal found by the same audit.** The NTP rule
   (`udp/123`) unconditionally targeted the `direct` outbound, which is only
   emitted when routing needs one. With private-bypass off and no direct
   rules, the generated config referenced a non-existent outbound. The rule
   now targets `direct` only when it exists, otherwise the selected proxy.
4. **General guard.** `verify()` now rejects any routing rule that targets a
   missing outbound or references a missing inbound — the whole class of
   "generated config the core would refuse at load" is caught at build time.
5. **Regression tests** in `XrayConfigHardenerTest` pin the matrix: Allow LAN,
   HTTP inbound, both, HTTP port equal to the SOCKS port, out-of-range HTTP
   port, and the no-direct-outbound NTP case.

## 2. Other bugs found in the zero-to-hundred scan and fixed

* `JitterAutoTuner` formatted a loss percentage with the default locale
  (`"%.1f".format(...)`); on Persian/Arabic/German locales the decimal
  separator corrupts the (machine-compared, English) reason string. Now
  `Locale.US`, matching the earlier audit's fixes.
* `SingBoxManager`'s controller thread swallowed `InterruptedException`
  without restoring the interrupt flag. It now re-interrupts.
* `marble-cloud-gate.yml` grepped for `RankTargetScheduler.ordered` in
  `BenchmarkEngine.kt`, a call that no longer exists — the gate would fail
  every cloud-candidate push. The stale grep is removed (see §3).

Everything else the scan surfaced (guarded `first()`/`[0]` sites, the
`DnsWireCodec`/`AddressFamilyPolicy` paths, JNI descriptor ownership in
`marbleng_jni.c`, the SingBox builder's own inbound generation) was verified
to be already guarded and is unchanged.

## 3. Useless code deleted (no effect on the working app)

Production files that **nothing in `app/src/main` referenced** (verified by a
whole-tree reference scan; AndroidManifest entry points excluded):

| Deleted | Why it was dead |
| --- | --- |
| `core/CensorshipAwareDeadlinePolicy.kt` | only a unit test called it |
| `core/DnsHedgePolicy.kt` | only a unit test called it |
| `core/HandoverCoordinator.kt` | only unit tests called it; live handover is `ContinuousRouteOptimizer` |
| `core/IranModeEngine.kt` | referenced nowhere at all, not even tests |
| `core/RankTargetScheduler.kt` | only a unit test called it; also removes the stale cloud-gate grep above |
| `model/ConnectDeck.kt` | retired V233 deck catalogue; Home uses `ConnectControl` since V241/V264 |
| `model/SlideConnectSpec.kt` | retired slide gesture spec; Home has no slide control since V241 |

Their dedicated tests (`HandoverCoordinatorAdaptiveTest`,
`ConnectDeckActionV233Test`, `SlideConnectSpecV234Test`) and the dead test
cases inside `NetworkPolicyTest`/`IranAwarePingTest` were removed with them,
as were the two `system-integrity-check.py` blocks that pinned the retired
specs (419 checks still pass).

`core/DnsBootstrapCodec.kt` was production-dead too, but it is genuine test
infrastructure (a DNS response codec for the fake DoH servers in
`CoreInteropRegressionTest`/`SingBoxNativeIntegrationTest`), so it moved to
the test source set with its package unchanged instead of being deleted.

Also removed: the no-op `SingBoxConfigBuilder.applyInboundSniff` stub
(inbound sniff fields are handled by route `sniff` actions since V111) and
eleven never-called private functions (`XrayManager.validationCached` /
`rememberValidation` / `sha256` plus the never-populated
`validatedConfigHashes` cache, `BenchmarkEngine.materiallyBetter` / `canMux`,
`RouteProbe.httpMeasure` / `standardDeviation`,
`PattRankEngine.nativeScore`, `SingBoxConfigBuilder.hasCertificatePinning`,
`AppRepository.clearBenchmarks`, `HomeScreen.pingStageLabel`), with the
imports they orphaned.

## Verification

* `python3 scripts/system-integrity-check.py` — 419/419 pass.
* `python3 tools/kotlin-structure-check.py`, `tools/compose-scope-check.py` — clean.
* All `scripts/test-*.sh` offline fixtures pass.
* Kotlin unit tests (including the new hardener parity tests) run in the
  `verify` workflow on the pull request.
