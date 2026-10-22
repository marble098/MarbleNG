# THE DISCONNECT ABORT — V223

**Why the app died with `SIGABRT` (status 6) a few hundred milliseconds after tapping disconnect while
the tun2socks datapath was running, and what changed so it cannot happen again.**

Marker: `MARBLE_TUN2SOCKS_SHUTDOWN_V223`. Pins: unchanged — `core-lock.json` (Xray, HEV,
sing-box extended, tun2socks v2.7.0). No core binary moved in this chapter.

> **Follow-up (V224):** one assumption in this chapter — that removing the wrapper's own close
> meant nothing native closed the service's descriptor — was wrong: `Stack.Wait()` closes the link
> endpoint, and the pinned tun2socks `FD.Close()` closes the fd it was opened with. That was the
> `fdsan: double-close of file descriptor 148` disconnect abort. The correction (borrowed
> descriptors are duplicated at the JNI boundary; each datapath closes only its own dup) is
> `docs/FDSAN_DOUBLE_CLOSE_V224.md`. Everything else in this chapter still stands.

---

## خلاصه (Persian summary)

- **چرا فقط روی tun2socks؟** در مسیر HEV، خودِ MarbleNG تنها کسی است که descriptor را می‌بندد
  (`hev_socks5_tunnel_fini()` برای descriptor بیرونی هیچ کاری نمی‌کند). در مسیر tun2socks، کد Go هم
  همان عدد را می‌بست. یعنی یک عدد دوبار بسته می‌شد، با فاصلهٔ چند صد میلی‌ثانیه — همان بازه‌ای که
  سرویس مشغول نوشتن وضعیت و نوتیفیکیشن است و مرتب descriptor باز و بسته می‌کند. اگر بین دو `close`
  عدد آزادشده به یک descriptor زندهٔ دیگر برسد، بار دوم آن را می‌کُشد. نتیجه: `abort()` بومی،
  بدون exception جاوا و بدون tombstone — دقیقاً همان چیزی که در لاگ دیدی.
- **قرارداد gVisor هم شکسته بود.** endpoint از نوع `fdbased` صریحاً می‌گوید descriptor را تصاحب
  نمی‌کند و باید تا پایان `Wait()` باز بماند. نسخهٔ قبلی، پیش از آنکه dispatcherها متوقف شوند،
  descriptor را می‌بست و هیچ‌وقت `Attach(nil)`/`Wait()` را صدا نمی‌زد؛ در نتیجه netstack می‌توانست
  از شماره‌ای که به یک descriptor دیگر رسیده باشد `readv` کند.
- **یک باگ دوم هم در همان مسیر بود:** HEV را هم در نشست tun2socks متوقف می‌کردیم. `hev_socks5_tunnel_stop()`
  یک بیت `SYNC_STOP` ماندگار می‌گذارد و اگر `SYNC_SEND` هنوز ست باشد ولی socketpair رویداد بسته شده
  باشد، `assert(res > 0 && "socks5 tunnel write event")` شکست می‌خورد و `abort()` می‌کند. حالا هر
  نشست فقط دیتاپث خودش را متوقف می‌کند.
- **`status=6` حالا خوانده می‌شود.** Bug Finder و گزارش اشکال‌یاب، رکورد `SIGNALED` را به نام سیگنال
  و معنی آن ترجمه می‌کنند (`SIGABRT(6) • native abort()…`) و دیگر یک عدد خالی نمی‌نویسند.

---

## 1. The evidence, read properly

The report carried no Java `FATAL EXCEPTION`, no `SIGSEGV`, no tombstone and no
`fatal-crash-pending.txt`, and the two records in PROCESS EXIT HISTORY were:

```
2026-10-22T21:35:53.461Z  reason=SIGNALED (2)  status=6
2026-10-22T21:36:01.261Z  reason=SIGNALED (2)  status=6
```

with the surrounding runtime log

```
21:35:53.084  VPN | shutdown-begin   session=mvk1rkmj-465d6d
21:35:53.086  TUN2SOCKS | run-exit   session=mvk1rkmj-465d6d  code=0
21:35:53.461  <process exit: SIGNALED status=6>
21:35:58.764  <new process 10878>
```

Two facts follow from that, and they set the whole investigation:

