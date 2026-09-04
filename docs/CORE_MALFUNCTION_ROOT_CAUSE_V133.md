# Core malfunction: a healthy core with dead functional layers (V133)

Runtime evidence analysed: a session in which Xray started cleanly and never crashed
(`XRAY start-begin` → `socks-ready`, exit code 0) while the layers above it — encrypted DNS and
route health — failed continuously:

- `Resolver health` reporting 16 `DoH deadline` events with **zero** TLS/cert events;
- `home-connection-ping` reporting `responders=1` / `responders=2` out of five probes;
- `TURBO live-inconclusive-backoff` at 600 s, 1200 s and 1800 s, repeating;
- `ROUTE jitter-control-enter` / `jitter-control-exit` alternating;
- `EGRESS startup-observation-inconclusive` with `literalIpHttps=true, domainHttps=false`, followed
  by `TURBO startup-family-tune-requested`;
- the userspace MTU differing between sessions of the same route (1360 vs 1400);
- `IPv6 preferred, IPv4 raced after 60 ms`;
- 13 `killDueToPackageUpdate` records (`ApplicationExitInfo.REASON_PACKAGE_UPDATE`, reason 16).

The conclusion below is the part that matters: **these were not six independent faults.** One
structural defect — every deadline and threshold in the stack was a constant sized for a fast link,
while this route measured ~1126 ms RTT with 50–150 ms of jitter — produced the DNS failures, and
the failures then propagated through the health engine into the acceleration engine. Fixing the
symptoms one by one would have left the propagation paths intact.

---

## 1. Encrypted DNS deadlines ignored the measured link (the primary fault)

Ordinary app DNS is routed **through the tunnel**: the hardener emits a routing rule that sends
everything tagged `xgc-dns` to the proxy outbound. A single encrypted query therefore costs a TCP
handshake, a TLS handshake and the request/response — three round trips of the *tunnel's* RTT.

The budget for that query was a constant:

```kotlin
// before
fun dnsTimeoutMs(index: Int): Long =
    if (freedomDnsTimed) 8_000L + index * 1_000L
    else if (index == 0) 1_350L else 1_650L + (index - 1) * 250L
```

At 1126 ms RTT the query needs ≈3.4 s. The 1350 ms budget expired first, on every resolver, every
time. That is exactly the observed signature — `DoH deadline` with no TLS and no certificate errors:
the resolvers were reachable and answering, the budget had already run out. With no answer from any
resolver Xray could not resolve a single destination domain, so a tunnel that was demonstrably up
delivered no internet.

**Fix** — `LinkDeadlinePolicy` (`core/LinkDeadlinePolicy.kt`) derives every budget from measured
evidence: `hops × tail-RTT + jitter/loss headroom`, clamped to the old constants as a **floor** and
to the 10 s ceiling used by the upstream XTLS `serverless_for_Iran.jsonc` as a **ceiling**.

`LinkEvidence` is optional end to end (`LinkEvidence.UNKNOWN` reproduces the legacy constants), so a
first-ever connect, a delay test and a ranking config are bit-for-bit unchanged. Measured evidence
reaches the config writer through `XrayConfigHardener.harden(..., link)` ←
`XrayManager.start(..., link)` ← `MarbleVpnService.linkEvidenceFor(profileId)`, which reads the
persistent per-node health record for the current physical network and falls back to the last honest
live ping. The `https+local` endpoint bootstrap resolvers keep their own direct-link budgets: they
dial the underlay, not the tunnel, so the tunnel RTT must not inflate them.

The Freedom fragment chain also keeps its 8 s/9 s/10 s schedule, because there the 1-byte
first-write pacing — not the link — dominates the handshake.

## 2. The same constants truncated the Home ping race

`home-connection-ping` raced five probes with per-probe budgets of 1600–2000 ms inside a 2600 ms
batch. On this link only one or two probes could finish, so the published readout was the fastest of
a truncated sample, and `responders=1` read like four dead providers.

**Fix** — the per-probe budget and the batch budget now come from the same policy
(`httpsProbeTimeoutMs` / `probeBatchBudgetMs`), so the batch is always large enough to cover one
probe. The diagnostics line reports both budgets, so a truncated race is visible instead of silent.

## 3. The acceleration backoff was self-locking

