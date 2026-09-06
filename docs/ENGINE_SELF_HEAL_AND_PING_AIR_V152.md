# V152 — The 8.0.6 total-outage root cause, engine self-healing, and the ping readouts' air

A user's 8.0.6 debug log ends `DISCONNECTED`, `xrayAlive=false`, seventeen profiles deep into
failover. This document traces every finding in that log to its cause in this tree, fixes the
causes, and adds the Marble Intelligence layer that repairs the whole *class* automatically.

---

## 1. The critical fault: `sing-box rejected the config` — every session BLOCKED

The log, eight times over, across Netherlands 3, Turkey 15/14/8/7, Netherlands 1 and UK 1/3:

```
sing-box rejected the config: outbounds[3]: dns outbound is deprecated in sing-box 1.11.0
and removed in sing-box 1.13.0, use rule actions instead
```

`SingBoxConfigBuilder.build()` wrote a `{"type":"dns","tag":"dns-out"}` outbound as
`outbounds[3]`. sing-box deprecated that outbound type in 1.11.0 and **removed it in 1.13.0**;
the pinned core is `v1.14.0-extended-2.7.1`, so `sing-box check` refused the config before any
process was spawned. The VPN service recorded the failure, armed the kill switch (`BLOCKED`),
and failover walked to the next profile — which carried the *same* config shape and died on the
*same* line. Seventeen nodes, one bug: the outage was structural, not per-node.

### The fix

- The `dns` outbound is gone from the builder. The route already carried the replacement the
  error message itself names — the `hijack-dns` **rule action** (`route.rules: {protocol: dns,
  action: hijack-dns}`) — so nothing routes to the removed type any more.
- `DNS_OUT_TAG` no longer exists; a test and an integrity invariant now fail the build if the
  removed outbound (or a rule targeting it) ever comes back.

## 2. Marble Intelligence: the engine self-heals

Fixing this bug is necessary and not sufficient — the next core bump that removes another field
would reproduce the same blackout. Two new automatic layers:

### `SingBoxConfigDoctor` — config repair

When the core rejects a config at `check`, the doctor repairs the JSON against the known
sing-box 1.12/1.13 migrations and the manager re-checks before the attempt is allowed to fail:

| Migration | Repair |
| --- | --- |
| removed `dns` outbound (1.13) | dropped from `outbounds` |
| route rule `{protocol: dns, outbound: dns-out}` | converted to the `hijack-dns` rule action |
| route rule pointing at a removed outbound | dead target dropped, matcher falls to `route.final` |
| DNS server `address` key (pre-1.12) | renamed to `server` |

`SingBoxManager.start()` runs the doctor on any `check` rejection, rewrites the config and
re-checks; applied repairs surface as `config-selfheal-note` diagnostics, never silently. The
throwaway URL-test instance gets the same pass.

### Engine-level fault classification — failover that knows when to stop

`SingBoxConfigDoctor.isEngineLevelFault(reason)` recognises config/schema refusals
("rejected the config", "removed in sing-box", …) as **engine-level** faults. In
`MarbleVpnService.startXrayAndForward`, when sing-box fails that way:

1. one retry per session on the **Xray engine** with the *same* profile — the user connects
   instead of reading a schema error;
2. a process-lifetime `singBoxConfigFaultLatch` holds, so later sessions skip straight to Xray
   (`engine-selfheal-latch-active`) instead of replaying the refusal profile by profile;
3. a successful sing-box start (or a process restart) clears the latch and re-arms selection.

Node/network failures are unchanged: those are exactly what per-profile failover exists for,
and misclassifying them would strand the user — the classifier is deliberately narrow.

## 3. DNS: all public DoH endpoints demoted at once

The log shows `context deadline exceeded` storms against `https://1.1.1.1/dns-query`,
`8.8.8.8` and `9.9.9.9`, and all three repeatedly demoted. Two consequences and two fixes:

- **Nothing healthy to promote.** `MarbleIntelligence.STOCK_DOH_RESOLVERS` was exactly those
  three endpoints, so once the operator disrupted the set, the "demote last, promote first"
  order was a shuffle of dead endpoints. The pool now mirrors the ladder
  `CensorshipAwareDnsResolver` already races: **AdGuard, Shecan and Cloudflare's 1.0.0.1
  sibling** — different infrastructure for the demotion engine to promote.
- **Domain egress died while IP egress lived** (`EGRESS startup-observation-inconclusive,
  literalIpHttps=true, domainHttps=false`, 20+ sessions). The sing-box DNS graph now carries a
  `type: local` (system resolver) server, and the node endpoint's *own hostname* resolves
  through it — the tunnel must never depend on an encrypted resolver being alive just to find
  the server it is about to dial through. Literal-IP endpoints need no rule and get none.

The Home connection ping's `measured=0` entries were downstream of all this (no tunnel, no
answer): the FAILED state and its failure-class label already tell that story truthfully, and
the existing `recordResolverSuccess` recovery loop re-promotes an endpoint the moment it
answers again.

## 4. The ping readouts on Home and Servers

Two asks: the latency next to each server was cramped, and its background had to go.

- `ServersPingCapsule` (Servers page) lost its tinted pill fill, border and shape — the
  glyph/number/`ms` triad stands alone in its quality tone, one type-size larger
  (`labelMedium`), with wider internal spacing. The fixed minimum width survives so every
  row's number still column-aligns, and the clearings to the name column and the three-dot
  menu grew.
- `HomeServerLatencySlab` (Home's server box) got the same treatment at its own scale, plus a
  wider gap to the live/selected mark so the two never read as one glued chip.

Both keep their `contentDescription` semantics, and the measured/attempted/unknown distinction
(V121's no-phantom-ping rule) is untouched: `—` stays quiet, `✕` stays red.

## 5. The remaining log items

- **JAVA_CRASH (one, 2026-09-05)** — a single crash on a superseded build with no surviving
  stack; nothing in the current path reproduces it and the crash-report plumbing below is the
  piece that would have captured it.
- **`No log file yet: fatal-crash-pending.txt`** — this line *is* the mechanism working: it
  prints when no fatal crash log exists yet. It is not an error.
- **REASON_16 `killDueToPackageUpdate` ×10** — Android killing the process while the
  package is replaced during development; `MarblePackageReplacedReceiver` already owns the
  post-update resume path.

## Verification

- `scripts/system-integrity-check.py` — **177/177** (8 new invariants: no removed dns
  outbound / hijack-dns action present; the system-resolver bootstrap; the doctor wired into
  the manager; the engine self-heal latch; the self-heal tests pinned; the widened resolver
  pool; both ping readouts tone-only).
- New `SingBoxSelfHealV152Test` — replays the exact 8.0.6 rejection verbatim and asserts the
  repair, the 1.12 key migration, the untouched-modern-config case, no guessing at unreadable
  JSON, and the fault classifier's direction (engine-level in, node-level out).
- `SingBoxCoreV151Test` — updated: the routing-tag test no longer demands the removed
  outbound; two new tests pin the never-write-it rule and the hostname bootstrap.
- `tools/kotlin-structure-check.py`, `tools/compose-scope-check.py`, `bash -n scripts/*.sh` —
  clean.

**Not verified locally:** no JDK/Android SDK/Gradle in this environment (the download hosts
are unreachable), so `:app:testDebugUnitTest` and `:app:compileDebugKotlin` run on the PR's CI,
which is this branch's compile-and-test gate.
