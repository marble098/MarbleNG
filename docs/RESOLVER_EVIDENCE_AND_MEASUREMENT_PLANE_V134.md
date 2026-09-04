# An open resolver loop and a measurement plane sized for the wrong link (V134)

Runtime evidence analysed: a session that was, by every transport metric, **healthy** —
`state=CONNECTED` for two and a half hours, `xrayAlive=true`, IPv4-only and working, the Home ping
race answering with four or five responders out of five, a verified route RTT of 267–444 ms — and
still carried three defects that a healthy session should not have:

- `Resolver health` reporting **29** `DoH deadline` events, accumulated steadily, with no TLS or
  certificate events beside them;
- `TURBO live-inconclusive-backoff` still reaching **1800 s**, correlated with the high-jitter
  windows (267 ms and 444 ms verified pings);
- `PSS` around **79 MB** (down from 89 MB in the previous, fully disconnected session).

The previous release (V133) had already removed the *systematic* cause of the deadline storm: every
DNS budget became a function of the measured link, so a 1350 ms budget was no longer asked to cover a
1126 ms route. That is why this session connected and stayed connected. What the numbers above show
is the second layer: **the fixes V133 made were correct for the live tunnel and absent everywhere
else** — in the loop that was supposed to learn from resolver failures, in the processes that measure
the route, and in the report that tells the user what any of it means.

---

## 1. The resolver-health loop was open (the 29 `DoH deadline` events)

Xray already says exactly which resolver failed and why. The core log line is:

```
[Error] app/dns: failed to retrieve response for www.google.com. >
  Post "https://1.1.1.1/dns-query": context deadline exceeded
```

Marble had every part of a feedback loop for that line except the wire between them:

| part | where it lived | what it did with the line |
|---|---|---|
| classification | `ResolverFailureClassifier` | deadline / EOF / TLS / cert, shutdown-safe separated |
| per-endpoint circuit breaker | `ResolverEndpointQuarantine` | used by the Freedom DNS module only |
| reporting | `BugFinder` | counted the lines and printed a total |
| emission | `XrayConfigHardener` | emitted a **fixed** resolver list |

The emitted encrypted list was `[user primary, user secondary] + [Cloudflare, Google, Quad9]`,
deduplicated, truncated to three — in that order, forever. An endpoint the operator was actively
disrupting therefore kept its rank for the life of the installation, and **every cold lookup paid its
whole RTT-derived deadline before failover reached a resolver that could answer**. V133 made that
deadline correctly generous, which is precisely why the cost went up rather than down: a correctly
sized budget on a filtered endpoint is a longer stall, not a shorter one.

Reporting the count is also what made `16 → 29` read like a regression. Bug Finder compared raw
totals across sessions of different lengths: 16 events inside a short *disconnected* session and 29
events spread over a two-and-a-half-hour *connected* one. The absolute comparison says the healthy
session is worse. It is not; the rate is 5.3/min versus 0.18/min.

### The fix

`core/ResolverEvidencePolicy.kt` (new) is the missing wire, kept pure and side-effect free so every
rule is unit-testable:

1. **Attribution.** A failure is attributed to the endpoint the core actually named, parsed from the
   quoted transport URL. The URL must be DNS-related *and* look like a resolver transport
   (`https+local`, `dot`, `quic`, `h2c`, `h3`, or an `https` URL with a `dns`/`query` path), so an
   outbound HTTPS failure that quotes a *destination* URL is never charged to a resolver. A line that
   names no endpoint is counted but attributed to nobody: an unattributable failure cannot demote a
   healthy resolver.
2. **Shutdown-safety.** `context canceled` and `read/write on closed pipe` are teardown artefacts
   and build no evidence at all, so a reconnect can never demote a resolver for having been
   interrupted.
3. **Decay and TTL.** Counters halve on a 15-minute half-life and a demotion expires after 30
   minutes. Decay is anchored to the instant the counters were last decayed from, not to the original
   failure: halving repeatedly against the original timestamp compounds on every observation pass and
   would erase a live filtering window in two minutes.
