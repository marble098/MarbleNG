# PING SPEED AND REMEMBERED PING — V160

**Why a three-sample Real delay paid for three cold starts, why a URL test gave a minute of a
hundred-node sweep to naps between readiness probes, why the measurement was the one thing the
product forgot on restart, and what Settings now says about the core it is running.**

Engine pin: unchanged — `shtorm-7/sing-box-extended` and the pinned Xray-core from
`core-lock.json`. Nothing in this chapter touches a core binary or a measurement method: no
threshold, no sample count, no target and no timeout was changed. Four of the five changes are
about not paying twice for work already done; the fifth is about not throwing away work the user
already paid for.

---

## خلاصه (Persian summary)

- **Real delay ۳۰ تا ۶۰ درصد سریع‌تر شد، با همان دقت.** تا قبل از این، هر نمونه یک اتصالِ
  تازه می‌ساخت (مذاکرهٔ SOCKS + TCP + TLS)؛ یعنی یک پینگِ سه‌نمونه‌ای، سه بار کل هزینهٔ سردِ
  مسیر را پرداخت می‌کرد. حالا همهٔ نمونه‌ها روی همان نشستِ تأیید‌شده گرفته می‌شوند: یک
  handshake، و بعد هر نمونه فقط یک درخواست و یک پاسخِ واقعی. تعداد نمونه، بودجهٔ هر نمونه،
  حذفِ نمونهٔ سرد و میانه‌گیر (median) دقیقاً مثل قبل است — و تازه عددِ خانه با عددِ Rank یکسان
  می‌شود، چون Rank از V156 همین کار را می‌کرد. فاصلهٔ سکوتِ بین نمونه‌ها هم سر جایش است تا
  نمونه‌ها به صورت burst اندازه‌گیری نشوند.
- **URL test سریع‌تر شد.** حلقه‌ای که منتظر آماده شدنِ هسته می‌ماند، بین هر دو بررسی ۶۰
  میلی‌ثانیه می‌خوابید؛ در یک سابِ صدتایی این یعنی یک دقیقه خوابِ خالص. حالا بررسی‌ها اول سریع
  و بعد با فاصلهٔ بیشتر انجام می‌شوند (۴، ۸، ۱۶، ۲۵ میلی‌ثانیه). علاوه بر آن، تعداد هسته‌های
  اندازه‌گیریِ هم‌زمان دیگر یک عددِ ثابت نیست و به توانِ دستگاه بستگی دارد: گوشیِ هشت‌هسته‌ای
  با هیپِ واقعی هشت فرزند را هم‌زمان بالا می‌آورد، و دستگاهِ کوچک همان چهار تای همیشگی را.
- **آخرین پینگِ هر سرور دیگر فراموش نمی‌شود.** خروج از برنامه، ری‌استارت و حتی کشته شدنِ
  پروسه دیگر عددِ کنار هیچ سروری را پاک نمی‌کند: جدولِ اندازه‌گیری‌ها روی دیسک نوشته می‌شود و
  در اجرای بعدی بازخوانی می‌شود (حداکثر ۴۰۰ ردیف، حذفِ خودکارِ چیزی که از ۳۰ روز گذشته است و
  ردیف‌هایی که سرورشان دیگر وجود ندارد).
- **در صفحهٔ Settings، کنار عنوانِ «Tunnel core» نامِ هسته نوشته می‌شود** (مثل `Xray-core` یا
  `sing-box extended`)؛ قبلاً این نام فقط در یک زیرنویسِ کم‌رنگ و تکراری بود.
- **پیش‌فرضِ نصبِ تازه: تم شماره ۲ (شناور) و فونت Google Sans.** کسی که قبلاً انتخاب کرده،
  انتخابش دست‌نخورده می‌ماند — فقط نصبِ اول است که پیش‌فرض را می‌گیرد.

---

## 1. Real delay — every sample on one session

### The defect

