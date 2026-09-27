# IPv6-first, with a ladder instead of a cliff (V196)

`MARBLE_IP_FAMILY_SCAN_V196` · `MARBLE_IPV6_FALLBACK_LADDER_V196` · `MARBLE_DNS_DOMAIN_FAULT_V196`

## 1. The session that produced this chapter

One evening, one user, one library of about forty servers. Force IPv6 was selected in
Settings → DNS & IP version. The Wi-Fi had no global IPv6 address at all, and most of the saved
servers (Germany 2/3/4/5, Turkey 1/6/8, mci, Netherlands 3) publish only an A record.

```
18:29  BLOCKED • Kill switch active • This server has only an IPv4 address…
18:29  BLOCKED • Kill switch active • …
18:30  BLOCKED • Kill switch active • …
```

Every attempt was refused *before* the tunnel was built, so the kill switch kept the previous TUN
in place and blackholed traffic while the user tried server after server. Later in the same log:

```
18:51  app/dns: failed to lookup ip for domain noveo.ir at DOH//1.0.0.1 > context deadline exceeded
18:51  app/dns: failed to lookup ip for domain noveo.ir at DOH//149.112.112.112 > context deadline exceeded
       DNS storm guard: resolver pool entered parallel-race mode
```

No crash. No core error. Two policies, each locally reasonable, producing an outage and a false
alarm between them.

## 2. Root cause 1 — a preference implemented as a gate

`AddressFamilyPolicy.preference()` degrades every other mode when the evidence does not support
it: `SMART` falls back to IPv4, `PREFER_IPV6` becomes IPv4-first on a link without IPv6. One mode
did not:

```kotlin
AddressFamilyMode.FORCE_IPV6 -> return IpFamilyPreference.IPV6_ONLY   // unconditional
```

From that single line, seven independent readers each drew the same conclusion and refused:

| reader | how it refused |
|---|---|
| `MarbleVpnService.profileCompatibilityIssue` | `failBeforeTunnel` → kill switch → `BLOCKED` |
| `ProfilePreflightValidator.validate` | verdict `ipv4-disabled`, node quarantined in ranking |
| `XrayConfigHardener` | `require(!excludedIpv4Endpoint(...))` → `IllegalArgumentException` |
| `SingBoxConfigBuilder` (×2) | same `require` |
| `RouteProbe.measureUnified` | probe returns `UNREACHABLE`, loss 100 % |
| `AddressFamilyPolicy.excludedIpv4Endpoint` | the predicate all of the above share |

Every one of them was *right about the fact* — an IPv4-only node genuinely cannot be dialled over
IPv6 — and *wrong about the response*. "Fail closed" is the correct answer for a **leak** (never
send traffic outside the tunnel). It is the wrong answer for a **transport preference**, because
the alternative to a v6 dial is not a leak: it is a v4 dial to the very same encrypted server,
with IPv6 still carried inside the tunnel.

### The ladder

`core/Ipv6FallbackLadder.kt` replaces the cliff with four rungs. The first one the evidence
supports wins:

| rung | condition | effective mode | what the user sees |
|---|---|---|---|
| `IPV6_STRICT` | node answers over IPv6, link carries IPv6 | `FORCE_IPV6` | "IPv6 end to end" |
| `IPV6_FIRST` | node advertises AAAA, not proven here | `PREFER_IPV6` | "IPv6 first, IPv4 held in reserve" |
| `IPV4_FIRST` | node is IPv4-only, **or** link has no IPv6 route | `PREFER_IPV6` | "Server dialled over IPv4 • IPv6 stays on for destinations" |
| `REFUSED` | strict opt-in, or an IPv6-literal node on a link with no IPv6 | unchanged | the refusal, plus the concrete alternative |

Two design decisions carry the whole fix:

1. **The decision is expressed as a different `AddressFamilyMode` for that one session**, not as a
   special case inside the connect path. Patching one reader would have produced the exact class
   of bug this project keeps fixing: a policy six subsystems honour and one does not. Rewriting
   the mode means the two `require` calls that used to throw now see `PREFER_IPV6` and emit a
   perfectly ordinary config.
2. **It is applied in `MarbleIntelligence.effectiveSettings`** — the one function the connect path,
   the delay test, the ranking pool and every JVM prober already share. `AppRepository`
   `effectiveSettingsFor` applies it too, for the case where the intelligence engine is switched
   off. Applying it twice is harmless: `apply()` is idempotent.

Nothing is persisted. The ladder is re-evaluated for every connect, so joining an IPv6-capable
network or scanning an IPv6-capable node restores strict Force IPv6 by itself, with nothing for
the user to re-enable.