4. **Demotion, never deletion.** A decisively failing endpoint (4 attributed failures, or **one**
   expired certificate) moves to the *end* of the emitted list. Marble cannot prove a resolver is
   dead from inside a tunnel, so an encrypted fallback that is merely last is still a fallback. If
   *every* candidate is failing the configured order is kept — reordering cannot help and would only
   churn the config into a reconnect.
5. **Recovery.** A proven answer clears the pressure immediately. The adaptive DNS audit sends a real
   RFC 8484 wire query (`application/dns-message`, HTTP 200 and ≥12 bytes of answer), so a success is
   an actual resolution and not a reachable socket; every endpoint that answers is recorded.
6. **Racing only when it pays.** Xray's `enableParallelQuery` is the documented remedy for "slow DNS
   resolution caused by serial fallback through many servers", and it is also three times the DNS
   traffic through the tunnel. It is therefore armed only when an endpoint that is *about to be
   emitted* is decisively failing (`parallelQueryJustified`), and disarmed as soon as that evidence
   decays or a proven answer arrives. The Freedom fragment chain keeps serial failover: there the
   first write of every stream is fragmented into 1-byte packets with 4 ms pacing, so racing six
   resolvers multiplies the pacing cost instead of the odds of an answer.

Persistence is per physical network in the intelligence preferences
(`resolver-evidence:<networkKey>`, bounded to 8 endpoints) — no schema migration, and evidence about
a network says nothing about another one. The verdict travels to the config writer inside the
settings object as two **transient** fields (`measuredDnsDemotedEndpoints`,
`measuredDnsParallel`), exactly like `measuredIpv6Unhealthy` from V133: the resolver list is
assembled by the writer, so a verdict only the intelligence layer knew about is the reason 29
attributed failures changed nothing.

