# V278 — automatic draft releases, and a debug report that tells the truth

`MARBLE_AUTO_RELEASE_V278` · `MARBLE_HEV_STACK_HEADROOM_V278` · `MARBLE_LIVE_STREAM_TRUTH_V278` ·
`MARBLE_NAMED_WORKERS_V278` · `MARBLE_STARTUP_DEDUP_V278`

This pass answers a thirteen-point reading of one debug report (session of 2026-11-11 18:16) and
one request: *every merge should pick its own version and build the signed APKs as a draft.*

## 1. Every merge becomes a draft release

`.github/workflows/auto-release.yml` runs on every push to `main` (a merged pull request is one).

| step | what decides it |
| --- | --- |
| base | highest `vX.Y.Z` among tags and releases, drafts included |
| weight | lines added + removed since the base in what ships in the APK (docs, Markdown, tests, CI, fixtures excluded) |
| level | `< 3000` patch · `3000–19999` minor · `≥ 20000` major |
| versionName | the decimal ladder the project already uses: `11.6.9 → 11.7.0`, `11.9.9 → 12.0.0` |
| versionCode | `max(Unix time, highest recorded versionCode + 1)` |

The six releases before this pass weighed 134, 237, 1246, 1728, 0 and 2491 lines — all patches
under these thresholds, as they were by hand.

The workflow then dispatches **Build signed Android APKs** with the two values and waits for it.
The build is unchanged: same collision guard, same signing, same five APKs, and the release it
creates is still a **draft** that a person publishes. Releases are serialized; merges that land
during a build are folded into the next one. `[skip release]` in a merge commit message skips it,
and the manual *Run workflow* button still works.

The rule is `scripts/auto-release-plan.sh`; `scripts/test-auto-release-plan.sh` proves it offline
against a throwaway repository and a fake `gh`, on every pull request.

## 2. The report, point by point

| # | finding | verdict | change |
| --- | --- | --- | --- |
| 1 | native `SIGSEGV / SEGV_ACCERR`, "stack pointer is not in a rw map" | **mitigated, not proven** | see §3 |
| 2 | "LIVE STREAM" replays days-old core logs | **defect, fixed** | see §4 |
| 3 | tun2socks dials `127.0.0.1:10808` and is refused | **stale evidence** | the lines are the 5 November backlog replayed by #2; the SOCKS-listener gate and the stop acknowledgement landed in V274 (7 November), and the 11 November session shows TUN at 18:16:31.494, SOCKS ready at 18:16:33.903, as designed |
| 4 | CONNECTED, then DISCONNECTING 930 ms later | **not a defect in the log** | `com.marbleng.STOP` is only sent by `AppRepository.stopVpn()` and the notification's Disconnect action, i.e. by a person: the connect button, the quick tile, the notification, or changing the connection mode or tunnel engine in Settings while connected, or Bug Finder's reset. Recovery and failover never send it |
| 5 | CPU and thread count while disconnected | **needs one more log** | the heartbeat now names thread families and which of them are runnable; the service and repository pools are named instead of `pool-N-thread-M` |
| 6 | Iran-mode and warmup run twice at start | **defect, fixed** | init and the first network callback both ran them; `RecentKeyGate` lets one through |
| 7 | datapath stop events have no session | **defect, fixed** | `cleanupRuntime` cleared the session before stopping; the stop now carries it |
| 8 | stale probe results after disconnect | by design | they are measured, recognised as stale and discarded |
| 9 | update check fails on DNS while offline | **noise, fixed** | reported as `check-deferred-offline`; retried on next foreground |
| 10 | some IPv6 endpoints `UNREACHABLE` | by design | no IPv6 route on that underlay is an answer, not an error |
| 11 | HEV native log is empty | by design | HEV logs at `error`; an empty file means it reported none (a SIGSEGV writes nothing) |
| 12 | Xray warns that Shadowsocks is deprecated | upstream notice | printed for any profile that uses it; nothing to change in the app |
| 13 | update kill, `staleArtifactsRemoved=none` | by design | `none` means no stale runtime file existed to remove |

## 3. The native crash

HEV runs every session on a coroutine whose stack `hev-task-system` allocates with `mmap` and
protects with a `PROT_NONE` guard page (`CONFIG_STACK_OVERFLOW_DETECTION`, the Android build's
setting). Running off such a stack produces exactly the reported record — `SEGV_ACCERR`, a stack
pointer outside any read-write mapping — on the thread blocked in `HevTunnel.run`, which was the
service's unnamed `connectionWorker`, i.e. a `pool-N-thread-1`.

Marble configured `task-stack-size: 86016`. With the 64 KiB TCP buffer that leaves 20 KiB for
everything beneath it — upstream's floor (`TASK_STACK_SIZE + tcp_buffer_size` in `hev-config.c`),
on a pin that follows upstream's unreleased `snapshot`. The stacks are committed page by page, so
headroom costs address space, not memory: sessions now get **256 KiB on 64-bit** and **128 KiB on
32-bit** processes (`HevTunnelPolicy.taskStackSize`).

What this does not claim: the report also names `marble-endpoint` and `android.bg`, threads that
never run HEV, and the report held no backtrace — Android hands a native crash over as a protobuf
tombstone, which the report printed as text and therefore as noise. The exit snapshot now prints
the tombstone's readable fields (thread, cause, libraries, frames), and the datapath thread is
called `marble-vpn-data`. If the crash returns, the next report says where.

## 4. The live stream

The mirror that copies `xray.log`, `hev-native.log` and `tun2socks.log` into a debug session kept
its offsets from zero, so enabling debug mode replayed each file from its first byte under
"LIVE STREAM", stamped with the current time. That is how a tun2socks log appeared in a HEV
session. A debug session now enters existing files at their end (`NativeLogMirror`), reads files
that appear or rotate from their start, and writes one `native-log-backlog-skipped` line per file
saying how many bytes it did not replay and when they were last written.

## 5. Tests

`DiagnosticsTruthV278Test` (mirror offsets, tombstone strings, start-up gate, thread census, HEV
headroom), `SocketFlightV168Test` (the emitted `task-stack-size`), and
`scripts/test-auto-release-plan.sh`.
