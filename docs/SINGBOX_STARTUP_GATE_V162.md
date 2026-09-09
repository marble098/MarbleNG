# SING-BOX START-UP GATE — V162

**Why one connect on the sing-box engine ended in `core-start-timeout` after 12 seconds with the
kill switch held, why the log that came with it could not say what had failed, and why the fix is
to stop asking a diagnostic whether the tunnel is up.**

Engine pin: unchanged — `shtorm-7/sing-box-extended` `v1.14.0-extended-2.7.1`
(`217f77643f19e915ea38ba2a9ac7e28e22a0cec5`) with `crash-fix.1`, from `core-lock.json`. Nothing in
this chapter rebuilds a core or changes one line of Go: the defect was in what MarbleNG waited
for, and in how long it let a child prove itself. The marker every layer of the proof greps for is
`MARBLE_SINGBOX_STARTUP_GATE_V162`.

---

## خلاصه (Persian summary)

- **اتصال با هستهٔ Sing-box بعد از ۱۲ ثانیه با `core-start-timeout` شکست خورد و برنامه مسیر را
  بست (BLOCKED)، در حالی که فرآیندِ هسته نمرده بود.** ماربل خودش آن را کشت، چون منتظر چیزی بود
  که نیامد.
- **علت:** شرطِ «هسته آماده است» دو تا بود: پورتِ محلی (همان که تونل واقعاً به آن وصل می‌شود)
  **و** کنترلرِ Clash. پورتِ محلی در مرحلهٔ `Start` باز می‌شود، اما کنترلر در `Started` —
  **آخرین** مرحلهٔ راه‌اندازیِ هسته، بعد از قدم‌زدنِ همهٔ خروجی‌ها — باز می‌شود. یعنی ماربل در
  عمل منتظرِ کلِ راه‌اندازی هسته بود، نه چیزی که ترافیک از آن رد می‌شود.
- **دو اشکالِ دیگر هم در همین مسیر بود:** (۱) `sing-box check` از همان بودجهٔ ۱۲ ثانیه‌ایِ اجرا
  خرج می‌کرد، پس یک اعتبارسنجیِ کُند می‌توانست سهمِ هسته را به چند ثانیه برساند؛ (۲) پیامِ خطا
  نمی‌گفت کدام نیمه وقتش تمام شده — «پورت باز نشد» (هسته‌ای که روی این دستگاه کار نمی‌کند) و
  «تونل بالا بود ولی کنترلر نیامد» (مسیر سالم با یک سطحِ اندازه‌گیریِ غایب) یک جملهٔ یکسان داشتند،
  و جالب‌تر اینکه پاراگرافِ راهنماییِ «ماربل را آپدیت کن / برو روی Xray» — که مالِ کرشِ V157 است —
  برای این خطا هم چاپ می‌شد.
- **اصلاح:** پورتِ محلی تنها شرطِ آمادگی است. کنترلر بعد از آن در یک پنجرهٔ محدود (۲۵۰۰ میلی‌ثانیه)
  صدا زده می‌شود و اگر جواب نداد، **گزارش می‌شود نه اینکه تونل را بکشد**. `check` بودجهٔ مستقل دارد.
  و هر نیمه نامِ خودش را در لاگ و در Bug Finder می‌نویسد.
- **توجه:** اگر در دستگاهِ شما همان نیمهٔ اول باشد (پورت اصلاً باز نشود)، این نسخه آن را درست
  نمی‌کند — ولی برخلاف قبل، دقیقاً می‌گوید کدام نیمه است، و دیگر یک تونلِ سالم را به خاطر یک
  ابزارِ تشخیصی نمی‌کشد.

---

## 1. The evidence