```kotlin
// before
tuningInconclusiveStreak = (tuningInconclusiveStreak + 1).coerceAtMost(8)
tuningBackoffUntilMs = System.currentTimeMillis() + backoffMs   // up to 1800 s
```

Three properties made this a blackout rather than a backoff:

1. **Cause-blind escalation.** `TuningReport.healthy` is `trials.any { it.success > 0 }`, so entering
   the inconclusive branch always meant "no trial measured anything" — a resolver/probe failure. The
   code escalated the *transport* backoff on it anyway, so a DNS window bought a 30-minute
   suppression of the very engine that could have improved the route.
2. **No decay.** The streak was only reset by a successful pass, and a successful pass cannot happen
   while the backoff blocks passes.
3. **No release.** `jitter-control-exit` proved the route had recovered; the timer stayed armed.

**Fix** — `TurboBackoffPolicy` (`core/TurboBackoffPolicy.kt`):

- cause separation. The discriminator is the live route meter, because the report alone cannot tell
  the two apart: if the tunnel is answering verified HTTPS probes while the tuning trials measured
  nothing, that is a probe problem (`PROBE_UNAVAILABLE` → 180 s, no escalation); if the live route
  is silent too, the transport evidence really is inconclusive and escalation is correct;
- time decay — the streak halves per 10-minute half-life, so an escalation cannot outlive the
  conditions that caused it;
- early release on recovered-route evidence, bounded to two releases per session so a flapping link
  cannot re-arm the engine indefinitely.

## 4. Jitter control oscillated because an ambiguous tick erased the release streak

```kotlin
// before
else -> { jitterLowStreak = 0; jitterHighStreak-- }   // every ambiguous tick
```

Entry needed three degraded ticks; release needed four *consecutive* clean ones, and any tick with
mixed or insufficient evidence wiped the release counter. On a link whose jitter swings between 50
and 150 ms, clean runs of four are rare — so jitter control latched on, forced `degraded=true` into
the tuning gate, dropped the probe cadence to the degraded interval, and alternated enter/exit.

**Fix** — `JitterControlPolicy` (`core/JitterControlPolicy.kt`): an ambiguous tick is a **hold** that
changes no counter; an opposite-kind tick decrements the other streak by one instead of erasing it;
both transitions get a dwell window (30 s minimum hold, 45 s minimum dwell) so neither state can
flip on a single sample.

## 5. The egress observation compared two incomparable probes

`domainHttps` performed a full HTTPS GET with a 3500 ms budget; `literalIpHttps` measured only the
TLS first byte with 1500 ms. On a 1126 ms link the GET needs ≈3 round trips and the first-byte probe
≈2, so the domain leg failed and the literal leg passed. The code read that asymmetry as "literal IPs
work, domains do not" and immediately raised `startup-family-tune-requested`, sending the
acceleration engine off to re-measure address families on a route whose only real problem was a probe
budget. The observation was also one-shot: the reading at t+20 s was the last word for the session.

**Fix** — both legs now measure the same thing (TLS first byte) with the same RTT-derived budget, and
`EgressObservationPolicy` (`core/EgressObservationPolicy.kt`) requires two consecutive agreeing
readings before a family tune is requested, distinguishes "nothing answered" (route suspect, not a
DNS verdict) from "only literals answered", and re-arms on a bounded 30/60/120 s schedule.

## 6. A per-socket MSS was treated as a path MTU

`TCP_INFO`'s `pmtu` is the MSS of whichever socket the telemetry sampled, so it legitimately
alternates between 1400 and 1360 as flows rotate. The monitor committed every sample to the learned
store and requested a re-measurement whenever `activeMtu > pmtu`:

```kotlin
// before
repo.intelligence.rememberPathMtu(activeProfileId, transport.pmtu)
if (activeMtu > transport.pmtu) tuningRequested.set(true)
```

That explains both remaining symptoms at once. The last sample won and was pinned for the whole TTL,
so MTU differed *between sessions* of the same route instead of converging; and `tuningRequested`
fired on every rotation, and that flag makes the next acceleration pass **forced** — bypassing the
sample, thermal, heavy-traffic and interval guards. One unstable counter was feeding the backoff
storm in §3.

**Fix** — `PathMtuPolicy` (`core/PathMtuPolicy.kt`): a value is committed only after two consecutive
identical observations, commits are rate-limited to one per 45 s in both directions, and a
re-measurement requires a *material* (≥40-byte, i.e. one MSS step) corroborated drop.