`RouteProbe.tunnelHttpsMeasure` asked for **one sample per connection**:

```kotlin
for (round in 0 until rounds) {
    if (round > 0 && !pauseBetweenSamples()) break
    val result = SocksHttpClient.tunnelRttBatchUrl(
        port = socksPort, url = url, samples = 1, timeoutMs = …
    )
    …
}
```

`tunnelRttBatch(port, …, samples = 1)` opens a SOCKS connection, negotiates, dials the origin
through the tunnel, completes a TLS handshake, times one request and closes. So a three-sample
Real delay paid the **whole cold start of the route three times** — negotiation, TCP through the
tunnel, TLS, and one extra quiet gap between each pair.

On a ~1 s route with three samples that is roughly:

```
3 × (SOCKS + TCP + TLS ≈ 3 RTT)  +  3 × (request ≈ 1 RTT)  +  2 × 60 ms
≈ 12 RTT + 120 ms  ≈  12 s
```

and the slowest part of the published number was setup the user never asked about.

### The fix

All rounds now go through the session the first one opened:

```kotlin
SocksHttpClient.tunnelRttBatchUrl(
    port = socksPort, url = url, samples = rounds,
    timeoutMs = budget, spacingMs = PingBudget.SAMPLE_SPACING_MS
)
```

`tunnelRttBatch` has always been able to take several samples on one keep-alive connection — this
is the path `BenchmarkEngine.measure` (Rank) has used since V156, and it is v2rayNG's own
semantics: *"attempt two reuses the same verified session when the origin permits keep-alive"*.
So the two Home paths now run the same measurement the sweep always ran, and the number on the
Home ping button is the number Rank publishes.

```
1 × (SOCKS + TCP + TLS ≈ 3 RTT)  +  3 × (request ≈ 1 RTT)  +  2 × 60 ms
≈ 6 RTT + 120 ms  ≈  6 s        →  about half
```

### Why accuracy is untouched

- **Sample count** — identical (`rounds`, from the user's own ping budget).
- **Per-sample budget** — identical: `tunnelRttBatch` gives each read the same socket timeout and
  the whole batch the same wall clock (`timeoutMs × samples`, now including the quiet gaps), so
  one slow sample still cannot starve the run.
- **Warm-up** — still discarded by `summarize(…, warmupDiscarded = true)`; the cold sample is now
  the only one that carries setup, which is exactly the sample that rule exists to drop.
- **Median, loss and success** — computed from the same list by the same function.
- **No burst** — `SAMPLE_SPACING_MS` moved *into* the batch (`spacingMs`), because three
  back-to-back requests on a live TLS session are the burst signature an adaptive filter learns;
  a throttled sample would be a wrong sample.
- **Non-keep-alive origins** — an origin that answers `Connection: close` still ends its batch
  after one sample and gets a fresh connection for the rest (`tunnelRttBatchUrl` already did
  this), which is precisely what the per-round loop did.
- **Transient silence** — a target that answers nothing is retried once with the same quiet gap
  (`CONSECUTIVE_FAILURES_BEFORE_ABANDON`), except in a one-sample run, which used to get one
  attempt and still does.

## 2. URL test — the readiness poll and the width of the pool

A URL test is the core's own `GET /proxies/{tag}/delay`, so it cannot be measured without a
native child: one spawn per node, and the spawn is a large fraction of one node's cost (V156
measured this — deleting the `sing-box check` spawn roughly halved a sweep).

Two things were paid for that buy nothing:

**(a) A flat 60 ms nap between readiness probes.** `SingBoxProcessSession.open` polled
`listening(socksPort)` and `apiReady(apiPort, …)` and then slept `minOf(60, left())`. The probes
themselves are nearly free on loopback (a port that is not open yet is refused immediately), so
on a core that needs ~200 ms the loop napped two or three times and handed the measurement up to
60 ms of idleness it could not use. It now ramps: `4 → 8 → 16 → 25 ms`, backing off only when a
core genuinely takes its time. Same conditions, same startup budget, same verdicts — up to ~55 ms
sooner per node, which is a minute over a hundred-node subscription.

