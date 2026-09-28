# IPv6 truth, real flags, and a keyboard that stops asking (V197)

`MARBLE_IP_FAMILY_TRUTH_V197` · `MARBLE_SERVER_LOCATION_V197` · `MARBLE_IME_HYGIENE_V197` ·
`MARBLE_ROUTE_PROBE_MULTI_TARGET_V197`

## 0. The session that produced this chapter

One user, one logcat, four unrelated complaints that turned out to be four real defects:

1. *"The IPv4/IPv6 scan is not accurate. A server answers on both, but the app only shows IPv4 — and
   my whole goal is the highest speed and quality, which is the IPv6 path."*
2. *"Whenever I add a server or a subscription, the app must detect its location itself, with a real
   detection algorithm, and put its flag in the circle."*
3. Sixteen minutes of log with no crash, no exception and no ANR — and more than a hundred
   `InsetsController.hide(ime())` calls, each answered `PHASE_CLIENT_ALREADY_HIDDEN`, in one
   nine-second window roughly every 500 ms.
4. At 00:47:29 the route probe pivoted `1.1.1.1 → 8.8.8.8` with the reason
   `previous literal HTTPS target produced zero verified RTTs`, and the next three samples reported
   a `successLowerBoundPercent` of 44 %.

None of them is a crash. All of them are the same class of bug: a policy that is locally reasonable
and globally wrong because it is missing one distinction.

| # | the missing distinction |
|---|---|
| 1 | "a resolver answered" vs "a resolver answered *with that record*" |
| 2 | "one service disagreed" vs "four services could not be reached" |
| 3 | "the user finished with the keyboard" vs "a composition ran again" |
| 4 | "this target is filtered" vs "this route is broken" |

---

## 1. A dual-stack server reported as IPv4-only

### Root cause 1 — the race picks a winner before it reads the answer

`EncryptedEndpointResolver.resolve` raced five DoH providers and kept the first response that came
back:

```kotlin
fun resolve(host: String): Array<InetAddress> = resolveWith(host) { wire ->
    pool.raceResolve(wire, providers(), 2_500).takeIf { it.success }?.body
}
```

`resolveWith` asked AAAA (type 28) and then A (type 1), serially, one race each. The winner of a
race is chosen the moment its HTTP call succeeds — before `DnsWireCodec.parseAnswers` has looked
inside. A provider that answers NOERROR with an **empty answer section** therefore ends the race
successfully, and the providers that did hold the AAAA record are cancelled mid-flight.

Downstream, `hasIpv6 = false`, and `IpFamilyScanner.classify` reached the only branch it could:

```kotlin
ipv4Ok -> IpFamilyVerdict.IPV4_ONLY   // "this node publishes no IPv6 address"
```

That is not a mislabel, it is the most expensive wrong answer in the product. `IPV4_ONLY` is the
verdict with no remedy — the ladder routes around it permanently, the row shows `v4`, and the user
is told the node has no IPv6 while the node has been answering over IPv6 all evening. For a product
whose speed thesis is "IPv6 when it is real", a false `IPV4_ONLY` does not misdescribe a row; it
removes a server from the family the tunnel should be using.

**Fix.** `EncryptedEndpointResolver.resolveAll` inverts the contract for the one question that needs
it:

- A and AAAA are queried **in parallel**, so one slow family can no longer eat the other family's
  budget (the old serial order asked AAAA first with a 3 s ceiling inside a 2.5 s outer budget,
  which is how a merely slow network produced *no verdict at all*);
- each family asks up to `MAX_WITNESS_PROVIDERS` (3) providers and keeps the **union**; only
  providers that actually returned addresses count as witnesses, and the loop stops once
  `MIN_WITNESS_PROVIDERS` (2) independent resolvers agree the record exists;
- `AddressFamilyPolicy.resolveFamilyWithBudget` is the new seam, and it is what the scanner now
  uses by default.

### Root cause 2 — one silent answer was believed as absence

Even with a union resolver, "no AAAA" is a claim about a *distributed database*, and one lookup is
one sample of it. `IpFamilyScanner.resolveFamilies` therefore refuses to conclude absence from a
single silent answer: when one family answered and the other did not, it asks again
(`RESOLVE_CONFIRM_PASSES`, half the budget) and only then believes the record does not exist.

The rule is deliberately narrow: when **neither** family answered, the name did not resolve, and
asking again would only burn the sweep's budget. That case stays `UNKNOWN` — the one verdict that
forbids every conclusion, including "IPv4-only".

### Root cause 3 — one TCP connect decided reachability

A single 1.4 s connect per address is a coin toss on IPv6. The packets a link is most likely to
lose are the first ones: neighbour discovery, PMTU discovery, a cold radio. Losing exactly that
packet turned a dual-stack node into `IPV6_UNPROVEN` and, combined with root cause 1, into
`IPV4_ONLY`.