## 7. The measured IPv6 verdict never reached the config

`MarbleIntelligence.effectiveSettings` computed a per-node IPv6 verdict from `SmartIpRacePolicy` and
folded it into `preferIpv6`. `AddressFamilyPolicy.preference()` then re-derived the preference as
`settings.preferIpv6 || underlayHasIpv6` — so on any IPv6-capable underlay the demotion was thrown
away and the plan went back to `IPV6_FIRST`. Bug Finder made it worse by building its family plan
from the *stored* settings rather than the effective ones, which is where the
`IPv6 preferred, IPv4 raced after 60 ms` line came from: a description of a plan the tunnel was not
running.

**Fix** — the verdict now travels inside the settings object as a transient
`AppSettings.measuredIpv6Unhealthy` (never persisted: a verdict belongs to the network session that
produced it), `AddressFamilyPolicy` honours it from either source, and Bug Finder describes
`effectiveSettingsFor(activeProfile)`. An explicit user demand for IPv6 still wins — a measurement
demotes the automatic ordering, it never overrides the user.

The Happy Eyeballs attempt delay is now a fraction of the measured RTT (RFC 8305 §5). A fixed 60 ms
on a 1126 ms link starts the second dial before the first SYN-ACK can possibly arrive, so both
families were dialled on every connection and the preferred family could never win the race. Scaling
only ever lengthens a race the policy already armed; a disarmed race stays disarmed.

## 8. `REASON_PACKAGE_UPDATE` left the runtime dirty and the tunnel unrestorable

Android kills the process to install a new APK — including a core-module update — with no teardown
callback. Three things were left behind: a runtime config that `writeText` had already truncated but
not yet rewritten (the next start would feed the core a corrupt document), a TCP_INFO telemetry file
belonging to a dead process, and no record that the user had asked to be connected.

**Fix**

- `XrayManager.writeRuntimeConfig` writes a sibling temp file, fsyncs and renames over the target, so
  the on-disk config is always either the previous complete document or the new one;
- `XrayManager.discardStaleRuntimeArtifacts()` removes what the killed process owned (the Xray log is
  preserved — it is Bug Finder's evidence, and the next start rotates it anyway). JNI state needs no
  handling: `libmarbleng.so` and its tunnel handles die with the process;
- `MarblePackageReplacedReceiver` handles `MY_PACKAGE_REPLACED`, runs that cleanup and offers the
  restore. It deliberately does not start the foreground service: Android 12+ forbids a background
  receiver from doing so, and a `ForegroundServiceStartNotAllowedException` would present as another
  crash. The tap is a user-visible action, which is the supported path back into a foreground tunnel;
- a durable tunnel intent (`AppStore.setTunnelIntentActive`) is written with the last-route reference
  on connect and cleared only by an explicit disconnect, so `MainActivity` restores the exact route
  instead of the user having to notice and reconnect by hand.

---

## Propagation, and why the order of the fixes matters

```
fixed 1350 ms DNS budget on a 1126 ms link
        └─> every DoH query expires  ->  no destination resolves  ->  "no internet"
        └─> domain egress probe fails ->  startup-family-tune-requested
        └─> tuning trials measure nothing  ->  cause-blind escalation
                └─> 600s -> 1200s -> 1800s backoff, no decay, no release
unstable per-socket MSS
        └─> tuningRequested on every rotation  ->  forced passes  ->  same escalation
ambiguous ticks wiping the release streak
        └─> jitter control latched  ->  degraded=true forced into the tuning gate
```

The DNS deadline is the origin; the backoff and jitter faults are what turned a resolver problem into
a session-long suppression of every adaptive layer. Both had to be fixed: sizing the deadlines
correctly removes the trigger, and the policy changes remove the amplification that would otherwise
wait for the next trigger.

## Verification

`LinkDeadlinePolicy`, `TurboBackoffPolicy`, `JitterControlPolicy`, `PathMtuPolicy`,
`EgressObservationPolicy` are pure and side-effect free, so each fix has a unit test that pins the
observed failure and its correction, including the regression guard that an unmeasured link keeps the
legacy constants exactly. `DnsDeadlineConfigTest` asserts the same thing end to end on the emitted
Xray JSON — the resolver `timeoutMs` values, the untouched bootstrap budgets, the family order and
the race delay.