- **`status` on a `SIGNALED` record is the signal number**, so `status=6` is `SIGABRT`. That is
  `abort()` — libc's `assert`, `__stack_chk_fail`, a heap check, or ART's `LOG(FATAL)`/JNI check.
  A Go panic or a Go `runtime.throw` exits with status 2 (`REASON_EXIT_SELF`), and a native fault
  exits with signal 11. Neither happened here.
- **Nothing in the JVM ran.** An `abort()` never unwinds Java frames, which is why
  `fatal-crash-pending.txt` was empty and why the report had no stack of ours: the tombstone, if
  the device kept one, is in logcat for that pid under `Abort message`.

The remaining question was *which* native `abort()` — and the answer has to be in code that only
the tun2socks datapath runs, because the HEV datapath, which is what every previous release
actually shipped traffic on, never produced this record.

## 2. What only the tun2socks datapath did (V222)

### 2.1 Two owners closed one descriptor

`native/tun2socks/main.go` ended `marble_t2s_run` with `FD.Close()`:

```go
func (f *FD) Close() {
    if !f.closed {
        defer f.LinkEndpoint.Close()
        _ = unix.Close(f.fd)      // ← closes a descriptor this library does not own
        f.closed = true
    }
}
```

and `MarbleVpnService.closeHevFd()` closed the same number again:

```kotlin
private fun closeHevFd() {
    val fd = hevFd
    hevFd = -1
    if (fd >= 0) runCatching { ParcelFileDescriptor.adoptFd(fd).close() }
}
```

HEV never does this. On an externally provided descriptor (`extern_tun_fd >= 0`) it sets
`tun_fd_local = 0`, and its `tunnel_fini()` starts with `if (!tun_fd_local) return;`. So on the HEV
datapath the service is the one and only closer — and on the tun2socks datapath it was one of two.

The two closes are not adjacent. The Go side closes at the end of `run()`, the service closes in
`cleanupRuntime`/`handleFailure` after `coreStop()`, `releaseLowLatencyWifiLock()` and the repo
writes — the 300–400 ms that separate `run-exit` from the exit record in the report. In that
window the process opens and closes sockets, log files, pipes and `DataStore` files; the freed
number is handed out again quickly, and the second `close()` destroys a **live, unrelated**
descriptor. What the victim's owner does next (a socket write that returns `EBADF` inside a
library, a file that cannot be written, an ART `CHECK`) can end in `abort()` with nothing in Java
to see.

### 2.2 The descriptor was closed under a live dispatcher

`golang.org/x/…/fdbased` documents its contract in the pinned gVisor's own words:

> Makes fd non-blocking, but does not take ownership of fd, which must remain open for the lifetime
> of the returned endpoint (until after the endpoint has stopped being used and `Wait` returns).