`probeAddress` now dials each address `CONNECT_ATTEMPTS_PER_ADDRESS` (2) times. Bounded, so
`budgetMsFor` is still a proof that a forty-node sweep terminates: worst case per endpoint is the
resolve budget plus its confirmation pass plus every address of both families dialled twice, and
the waves simply multiply.

### Root cause 4 — IPv6 lost on noise

`fasterFamily` handed the family to IPv4 whenever IPv4's single handshake came back even 1 ms
lower. Two connects taken 200 ms apart on the same radio differ by more than that every day, so a
node's family was being decided by noise. `IPV6_PREFERENCE_TOLERANCE_MS` (8 ms) fixes the
direction: inside the noise floor **IPv6 still wins**, because the user's goal is the fastest path
and for this product that path is IPv6. `ipv6Preferred` exposes the result.

### What did *not* change

- Encrypted IP-literal DoH only. A scan still never reveals a node name to the phone's resolver.
- The five-state verdict machine, `usableOn`'s network scoping and the six-hour TTL are untouched.
- The probes are still bounded, and `budgetMsFor` is still finite.

---

## 2. "Detect the location yourself, and put the flag in the circle"

The old sweep (`ensureServerLocations`) could not satisfy that request, for three separate reasons.

### Root cause 1 — a label that named a country was never tested

```kotlin
val offline = ServerCountry.of(p.name, p.host)
// A label that already names a country is a provider statement; the test
// runs in the background but is not urgent, so unknowns go first.
if (offline.isKnown && answered < candidates.size / 2) continue
```

A node called *🇩🇪 Germany 3* was skipped, so the flag the user saw was the one the **subscription**
wrote. The request was to detect the location, which means measuring the address: a name is a claim,
an address is a fact. The label now **orders** the queue (unknowns first) and never decides whether
a server is measured.

### Root cause 2 — the quorum was unreachable, so a measured location drew no flag

`voteCountry` required two independent services to agree, and the app asked three. These are free,
keyless endpoints; each of them is rate-limited, blocked, or simply unreachable from some networks
some of the time. The common case on a filtered network was *one answer*, and the rule threw it
away — so the app successfully located a server and then drew an empty circle.

Two changes:

- **Five providers**, with deliberately different answer shapes (`country_code`, `countryCode`, a
  spelled-out `country`), so one upstream changing its schema cannot empty the whole vote:
  `ipwho.is`, `api.country.is`, `ipapi.co`, `freeipapi.com`, `api.ip.sb`.
- **Silence is not disagreement.** When most of the pool could not be reached *at all* and the one
  service that did answer stands uncontradicted, that is evidence about the address — it is the
  *network* that is thin. The answer is accepted and marked `LocationConfidence.LONE`. A genuine
  disagreement (two services naming two countries) is still `""`: that is worse evidence than one
  answer, because it proves the address is contested.

`ServerLocationVerdict` carries the evidence (`code`, `confidence`, `observers`, `providers`,
`witnesses`) so the caller can tell the two apart. That is what lets the app do the only honest
thing with a thin answer: **show it now, re-test it later.**

### Root cause 3 — the sweep was serial and one-shot

One profile at a time, up to three seconds each, so a forty-server import took minutes and the
session budget ran out long before the list did. The sweep now runs on a bounded pool
(`LOCATION_SWEEP_CONCURRENCY` = 4) with an end-to-end deadline, dedupes by endpoint, and re-tests
`provisional` verdicts — including when the physical network changes, because a different network is
a different set of reachable providers.

### The flag itself

`ProtocolTile` / `CountryFlagCircle` already drew the measured flag inside the circle; what was
missing was the measurement. `ensureServerInsights()` is now called from every path that adds a
server or a subscription (manual node, chain, import text, subscription refresh, duplicate, QR via
the shared intake, and app start), and it runs **both** halves — location and address family — in
the background, off the frame clock, never on the connect path's critical section.

---

## 3. One hundred `hide(ime())` calls with nothing to hide

The log is unambiguous: `PHASE_CLIENT_ALREADY_HIDDEN` means the platform was asked to hide a
keyboard that was not open. Nothing in this app called `hide(ime())`, so the requests come from the
text-input machinery, and the only way to make them in a loop is to keep tearing down and
re-creating an input session that has nothing to type into.

Two shapes in the product can do that:

1. **A surface that looks like a text field but is not.** `ServersDropdownField` is a real
   `OutlinedTextField` — so its label floats in line with the real fields beside it — wrapped in a
   click handler that opens a menu. It is focusable, so the platform can hand it an input session
   and take it away again, once per pass, forever. `Modifier.marbleImeInert()` (`focusProperties {
   canFocus = false }`) removes that.
