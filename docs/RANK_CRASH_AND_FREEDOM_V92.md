# MarbleNG V92 — Rank reader crash + serverless Freedom hardening

## 1. Rank crash: `read interrupted by close() on another thread`

### Observed stack

```
java.io.InterruptedIOException: read interrupted by close() on another thread
    at ... BufferedReader.readLine
    at ... Kotlin LinesSequence / Sequence iterator (thread `marble-rank-reader`)
    at java.lang.Thread.run
```

### Root cause

`PattRankEngine` drains `marble-rank` output on a daemon reader thread using
`process.inputStream.bufferedReader().useLines { … }`. When the native helper finishes,
times out, or is reaped by `process.destroy()` / `destroyForcibly()`, the pipe is closed
while that thread is blocked in `readLine()`. Java surfaces the close as
`InterruptedIOException` from inside the `Sequence` iterator, the exception escapes the
`Thread` body, and Android's default uncaught handler terminates the process.

The app's `RuntimeDiagnostics` crash handler therefore records the tombstone shown in the
report, but the process still dies — it is not a recoverable task error because the failure
happens on the reader thread, not inside `task { … }`.

### Fix

- The reader `Thread` now wraps the whole stream-drain loop in `try/catch`; a closed/read
  interrupted pipe is recorded as `reader-closed`/`rank-reader-closed` and exits quietly.
- Per-line event handling (JSON parsing, scoring, callbacks) is also isolated so a single bad
  event can no longer turn into a process-killing uncaught error.
- After `process.waitFor`, the reader is interrupted first, then force-closed and awaited with
  the same `CountDownLatch` so no unhandled reader thread leaks into the next Rank run.
- `process.waitFor` is now interruption-safe.
- The remaining uncaught `Thread.sleep` paths in the Rank flow were made interruption-safe
  (`AppRepository.smartRankRun` network-change restart and
  `MarbleFreedomSmartRanker.probeProfile` retry backoff).

## 2. Other Rank-crash/validity causes found

- `XrayConfigHardener` rejected a **freedom-only serverless config with no fragmenting
  hop** (the `NORMAL` tier / a hand-imported serverless JSON) with `No proxy outbound`.
  `harden()` and `hardenForDelayTest()` now fall back to a freedom/direct outbound as the
  exit when no non-infrastructure proxy exists.
- Plain serverless Freedom exits now get the same outbound-level address-family plan as the
  official XTLS config, so they also resolve through Xray's encrypted DNS module instead of
  the poisoned OS resolver.

## 3. Marble Freedom serverless JSON hardening

Re-verified against the official sources:

- `XTLS/Xray-examples` → `Serverless-for-Iran/serverless_for_Iran.jsonc`
- `XTLS/Xray-core` discussion #5969 (2026 field notes: MCI / Irancell / Shatel reassemble
  plain TCP/TLS fragmentation)

Changes:

- **`targetStrategy` support.** The official serverless config gives the dedicated
  `udp-noises` outbound `targetStrategy: ForceIPv6v4`. Marble now emits the same field in
  `ServerlessFreedomEngine` and in hardened Freedom/direct hops. UDP dials are planned with
  `tcpTransport = false`, so they get a deterministic `ForceIPv6v4` / `ForceIPv4` plan instead
  of a TCP Happy Eyeballs block.
- **Plain Freedom exits** (`NORMAL` tier) carry `domainStrategy` / `targetStrategy` too.
- Existing anti-DPI recipes are unchanged: no `tlshello` default (server RST), 2-hop default,
  per-operator steel chains, QUIC/UDP-443 TCP fallback, poison range blocking, pinned DoH.

## 4. Files changed

- `app/src/main/java/com/marbleng/app/core/PattRankEngine.kt`
- `app/src/main/java/com/marbleng/app/core/XrayConfigHardener.kt`
- `app/src/main/java/com/marbleng/app/core/ServerlessFreedomEngine.kt`
- `app/src/main/java/com/marbleng/app/core/MarbleFreedomSmartRanker.kt`
- `app/src/main/java/com/marbleng/app/AppRepository.kt`
- `app/src/test/java/com/marbleng/app/core/XrayConfigHardenerTest.kt`
