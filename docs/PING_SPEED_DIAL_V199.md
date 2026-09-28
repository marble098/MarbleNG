# PING SPEED DIAL — V199

**Why every ping measurement got a speed dial, why the default answer is "about 50 % faster",
and why not one sample count, timeout or target moved to pay for it.**

Engine pin: unchanged — `shtorm-7/sing-box-extended` and the pinned Xray-core from
`core-lock.json`. Nothing in this chapter touches a core binary or a measurement method: no
threshold, no sample count, no target and no timeout was changed. Every gain below is width
(how many servers are measured at once) or pacing (how long the quiet gaps are) — the two
costs of a sweep that are pure time.

---

## خلاصه (Persian summary)

- **پیش‌فرضِ جدید: هر سه روش پینگ حدود ۵۰٪ سریع‌تر.** با خاموش بودن نوار سرعت، هر پویش حدود
  ۱.۵ برابر سرعت قبلی اجرا می‌شود: پویش مستقیم (TCP ping) به‌جای ۱۶، ۲۴ سرور را هم‌زمان
  می‌سنجد؛ استخر هسته‌های Real delay روی موتور Xray از ۲ تا ۴ به ۳ تا ۶ هسته (در سقف توانِ
  دستگاه) باز می‌شود؛ فاصلهٔ سکوت بین نمونه‌ها از ۶۰ به ۴۰ میلی‌ثانیه و لرزشِ ضدردیاب هم به
  همان نسبت کوتاه‌تر می‌شود. **هیچ تعداد نمونه، مهلتی کوتاه‌تر، هدفی جدید یا میانه‌ی متفاوتی
  در کار نیست** — عددِ هر سرور در سرعتِ جدید، همان عددی است که سرعتِ قبلی منتشر می‌کرد.
- **نوار تعیین سرعت در Settings › Testing › Ping.** اگر خاموش باشد (پیش‌فرض)، همان سرعتِ
  جدیدِ بند بالا اجرا می‌شود. اگر روشنش کنید، خودِ عددِ نوار ضریبِ شماست: ۱۰۰٪ سرعتِ کلاسیکِ
  همیشگی، کشیدن به راست سریع‌تر (استخر پهن‌تر، فاصلهٔ کمتر)، کشیدن به چپ کندتر و ملایم‌تر
  برای اینترنت ضعیف — از ۵۰٪ تا ۲۰۰٪، با گام ۵٪. نوار هیچ‌وقت بودجهٔ دقت را تغییر نمی‌دهد:
  «مهلت هر سرور»، «تعداد نمونه» و «سرورهای هم‌زمان» (تراشه‌های بودجه) سر جایشان هستند و نوار
  فقط تعیین می‌کند آن قراردادِ دقت با چه سرعتی اجرا شود.
- **هیچ دستگاهی از بودجهٔ حافظه‌اش بیشتر مصرف نمی‌کند.** استخرِ هسته‌های اندازه‌گیریِ
  sing-box (URL test و Real delay روی موتور sing-box) همان سقفِ وابسته به دستگاهِ V160 را
  دارد و نوار هیچ‌وقت یک فرزند از آن بالاتر نمی‌رود؛ استخرِ فرزندهای Xray هم با همان سیاستِ
  `MeasurementCoreBudget` سقف زده شد. دستگاهِ کوچک دقیقاً همان استخرِ همیشگی‌اش را اجرا
  می‌کند.

---

## 1. The default — about 50 % faster, for all three methods

### Where a sweep's wall clock actually goes

A sweep is waves: `ceil(servers ÷ width)` rounds of the per-server measurement, plus the quiet
gaps inside every multi-sample run. V160 made each *sample* cheaper without changing what it
measures. What was still fixed was the shape around the samples:

| lever | V160 value | V199 default (dial off) | effect |
| --- | --- | --- | --- |
| direct sweep width | 16 servers at once | **24** | a 100-node TCP-ping sweep: 7 waves → 5 |
| Xray Real-delay pool | 2..4 CPU-derived children | **3..6**, bounded by the device | waves shrink ~⅓ on a capable device |
| sample quiet gap | 60 ms | **40 ms** | 2 gaps per 3-sample server: 120 → 80 ms |
| anti-probing stagger | 50–400 ms | **33–267 ms** | same randomized discipline, proportionally shorter |

URL test keeps its V160 pool width — that pool was already widened to the device's own ceiling
(4 → 6/8) in V160, and that ceiling is a memory decision the dial may not overrule (see §3).
Its default-speed share is the pacing economy every measurement shares, and the dial gives it
real teeth the moment the user raises it below the ceiling (a slower dial now genuinely
narrows the pool instead of being clamped back up).

### Why the numbers do not move

The dial never touches:

- **sample counts** (`pingSampleCount()` still reads `pingSamples` raw);
- **per-sample timeouts** (`pingTimeoutMs()` is byte-for-byte the V145 accessor);
- **delay-test targets** and the three-origin walk with full budgets (V159, untouched);
- **the median, the warm-up discard, the success/loss denominators** (`summarize`, untouched);
- **the burst discipline** — the scaled gap keeps a 20 ms floor and the stagger stays a real
  randomized 20–267 ms range at the default, so a faster sweep is never a burst an adaptive
  filter can learn; a slower dial *lengthens* both, never past the shipped maximums.

