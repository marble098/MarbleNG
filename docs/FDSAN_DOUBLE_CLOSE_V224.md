# THE fdsan DOUBLE-CLOSE — V224

**Why a tun2socks disconnect still died with `fdsan: double-close of file descriptor 148 detected`
(SIGABRT) after V223, who actually performed the first close, and what changed so neither datapath
can ever close a descriptor the service owns.**

Marker: `MARBLE_FDSAN_DOUBLE_CLOSE_V224`. Completes `MARBLE_TUN2SOCKS_SHUTDOWN_V223`
(`docs/TUN2SOCKS_SHUTDOWN_V223.md`) — every V223 rule still holds; this chapter adds the one
beneath them. Pins: unchanged — `core-lock.json` (tun2socks v2.7.0 / `8dda19e`, HEV, Xray,
sing-box). No core binary moved.

---

## خلاصه (Persian summary)

- **لاگ چه می‌گوید؟** قطع اتصال روی datapathِ tun2socks کاملاً سالم پیش می‌رود
  (`datapath-stop-ack` ← `run-exit code=0` ← `datapath-released-fd`) و درست یک خط بعد از
  `fd-close` سرویس (ساعت 02:38:04.843) پروسه با خطای fdsan و سیگنال SIGABRT کشته می‌شود:
  `double-close of file descriptor 148`. یعنی وقتی سرویس — طبق قرارداد V223، تنها مالک و تنها
  بستن‌کننده — عدد 148 را می‌بندد، آن عدد **از قبل در سطح کرنل بسته شده است**.
- **بستنِ اول کجا بود؟** در کد Marble نبود. V223 بستن صریح Go را حذف کرده بود و فرضش این بود که
  «دیگه هیچ‌کس این عدد را نمی‌بندد». ولی `Stack.Wait()` در gVisor (نسخهٔ پین‌شده) همهٔ NICها را با
  `closeLinkEndpoint=true` حذف می‌کند، یعنی روی link endpoint متد `Close()` را صدا می‌زند — و
  link endpoint ما همان `FD` کتابخانهٔ tun2socks است که `Close()` اش `unix.Close(f.fd)` است.
  (endpoint خود gVisor واقعاً no-op است؛ بستن در لایهٔ tun2socks است، نه gVisor.) پس اولین بستن با
  syscall خام انجام شد، جدول fdsan در libc از آن خبر نداشت، و بستنِ دوم سرویس با
  `ParcelFileDescriptor.adoptFd` — که بستن تگ‌دار fdsan است — با EBADF مواجه شد. در bionic،
  «بستن تگ‌دار + EBADF» دقیقاً همان خطای `double-close … detected` است و عمداً abort می‌کند.
- **Fix:** قانون «descriptor قرضی». عددی که VpnService به هر datapath می‌دهد فقط **قرض** داده
  می‌شود. هر دو سمت native سر مرز JNI از آن dup می‌گیرند (`F_DUPFD_CLOEXEC`: در wrapper گو و در
  پل JNIِ HEV) و فقط روی dup خودشان اجرا و بسته می‌شوند. از این به بعد هر `Close()` ای — چه از
  داخل `Stack.Wait()`، چه teardown صریح، چه نسخه‌های آیندهٔ stack — فقط می‌تواند عدد خود native
  را ببندد؛ عدد سرویس تا لحظهٔ بستنِ یگانهٔ خودش باز می‌ماند و fdsan دلیلی برای abort ندارد.
  خلاصه: یک TUN، دو عدد، هر عدد یک مالک و یک بستن.
- **چرا بستن سرویس حذف نشد؟** چون آن وقت در مسیرهای ردشدن اولیهٔ native (کدهای ‎-1/-5/-7‎ که
  اصلاً stack ساخته نمی‌شود) هیچ‌کس عدد سرویس را نمی‌بست و هر connect ناموفق یک fd نشت می‌کرد.
  بستن idempotent و lock‌دار V223 سر جایش ماند؛ مشکل از مالکیت مشترک بود، نه از آن بستن.
- **محافظت در برابر برگشت باگ:** گیت همان تغییری است که CI می‌خواند: تست JVM
  (`FdsanDoubleCloseV224Test`)، چک‌های V224 در `system-integrity-check.py` (wrapper فقط روی
  `ownedFd` device باز می‌کند، هرگز روی fd قرضی نه؛ هر دو پل باید dup بگیرند)، و نگهبان زمان
  ساخت در `scripts/prepare-native.sh` که سورس استیج‌شده‌ای را که dup را گم کرده باشد همان‌جا
  fail می‌کند — قبل از اینکه APK به دست کاربر برسد.

