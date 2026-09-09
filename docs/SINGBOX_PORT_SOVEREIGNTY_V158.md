# SINGBOX PORT SOVEREIGNTY & LOCAL-CLIENT NOISE — V158

**Why one device spent eight minutes refused with `bind: address already in use` while both
engines reported `alive=false`, why the refusals walked three readers per node, and why local
clients aborting their own SOCKS handshake were counted as Bug Finder failures.**

Engine pin: unchanged — `shtorm-7/sing-box-extended v1.14.0-extended-2.7.1` @
`217f77643f19e915ea38ba2a9ac7e28e22a0cec5`, built with the V157 backport
(`-marble.crash-fix.1`). Nothing in this chapter touches the core.

---

## 1. The evidence

One device, 2026-09-08 → 2026-09-09. Three fault classes, one root architecture.

```
07:00:31  core-start: exited 2: … panic … [signal SIGSEGV … pc=0x5c0a469b6c]   mode=tun
07:00:56  core-start: exited 2: … panic … [signal SIGSEGV … pc=0x628ff65b6c]
08:16:17  core-start: exited 2: … panic … [signal SIGSEGV … pc=0x5e5d6e1b6c]

09:18:08  core-start: exited 1: … FATAL start service: start inbound/mixed[socks-in]:
          listen tcp 127.0.0.1:10808: bind: address already in use
          → VPN | blocked | killSwitchHold=true | xrayAlive=false | hevFd=-1 | tunOpen=true
09:18:20  (same)     09:25:09 (same)     09:26:10 (same — four attempts, eight minutes)
09:26:14  core-start: exited 2: … SIGSEGV …                             mode=proxy
09:18:33  BUGFINDER | scan-finish | failures=1

2026-09-09
01:59:01  ROUTE | probe-failed | count=1 | profile=729909d5778d
01:59:32  ROUTE | probe-failed | count=1
02:04:54  ERROR [4288097813 2.49s] inbound/mixed[socks-in]: process connection from
          127.0.0.1:48528: read fqdn: unexpected EOF      ← six of these in six seconds
          …through 02:05:00, ports 48478–48580, durations 1.50s/2.49s/5.0s
```

Read together:

* The three `exited 2` SIGSEGVs at 07:00–08:16 and the one at 09:26:14 are **V157's fault on a
  pre-fix build** — the app-UID nil-interface-monitor panic in the core's `direct` outbound, the
  `packages.xml` WARN riding above every one of them as evidence, never as cause. V157's compiled
  core, self-test and classification already answer this class; nothing in this chapter re-fixes
  it. They are in this document because they bracket the *new* fault in the same window.