```
12:17:19.508  CORE     engine-switch     from=xray to=singbox
12:17:22.809  SINGBOX  start-begin       engine=singbox profile=SOLIDVPS port=10808 mode=tun
                                         dnsStrategy=UseIPv4 linkRttMs=90 linkJitterMs=7
                                         linkSamples=3 dnsTimeoutMs=1350
12:17:23      (core)   WARN network: initialize package manager: read packages list: open
                             /data/system/packages.xml: permission denied
12:17:34.930  SINGBOX  start-result      ok=false elapsedMs=12119 phase=failed alive=false
                                         reason=core-start-timeout: …the WARN above, and nothing else…
12:17:34.931  SINGBOX  core-start-failure-not-a-server-verdict
                                         selfTest=core starts and serves its local inbound
12:17:34.932  VPN      failure-generation generation=3
12:17:34.934  VPN      blocked           killSwitchHold=true xrayAlive=false hevFd=-1 tunOpen=true
12:17:34.935  APP      state             from=CONNECTING to=BLOCKED • Kill switch active
```

Four facts in that report, and they are not the four the previous chapter had to read:

1. **The canary passed** (`selfTest=core starts and serves its local inbound`). The binary starts
   on this device. This is *not* V157's nil-interface-monitor crash, whose signature is
   `core-start: exited 2: … panic: …`.
2. **The core's log ends one line in.** The `packages.xml` WARN is printed by every
   `GOOS=android` core with no platform interface, on healthy starts too (V157 §2.3): it is a
   fingerprint, not a cause. What is new is that it is the *last* thing the process ever said.
3. **The process did not die.** `start-result` reports `alive=false` only because the session had
   already been closed by then; a child that had exited would have produced
   `core-start: exited N: <log>`, which is the branch the same loop reports first. The app waited
   the whole window and then killed a process that was still running.
4. **The user paid for it with the route.** `killSwitchHold=true`, `hevFd=-1`: fail-closed, no
   traffic, because a condition that is not the tunnel did not come true.

Two other entries in the same report are correctly *not* faults: the historical process exits
(`REMOVE TASK`, `installPackageLI`) are Android's own lifecycle, and the
`[socks-in -> block]` lines for IPv6 destinations are `AddressFamilyPolicy`'s `::/0` block rule
doing exactly what the user asked for (Settings → IPv6 off). Neither is addressed here.

The block does have a cost worth naming, because it is the one entry in the report that can make
browsing *feel* slow without ever failing: a browser that resolves a hostname itself (Chrome's
built-in async resolver) asks the tunnel for the AAAA address first and gets an immediate
`block`, so it pays its Happy-Eyeballs fallback delay on every new host before it tries the A
record. That is a few hundred milliseconds per host, not an outage. If the underlay carries IPv6,
turning Settings → IPv6 on removes it; if it does not, the block is the cheaper of the two, because
the alternative is a stalled AAAA dial. Either way it is a routing decision, not this fault.

## 2. Reading the core, not the error message

### 2.1 What MarbleNG was waiting for

The pinned core's `box.Start()` walks its components through five stages. Read at the pinned
commit, in the order MarbleNG's two conditions become true:

| Stage | What starts | What MarbleNG needs |
| --- | --- | --- |
| `New()` | cache file (bbolt), DNS transports, every inbound/outbound **created** | — |
| preStart `Initialize` | cache file open, clash server created, network monitors, router | — |
| preStart `Start` | outbound, provider, dns/transport, **network** ← *the packages.xml WARN*, connection, http client, router rule-sets, dns router | — |
| `Start` | internal services (cache file `LoadMode`), endpoint, **inbound/mixed listens** | ✅ **condition 1** |
| `PostStart` | every outbound's post-start — the V157 crash site — rule-set updater, inbound | — |
| `Started` | every manager's final stage, then internal services: **the clash controller binds** | ✅ **condition 2** |

`box.Start()` opens the inbounds before it walks the outbounds' post-start — that ordering is why
V157 added a settle window at all — and the controller is served by an *internal service*, whose
`Start` binds the listener only at `StartStateStarted`. So the readiness condition
`listening(socksPort) && apiReady(...)` was not two checks on one milestone: it was a check on the
first milestone plus a check on the very last one, and everything in between was on the critical
path of the connect.

None of what sits between the two carries a byte of user traffic. Every byte goes through the
mixed inbound on `127.0.0.1:10808`, which is what hev-socks5-tunnel dials.

