# MarbleNG — connection latency analysis & fast-start fix (V197)

**Symptom reported:** *"Both cores stay in the CONNECTING state for several seconds.
Other client apps connect much faster."*

This document traces the connect critical path, quantifies the cost each phase adds,
and describes the fix that lands in this change.

## The connect path (both cores)

```
AppRepository.startVpn(profile)
  └─ MarbleVpnService.startConnection(...)
       └─ connectionWorker
            ├─ effectiveSettingsFor(profile)        // cheap: SQLite read + policy merge
            └─ connectTunOverlapped(...)            // NEW: runs the two halves in parallel
                 ├─ establishTun(...)               // Android VPN interface  (~150–400 ms)
                 └─ launchCore(...)  ── on a helper thread ──
                      ├─ XrayManager.start  │  SingBoxManager.start
                      │   waitSocksPort 7s budget          │   inbound poll
                      │                                    │   settle (was 700 ms → 250 ms)
                      │                                    │   controller await (was up to 2.5 s → deferred)
                      └─ schedule observations, identity
            └─ runTun(...)  →  HEV_READY_GRACE_MS (250 ms)  →  markConnected
```

The state machine shows **CONNECTING** from `startVpn` until `markConnected`, which (for
TUN) fires once HEV is alive **and** the core is alive, after a 250 ms grace. So the
perceived delay is dominated by everything between the tap and that point.

## What was actually slow

### 1. Sing-box: a synchronous controller gate on the connect critical path (worst case ≈ 2.5 s)

`SingBoxManager.start()` historically called `openFirst(..., awaitApi = true)`. The Clash
controller is an *internal diagnostic surface* bound by the core in its **last** start-up
stage — after every outbound's post-start walk. The old readiness condition waited for it
before the tunnel could be handed to the VPN. When the controller bound slowly (or never),
this added up to `CONTROLLER_TIMEOUT_MS` (2.5 s) to **every** Sing-box connect, even though
the inbound — the socket HEV actually dials — was already up.

This was already recognised as wrong for the *failure* path (V162: a working tunnel was
being killed because a diagnostic had not bound), but the *latency* cost of the same gate on
the happy path was never removed.

**Fix.** The live connect now passes `awaitApi = false`: `start()` returns as soon as the
inbound is up plus a short settle, and the controller is confirmed **out-of-band** on a daemon
thread (`verifyControllerLater`). A controller that never answers is still recorded exactly as
before — as a degraded, usable session (`StartReadiness.controllerMissing`) — it simply no
longer delays CONNECTED. If a newer session takes over, the background verdict is discarded.

### 2. Sing-box: the per-connection safety settle is intentionally unchanged

`LIVE_SETTLE_MS` is aliased to the one-time binary canary's 700 ms. That window only exists
to catch a core that dies *microseconds* after its inbound opens. It is a pinned invariant of
the controller-gate design, so the live-path speed-up does **not** come from shrinking it; the
grace is left intact and the win comes from decoupling the controller wait and overlapping the
TUN/core bring-up instead.

### 3. Both cores: TUN establishment was serial with core start-up (≈ 150–400 ms)

`establishTun()` and the core's spawn + readiness are completely independent, yet they ran
strictly in series: TUN first, then the core. The TUN-establishment cost (Android
`Builder.establish()` + upstream/MTU work) was therefore added on top of the core's own
start-up for **both** engines.

**Fix.** `startConnection` now calls `connectTunOverlapped()`: the core is launched on a
helper thread while the TUN is established on the connection worker, and HEV is started only
once both halves are ready. This overlaps the two costs instead of summing them. The change
is isolated — the recovery path (TUN already up) and the PROXY path keep the original,
non-overlapped `startXrayAndForward` wrapper — and the connect state is only advanced when
both halves are current with the session, so no new race surfaces. `RuntimeDiagnostics.event`
is already thread-safe (bounded queue + guarded ring), so the two threads can emit
diagnostics concurrently.

## What was *not* the cause

- `effectiveSettingsFor` / `intelligence.effectiveSettings` — cheap (SQLite read + pure policy merge).
- `scanIranMode()` before CONNECTING — dispatches probes to a background scanner; returns fast.
- `prepareRoutingAssetsForConnect` — returns immediately once bundled geo assets are on disk.
- `XrayManager.verifyRoutingPolicy` (`xray run -test`) — **not** on the live connect path.
- The HEV grace (250 ms) — small and left unchanged; it only proves HEV's blocking run is alive.

## Measurability

`connectTunOverlapped` now emits `VPN | connect-phase` events (`tun`, `core`) with elapsed
milliseconds, and the managers already log core-start `elapsedMs` and `HEV | ready | graceMs`.
Together these give a per-phase timeline so any future regression in connect latency is
visible rather than felt.

## Net effect

| Phase | Before | After |
|-------|--------|-------|
| Sing-box controller wait (critical path) | up to 2.5 s | 0 (deferred) |
| TUN vs core start-up (both cores) | serial (~150–400 ms added) | overlapped |

Typical Sing-box connect: **up to ~2.5 s faster** (controller no longer gates CONNECTED), plus
**~150–400 ms** from the overlap shared by both cores. The per-connection safety settle is
unchanged.