The V222 wrapper closed the descriptor, never called `Attach(nil)` (the only thing that fires the
dispatcher's stopfd), never called `Wait()`, and threw the stack away with `_ = stack`. The
gVisor endpoint's own `Close()` is a no-op, so nothing ever told the dispatcher to stop: it stayed
inside `ppoll(fd)`/`readv(fd)`, on a number the service was about to recycle. A netstack that
starts consuming bytes from somebody else's descriptor is a second, independent path to the same
kind of damage.

### 2.3 The stop was not a join, and state lied

`marble_t2s_quit()` cleared `running` *before* `run()` had finished tearing down:

```go
func marble_t2s_quit() {
    mu.Lock(); stop := stopCh; stopCh = nil; running = false; mu.Unlock()
    close(stop)
}
```

The service auto-recovers and reconnects; a reconnect inside that window passed the `running`
check in `start()` and opened a **second** gVisor stack and tunnel over the same descriptor, sharing
one `statistic.DefaultManager` between two live datapaths. The first teardown then closed state out
from under the second one.

### 2.4 An error string freed under a reader

`setError()` freed `lastErrorC` and immediately published a new one, while `marble_t2s_error()` on
the JNI thread may be inside `NewStringUTF()` holding the pointer it just returned. A use-after-free
of a heap string is, again, a heap check abort.

### 2.5 HEV was stopped by a session that never started it

`stopDatapath()` called both quits on every cleanup:

```kotlin
private fun stopDatapath() {
    runCatching { HevTunnel.quit() }
    runCatching { Tun2SocksTunnel.quit() }
}
```

HEV's quit is not a no-op. From `hev-socks5-tunnel.c`:

```c
void hev_socks5_tunnel_stop(void) {
    res = atomic_fetch_or(&tsync, SYNC_WAIT);
    ...
    if (res & SYNC_SEND) {
        res = atomic_fetch_or(&tsync, SYNC_SENT);
        if (!(res & SYNC_SENT)) {
            res = write(event_fds[1], &res, 1);
            assert(res > 0 && "socks5 tunnel write event");   /* abort() on failure */
        }
    } else {
        atomic_fetch_or(&tsync, SYNC_STOP);                   /* sticky */
    }
}
```

Two consequences for a tun2socks session that pokes it:

- `SYNC_STOP` is **sticky** and is only consumed by `hev_socks5_tunnel_run()`, which returns
  immediately the next time it sees it — an HEV session, in the same process, that "connects" and
  carries nothing;
- when the bit pattern is `SYNC_SEND` set with `event_fds[1]` already closed by
  `hev_socks5_tunnel_fini()`, the `assert` fails and **`abort()`s the process**. That is a
  `SIGABRT`, from C, with no Java frame anywhere.

Whichever of these fired first on the device, the rule is the same: a cleanup stops the datapath
that started, and nothing else.

### 2.6 Which of these is *the* one

Honest ordering, because the app process cannot tell us which fired:

1. **The double close (2.1) is unconditional.** Every tun2socks disconnect closed the same number
   twice. Whether the second close hit a free number (harmless `EBADF`) or a recycled, live one
   (fatal for its owner) is a matter of timing — and the report's timeline is exactly the window in
   which the process recycles numbers. This is the defect, not a race.
2. **2.2 and 2.4 are the same class**, with smaller windows: reading or freeing something a
   descriptor/pointer no longer owns.
3. **2.5 is real but needs a particular interleaving**, and it only fires when HEV is touched by a
   session that did not start it. It is fixed because the fix is one `when` — not because it is
   certainly what aborted here.
4. **2.3 needs a reconnect inside a teardown**, which the app does on every recovery attempt.

All four are on the same teardown path, so V223 closes all four rather than betting on one. If a
`SIGNALED` record ever comes back, §5 tells you where the one piece of evidence the process cannot
write itself lives.

## 3. What V223 changes

### 3.1 The library never closes the descriptor

`native/tun2socks/main.go` now stops the datapath in an order that respects both contracts:

```go
func teardown(dev device.Device, transport *tunnel.Tunnel, st *stack.Stack) {
    stopLinkEndpoint(dev)   // Attach(nil): fires the stopfd and waits for every dispatch loop
    if st != nil { st.Close(); st.Wait() }
    if transport != nil { transport.Close() }
}
```

`stopLinkEndpoint` (`dev.Attach(nil)`) is the implementation-specific stop the gVisor stack
documents for link endpoints; it is what makes the dispatcher leave `ppoll`, and it blocks until it
has. The transport fan-in is closed **last** on purpose: the transport handles a forwarded
connection by sending it on an unbuffered channel, so closing it first would leave the stack waiting
on a queue nobody drains.

The stack is kept (`liveStack`) instead of discarded, so `Close()`/`Wait()` can reach the
forwarders. The descriptor is left exactly as it was found — open, for its owner.

### 3.2 `marble_t2s_stop(wait_ms)`: the service learns when the descriptor is free

```c
int marble_t2s_stop(int wait_ms);   /* 1 = nothing is running any more, 0 = budget expired */
```

`marble_t2s_quit()` keeps its "signal and return" behaviour (it is called from the cleanup path,
where blocking is an ANR); `marble_t2s_stop` is the join the service needs before it closes
anything. `Tun2SocksTunnel.stopAndWait(timeoutMs)` wraps it, and the JNI shim treats the symbol as
optional so an older `libtun2socks.so` still loads.

### 3.3 One owner for the descriptor, and an exactly-once close

`MarbleVpnService` now tracks who holds the descriptor:

| event | effect |
| --- | --- |
| `runTun` duplicates the TUN fd | `adoptDatapathFd(fd)` — `hevFd` set under a lock, `datapathOwnsFd = true` |
| the native run returns (any path, `finally`) | `releaseDatapathFd()` — ownership cleared, `closeHevFd("datapath-worker")` |
| a cleanup asks while the datapath owns it | `fd-close-deferred` logged; the worker closes it |
| a cleanup asks with no datapath run | `fd-close` logged, the number is taken out of `hevFd` under the lock and closed once |
| `onDestroy` | one last `closeHevFd("service-destroy")` as a safety net |

The close itself takes the number out of the field under `hevFdLock`, so two threads racing for it
cannot both close it.

### 3.4 A real state machine

`idle → running → stopping → idle`. `start()` refuses a live datapath with `-1` and a previous
datapath's teardown with the new code `-7`, which the service logs with the core's own text
(`native-refused code=-7`), so a reconnect that lands inside a teardown is visible evidence instead
of a second stack.

### 3.5 The error string is no longer freed under its reader

`setError()` publishes into a four-slot ring of C strings and frees the slot it is about to reuse,
so a reader holding the newest pointer is always looking at live memory.

### 3.6 Every cleanup stops its own datapath, and says so

`TunnelShutdownPolicy.stopPlan(core, live)` is the single decision point:

| datapath | running | plan |
| --- | --- | --- |
| HEV | yes | `QUIT` — signal only, exactly as V222 did for its default |
| tun2socks | yes | `QUIT_AND_JOIN` — signal, then wait for the teardown |
| any | no | `NONE` — nothing is poked, no sticky HEV stop bit is armed |

The service logs `TUNNEL | datapath-stop`, `TUN2SOCKS | datapath-stop-ack` (or
`datapath-stop-timeout`), `TUN | fd-owned-by-datapath`, `TUN | fd-close` and `TUN | fd-close-deferred`,
so the next report shows exactly who closed which number and when.

### 3.7 The report reads the exit record

`TunnelShutdownPolicy.forcedExitReading(reason, status)` turns a `SIGNALED` record into

```
signal=SIGABRT(6) • native abort(), not a Java exception: an assert/abort() inside a native
library (HEV's socks5 tunnel write assert, a libc heap check) or an ART JNI/LOG(FATAL) check.
The Abort message is in logcat for this pid.
```

and both Bug Finder's PROCESS EXIT HISTORY and the external debug report print it. Two more gaps
in the same report are closed while here: `TUN2SOCKS |` and `TUNNEL |` were missing from the
timeline tags (so the second datapath's lifecycle never appeared in the timeline at all), and the
tun2socks core log was read into its own section but never indexed by the ERROR/CRASH/BUG INDEX.

## 4. What the tests pin

`Tun2SocksShutdownV223Test` — no device, no native library:

- a cleanup stops the datapath that started and never the other one, for every `TunnelCore`;
- exactly one datapath is joined, and it is the one that owns the descriptor;
- the service may not close the descriptor while the datapath owns it;
- the reported record (`reason=2`, `status=6`) reads as `SIGABRT(6)`/`abort()`, other signals read
  as themselves, and non-signal exits borrow none of that wording;
- the native contract the fix rests on exists: `-7`, `-1000/1001/1002`, and a join budget that
  stays under the platform's service timeout.

`scripts/system-integrity-check.py` adds the structural half: the Go wrapper contains no
`unix.Close(` on the descriptor and does contain the stop/join sequence, the JNI shim exports the
stop with a budget, the service goes through `TunnelShutdownPolicy`, and the report decodes the
signal.

## 5. How to confirm on a device

1. Connect on the tun2socks datapath, then disconnect. Expected log order:
   `VPN | shutdown-begin` → `TUNNEL | datapath-stop` (HEV) or
   `TUN2SOCKS | datapath-stop-ack` → `TUNNEL*. | run-exit code=0` →
   `TUN | datapath-released-fd` → `TUN | fd-close owner=datapath-worker`.
   The `fd` number appears exactly twice: once adopted, once closed.
2. `fd-close-deferred` is expected only when a cleanup and a datapath run race; it is normal, and
   the close still happens once, in `datapath-worker`.
3. If a `SIGNALED` record appears again, PROCESS EXIT HISTORY now prints the signal name and the
   next step. Pull `adb logcat -b crash` (or `adb shell dumpsys dropbox --print data_app_native_crash`)
   for the same pid and read the `Abort message:` line — that line names the library and the
   assertion, which is the one piece of evidence the app process cannot write itself.
