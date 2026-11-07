# MARBLE_DATAPATH_STOP_ACK_V274 — the run/stop handshake that ends the BLOCKED↔CONNECTING loop

> The code lives in `app/src/main/java/com/marbleng/app/core/DatapathLifecyclePolicy.kt` (the
> decisions) and `MarbleVpnService.kt` (the threads), and is pinned by
> `app/src/test/java/com/marbleng/app/core/DatapathStopAckV274Test.kt`.

## ۱. گزارش

The failing night produced this loop, three generations deep, from one root cause:

```
03:28:34.430  VPN | blocked | reason=Xray core stopped | killSwitchHold=true | xrayAlive=false
03:28:34.464  VPN | blocked | reason=hev-socks5-tunnel stopped (0)
03:28:37.948  HEV  | run-enter | hevFd=118
03:28:37.950  HEV  | run-exit | code=0 | runningFlag=true | xrayAlive=true
03:28:37.950  HEV  | exited-before-ready | code=0
```

Three defects, each necessary for the loop to keep turning:

1. **A stop nobody acknowledged.** A run that exited *because Marble asked it to stop* was
   filed with the same failure sentence as a run that died on its own. Every legitimate
   teardown of a failed session opened a second failure generation — a second BLOCKED, a
   second recovery — for the one root cause that started it.

2. **A stale quit that armed the next run's landmine.** `hev_socks5_tunnel_quit()` ORs a
   sticky `SYNC_STOP` bit into HEV's `tsync` whenever it is called while no lifecycle is
   active, and the *next* `hev_socks5_tunnel_run()` consumes that bit and returns `0`
   **without running at all** (read in the pinned tree: `hev-socks5-tunnel`'s `tsync` is
   never reset from the bridge; only a run consumes it). The service's failure path called
   `stopDatapath()` while the finished run's descriptor was still marked owned — one more
   quit into a finalized core, arming the latch the recovery run then stepped on
   **2 milliseconds** later. `run-enter → run-exit` in 2 ms is not a crash; it is the latch.

3. **Labels that pointed at the wrong engine.** The session ran `engine=singbox`, and the
   failure still said `Xray core stopped`, because the reason strings and the `xrayAlive`
   diagnostic key were written when Xray was the only core. A reader (and Bug Finder) was
   sent to debug a core that was not even selected.

## ۲. علت ریشه‌ای (root cause)

`hev_socks5_tunnel_quit()` (the C bridge, in `app/src/main/jni/marbleng_jni.c`) writes the
`SYNC_STOP` bit into the shared `tsync` unconditionally. When the call arrives while no
`hev_socks5_tunnel_run()` is blocking — before the first run, or after one has returned —
nothing consumes the bit, and it sits armed in `tsync`. The next run checks the bit at
entry, runs nothing, performs its normal config-parse/init/fini, and returns `0` in single-
digit milliseconds. The bridge cannot reset `tsync` from outside (the pointer is owned by
the native library's lifecycle), so the only correct move is the one the pinned core itself
makes: **let a run consume the bit, exactly once.**

The loop was therefore self-sustaining: root failure → teardown files a *second* failure →
recovery run trips the armed latch → instant clean exit → filed as *another* failure →
teardown again … The distance between generations was 2 ms of native execution.

## ۳. طراحی (the fix)

### The policy (`DatapathLifecyclePolicy.kt`)

Every decision is pure and device-free:

| decision | rule |
| --- | --- |
| `readingOf(code, stopRequestedForThisRun, elapsedMs, attempt)` | `ACKNOWLEDGED_STOP` (asked + clean), `LATCHED_NOOP` (clean + `< 250 ms` + unasked + within retry budget), `EXITED` (everything else) |
| `LATCHED_NOOP_MAX_MS = 250` | an order of magnitude above the observed 2 ms; three orders below a real session |
| `LATCH_RETRIES = 1` | the latch is consumed by the run that tripped it; a second instant exit is a *real* refusal |
| `datapathStoppedReason(core, code)` | `"hev-socks5-tunnel stopped (0)"` — the engine that actually stopped, and the code the native log can be correlated against |
| `coreStoppedReason(engine)` / `localProxyStoppedReason(engine)` | `"sing-box core stopped"`, never `Xray core stopped` on a sing-box session |
| `isDatapathEcho(reason)` | matches the teardown sentences the product actually emits (the old guard compared against `"HEV stopped"`, a sentence nothing emits) |
| `socksGateVerdict(probe, coreAlive)` | `PROCEED` / `RETRY` / `ABANDON` |

### The service (`MarbleVpnService.kt`)

* **`stopDatapath()` signals only what is in flight.** A `datapathRunEpoch` counter
  identifies each native run; `stopDatapath()` latches `datapathStopTarget = epoch` *only
  when `datapathRunInFlight` is true*, then asks `TunnelShutdownPolicy.stopPlan` for the
  core-specific signal. With the fd owned but no run in flight the stop is **skipped with a
  diagnostic** (`datapath-stop-without-signal`) — that is the arming window closed. No run
  and no fd: `datapath-stop-skipped`.
* **The run answers its own stop.** After the native call returns,
  `stopTarget == myEpoch && code == 0` ⇒ `stopped-by-request` — a diagnostic, **not a
  failure**. This is the acknowledgement.
* **A latched no-op is re-entered exactly once.** `LATCHED_NOOP` loops back into the native
  run under a fresh epoch (`stop-latch-consumed`). If the retry also returns instantly and
  clean, the failure says so: `reason` + `" • its stop latch consumed the run twice"` —
  latch exhaustion, a real failure with a precise sentence.
* **A stop that lands before the native entry is answered by abandonment.** The worker
  re-checks `isRouteCurrent` immediately before entering the native run; a stop that
  arrived in that window shows up as `run-abandoned-before-native` and the run returns
  without touching any core state — which a quit in that window never could be.
* **The SOCKS gate.** Before the datapath dials `127.0.0.1:<port>`, `awaitSocksInbound`
  polls the core's loopback SOCKS listener (`SOCKS_GATE_TIMEOUT_MS = 2500 ms`, poll 200 ms,
  connect 250 ms) — placed **after** the config-ready/`isCurrent` check and **before** the
  app/state bookkeeping and the ready-timer, so a dead core never becomes a datapath that
  logs a burst of `connect: connection refused` that reads like a server fault. Verdicts:
  proceed, retry while the core lives, abandon when it is gone.