**(b) A pool width that ignored the device.** Every URL test and every sing-box Real delay takes a
slot from `SingBoxManager.testSlots`, hard-coded at four. Four was chosen when a measurement also
paid a `check` spawn; with that gone, four also means a hundred-node sweep runs in twenty-five
waves, each waiting for its slowest spawn.

`MeasurementCoreBudget` now sizes the pool from what the device reports — cores, per-app heap and
`isLowRamDevice`:

| device | pool |
| --- | --- |
| < 6 cores, low-RAM, or < 192 MB heap | 4 (unchanged) |
| ≥ 6 cores, ≥ 192 MB | 6 |
| ≥ 8 cores, ≥ 256 MB, not low-RAM | 8 |

The floor never moves: a device that cannot carry more runs exactly the pool it has always run,
and the manager's semaphore stays the authority on how many children may exist at once. The
policy is a pure function (`ceiling(cpus, memoryClassMb, lowRam)`) and is pinned by unit tests in
both directions.

## 3. Remembered ping — the measurement the product used to forget

A sweep over a big subscription is minutes of radio time, and every result lived in
`AppRepository.benchmarks` and nowhere else: the last route, the sources and the settings all
survived a restart, and the latency next to each server did not.

`BenchmarkResult` now has one new field — `measuredAtMs`, stamped where a result enters the
table — and the table is written to the store every time a measurement lands (off the main
thread, because a sweep publishes a node at a time) and read back at construction:

- bounded at **400 rows**, newest first, one row per node;
- rows older than **30 days** are dropped on read and on write — a number from a month ago
  describes a network that no longer exists;
- rows whose node is gone are dropped on read, because a measurement of a server the library
  cannot open is not a memory, it is clutter;
- deleting a node, a source, or re-writing a config (which makes a new node) removes its row.

The disk form is pinned by `RememberedPingV160Test`, including the legacy case: a row written by
a build that did not remember pings opens with honest defaults instead of an empty evidence tier.

## 4. Settings — the core's name next to its title

The "Tunnel core" row used to answer *"which core?"* twice and identically (`Xray core •
Xray-core`) in a subtitle line too faint to notice. `SettingsHubRow` gained a `badge` slot that
sits on the title's own line, and the row and the core page now name the engine that is running
(`Xray-core` / `sing-box extended`).

## 5. Fresh-install defaults

- **Home presentation: Theme 2 (Floating)** — the floating action button keeps the primary
  control under the thumb and has the widest servers box of the four.
- **Typeface: Google Sans** — the product's own geometric sans, which is the face every brand
  surface in the app was drawn against. Persian copy still forces the bundled Vazirmatn ramp
  whatever the Latin choice is, so Persian shaping is unaffected.

Both changes are **first-launch only**. `AppStore` now reads these keys with `null` instead of
handing a default to `prefs.getString`: a default passed to `getString` is written back the first
time settings are read, which turns "whatever the default was on the day you installed" into a
permanent explicit choice and makes the default impossible to change ever again. Absence and
choice stay apart, so an update never rearranges a Home or a font the user already chose.

## 6. Verification

- `app/src/test/java/com/marbleng/app/model/RememberedPingV160Test.kt` — the measurement's disk
  form round-trips, an unstamped row never looks fresh, and a legacy row opens with defaults.
- `app/src/test/java/com/marbleng/app/core/MeasurementCoreBudgetTest.kt` — the pool never falls
  below the shipped floor, never exceeds the policy maximum, and is never widened on a device
  that cannot carry it.
- `app/src/test/java/com/marbleng/app/ui/MarbleHomeStyleTest.kt` — the new Home-style and
  typeface defaults resolve through one name.
- `scripts/system-integrity-check.py` — new invariants for the first-launch-only defaults and for
  every presentation still being modelled and reachable.