The observing end is `MarbleVpnService.harvestResolverEvidence`: an incremental `RandomAccessFile`
read of the core log every 30 monitor ticks, at most 256 KB per pass, consuming **whole lines only**
(a line cut in half by the chunk boundary may name no endpoint, and charging a fragment to whoever it
half-mentions is how a healthy resolver gets demoted for somebody else's failure). The offset resets
on log rotation and per session, and a preference write happens only when the attributed evidence
actually changed.

A demotion takes effect at the **next config emission** — the next connect, route change or hot-apply
— and deliberately does not force one. Tearing down a working tunnel to reorder three resolvers would
cost the user more than the stall it removes, and the running config already absorbs the failure:
`serveStale`/`serveExpiredTTL` answer from cache, serial failover reaches the next provider, and the
evidence is on disk for whichever emission happens next.

Bug Finder now reports the rate over the session window (`window(events, tunnelUptimeMs)`, the
denominator coming from `connectedSinceMs`) with endpoint attribution — `worst=<endpoint> (n) •
demoted=<endpoints>` — and its severity follows the rate: SEVERE → WARN, no honest denominator plus
an elevated absolute count → WARN, otherwise INFO "contained". A long stable session no longer reads
worse than a short broken one.

---

## 2. The measurement plane was sized for a faster link than the one it measures (TURBO 1800 s)

V133 derived every deadline from `LinkEvidence`, and the live tunnel got it. The processes that
*measure* the route did not: `XrayManager.temporary(...)` hardened every throwaway core with
`LinkEvidence.UNKNOWN`, i.e. with the legacy 1350/1650 ms encrypted-DNS budgets. A tuning trial
probes `cp.cloudflare.com` — a **domain** — through a core whose own encrypted DNS is routed through
the tunnel being measured, so one trial costs a core start, a DoH lookup (three tunnel round trips)
and the GET (four more). On a 267–444 ms link with 90 ms of jitter that budget does not survive, the
trial dies inside DNS, and the tunnel that is carrying traffic correctly reports that its own
acceleration engine measured nothing.

A second constant made it worse: the pass budget (`connectTuningBudgetSec`, 4–12 s) was computed
independently of the trial budget (`min(benchTimeoutSec, 4) × 1000`). A pass could therefore be too
short to contain even one trial — the same shape as the `responders=1` Home ping race V133 fixed, one
layer down.

And a third defect decided what that empty measurement *meant*. The service collapsed "no report" and
"unhealthy report" into one `if (report == null || !report.healthy)`, then asked `report != null` to
tell them apart — which by construction answered `TRANSPORT_INCONCLUSIVE` for every pass that never
started. A thermal veto at 40 % budget therefore escalated the **transport** backoff 600 s → 1200 s →
1800 s, suppressing the engine that was supposed to improve the route for half an hour. Absence of a
measurement is not a measurement.

### The fix

- `XrayManager.temporary(...)` takes `link: LinkEvidence` and passes it to both inner `harden(...)`
  calls; `ConnectionTuner`, `BenchmarkEngine` (both `measure` and `quickMeasure`) and
  `MarbleFreedomSmartRanker` now derive it from `MarbleIntelligence.linkEvidenceFor` and pass it.
- `TurboBackoffPolicy.Cause.NOT_ATTEMPTED`: a pass that never ran keeps the streak exactly as it was
  (its decay is driven by elapsed quiet time) and arms a short 90 s retry instead of escalating. The
  service classifies `report == null` as `NOT_ATTEMPTED`, a live route that is answering as
  `PROBE_UNAVAILABLE`, and only genuine transport evidence as `TRANSPORT_INCONCLUSIVE`. The recorded
  event also carries `tunerFailure` so a thrown pass is distinguishable from a vetoed one.
- `LinkDeadlinePolicy.tuningTrialTimeoutMs(evidence, floorMs)` sizes a trial from the same evidence as
  every other full HTTPS request, with the legacy constant kept as the **floor** (an unmeasured link
  behaves bit for bit as before) and a 9 s ceiling; `tuningPassBudgetMs(trialMs, requestedMs)` makes
  the user's tuning budget a floor too and always leaves room for one whole trial plus core start and
  teardown, bounded at 24 s.
- The per-sample probe budget inside a Rank is deliberately **not** inflated with the link evidence.
  Rank is a comparative measurement with a user-visible wall clock, and a node that needs seconds per
  sample deserves the score it gets. What was mis-sized there was the resolver inside the measurement
  core, not the stopwatch outside it. This is written down in the code so the next reader does not
  "fix" it.

---

## 3. Two latent defects the same investigation exposed

**`temporary(port = 0)` was rejected.** The guard was `port !in 1..65535` while
`reserveTemporaryPort` already coerces any preference into its own 18080–62000 range, so `0` — "any
free port" — was a valid intent the guard refused. `MarbleFreedomSmartRanker` passes `0` for every
profile it ranks, which is why its probe leg never produced a single sample and every ranked node was
judged on endpoint TCP evidence alone. The guard is now `port !in 0..65535`.

**The first connect of a session had no link evidence.** V133 read one source: the per-node health
record *for this physical network*, with the last honest live ping as a fallback. That record is
written by the measurements the session is about to make, so the very first config of a session was
emitted with the legacy budgets even for a node measured at 444 ms ten minutes earlier on another
network. `MarbleIntelligence.linkEvidenceFor` now merges, in order of evidence strength — this node on
this network, this node on a previous network, and only when the node has never been measured the
round-trip scale of *this network* from the nodes that actually worked on it (median, and only above
a 50 % success EWMA, so a dead server cannot inflate a healthy one's deadlines) — with the last live
ping folded in. The merge is `LinkEvidence.conservativeOf`, because deadline sizing is asymmetric: a
generous budget costs one slow failure detection, a truncated one costs every lookup on the route.
`UNKNOWN` is the identity, so a first-ever connect still reproduces the legacy constants exactly.

---

## 4. Resident memory, honestly

`PSS` ≈ 79 MB is not a leak and cannot be removed without replacing the core: it is the Go runtime
that Xray-core is written from (its own stacks, GC heap and goroutine bookkeeping) plus the native
`hev-socks5-tunnel` datapath, both of which are resident for as long as the tunnel is up. What *is*
addressable is growth over a long session, and there was exactly one structure in the app that grew
without an external trigger: the diagnostics ring. It was bounded by **count** (2 000 lines) but every
line is caller-sized — each field is capped at 4 000 chars, the number of fields is not, and
`error()` adds a 24-frame stack — so a long quiet session could retain tens of megabytes of UTF-16 in
the one process that has to stay small next to a Go runtime. The ring is now also bounded by retained
size (`RING_MAX_CHARS`, ≈2 MB), evicting oldest-first. Nothing durable is lost: the full history is
mirrored to the report file, so the ring is a recent-events window.

---

## Propagation, and why the order of the fixes matters

The three problems in the log are one propagation path. A measurement plane sized for a fast link
(§2) produces empty trials; empty trials were classified as transport evidence and escalated the
backoff to its ceiling; the backoff then suppressed the only component that could have re-measured
the route. In parallel, the resolver loop being open (§1) meant the endpoint that caused the trials to
fail inside DNS kept the rank that made them fail, and the report that should have shown the user what
was happening compared a total against a threshold and drew the wrong conclusion.

So the order is: give the measurement plane the same evidence as the tunnel (§2), stop treating an
absent measurement as a verdict (§2), close the resolver loop end to end (§1), report the rate rather
than the count (§1) — and only then the two latent defects (§3) and the resident bound (§4), neither
of which changes behaviour on a healthy session.

Every change keeps the legacy behaviour bit for bit when there is no evidence: `LinkEvidence.UNKNOWN`
reproduces the V133 floors, an empty evidence set reproduces the fixed resolver order, serial failover
stays the default, and `window(0, …)` is `CONTAINED`.

---

## Verification

- `app/src/test/.../ResolverEvidencePolicyTest.kt` (new, 21 tests): attribution and non-attribution of
  destination URLs, shutdown-safe exclusion, certificate-expired decisive on one event, demotion
  ordering and the never-demote-all rule, decay across half-lives, TTL expiry, recovery by a proven
  answer, stale successes not excusing newer failures, case-insensitive matching, bounded persistence
  and a serialize/deserialize round trip, racing armed only by evidence about emitted candidates, and
  the rate-versus-absolute reading of the two sessions from the log.
- `LinkDeadlinePolicyTest`: conservative merge (identity, max-merge, longer deadlines), a prior
  reaching the emitted budget on a first connect, tuning trial floors and ceilings, and a pass that
  always contains one whole trial.
- `TurboBackoffPolicyTest`: `NOT_ATTEMPTED` never escalates (streak stays 0, 90 s retry, four vetoes
  in a row) and the next genuine transport verdict still starts at the base window; the three causes
  are distinguishable in the recorded reason.
- `DnsDeadlineConfigTest`: the emitted resolver list keeps three independent encrypted providers, a
  demoted provider moves last and is never deleted, demoting every provider keeps the configured
  order, the endpoint bootstrap resolvers keep their own direct budgets, and racing is armed only by
  measured evidence (serial stays the default; `adaptiveDnsEnabled = false` keeps the deterministic
  serial graph the user asked for).
- `scripts/system-integrity-check.py`: 137 source-wide invariants, all passing — ten of them new
  (endpoint attribution, decaying time-bounded non-deleting demotion, evidence-ordered emission,
  evidence-armed racing, rate-based reporting, measurement cores inheriting link deadlines,
  link-derived tuning budgets, non-escalating `NOT_ATTEMPTED`, conservative evidence merging, and the
  resident ring bound). One previous invariant, *"Xray encrypted DNS fallback is bounded and serial"*,
  is deliberately **replaced** by *"Xray encrypted DNS races only on attributed resolver-failure
  evidence"*: serial failover is still the default, but it is now a default rather than an absolute,
  and the default-off half of that invariant is pinned by `DnsDeadlineConfigTest` rather than by a
  string match.
- CI gate: `gradle :app:testDebugUnitTest :app:compileDebugKotlin :app:compileReleaseKotlin` plus the
  integrity audit on every pull request (`verify.yml`), and the signed release build (`build.yml`)
  after merge.