A server measured at the default speed publishes the same latency it published at the classic
pace. That is the whole contract, and `PingSpeedTest.theDialNeverTouchesTheAccuracyBudget`
pins it.

## 2. The dial — one ruler, not two

`Settings › Testing › Ping` grows one section: **Ping speed**.

- **Off (the default):** the shipped ~1.5× baseline above. The switch's subtitle says so, and
  the readout under it shows the width and gap the sweeps will really run.
- **On:** the slider *is* the multiplier against the classic V160 pace — 100 % plays it
  classic, 50 % is half-speed (gentler, for a weak link where accuracy matters more than
  time), 200 % is twice the classic pace. Step 5 %, committed on release, never per frame.
- **"Use the default speed"** returns to the off state in one tap; the stored percent survives
  so a re-enabled dial reopens where the user left it.

The default and the manual scale are one ruler: the default is 150 % of the classic pace, and
the manual percent multiplies the same baseline. Nothing about the budget chips below the dial
changes with it — they remain the accuracy contract; the dial only decides how fast that
contract is executed.

Persian UI ships with the feature (the lexicon carries every new string), and the readout
lines show live numbers — servers at once and milliseconds between samples — so the slider is
never a label without a consequence.

## 3. The width policy — one pure function, bounded by the device

`BenchmarkEngine.sweepWidth(...)` is now a pure, unit-tested function with four rules:

1. **Direct TCP-ping sweep** — exactly as wide as the caller asked. The sweep builds its
   settings through `AppSettings.pingWorkers()`, the user's concurrency at the dial's width,
   clamped through the same `PingBudget.concurrency` as a raw value. MARBLE_PING_CONTROL_V145's
   rule survives verbatim: no hidden clamp rewrites the user's number.
2. **Native-child measurements** (URL test, and Real delay on the sing-box engine) stay under
   `MeasurementCoreBudget`'s device ceiling. The ceiling is a memory decision; the dial feeds
   the pool as fast as the device allows and never one child wider.
3. **Real delay on the Xray engine** (the default engine) — was pinned to 2..4 CPU-derived
   children whatever the device was. It now scales with the dial (default 3..6), bounded by
   `XrayManager.measurementCoreCeiling` — the same `MeasurementCoreBudget` read the sing-box
   pool uses, and a conservative bound for it, because an Xray child owns no controller API and
   no cache workspace. A device that cannot carry more runs exactly the pool it has always run.
4. **The legacy v2ray-style ladder** keeps its unscaled 2..4 envelope.

The batch deadline is honest about pacing too: `PingBudget.perServerBudgetMs(timeout, samples,
spacing)` takes the dial's gap, so the deadline describes the sweep that will actually run.

## 4. What the dial changes, file by file

- `model/Models.kt` — `PingSpeed` (the scale, the default factor, the label), the two settings
  fields, and the three derived accessors: `pingSpeedFactor()`, `pingWorkers()` (now scaled),
  `pingSampleSpacingMs()`; plus the spacing-aware `perServerBudgetMs` overload.
- `core/BenchmarkEngine.kt` — `sweepWidth()` (the pure policy) and the dial-scaled batch
  deadline.
- `core/XrayManager.kt` — `measurementCoreCeiling`, the device read for the Real-delay child
  pool.
- `core/RouteProbe.kt` — the quiet gap flows from settings into every multi-sample run
  (`pauseBetweenSamples(gap)`), and `tunnelHttpsMeasure(Targets)` carry a `sampleSpacingMs`
  whose default stays the shipped spacing.
- `core/MultiVectorReachability.kt` — the anti-probing stagger takes the dial's scale; the
  range is scaled, never removed.
- `AppRepository.kt` — the throwaway-tunnel Real-delay hook passes the dial's gap.
- `data/AppStore.kt` — `pingSpeedCustom` / `pingSpeedPercent` round-trip, clamped on the way
  in.
- `ui/Aether2026.kt` + `ui/MarblePersianLexicon.kt` — the Settings control and its Persian
  copy.
- `scripts/system-integrity-check.py` — the V160 spacing invariant now pins the dial-driven
  form, and six new invariants pin the dial end to end.

## 5. Verification

- `app/src/test/java/com/marbleng/app/model/PingSpeedTest.kt` — dial off is *always* the
  shipped default whatever a stale percent says; the manual scale speaks the user's percent;
  the scaled width stays a legal concurrency for every chip × every percent; the quiet gap
  never becomes a burst or a stall; the accuracy budget is byte-identical at every dial
  position; the shipped defaults are unchanged.
- `app/src/test/java/com/marbleng/app/core/SweepWidthTest.kt` — the direct sweep is never
  double-scaled; native-child pools never exceed the device ceiling at any dial position; the
  Xray Real-delay pool scales and stays inside the device's bound; the legacy ladder is
  unscaled; no hostile input (cpus 1..16, workers 0..999, factors from −1 to NaN) escapes the
  legal ranges.
- `scripts/system-integrity-check.py` — the dial policy, its persistence, its UI, its Persian
  lexicon and its tests are pinned source-wide.