### 2.2 The two half-failures, and why one sentence for both was the defect

| What ran out | What it means | What it deserved |
| --- | --- | --- |
| the local inbound | the core cannot serve this device | a failed connect, a named phase, the core's log |
| the controller | **the tunnel is up** and a measurement surface is missing | a working route, a note, a Bug Finder WARN |

The second one was reported as the first. That is not only a reporting defect: the app killed the
session, closed it, and the VPN service blocked the route with the kill switch — a fail-closed
state, chosen because a tunnel that is up but unverified may leak. Here nothing was unverified;
the data path had answered and the process was alive.

And because the reason string carried the benign package-list WARN that rides above *every*
Android failure, `explain()` handed the user V157's paragraph — "update MarbleNG, or switch
Settings → Tunnel core to Xray-core" — for a timeout on a device that was already running the
fixed build. The advice was not wrong so much as it was about a different fault.

### 2.3 The other budget defect on the same path

`SingBoxProcessSession.open` started one wall clock (`deadline = now + startupTimeoutMs`) and paid
both process spawns out of it:

```kotlin
val deadline = System.nanoTime() + MILLISECONDS.toNanos(startupTimeoutMs)   // ← starts before check
if (validate) { validator.waitFor(left().coerceAtMost(8000), …) }           // ← spends the child's window
…
while (left() > 0) { … }                                                    // ← the child gets the remainder
```

`sing-box check` parses the same document, the same rule sets and the same outbound graph `run`
does. On a loaded device it is not free. A validation that took eight seconds left the child four
to open its inbound in — and the failure was then reported as `core-start-timeout`, i.e. as the
child's fault.

## 3. What changed

### 3.1 `SingBoxProcessSession` — a phased wait, and a named verdict

`open()` now waits in three phases, each of which keeps the V157 rule that a dying child is a
crash:

1. **inbound** — poll `probes.inbound(socksPort)` until the budget expires. A child that dies is
   still `core-start: exited N: <log>`. On expiry:
   `core-start-timeout: the local inbound never answered in <ms> ms: <tail>`.
2. **settle** — the V157 grace window (`settleMs`), unchanged, now paid by the live connect too
   (`SingBoxManager.LIVE_SETTLE_MS` = the canary's 700 ms). This is the window that catches a core
   that panics in an outbound's post-start microseconds after the port opened.
3. **controller** — only when `awaitApi`, and only inside `controllerTimeoutMs`
   (`CONTROLLER_TIMEOUT_MS` = 2 500 ms, never more than what is left of the budget). A controller
   that never answers is **recorded, not fatal**, unless the caller said otherwise:
   `requireController` defaults to `awaitApi`, so the URL test — which reads `/proxies` and cannot
   work without it — keeps demanding it, and only the live connect says the tunnel is what it is
   waiting for.

The result is published on the session as `StartReadiness(inboundUp, controllerUp,
controllerAwaited, phase, elapsedMs)`, with `controllerMissing` for the degraded case. The
`core-start-timeout:` prefix is unchanged, so no classifier, no sweep gate and no existing test
sees a new vocabulary — only a new sentence after the colon.

Covering the crash window does not get weaker. It was "the rest of the 12 s budget, via the
controller"; it is now `settle` + the controller window (≈3.2 s), and after the wait ends the VPN
service's one-second liveness watchdog (`coreAlive` in `startTelemetry`) catches a core that dies
later, which is a path that has always existed for every other late death.

### 3.2 `SingBoxProcessSession` — the validator gets its own budget

Two edits, because one alone is not a fix:

* `check` no longer waits on `left()`: it gets `validateTimeoutMs` (`VALIDATE_TIMEOUT_MS`
  = 8 000 ms), its own budget, so it can never be cut short by a window that belongs to the child;
* the child's window starts when the child does. `deadline` moved from the top of `open()` to the
  line above the `run` spawn, so `startupTimeoutMs` is now exclusively the time in which the child
  must open its inbound, whatever the validation cost. (`elapsed()` still measures the whole call,
  which is what a report wants to see.)

The worst-case start is longer by the validation time — the price of not charging the child for
work the validator did — and on every log we have, `check` costs a few hundred milliseconds.

### 3.3 `SingBoxAndroidRuntime` — a timeout is explained as a timeout

`isStartupTimeout` and `STARTUP_TIMEOUT_REMEDIATION` are new, and `explain()` consults them
*before* the package-manager heuristic, the same way it already prefers the bind conflict: the
WARN rides above every Android failure, so a heuristic that matches it must never outrank a
definitive one. The new paragraph names the evidence — a tail that holds nothing but the
package-list line means the process came up and then stopped talking — and asks for the Bug Finder
section rather than for an update the device already has.

### 3.4 `SingBoxManager`, `MarbleVpnService`, `BugFinder` — the two halves are witnesses

* the manager keeps `lastStartReadiness` and, when the controller is missing, adds one note to
  `lastSelfHealNotes` that says the route is up and what is not;
* `start-result` prints `inbound=` and `controller=` next to `ok`/`elapsedMs`, so the next report
  from the next device can be read without re-deriving the stage table in §2.1;
* Bug Finder's *SingBox core start-up* check grows a WARN for a degraded session (above the
  package-manager WARN, which every healthy Android core prints), and the
  `SINGBOX ANDROID RUNTIME CONTRACT` section prints `last start-up: inbound=… controller=…
  phase=… elapsedMs=…`.