---

## 1. The evidence

From the device log for `com.marbleng.app`, a tun2socks session disconnecting:

```
APP | state            from=CONNECTED            to=DISCONNECTING
VPN | command          action=com.marbleng.STOP
TUN2SOCKS | datapath-stop-ack
TUN2SOCKS | run-exit   code=0
TUN | datapath-released-fd      hevFd=148
TUN | fd-close                  hevFd=148   owner=datapath-worker   tunnelCore=tun2socks
fdsan: double-close of file descriptor 148 detected
Fatal signal 6 (SIGABRT), code -1 (SI_QUEUE) in tid 10454 (pool-19-thread-), pid 9204
```

Three facts set the investigation, and they are sharper than the V223 evidence was:

- **The abort fires inside the service's one close** (`fd-close`, on the datapath worker thread —
  the crashing tid), not hundreds of milliseconds later in some unrelated victim. This is not the
  V222 shape (two closes of one number, the second hitting whoever recycled it). The number was
  already dead when its single legitimate owner closed it.
- **`datapath-stop-ack` proves the joined stop completed**: `marble_t2s_stop` only returns 1 after
  `marble_t2s_run` has finished `teardown()` and closed `doneCh`. So by `run-exit`, everything the
  Go side will ever do to the descriptor has already been done.
- **The error text is a signature, not a symptom.** In bionic's `fdsan.cpp`,
  `android_fdsan_close_with_tag(fd, expected_tag)` ends with:

  ```cpp
  int rc = __close(fd);
  // If we were expecting to close with a tag, abort on EBADF.
  if (expected_tag && rc == -1 && errno == EBADF) {
      fdsan_error("double-close of file descriptor %d detected", fd);
  }
  ```

  `ParcelFileDescriptor.adoptFd(fd).close()` closes **with** a tag (the `FileDescriptor`'s fdsan
  owner id — the same machinery behind the earlier `fd … is owned by unique_fd …, was expected to
  be unowned` reports). A plain libc `close()` of an unowned number that returns EBADF does **not**
  abort. So the crash needs exactly two ingredients: the kernel already considers 148 closed, and
  the close is the service's tagged one. The first ingredient had an author — just not in this
  repository's code.

What this crash is **not**: the `avc: denied` SELinux lines (`libxray.so`, `cgroup`, `somaxconn`)
are policy denials the app already survives; `crash_dump64: Failed to initialize unwinder object`
is post-mortem and only says the tombstone is incomplete; the `ip-family-scan … UNREACHABLE`
lines are the reachability probe reporting a network state. None of them abort a process.

## 2. The first close: inside `Stack.Wait()`

V223 removed the wrapper's explicit close and adopted the rule *"this library never closes the
descriptor; `Attach(nil)` stops the dispatchers, `Stack.Close()`/`Wait()` release the stack, the
service closes"*. The integrity gate even forbade the text `unix.Close(` in the wrapper. All of
that held — and fd 148 was still closed on the native side, two libraries down:

1. The pinned gVisor (`v0.0.0-20260701204157-69c2d17aea96`, via tun2socks v2.7.0's `go.mod`)
   implements `Stack.Wait()` by removing every NIC with the link endpoint closed:

   ```go
   // pkg/tcpip/stack/stack.go
   act, _ := s.removeNICLocked(id, true /* closeLinkEndpoint */)
   ```

   ```go
   // pkg/tcpip/stack/nic.go
   if closeLinkEndpoint {
       ep := n.NetworkLinkEndpoint
       ep.SetOnCloseAction(nil)
       deferAct = ep.Close
   }
   ```

   `Wait()` collects those actions and runs them before returning.
2. The `ep` in that call is **not** gVisor's fdbased endpoint (whose `Close()` genuinely is
   `func (e *endpoint) Close() {}` — the fact V223 leaned on). It is the object MarbleNG passed
   to `core.CreateStack`, i.e. the pinned tun2socks device:

   ```go
   // github.com/xjasonlyu/tun2socks/v2/core/device/fdbased/fd_unix.go (v2.7.0)
   func (f *FD) Close() {
       if !f.closed {
           defer f.LinkEndpoint.Close()   // the gVisor no-op
           _ = unix.Close(f.fd)           // ← closes the VpnService's number
           f.closed = true
       }
   }
   ```