* The `bind: address already in use` cluster is the new fault. Every event carries
  `alive=false` / `xrayAlive=false`: **neither manager owned a live child.** The socket belonged
  to a process nothing tracked — an orphaned core from an earlier app process that Android killed
  (the update path documented in V133's receiver: `killDueToPackageUpdate`), or a child whose
  handle a superseded start dropped. `SingBoxManager.stop()` and `XrayManager.stop()` can only
  stop processes they remember. For eight minutes, every connect paid the full
  spawn → FATAL → BLOCKED round trip, and Bug Finder recorded `failures=1` for each scan.
* The `read fqdn: unexpected EOF` lines are **not MarbleNG's** and **not failures** — see §4.
* `ROUTE | probe-failed | count=1` is the advisory latch doing its job on a genuinely degraded
  exit (the same window the fqdn aborts came from): it needs `PROBE_FAILURES_BEFORE_RECOVERY = 4`
  consecutive misses *plus* independent confirmation before it touches the route, and `count`
  resetting to 1 between the two events means real traffic was still moving. No defect; the
  recovery ladder behaved exactly as V135 specified.

## 2. Why the port cluster was unavoidable from where V157 stood

### 2.1 The holder was outside the process model

`MarbleVpnService.coreStop()` stops both managers, and both managers stop what they track.
Nothing on the connect path ever asked the operating system **who actually holds
127.0.0.1:10808**. Android gives an app every primitive needed to answer that question about its
*own UID* — `/proc/net/tcp{,6}` maps a LISTEN port to a socket inode, `/proc/<pid>/fd/<fd>`
symlinks (`socket:[inode]`) map the inode back to a pid, and same-UID processes can always be
signalled — and before V158 the app used none of them. The stale core sat on the port until it
happened to die, and the product's only answer was a fatal message about a bind.

### 2.2 A bind conflict walked the reader candidates

`SingBoxManager.isConfigRefusal` classifies by prefix, and the core's bind death arrives wearing
the `core-start: exited 1:` prefix — the same prefix as a genuine schema refusal. So for every
profile, `openFirst` fed all three candidate spellings of the node to a core that was doomed to
die on the same port: three spawns, three FATALs, three refusals recorded as notes, for a fault
that is identical for every candidate. V157's own docstring had already said a port that will not
bind "is not a reason to try the same node in another spelling" — the code had never been made to
agree.

### 2.3 The blocked state mis-attributed the owner

`handleFailure(faultClass = …)` knew exactly two owners: `Core cannot run on this device` and
`Core/configuration error`. A port another app holds is neither — the user was told, in effect,
that the profile they tapped was misconfigured.

### 2.4 Xray had a preflight; sing-box did not

`XrayManager.start` refuses on a held port *before* spawning (a good instinct — wrong owner name
aside). `SingBoxManager.start` had no preflight at all: the first evidence of a held port was the
child's own `FATAL`. Neither one could *recover*, though — both refused forever, every retry,
because neither could name or free the holder.

## 3. What changed

### 3.1 `CorePortGuard` (new) — find the holder, reap ours, never touch theirs

One pure object, a `[Probe]` interface, and an `androidProbe()` production implementation:

* **Attribute.** `parseListenerInodes` reads the `/proc/net/tcp{,6}` table (LISTEN state `0A`,
  hex port, inode field 9 — the kernel's `tcp4/6_seq_file_seq` column order);
  `findHolderPids` matches `socket:[inode]` entries in each candidate pid's `fd` links.
* **Own before signal.** A holder is reaped only when **both** checks pass: its
  `/proc/<pid>/status` UID equals ours **and** its `cmdline[0]` is exactly
  `nativeLibraryDir/libsingbox.so` or `libxray.so`. Anything else — root's sshd, another app, a
  zombie — is named in the evidence and never signalled. Every unreadable `/proc` lookup is
  skipped; an unattributed process is never a target.
* **Escalate.** SIGTERM (a healthy core shuts down and closes its sockets), bounded wait,
  SIGKILL, bounded wait. The whole reclaim runs inside a 4 s budget, and the common case — port
  free — costs one bind probe.
* **Fail honestly.** If the port still is not ours, the reason is
  `core-port: 127.0.0.1:<port> is already in use (held by pid=… binary=…) — …` — the
  `core-port:` prefix every local-fault classifier already speaks.

### 3.2 Both engines reclaim before they spawn

`SingBoxManager.start` runs the reclaim after the self-test and before the first child;
`XrayManager.start` runs it behind its existing `portAvailable` preflight instead of refusing
forever. A reclaimed port adds a note to `lastSelfHealNotes`, so the Engine page says what
happened instead of the start silently succeeding. `SingBoxManager.stop` now records
`lastStopEvidence` from the session — "the previous core did not exit within the stop budget" is
the sentence that explains the *next* start's reclaim, and `SingBoxProcessSession.stop` returns
its exit verdict instead of leaving "stop returned" to imply "port free".

### 3.3 The walk is closed at the source

`isConfigRefusal` returns false for a bind conflict (raw `core-start:` form included), and the
catch site in `start` rewrites the core's FATAL into the `core-port:` prefix. A bind conflict now
costs exactly one spawn, and `CoreFailurePolicy.isLocal`, `ProbeLocalFaultGate.isSweepFatal` and
the history boundary all classify it as a device fault with no further work — the same pattern
V157 used for `core-crash:`.

### 3.4 The blocked state names the owner

`faultClass` gained a third arm: `SingBoxAndroidRuntime.isPortBindConflict(coreStartError)` →
`Local port in use`, for both engines. The reason text already carries the holder and the
remediation (`PORT_REMEDIATION`): change the local SOCKS port, stop the other app, or restart.

## 4. The `read fqdn: unexpected EOF` lines are client noise — now provably classified

The error shape reads through the server's own source: `sing`'s SOCKS5 request reader
(`common/metadata/serializer.go`) wraps `ReadSockString` as `read fqdn`, and
`io.ReadFull`'s `unexpected EOF` means the client sent `VER CMD RSV ATYP=03` **plus the length
byte**, then disconnected inside the name. Every producer on the device rules itself out:

| Candidate | Why it is not the client |
| --- | --- |
| MarbleNG's `SocksHttpClient` | writes greeting and request in one buffered flush; a mid-fqdn death is impossible from its byte stream |
| hev-socks5-tunnel | the SOCKS address comes from the lwIP PCB — literal IPv4/IPv6 only, never ATYP=domain |
| connect+close liveness probes (`listening()`, Bug Finder, `waitSocksPort`) | zero-byte connect aborts at the *first* byte, which the mixed inbound logs at **DEBUG** (`E.IsClosedOrCanceled`) — invisible at the configured `warn` level |
| Xray engine | the log line names `inbound/mixed[socks-in]`, sing-box's tag |

What remains is exactly one class: **a local app using the loopback proxy that aborted its own
handshake** — six source ports allocated in one kernel burst (48478→48580) and logged out of
order, so the clients were concurrent, not MarbleNG's sequential probe loops. The 1.50s and 5.0s
durations are client-side give-up budgets, not server faults.

The product defect was that `BugFinder.problems()` matches the bare keyword `error`, and sing-box
logs these lines at ERROR level — so every local client abort counted as a scan failure. Now
`CoreLogNoise.isLocalClientHandshakeAbort` recognizes the family (loopback source, local inbound
tag, client-side read failure inside the handshake) and the lines are excluded from failures —
while the `SINGBOX EXTENDED CORE STATUS` section reports `localClientHandshakeAborts=N (benign…)`
as evidence, because "some local app is half-using the proxy" is real information. Everything
else — panic, FATAL, bind conflict, dial refusal — still counts exactly as before.

## 5. What this does not claim

* It does not make the app able to kill another app's process. A port held by a foreign process
  still fails the start — but now with the holder named, the owner attributed, and the remediation
  spelled out, in one attempt instead of four.
* It does not close the TOCTOU window between reclaim and bind. A foreign process can still win
  the race; the catch site rewrites the core's own FATAL into the same `core-port:` fault when it
  does.
* It does not say the fqdn aborts never matter. If a *remote* source produced the same shape, the
  classifier still counts it — the benign path requires `127.0.0.1` and a local inbound tag.
* It does not change the ROUTE probe ladder. `probe-failed | count=1` is the advisory latch
  behaving as designed on a degraded exit.
* It does not claim the V157 crash class is newly fixed. The SIGSEGVs in this window are a
  pre-fix build; V157's core, canary and classification remain the whole answer to them.

## 6. Pinned by

`app/src/test/java/com/marbleng/app/core/SingBoxPortSovereigntyV158Test.kt`

* `/proc/net/tcp` parsing: LISTEN-only, exact-port, header and non-listen decoys rejected;
* the stale core is found, TERM'd, and the start proceeds — escalation to SIGKILL for a
  TERM-ignoring holder;
* a root-owned or non-core holder is named in the `core-port:` evidence and **never signalled**;
* a free port costs one probe and no signals;
* the exact 09:18 bind-conflict reason: not a config refusal (no reader walk), rewritten prefix
  is a local fault everywhere (`CoreFailurePolicy`, `ProbeLocalFaultGate`), carries
  `PORT_REMEDIATION`, and is not a crash;
* the six verbatim 02:04–02:05 `read fqdn` lines are benign, while the FATAL bind line, the panic,
  a remote-source abort and a server-side dial refusal all stay counted;
* `SingBoxProcessSession.stop` proves its verdict on real processes: graceful exit and the
  `trap '' TERM` escalation both end in a dead child and a returned verdict.

Also: the V158 invariants in `scripts/system-integrity-check.py` (the guard exists with a real
`/proc` implementation, both engines call it, the walk is closed, Bug Finder carries the benign
classifier, and the chapter's tests exist), all run in `verify.yml` before gradle.