## 4. What this does not claim

* **It does not fix a core that never opens its inbound.** If the reported device is failing in
  phase 1 — and the report cannot tell us which it was, which is the whole point of §3.4 — then
  the pinned core hangs somewhere between the package-manager probe and the inbound start on that
  device, and no Kotlin change can reach into it. What the next report will say is *that*: phase
  `inbound`, `inbound=false`, `controller=false`, and a log tail that still holds one benign line.
  That is the finding the next chapter needs.
* **It does not switch engines, and it does not stop being fail-closed.** Engine selection stays
  the user's explicit act (V151), and a connect that fails still blocks. What changed is which
  failures count as failures.
* **It does not make the controller unnecessary.** The URL test and the Engine page read it, and a
  live session without it is a degraded session that Bug Finder now names.
* **It does not lengthen the connect for a healthy device.** A core that starts in 300 ms and
  serves its controller is out of `open()` in roughly a second, which is what it was.
* **It says nothing about browsing speed on the Xray engine.** That complaint came in the same
  message and has no measurement behind it yet; `Bug Finder` and the retained route telemetry are
  where it would have to start, and it is a separate chapter.

## 5. Pinned by

`app/src/test/java/com/marbleng/app/core/SingBoxStartupGateV162Test.kt` — real child processes
(a POSIX shell standing in for the core) with injectable readiness answers:

* a tunnel whose controller never answers is handed out, is still alive, and is marked degraded;
* a controller the caller requires is still a failure, and the failure names the controller;
* an inbound that never opens names the inbound, and the controller is never asked;
* a child that dies after its inbound opens is still `core-start: exited 2:` — V157's contract;
* the first inbound probe carries the child's own window, not the validator's leftovers, and a
  child that needs longer than those leftovers still starts;
* a timeout is explained as a timeout, once, and not as the package-list WARN it carries;
* neither half is a verdict about a server: no sweep gate, no reader walk, still a local fault;
* `controllerMissing` does not fire for a core that never dialled the controller.

`app/src/test/java/com/marbleng/app/core/SingBoxNativeIntegrationTest.kt` — the real binary,
unchanged: every `open()` there uses the defaults, and `requireController` defaults to `awaitApi`,
so the URL-test cores and the cancelled-startup test keep the contract they were written for.

Native and CI: the `MARBLE_SINGBOX_STARTUP_GATE_V162` invariants in
`scripts/system-integrity-check.py` (the gate, the separate validation budget, and the timeout
remediation), which run before the JDK is installed and before any ABI is built.