3. `teardown()` calls `st.Wait()`. `Wait()` therefore performs a **raw-syscall close** of the
   borrowed descriptor — invisible to bionic's fdsan table (Go's `unix.Close` traps straight to
   the kernel) — and the datapath reports a clean stop (`datapath-stop-ack`, `run-exit code=0`).
4. The worker then runs `closeHevFd("datapath-worker")` → `adoptFd(148).close()` → tagged close →
   `__close` → `EBADF` → `fdsan_error("double-close of file descriptor 148 detected")` → SIGABRT.

Every tun2socks disconnect reproduces this deterministically; nothing about it is a race. (Whether
the `fdsan:` line reaches the app's own log depends on logcat timing — the app cannot log past its
own abort — which is why the earlier V223 reports showed only the bare `SIGNALED status=6`
record.) The HEV datapath never had the defect: nothing in HEV closes an externally provided
descriptor, and there is no stack teardown between HEV and the service's close.

### What V223 got right, kept verbatim

Only the started datapath is stopped; the tun2socks stop is joined (`marble_t2s_stop`) before any
close; the service's number is taken out of `hevFd` under `hevFdLock` so at most one service-side
close exists; a service-side close is deferred while the worker still holds the descriptor; the
Go lifecycle is a real state machine; `SIGNALED/6` is read as SIGABRT. V224 removes none of it —
it removes the assumption underneath it, that "no `close` in our Go file" meant "no close of our
fd anywhere".

## 3. The fix: borrowed descriptors are duplicated at the boundary

One TUN device will now have **two descriptor numbers**, each with exactly one owner and one close:

| Number | Created by | Used by | Closed by (once) |
| --- | --- | --- | --- |
| `tun.fd` (original) | `VpnService.Builder.establish()` | nothing directly — the device handle | `MarbleVpnService.closeTun()` (`tun.close()`) |
| `hevFd` (service dup) | `ParcelFileDescriptor.dup(...).detachFd()` in `runTun()` | lent to the datapath across JNI | `MarbleVpnService.closeHevFd()` (`adoptFd().close()`) |
| `ownedFd` / `owned_fd` (native dup) | `F_DUPFD_CLOEXEC` inside `marble_t2s_run`'s `start()` / `Java_…_HevTunnel_run` | the gVisor device+NIC+stack, or HEV | the native side: `Stack.Wait()` → `FD.Close()` (guarded fallback: `teardown()`'s `dev.Close()`), or `close(owned_fd)` when HEV's blocking call returns |

### 3.1 tun2socks (`native/tun2socks/main.go`)

`start()` duplicates the borrowed descriptor immediately before opening the device — late enough
that every refusal above it (already running `-1`, stopping `-7`, bad descriptor `-5`, bad proxy
`-2`, bad MTU/logger) returns without having created a number that needs closing — and opens the
device on the dup:

```go
ownedFd, err := unix.FcntlInt(uintptr(fd), unix.F_DUPFD_CLOEXEC, 0)
…
dev, err := fdbased.Open(strconv.Itoa(ownedFd), uint32(mtu), 0)
```

From there, each exit path closes `ownedFd` exactly once:

- **`fdbased.Open` fails** — neither the pinned tun2socks `open()` nor gVisor's `fdbased.New`
  closes the descriptor on an error path (verified against both pinned sources), so the wrapper's
  `_ = unix.Close(ownedFd)` is the dup's only close. This is the one raw close left in the
  wrapper, and it closes a number the wrapper created itself three lines earlier.
- **`CreateStack` fails** — a half-built stack is discarded without `Wait()`, so nothing else will
  ever close the device; `dev.Close()` (after `Attach(nil)`) is the dup's only close.
- **Normal teardown** — `Attach(nil)` → `st.Close()` → `st.Wait()` → `dev.Close()` →
  `transport.Close()`. `Wait()` runs `FD.Close()` through the NIC removal, so the wrapper's own
  `dev.Close()` is normally the pinned `FD.Close()` guard observing `closed == true` and doing
  nothing; if a future stack stops closing link endpoints, that call becomes the close that keeps
  the dup from leaking. Either way: exactly one close, of the native number, after every
  dispatcher has left it.

### 3.2 HEV (`app/src/main/jni/marbleng_jni.c`)

