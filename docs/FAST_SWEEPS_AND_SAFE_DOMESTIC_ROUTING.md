# Fast sweeps, direct-touch glass and conservative domestic routing

## Review

- The dock had layered glass but its spring chased **every pointer update**, making direct
  manipulation feel heavy. Held positions now render directly in both the layer and backdrop;
  the spring is synchronised while held and used only to settle on release. The capsule's
  ground is less opaque, leaving the existing rim, caustic, refraction and magnification legible.
  This is a glass-material treatment, not a claim of true screen-backdrop refraction.
- Real-delay workers previously used at most four CPUs as their base even on capable devices.
  The base now reaches six, still bounded by the existing memory-aware child-core ceiling
  (maximum eight), thermal budget and candidate count. Delay-only ranking no longer has a
  separate four-worker cap. HTTPS target verification, timeouts, sample counts, cancellation,
  distinct configurations and streamed per-server results are unchanged. This increases
  throughput on capable devices, not a promise of lower network RTT or a measured speedup.
- Notification navigation competed vertically with the routes and telemetry. Routes stay in
  order; page/source/open controls move into two-column footers with independent PendingIntents
  and explicit RTL direction. Telemetry is suppressed only while a picker is displayed.
  Existing ShadeDeck total-row limits and subscription browsing semantics remain authoritative.

## Domestic apps: opt-in, local, reversible

Routing → Which apps use the tunnel offers local suggestions from a conservative exact package
catalogue. A review dialog names the packages, explains the privacy consequence, and requires
confirmation before switching to BYPASS_SELECTED. Existing bypass entries are retained; an
ONLY_SELECTED list cannot be reinterpreted as a bypass list. That replacement is disclosed.
Cancel changes nothing. Entries can be removed using the existing chosen-app controls. Reconnect
is required for VpnService to rebuild its app filter.

No app list is uploaded, no QUERY_ALL_PACKAGES permission is added and there is no background
nationality inference. Android visibility and catalogue coverage mean **not every Iranian app can
be identified**. Package identity is not signature-based trust. Unknown apps stay in the tunnel;
users must review suggestions and can add any visible app manually.

## Geodata correctness rather than broader leaks

Reviewed upstream documentation: https://github.com/Chocolate4U/Iran-v2ray-rules and
https://github.com/Chocolate4U/Iran-sing-box-rules. The existing release bundles already fetch
Chocolate4U's geoip/geosite from one immutable snapshot and verify hashes; the existing multisource
GeoAssetIndex and SingBoxGeoRuleSets extract those tags for both engines. Those integrity paths
are retained, not replaced by unverified downloads or large generated assets.

The local MAXIMUM precision layer no longer emits brand substring rules. `alibaba.com`,
`bale.example`, `notdigikala.com` and `digikala.com.evil.example` must not become direct merely
because a brand occurs in the host. Country suffixes and explicit curated domain/subdomain
boundaries remain. User-authored rules and upstream data can still contain mistakes; zero-error
geolocation is not guaranteed. Enhanced/Maximum now have equal conservative local coverage.

## Validation

Host tests cover worker bounds, exact package suggestions and foreign-domain false positives.
Existing Compose drag tests, ShadeDeck reachability tests, compilation and release lint run in
Source verification. Visual review on physical Android/OEM SystemUI and measured latency/battery
benchmarks remain follow-up device checks, not results asserted by source tests.
