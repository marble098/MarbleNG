# Censorship Rank & Resolver Audit (Iran)

Root-cause audit for the 3.3.6 → 4.0.0 ranking, resolver, quarantine and diagnostics changes
described in the PR. The app runs inside Iran under severe filtering, throttling, TLS interference,
DNS tampering and unstable reachability, so decisions are optimised for real survivability under
blocking rather than clean-network latency benchmarks.

## Root-cause map

### Legacy unresolved issues (present before 4.0.0, still need fixing)

| Area | Symptom in logs | Root cause |
|------|-----------------|------------|
| Smart Rank | Probe-first false negatives (`requested=15, healthy=3, failed=12`) while the same nodes still connect | Ranking hard-fails a node whose short HTTPS generate204 probe times out. In Iran a probe timeout is not proof of death. |
| Broken profiles | "Turkey 4-All" fails config-load / xray-start validation (VLESS/TLS invalid config) | No structural preflight; an invalid profile joins the benchmark pool and burns a slot / poisons selection. |
| Resolver stability | Large retained resolver error counts + DoH deadline storms | Resolver pool keeps retrying endpoints that decisively fail (expired cert, handshake/EOF/deadline storms) instead of quarantining them; no session stickiness. |
| Diagnostics consistency | Summary reports `0 DNS EOF` while raw lines show DoH EOF | Summary used a few narrow hard-coded substrings; raw "unexpected EOF for DNS-over-HTTPS" lines fell through, so summary and raw disagreed. |
| Cancellation | "read/write on closed pipe" bursts around reconnect / teardown | Cancellations and closed-pipe events were not separated from real transport failures and could be counted as resolver outages. |

### Partial improvements introduced in 4.0.0 (preserved, do NOT regress)

- **Bug Finder is passive by default** — kept passive.
- **Startup stall is not reproduced** — kept.
- **Local SOCKS binding is checked safely** — `waitSocksPort` proves listener readiness with zero
  SOCKS traffic (`listener-bound-no-socks-handshake`), avoiding fake SOCKS EOF failures — kept.
- **Native HEV stats racing is avoided** — HEV native stats are mirrored off the hot path — kept.
- **XrayManager.temporary / stopTemporaryProcess** already has graceful-then-forced shutdown — kept.

### True regressions to fix in 4.0.0

1. Resolver summary classifier too narrow → summary/raw inconsistency (fixed by shared classifier).
2. No endpoint quarantine → decisive resolver failures stay in rotation (fixed).
3. No structural profile preflight → broken profiles still poison ranking (fixed).
4. Probe-first ranking still produces false negatives under censorship (fixed by survival re-rank).

## Files changed

- `core/ResolverFailureClassifier.kt` (new) — single source of truth for resolver failure
  categories; summary always derived from raw lines.
- `core/ResolverEndpointQuarantine.kt` (new) — session-scoped circuit breaker + stickiness.
- `core/ProfilePreflightValidator.kt` (new) — structural preflight, quarantine of invalid profiles.
- `core/DiagnosticsSummary.kt` (new) — machine-readable diagnostics block.
- `core/SurvivalFirstRanker.kt` — INVALID class, survival evidence (reconnect/resolver/MSS),
  ranking decision reason, `reorderResults`/`categorize`.
- `core/MarbleFreedomSmartRanker.kt` — preflight quarantine + survival-aligned Aegis decisions.
- `core/CensorshipAwareDnsResolver.kt` — real DoH, classification counters, endpoint quarantine,
  session stickiness, cancellation-safe accounting.
- `core/ProfileFlapGuard.kt` — flap reason + clean/cancelled shutdown counters.
- `core/BugFinder.kt` — resolver summary via the shared classifier.
- `AppRepository.kt` — quarantine invalid profiles before Smart Rank; survival re-rank + decision
  diagnostics for the Library path.

## Tests added

- `ResolverFailureClassifierTest` — DoH EOF counted, deadline+EOF+cancellation separated,
  shutdown-safe accounting.
- `ProfilePreflightValidatorTest` — "Turkey 4-All" VLESS/TLS invalid config quarantined.
- `SurvivalFirstRankerTest` — partial-probe-failure w/ successful history, UNCERTAIN vs DEAD,
  INVALID pinned last, Iran survival scoring, reconnect penalty.
- `ResolverEndpointQuarantineTest` — cert-expired/handshake/EOF/deadline quarantine, cancellation
  never quarantines, sticky preference.
- `DiagnosticsSummaryTest` — machine-readable block + summary/raw consistency.