2. **Fields with no event of their own.** Not one text field declared an IME action, so the
   keyboard's own Done key had nothing to call and a session only ever ended when the framework
   noticed. Every single-line field now declares `marbleImeOptions(...)` and
   `marbleImeActions(...)`, and a host that leaves (`MarbleImeReleaseOnDispose`) releases its
   session exactly once on disposal instead of bequeathing an open session to the screen underneath.

The third piece is **containment**, and it matters because the failure mode is invisible until it
is expensive. `MarbleImeHider.hide()` is guarded: a second request inside
`MARBLE_IME_HIDE_COOLDOWN_MS` (750 ms) that was not preceded by a fresh focus is dropped. A loop
therefore costs **one** call instead of a hundred, and a user who genuinely presses Done twice in a
second loses nothing — the focus is already gone. `suppressedHides` makes that visible in
diagnostics.

The rule going forward: **a hide is an event, never a composition.** Nothing in the app calls
`LocalSoftwareKeyboardController` at all, and the integrity check now enforces that.

---

## 4. The probe pivot

Two things were wrong with the 00:47:29 pivot, and only one of them was the visible number.

### The number was not a bug

`successLowerBoundPercent: 44` is the Wilson 95 % lower bound for three successes out of three
attempts. It is not a quality score and it is not displayed; it is one line in a diagnostic event.
No change was made to it, and none was needed.

### The pivot was reactive, and it was a queue

```kotlin
for ((index, alternate) in alternates.withIndex()) {
    val alternateSample = measureOne(alternate)   // serial, up to 1.25 s each
    if (alternateSample <= 0) continue
    ...
}
```

Filtering here is per-target and intermittent: a literal anycast address answers for weeks and then
stops for an afternoon while its neighbour is perfectly healthy. Measuring one target per cycle
meant every such afternoon **began** with a full cycle of "no verified RTT" before the pivot even
started, and if the first alternate was filtered too the cycle was spent discovering that.

`MARBLE_ROUTE_PROBE_MULTI_TARGET_V197` makes the probe multi-target from the start:

- **A cycle that follows a failed one races two targets from its very first sample**
  (`raceFirstSample`, partner chosen by `routeProbePartnerFor`). It no longer measures one, fails,
  and only then asks somebody else. A healthy cycle is unchanged: one target, one burst, no extra
  traffic.
- **A pivot is a race, not a queue** — both candidates are measured in parallel and the first
  verified answer wins the burst. The losers are cancelled.
- **The score waits for its evidence.** A pivot clears the window, so the first burst back is 3/3 —
  a healthy route that would momentarily score like a flaky one. `PIVOT_QUALITY_MIN_SAMPLES` (4)
  holds `liveRouteScore` and `liveRouteSuccessPercent` until the window is worth scoring; latency
  and jitter are real measurements the moment they land, so they still publish immediately, and the
  status line says *"score settling after target change"* instead of silently freezing.

---

## 5. Contracts pinned by tests

| file | what it pins |
|---|---|
| `IpFamilyScannerTest` | all six verdicts, v6-probed-first, bounded probes, network scoping, TTL, serialisation, group counts, **plus**: a lost first IPv6 packet, a family that only appears on the confirming resolution, absence that survives the confirmation, an unresolved name that is never confirmed, and IPv6 winning inside the noise floor |
| `ServerLocationResolverV192Test` | the quorum rule and tie refusal, unchanged |
| `ServerLocationResolverV197Test` | lone-accept vs. genuine disagreement vs. one-silence, provisional marking, `QUORUM` vs `LONE`, the new provider answer shapes, and zero requests for an unresolvable address |
| `MarbleImeHiderTest` | the first hide is honoured, a loop inside the cooldown costs one call, a real second press still works, and refocusing arms the next hide |

Plus the source-wide invariants in `scripts/system-integrity-check.py`, which now assert that no
composable uses `LocalSoftwareKeyboardController`, that every family field is event-driven, and
that the new resolver/sweep seams exist.

## 6. Files

```
core/EncryptedEndpointResolver.kt   resolveAll / queryFamily — parallel A+AAAA, witness union
core/AddressFamilyPolicy.kt         resolveFamilyWithBudget — the scan's resolver seam
core/IpFamilyScanner.kt             resolveFamilies (absence confirmation), probeAddress (retries),
                                    IPv6 preference inside the noise floor
core/ServerLocationResolver.kt      five providers, ServerLocationVerdict, lone-accept rule
data/AppStore.kt                    a learned location remembers whether it was a lone answer
ui/MarbleIme.kt                     one explicit keyboard policy + the rate guard
ui/Aether2026.kt                    inert dropdowns, IME actions, release-on-dispose
AppRepository.kt                    ensureServerInsights (location + family), parallel sweep
vpn/MarbleVpnService.kt             raceFirstSample, hedged cycles, pivot race, quality warm-up
```