`AppSettings.strictAddressFamily` (Settings → DNS & IP version → **Strict family enforcement**,
off by default) restores the old contract for a user who means "IPv6 or nothing" literally. It is
also what `excludedIpv4Endpoint` now keys on, so the raw-settings callers (ranking's preflight
pass, the probers, the builders' own guard) reach the same verdict as the connect path instead of
quarantining a node the tunnel is perfectly willing to dial.

Leak containment is unchanged in both directions: the `::/0` / `0.0.0.0/0` block rules are derived
from the **effective** family, so a session never routes outside the family it is actually
running.

## 3. Root cause 2 — the app could only guess a server's family

The ladder needs a fact the product did not have: *does this server have IPv6 at all?* The library
only ever had folklore — a node's name, its TLD, occasionally an AAAA record nobody had dialled.

`core/IpFamilyScanner.kt` measures it, and the Servers page exposes it exactly where the question
is asked:

- **⋯ → Scan IPv4 / IPv6** on any server;
- **group ⋯ → Scan IPv4 / IPv6** for a whole subscription, six probes in parallel;
- a chip on the row (`v4+v6`, `v6`, `v4`, `v4 • v6?`, `no route`) and a full report dialog.

The measurement is two questions per family, deliberately kept apart:

1. **Which records exist** — A and/or AAAA, resolved through the app's own encrypted IP-literal
   DoH (`AddressFamilyPolicy.resolveWithBudget`). System DNS is never consulted, so a scan cannot
   leak a node name to the local ISP and cannot be poisoned by it either.
2. **Which of them connects** — one bounded TCP connect per family to the node's real port.
   "Advertises AAAA" and "answers over IPv6" are different claims, and only the second is worth
   routing on.

The verdict is five-state, because each state has a different remedy:

| verdict | meaning | consequence |
|---|---|---|
| `DUAL_OK` | both families connect | Force IPv6 is safe here |
| `IPV6_ONLY` | only IPv6 connects | needs an IPv6 network; Force IPv4 cannot dial it |
| `IPV4_ONLY` | **no AAAA exists** | no retry can change it — ladder rung 3 |
| `IPV6_UNPROVEN` | AAAA exists, only IPv4 answered | can recover; IPv6 stays the *first* attempt |
| `UNREACHABLE` | records exist, nothing answers | not a family problem: the node or the port |
| `UNKNOWN` | no DNS answer | nothing may be concluded — never reported as IPv4-only |

Scope matters as much as the verdict. Every result is stamped with the physical network it was
measured on (the same key Marble Intelligence buckets node health by) and a six-hour TTL: a
verdict from a v6-capable home Wi-Fi must not keep Force IPv6 armed in a v4-only café, and a node
that gained an AAAA record last week must not stay branded IPv4-only forever.

The scan also runs itself. When a user connects to a node with no fresh verdict, the measurement
starts in the background — never on the dial's critical path. An unmeasured node is given the
benefit of the doubt exactly once (the dial is itself the cheapest possible measurement), and by
the time a failed attempt comes back around, the ladder has a measured answer instead of the same
guess.

## 4. Root cause 3 — a broken *name* read as a broken *resolver*

```
failed to lookup ip for domain noveo.ir at DOH//1.0.0.1        > context deadline exceeded
failed to lookup ip for domain noveo.ir at DOH//149.112.112.112 > context deadline exceeded
```

`ResolverEvidencePolicy` attributed both lines to the endpoint the core named, and the storm guard
counted both as pool failures. Two wrong reactions followed from one mis-attribution: two healthy,
independent providers were pushed down the emitted resolver order, and the parallel-race regime
armed — tripling DNS traffic through the tunnel for ten minutes with nothing actually broken. That
is the `DNS storm guard` warning the user saw.

Two independent providers missing their budget on the *same name* is not a resolver outage. It is
a fact about that name: a domestic ccTLD whose authoritative servers are slow from foreign anycast,
a domain under interference, or a host that no longer exists.

`core/DnsDomainFaultPolicy.kt` adds the missing classification. A failure becomes a **domain
fault** once the same name has failed against at least two *distinct* endpoints inside ten
minutes. From that moment its lines stop being evidence about any endpoint and stop feeding the
storm detector; they are recorded against the name, where they are actionable. Bug Finder reports
them as *DNS name faults* — `noveo.ir (2 resolvers, 2 failures)` — with the explicit note that
these lookups are a property of the name, not of the pool.

Everything else is unchanged: one endpoint failing many different names is still endpoint
evidence, still demoted, still able to arm the storm guard. Only the shape "many endpoints, one
name" — the shape that can never be fixed by changing resolvers — is reclassified.

The guard also gained its missing half. `DnsStormGuard.recordProvenAnswer` (now in its own file,
`core/DnsStormGuard.kt`) lets a **real answer** stand the regime down instead of only the clock:
each success halves the event window, so two consecutive answers end parallel racing within
seconds, while one lucky lookup during a genuine filtering burst does not.

## 5. Why the remaining reported symptoms were left alone

Short-term TCP stress (`stressed=true`, retransmissions, jitter spikes past 500 ms on Germany 1/4
and Turkey 8) always recovered on its own, and the adaptive MTU/MSS path already reacts to it.
Adding a second reaction to a self-correcting signal is how oscillation gets built.

## 6. Contracts pinned by tests

| file | what it pins |
|---|---|
| `Ipv6FallbackLadderTest` | every rung, both refusals, idempotent projection, and the nine reported IPv4-only nodes connecting |
| `IpFamilyScannerTest` | all six verdicts, v6-probed-first, bounded probes, network scoping, TTL, serialisation, group counts |
| `DnsDomainFaultPolicyTest` | name parsing for both cores, the two-endpoint rule, teardown artefacts ignored, windowing, bounded memory |
| `DnsStormGuardV196Test` | arming, recovery by proven answer, and the anti-flap half-life |
| `Ipv6LeakHardeningTest` | the **strict** contract is unchanged, and a degraded session still blocks nothing it is using |

## 7. Files

```
core/IpFamilyScanner.kt        measurement + verdict machine + group summary
core/Ipv6FallbackLadder.kt     the four rungs, evidence model, settings projection
core/DnsDomainFaultPolicy.kt   name-vs-resolver classification
core/DnsStormGuard.kt          extracted detector + recordProvenAnswer
```

Wiring: `MarbleIntelligence.effectiveSettings` / `.familyResolution` / `.ipFamilyEvidence`,
`AppRepository.scanIpFamily` / `.scanIpFamilyForProfiles` / `.familyResolutionFor`,
`AppStore.loadIpFamilyScans` / `.saveIpFamilyScan`,
`MarbleVpnService.profileCompatibilityIssue`, `BugFinder` (*DNS name faults*), and the Servers
page (`ServersNodeMenu`, `ServersGroupMenu`, row chip, `IpFamilyScanDialog`,
`IpFamilyGroupDialog`).
