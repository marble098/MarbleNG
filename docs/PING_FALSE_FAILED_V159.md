# PING FALSE FAILED — V159

**Why URL test and Real delay both work, yet occasionally publish a perfectly healthy server as
`FAILED`, and why the answer is budget arithmetic, not another method.**

Engine pin: unchanged — `shtorm-7/sing-box-extended v1.14.0-extended-2.7.1` @
`217f77643f19e915ea38ba2a9ac7e28e22a0cec5` (with the V157 backport) and the pinned Xray-core.
Nothing in this chapter touches a core binary.

---

## خلاصه (Persian summary)

هر دو روش «URL test» و «Real delay» درست کار می‌کنند، اما گاهی یک سرورِ سالم را «failed»
نشان می‌دهند. علت دو اشکال بودجه بود، نه پروتکل و نه سرور:

- **URL test** یک مهلتِ واحد را بین همهٔ آدرس‌های پشتیبان تقسیم می‌کرد؛ اولین درخواست (که
  سردترین درخواست ممکن است) اگر تمام مهلت را می‌خورد، به آدرس‌های پشتیبان هیچ چیزی نمی‌رسید
  و سرور failed می‌شد. حالا هر آدرس پشتیبان مهلتِ کاملِ خودش را می‌گیرد.
- **Real delay** در مسیر خانه (هم تونل زنده و هم هستهٔ موقتی) فقط یک URL را می‌خواند؛ اگر
  همان یک مبدأ لحظه‌ای فیلتر یا کند می‌شد، همهٔ نمونه‌ها شکست می‌خوردند. حالا مثل مسیر
  Rank، همهٔ موارد کاندیدِ `DelayTest` پشت سر هم با همان بودجه امتحان می‌شوند.
- اگر هستهٔ موقتی برای URL test بالا نیاید، یک بار دیگر تلاش می‌شود — طوفانِ spawn واقعیتی
  دربارهٔ گوشی است، نه حکمی دربارهٔ سرور.

## 1. The report

> «url test در حال حاضر درست کار می‌کنه اما بعضی اوقات بعضی سرور ها که سالم هستند رو در
> پینگ، failed نشون میده… همچنین real delay هم. هر دو درست کار می‌کنند اما گاهی اوقات
> بعضی سرور ها را که سالم هستند را failed می‌زنند.»

Both statements are true at once, and that is the signature of a *budget* defect, not a routing
defect: a broken path fails every time, a mis-budgeted path fails only when the honest answer is
slower than the room the code gave it. A ~1 s-RTT route needs roughly five round trips for a cold
HTTPS fetch (resolver, TCP, TLS, request) — about five seconds against the default five-second
ping timeout. There is no slack for one lost packet, one slow resolver answer or one momentarily
throttled SNI. That is exactly "works most of the time".

## 2. Defect one — the URL test's fallback was dead code for timeouts

`SingBoxManager.testTargets` computed **one** deadline for the whole candidate walk and handed
each fallback target only what the previous target had left:

```kotlin
val deadline = System.nanoTime() + timeoutMs            // ONE budget for every target
for (url in urls.distinct().take(3)) {
    val left = deadline - System.nanoTime()
    if (left <= 0) break                                 // ← the fallback starved here
    last = measure(url, left)
    if (last.ok) return last
}
```

The first target of a freshly spawned throwaway core is the coldest request the product ever
makes: the core's own DNS bootstrap, TCP to the node, the node's protocol handshake, TLS to the
origin, then the fetch. When that legitimately consumed the whole configured timeout — the
normal case on a ~1 s route — the secondary (`google/generate_204`) and the CDN alternative
(`cloudflare/cdn-cgi/trace`) received `left ≈ 0` and the walk broke with the primary's failure.
`DelayTest.candidates` existed precisely for "this origin is filtered right now", but it could
only ever help failures that were *fast* (an immediate controller error). The failure mode it
was built for is the one it was starved for — and the fallback targets are *warm* by then, since
the core's route to the node is already established by the first attempt.

## 3. Defect two — Real delay's Home paths fetched exactly one origin

`BenchmarkEngine.measure` (the Rank sweep) already walked every candidate:

```kotlin
val targets = DelayTest.candidates(s.delayTestUrl)
val batch = targets.firstNotNullOfOrNull { target -> … }
```

The two Home paths did not:

- connected: `RouteProbe.realDelay` → `tunnelHttpsMeasure(url = DelayTest.url(...))` — the
  primary only;
- disconnected: `installRealDelayHook` built the throwaway tunnel and then measured that same
  single URL inside it.

A single origin that is momentarily SNI-throttled — the documented behaviour of this network
class, and the reason the rotating target pool exists (V144) — failed **every sample** of an
otherwise perfect route. The server connected fine when tapped; only its ping row was red.

## 4. The fix — one walk, full budget per target, owned in one place

`ProbeTargetWalk` (new, pure JVM) is the single walk both methods now share:

| Promise | Where |
|---|---|
| every candidate target gets the **full** per-target budget, clamped to the same `500..30_000` ms window the core's delay endpoint accepts | `perTargetBudgetMs` |
| the first target that answers wins — a healthy server never pays for the walk | `urlTest` / `realDelay` short-circuit |
| a silent origin hands the identical budget to the next candidate | both walks |
| at most three distinct targets, so a dead node's worst case stays bounded | `MAX_TARGETS` |
| the walk is interruptible between targets, so a cancel still stops it | `checkInterrupted()` per iteration |

Wiring:

- `SingBoxManager.urlTestProfileTargets` / `urlTestLiveTargets` → `ProbeTargetWalk.urlTest`
  (throwaway core and live controller, identical semantics on both).
- `RouteProbe.realDelay` (live tunnel) → `tunnelHttpsMeasureTargets` → `ProbeTargetWalk.realDelay`
  over `DelayTest.candidates`.
- `installRealDelayHook` → the same walk, **inside one throwaway core** — never one core per
  target. The spawn retry the hook already owned ("measured == null") is unchanged: a
  measurement that ran is never repeated, so a dead node still costs one core.
- `urlTestProfileTargets` additionally retries **once** when the child never came up — the same
  spawn-storm mercy `realDelayHook` and `BenchmarkEngine.measure` already extend. A genuinely
  unbuildable config exits instantly on both attempts; the retry costs milliseconds where it is
  pointless and saves a healthy node where it is not.

Worst-case cost for a *dead* node rises from one target's budget to at most three (the same
ceiling the Rank sweep has always paid), and the batch wall clock (`MARBLE_BATCH_DEADLINE_V144`)
still bounds every sweep; a healthy node's cost is unchanged, because the walk stops at the
first answer.

## 5. What this chapter deliberately does not re-fix

The `packages.xml` WARN and the `app/dns … context deadline exceeded` lines that accompany this
report are already answered by their own chapters: the package-manager warning is V157's
evidence marker (benign on a fixed build, and the self-test/classifier own the crashing-core
case), and the DoH deadline lines feed the V134 resolver-evidence loop that demotes
`8.8.8.8`/`9.9.9.9` in the emitted resolver order. No new machinery is added for either.

## 6. Guard

- `app/src/test/.../ProbeTransientTruthV159Test.kt` pins the walk: full budget per fallback
  target, short-circuit on the first answer, the three-target cap, the budget clamp, the honest
  failure of an all-silent walk, and cancellation between targets.
- `scripts/system-integrity-check.py` asserts the wiring: the shared-deadline `testTargets` must
  stay gone, both engines' URL-test entry points and both Real-delay Home paths must walk
  `ProbeTargetWalk` / `DelayTest.candidates`, and the URL-test spawn retry must stay.