* **`handleFailure` ignores echoes.** While a recovery is already scheduled,
  `DatapathLifecyclePolicy.isDatapathEcho(reason)` keeps the teardown echoes of the run
  that just failed from opening a second generation.
* **Every blocked/failure diagnostic is engine-aware.** The blocked event carries
  `coreAlive` (not `xrayAlive`), the engine label, and — for sing-box —
  `singBox.coreDeathEvidence()`: `"process exited with code N"` / `"process is no longer
  running"` / absent while alive. `SingBoxProcessSession.exitCode` is the new evidence
  source.
* **Bug Finder reads the loop.** A "Recent failure timeline" check counts
  `blocked`/`failure-generation` events and `exited-before-ready` HEV exits after the last
  clean stop: **FAIL** if the app sits disconnected with evidence the loop turned, WARN if
  it recovered, PASS otherwise. The `cleanHevExit` filter now accepts a tun2socks run-exit
  with `code=0` when a stop was requested (or the old flags say nothing else is alive) —
  an acknowledged stop is clean by definition.

## ۴. چی عمداً انجام نشد (what was deliberately not done)

* **No bridge-side `tsync` reset.** The `tsync` memory belongs to the native library's
  lifecycle; poking it from the bridge would be a data race with the run loop. The latch is
  consumed the only safe way: by a run.
* **No retry loop on the latch.** One retry is provably enough (the consuming run empties
  the bit); a loop would turn a genuinely refusing core into a spin.
* **No change to the descriptor rules of V223/V224.** The fd adoption/release choreography,
  the dup rules and `stopAndWait()` are untouched; V274 only narrows *when a stop is
  signalled* and *how an exit is read*. The V223 integrity pin on the `stopPlan` call was
  co-updated to the narrowed call (`datapathActive = true` inside the in-flight guard).
* **No timeout classification.** A run that blocks and returns cleanly after hours is
  `EXITED` like any other; duration only distinguishes the latch, never invents a hang.

## ۵. جدول اجرا (verification)

`DatapathStopAckV274Test` (JVM, no device) pins: asked-stop ⇒ ACK; slow clean unasked ⇒
EXITED; the observed 2 ms shape ⇒ LATCHED_NOOP; the latch retried exactly once then
EXITED+latchExhausted; non-zero exits never latch; asked+non-zero ⇒ EXITED; the thresholds
(`≤ 250 ms`, `LATCH_RETRIES == 1`); engine-named reasons (`"sing-box core stopped"`,
`"hev-socks5-tunnel stopped (0)"`, `"tun2socks stopped (-7)"`); the echo guard against the
real sentences and against root-cause sentences that must never be swallowed; the SOCKS
gate truth table; the marker.

`scripts/system-integrity-check.py` carries the source-level pins (service ↔ policy ↔
test ↔ this chapter).

## ۶. تست روی دستگاه (device expectation)

The reproduction (connect, kill the underlying network, let a generation fail) should now
read: one `failure-generation`, one `datapath-stop` (only if a run was actually in
flight), one `stopped-by-request` **diag**, one recovery run that either blocks (latch
never armed) or consumes it once (`stop-latch-consumed`) and then blocks — and **no second
`failure-generation` for the same root cause**.
