# MarbleNG Smart Rank / Adaptive Aegis / DoH closed-pipe rewrite (V90)

This document records the three production fixes shipped under `MARBLE_SMART_RANK_V90` for
Iran's severe-filtering environment. It is the engineering rationale behind the unit tests in
`app/src/test/java/com/marbleng/app/core/`.

## 1. Smart Rank fix — the "14 nodes, 13 missing-address" bug

**Root cause.** Preflight quarantined 13 of 14 nodes with a blanket `missing-address`; the whole
Rank run finished in under 10ms with `selected`/`healthy` always 0 and no real probe executed.
The emitted Xray JSON had lost its server address during a severe-filtration refresh, but the
address still existed in the local cache and the fresh subscription — the validator never looked
at anything except the broken JSON.

**Fix.**
- `ProfileAddressCrossCheck` cross-checks the address against **at least two sources**
  (emitted config JSON + local cache host/port/URI + fresh subscription) and classifies the
  precise reason: `malformed-config`, `stale-subscription`, `address-resolved-but-invalid` or
  `missing-address` (only when truly absent everywhere).
- `ProfilePreflightValidator.partition(profiles, sources)` consumes that cross-check;
  `isMajorityQuarantined(invalid, total)` detects the >50% case.
- `AppRepository.smartRankRun` and `MarbleFreedomSmartRanker.bestProfile` **stop** when more than
  half the pool is quarantined, surface `"پروفایل‌ها به‌روز نیستند، لطفاً Refresh Subscriptions را بزنید"`
  and auto-offer a subscription refresh instead of silently finishing empty.
- `SmartRankGate` debounces the Rank button (default 1000ms cooldown) and guarantees single-flight
  with CAS, so the observed "9 triggers in 7 seconds" can never re-run the whole pool. The global
  `task()` mutex now reports acceptance so the gate is never left stuck.
- `ProfileSecurityAuditor.rankEligibility` / `partitionForRank` remove VLESS without TLS/REALITY
  and VMess without forward secrecy **before** ranking — matching scheme+security explicitly
  because these nodes are usually `WEAK`, not `INSECURE`.
- The single short HTTPS probe is replaced by a weighted `MultiSignalRankScorer` composite
  (TCP handshake success ratio, RTT median/p95, jitter, retransmission, loss, session lifetime).
  `native/rankhelper/main.go` now retries 2-3 attempts per target with exponential backoff and
  emits `handshakeSuccessRatio`/`rttMedianMs`; `PattRankEngine` reads them; the serverless probe
  retries each target on timeout. One HTTPS timeout can no longer fail a node.

**Regression test.** `ProfileAddressCrossCheckTest.thirteenOfFourteenMissingAddressesAreClassifiedPrecisely`
reproduces the exact 14-node / 13-missing-address scenario and asserts the precise reason replaces
the blanket `missing-address`.

## 2. Marble Freedom Aegis adaptive selector

- `AdaptiveAegisScorer` is a continuous, per-network scoring engine: a live loop re-measures the
  active connection (RTT, loss, stress flag) every 30-60s and silently migrates to the best
  alternative (soft session migration) instead of a raw reconnect.
- `NetworkFingerprint.compose` keeps a **separate score table per physical network** using the
  stable network key plus a **hashed** SSID and **hashed** mobile-network code — no plaintext
  identifiers are ever persisted (privacy boundary preserved).
- **Hysteresis:** 90s dwell after every successful selection; only catastrophic degradation
  (>40% loss or a full drop) bypasses it.
- **Inconclusive:** a first test such as `TURBO live-inconclusive-backoff` stays `uncertain`,
  is re-probed in the background, and never penalises the node.
- `ContinuousRouteOptimizer` records measured scores into the scorer and marks switches;
  `HandoverCoordinator.consultAdaptiveMigration` translates a migration decision into a
  make-before-break action (`START_CHALLENGER` → `SWITCH_NEW_FLOWS` → `DRAIN_OLD`).
  `IranModeEngine` owns and surfaces the scorer in `status()` and `activeCountermeasures()`.

## 3. DNS "closed pipe" fix

**Root cause.** DoH to 1.1.1.1 / 8.8.8.8 failed with `io: read/write on closed pipe` for
google.com, ws.chatgpt.com, dns.quad9.net and mtalk.google.com — the HttpsURLConnection was
closed (or a too-short context deadline cancelled it) before the response stream finished, and the
DoH exchange shared the general HTTPS pool.

**Fix.**
- `DohResolverPool` + `HttpUrlConnectionDohTransport` keep a **dedicated** DoH connection pool
  with keep-alive, bounded connect/read timeouts, and **always drain the full response body before
  disconnect**, so the socket returns to the pool intact.
- The pool **races at least four providers** (Cloudflare, Google, Quad9 + an internal/proxied DoH)
  in parallel; the first valid answer wins, and **any** error class (not just timeout) falls back
  automatically. Per-provider failures are reported so `CensorshipAwareDnsResolver` can quarantine
  the right endpoint and keep shutdown-safe `closed-pipe`/`cancelled` events out of outage counts.
- `DnsWireCodec` ships real RFC 8484 POST bodies and parses actual A/AAAA answers, so a resolver
  that answers but cannot resolve is treated as a failure.

## Invariants preserved

`scripts/system-integrity-check.py` (93 checks), the Prism Product UI invariants, routing-source
invariants, Compose-function-type checks and release-publishing safety checks all still pass. No
`GlobalScope` was introduced and all changes are thread-safe (CAS gates, `@Synchronized` entry
points, `ConcurrentHashMap` tables).

## 4. Turbo Rank — all-node parallel real-tunnel rank (MARBLE_TURBO_RANK_V91)

User requirement: Rank must measure **ALL** nodes with real Xray tunnel tests, in parallel, very
fast, with no strict gating, and show every result.

- `native/rankhelper/main.go`: worker cap raised 32 → 128 (default 64). Each worker is an
  in-process `core.New`/`core.Dial` Xray instance, not a CLI child, so one wave dials every
  node of a normal subscription.
- `AppRepository.smartRankRun`: the preflight quarantine is no longer a hard gate — the whole
  enabled pool enters `PattRankEngine` (real `TUNNEL` probes via `marble-rank`) and every node
  publishes a result. Quarantined/deprecated nodes are only pinned to the bottom of the
  survival-first ordering, never selected.
- Rank workers: `maxOf(settings.tcpWorkers, 64).coerceAtMost(128)` instead of `coerceIn(4, 16)`
  (the old 4–16 cap forced several sequential waves).
- Results stream to each Library card through `beginProbeBatch` / `markProbeResult` as they
  complete, and the summary line reports `healthy/total`.
- The stale-subscription *stop* (majority quarantined) is removed from the Library rank path;
  it still exists in the Smart Aegis auto-connect path where no selection is possible without a
  valid pool.
