# MARBLE_HIGH_JITTER_SHIELD_V206 — very high jitter

*Core:* `app/src/main/java/com/marbleng/app/core/HighJitterShield.kt`
*Tests:* `app/src/test/java/com/marbleng/app/core/HighJitterShieldTest.kt`
*Wiring:* `MarbleVpnService.sampleRouteLatency` → `observeHighJitter`, `shouldSampleRoute` →
`highJitterProbeCadenceTicks`, `maybeAccelerate` → the urgency gate.

---

## 1. The complaint

"Very high jitter" is not one number going up. It is three different failures that the product
used to answer with the same blunt instrument:

| Symptom on a Tehran evening | What the old code concluded | What is actually true |
|---|---|---|
| One stalled TLS handshake in a 24-sample window | "the link has 180 ms of jitter" | one *packet* stalled; the link is fine |
| A route alternating between a filtered path and its failover | (mean-based) "very jittery" / (MAD-based) "0 ms, perfectly smooth" | the link has two modes; the truth is the distance between them |
| A fibre line at 95 ms of jitter vs Iranian mobile at 95 ms | identical verdict | one is dying, the other is a Tuesday |

The response was binary (`JitterControlPolicy.active`), so a 24 ms link and a 900 ms link
received the same treatment, and the transition between them was a **step**: the probe cadence
halved in a single tick. Steps are what a user perceives as flapping.

And the cost was unbounded in the wrong direction — the worse the link, the more the engine
measured it, on the very link that could least afford the traffic.

## 2. The mechanism

Three parts, in this order.

### 2.1 Measure it honestly — `RobustWindow`

* A fixed ring buffer of the last 24 samples. Two `DoubleArray`s allocated once, in the
  constructor; **no allocation ever again**.
* Dispersion is the **interquartile range** scaled to a sigma-equivalent (`IQR / 1.349`),
  *not* the standard deviation and *not* the median absolute deviation:
  * the standard deviation is destroyed by one stalled packet;
  * the MAD is **zero** on a bimodal window (more than half the samples sit on the median),
    which is exactly the shape a route with a failover produces. The test
    `a bimodal window reads as jittery, not as perfectly smooth` pins this.
* A **spike ratio**: the fraction of samples outside Tukey's fence (quartiles ± 1.5·IQR). A
  robust estimator can only ever flag a *minority* of its window, so this is deliberately a
  minority detector — it separates "noisy" from "noisy plus a few packets from another planet",
  which the dispersion alone cannot.
* The statistics are **dirty-flagged**: `add()` marks the window dirty and returns; the two
  sorts happen on the first read afterwards. A monitor that writes ten samples and reads one
  statistic pays for one sort, not ten (`evaluations` proves it).

### 2.2 Judge it against the link itself — `observe`

* A **baseline** is learned from the link's calm readings, with two rules whose order is the
  design: it is **seeded once** from the first graded reading (only if that reading is itself
  plausible as a calm one — a link first met mid-storm does not get to define its own normal),
  and after that **only calm evidence moves it**.
* Severity is `jitter / max(ABSOLUTE_CALM_MS, baseline · BASELINE_HEADROOM)` — relative to the
  link, with an absolute floor so a link that has never been calm is still graded.
* A spiky window escalates the verdict one step.

### 2.3 Spend a bounded amount on it — `Plan`

* The mitigation is one **continuous level**, `0..1`, chased through an asymmetric filter:
  attack 0.55 when the link worsens, release 0.06 when it recovers, and a 20 s **hold** after an
  escalation during which the level may rise but never fall. An **idle** link (20 s without
  evidence) decays at 0.25, so a phone in a pocket does not wake up already escalated.
* The level drives a **probe burst** (2→3, hedged: the fastest verified answer wins) and a
  **cadence** (30 ticks → 6 ticks), and the product of the two is capped by
  `MAX_PROBES_PER_MINUTE`. Mitigation may cost bandwidth; never an unbounded amount of it.
* At EXTREME, after a streak and outside a cooldown, it asks the route optimizer for another
  route — through the same request flag the Rank action uses, so the optimizer's own guards
  (cooldown, thermal budget, Identity Guard) keep the last word.

## 3. The critique of the first cut, and what it became (V207)

The first cut of this file shipped the right estimator with the wrong controller.

| # | Defect in V206 | Fix |
|---|---|---|
| A | Sorted inside `add()`: O(n log n) per sample **even when nobody read a statistic** | Dirty flag; recompute lazily, once, on the first read after a write |
| B | Read `System.currentTimeMillis()`; one NTP correction is a negative interval | The shield never reads a clock. The caller supplies the timestamp it already owns |
| C | Four fixed levels (0 / .35 / .70 / 1.0): crossing a threshold moved the cadence by a third in one tick | A continuous level chased by an asymmetric filter, with hold and idle decay. The verdict is now only the level's label |
| D | An idle link held its level forever; the next reconnect started escalated | Idle decay at 0.25 — stale evidence fades, it does not vanish |
| E | MAD dispersion collapsed to zero on a bimodal link — the worst jitter of the evening reported as a perfectly smooth 0 ms | IQR dispersion, which for that window is the distance between the two modes |

`naiveJitterMs` (mean of consecutive deltas) is kept **only** as the counter-example the tests
assert against: `one stalled handshake does not move the robust jitter but destroys the mean`
feeds both estimators the same window and shows the mean panicking at 125 ms while the robust
estimate does not move.

## 4. What it costs

* Per sample: one array write, one comparison, and a dirty flag. O(1), no allocation.
* Per *read* after a change: two sorts of ≤ 24 elements into a pre-allocated buffer.
* Per burst: one object allocation (`Plan` + `State`), on the route-monitor thread.
* The burst never drops to one probe: the first sample of a burst is the number the user reads
  as their ping, and it takes a second to say anything about variation — `MIN_BURST` is 2.
* The shield adds no thread, no timer and no frame callback.

## 5. Where it is wired

* `MarbleVpnService.observeHighJitter(session, rttSamples)` — every verified sample of a burst,
  downstream of the publication so the honest measurement reaches the UI first.
* `MarbleVpnService.highJitterProbeCadenceTicks()` — the cadence is the tighter of the
  hysteresis latch and the shield, keeping the latch's instant reaction and adding the
  shield's proportionality.
* `MarbleVpnService.maybeAccelerate` — the tuner's urgency gate now reads the continuous level
  in addition to the latch, so a route turning bad *between* two latch transitions is still
  measured urgently.
* `resetHighJitterShield()` on every connect and every network change: the baseline belongs to
  the link that was just left behind.

Diagnostics rows: `ROUTE jitter-shield-verdict` (with `from`, `to`, `detail`) and
`ROUTE jitter-shield-rerank-requested`.