HEV does not close external descriptors today. But its pin is auto-bumped by
`update-core-lock`, and "the current upstream happens not to close it" is precisely the assumption
that failed on the other datapath — on the datapath every default session uses. So the bridge now
applies the same rule: `fcntl(fd, F_DUPFD_CLOEXEC, 0)`, run HEV on `owned_fd`, `close(owned_fd)`
when the blocking call returns (the exact point at which the service previously closed the
original). The closing call is a plain libc `close()` of an fdsan-**unowned** number this file
created, so even a hypothetical future HEV that closed its descriptor would degrade it to a
harmless `EBADF` — never to the tagged-close abort.

### 3.3 What deliberately did not change

- **The service still closes `hevFd`, once, under the lock.** Removing that close (the "native
  owns it now" design) would leak the number on every early-refusal path and would make ownership
  conditional on how far a native run got — the ambiguity that produced this family of bugs. The
  service's close was never the bug; being the *second* close of a shared number was.
- **No liveness probing before the close.** An `fcntl(fd, F_GETFD)` check cannot make a double
  close safe: if the number had been closed and recycled, the probe would succeed against someone
  else's live descriptor and the "defended" close would destroy it. The defence is the ownership
  split, not a check.
- **Stop/join ordering, error codes, session state machine, stats, logs** — untouched. The
  wrapper's tun2socks log additionally prints `borrowed fd=%d owned dup=%d` once per run, so a
  future fd report names both numbers.
- **Diagnostics:** the SIGABRT forced-exit reading now lists "a libc fdsan double-close of a file
  descriptor" among the native-abort causes, so the next report from this family arrives
  pre-diagnosed.

## 4. How the fix is held in place

The defect crossed three languages, so the pins are placed where each language is read:

1. **`app/src/test/…/FdsanDoubleCloseV224Test.kt`** — the JVM-visible half: the chapter marker
   beside V223's, the unchanged service-side close contract (deferred under a live datapath,
   performed after it, joined stop first), and the fdsan wording in the forced-exit reading.
2. **`scripts/system-integrity-check.py`** (V224 checks) — the Go wrapper must `F_DUPFD_CLOEXEC`,
   must open its device on `ownedFd`, must never open on or close the borrowed `fd`; `dev.Close()`
   must sit between `st.Wait()` and `transport.Close()` in `teardown()`; the HEV bridge must dup
   and run/close `owned_fd`; the stage-time guard, test and chapter must exist. Note the previous
   V223 form of this check — *"no `unix.Close(` text in the wrapper"* — was green on the crashing
   code, because the close lived in `Stack.Wait()`; the check now asserts the *ownership*
   property instead of the absence of a word.
3. **`scripts/prepare-native.sh`** — after the wrapper is copied into the pinned tun2socks tree,
   the release build re-asserts the same four facts on the exact bytes it is about to compile and
   dies with the reason if any is missing. A regression now fails a build, not a disconnect.

Verification limits, stated plainly: CI compiles and vets the Go wrapper against the pinned
tun2socks (the tun2socks smoke in `verify.yml`), compiles and unit-tests the Kotlin, and runs the
integrity audit. It cannot press "disconnect" on a phone; the on-device proof is the same
connect→disconnect loop that produced the `fd 148` log, on the tun2socks datapath, expecting a
clean `fd-close` and no `fdsan:` line.

## 5. If `fdsan:` ever comes back

The `Abort message` for the pid in logcat is the ground truth (the process cannot log its own
abort). The three shapes and what each means:

- `double-close of file descriptor N detected` — a tagged close (anything
  `ParcelFileDescriptor`- or `FileDescriptor`-based) of a number the kernel already considers
  closed: somebody closed a borrowed/shared number again. `grep` every native datapath in the
  process for a path from the lent descriptor to a close, *including through stacks*
  (`Wait()`, `Destroy()`, endpoint removal) — that indirection is what hid this one.
- `attempted to close file descriptor N, expected to be unowned, actually owned by …` (or the
  reverse) — a close crossed an ownership boundary: an untagged `close()` of a tagged number, or
  `adoptFd` on a number somebody else tagged. Same audit, at the JNI seam.
- `fdsan_exchange_owner_tag` errors — ownership was handed off twice (e.g. `detachFd()` called on
  one number in two places). The service detaches exactly once, in `runTun()`.

In all three, the question is never "which close is wrong" in isolation — it is "which number has
more than one owner". One device may have many numbers; one number may have only one owner, one
close.
