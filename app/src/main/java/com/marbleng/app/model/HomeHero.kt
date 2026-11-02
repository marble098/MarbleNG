package com.marbleng.app.model

/**
 * MARBLE_HOME_HERO_V261 — the numbers Home's hero reads, and the one function that decides what a
 * reader is told when the honest answer is "we do not know yet".
 *
 * The brief asked for a hero that carries the *essential data*: ping, jitter, connection uptime, the
 * server it is connected to and the address it is exiting from. Every one of those already existed
 * somewhere in the app — a chip, a caption, a card three sections down — and the hero printed one of
 * them. So the work is not measurement, it is **presentation with a contract**, and the contract is:
 *
 *  1. **A metric is a value, a unit and a state — never a sentence.** [HeroMetric] carries the digits
 *     separately from the unit so the type ladder can set `48.2` in display weight and `ms` beside it
 *     in label weight, and so Persian digits can be applied to the digits alone.
 *  2. **A missing metric is a dash, not a zero.** `0 ms` is a measurement; `—` is the absence of one.
 *     Every app that prints `0 ms` while it is connecting has taught its readers to distrust the
 *     number, and this is the file that refuses to.
 *  3. **A working metric says so.** While a probe runs, the metric's [HeroMetric.tone] is
 *     [HeroTone.WORKING] and its value is the ellipsis the rest of the product uses, so the hero
 *     pulses in exactly one place instead of three.
 *  4. **The four tiles are a *ranked* set.** Ping first because it answers "is it good?", jitter
 *     second because it answers "is it steady?", uptime third because it answers "how long?", and the
 *     exit address fourth because it answers "where from?". A hero that showed them in repository
 *     order would be a debug dump.
 *
 * Nothing here is Android and nothing here reads a repository: [HomeHeroSnapshot] is a value, so the
 * composable is testable without a device and the mapping is testable without a composition.
 */
object HomeHeroStudio {
    const val MARKER: String = "MARBLE_HOME_HERO_V261"

    /** How many metrics the hero's grid draws. Four fit two rows of two on a 360dp phone. */
    const val METRIC_COUNT: Int = 4

    /** The placeholder for a value nobody has measured. An em dash, not a zero. */
    const val ABSENT: String = "\u2014"

    /** The placeholder while a measurement is running. */
    const val WORKING: String = "\u2026"

    /**
     * The jitter a route is called *steady* under, and the ceiling above which it is called noisy.
     *
     * These are not made up for the hero: 30 ms is the point where a voice call starts to clip and
     * 80 ms is the point where it is unusable, so the two thresholds the hero colours against are the
     * two thresholds a reader already feels. Above [JITTER_NOISY_MS] the hero says so with the
     * warning tone rather than inventing a third colour for "quite bad".
     */
    const val JITTER_STEADY_MS: Int = 30
    const val JITTER_NOISY_MS: Int = 80

    /** Latency bands, the same pair the rest of the app grades a server with. */
    const val PING_FAST_MS: Int = 80
    const val PING_SLOW_MS: Int = 180

    /**
     * The ping a reader is shown, and *why*.
     *
     * While the tunnel is up the live RTT is the honest number — it is measured through the tunnel
     * that is actually carrying traffic. Before that, the only thing that exists is the last
     * measurement of the route the connect control would use, and showing the live one (which is
     * zero) would print `0 ms` for a route nobody has dialled.
     */
    fun ping(connected: Boolean, livePingMs: Int, selectedPingMs: Int): Int =
        if (connected) livePingMs.coerceAtLeast(0) else selectedPingMs.coerceAtLeast(0)

    /**
     * The jitter a reader is shown.
     *
     * The live jitter only exists while connected — it is the standard deviation of the live RTT
     * samples. Before that the hero falls back to the *last benchmark of the selected route*, which is
     * the only measurement the app has, and returns -1 when there is none so the tile can print a dash
     * instead of a confident zero.
     */
    fun jitter(connected: Boolean, liveJitterMs: Int, benchmarkedJitterMs: Double?): Int = when {
        connected && liveJitterMs > 0 -> liveJitterMs
        benchmarkedJitterMs != null && benchmarkedJitterMs >= 0.0 -> benchmarkedJitterMs.toInt()
        else -> -1
    }

    /** The band a latency falls in. `null` means "no measurement", which is not the same as slow. */
    fun pingTone(pingMs: Int): HeroTone? = when {
        pingMs <= 0 -> null
        pingMs <= PING_FAST_MS -> HeroTone.GOOD
        pingMs <= PING_SLOW_MS -> HeroTone.FAIR
        else -> HeroTone.POOR
    }

    /** The band a jitter falls in. */
    fun jitterTone(jitterMs: Int): HeroTone? = when {
        jitterMs < 0 -> null
        jitterMs <= JITTER_STEADY_MS -> HeroTone.GOOD
        jitterMs <= JITTER_NOISY_MS -> HeroTone.FAIR
        else -> HeroTone.POOR
    }

    /**
     * The whole hero, in the order it is drawn.
     *
     * A metric is [HeroTone.WORKING] while its own measurement is running, [HeroTone.PENDING] before
     * one has ever run against a route, and [HeroTone.IDLE] when there is simply nothing to measure
     * yet (no server chosen). The three are deliberately distinct: a spinner on a metric nobody can
     * measure is a lie about what the app is doing.
     */
    fun metrics(
        connected: Boolean,
        working: Boolean,
        pingMs: Int,
        jitterMs: Int,
        uptimeLabel: String,
        exitAddress: String,
        hasRoute: Boolean
    ): List<HeroMetric> {
        // Both latency readings come from one probe, so one spinner is the truth: while it runs,
        // neither of them has a value and *both* of them are the same "being measured".
        val ping = when {
            working -> HeroMetric(HeroMetricKind.PING, WORKING, "", HeroTone.WORKING)
            pingMs > 0 -> HeroMetric(
                HeroMetricKind.PING,
                pingMs.toString(),
                "ms",
                pingTone(pingMs) ?: HeroTone.IDLE
            )
            !hasRoute -> HeroMetric(HeroMetricKind.PING, ABSENT, "", HeroTone.IDLE)
            else -> HeroMetric(HeroMetricKind.PING, ABSENT, "", HeroTone.PENDING)
        }

        val jitter = when {
            working -> HeroMetric(HeroMetricKind.JITTER, WORKING, "", HeroTone.WORKING)
            jitterMs >= 0 -> HeroMetric(
                HeroMetricKind.JITTER,
                jitterMs.toString(),
                "ms",
                jitterTone(jitterMs) ?: HeroTone.IDLE
            )
            !hasRoute -> HeroMetric(HeroMetricKind.JITTER, ABSENT, "", HeroTone.IDLE)
            else -> HeroMetric(HeroMetricKind.JITTER, ABSENT, "", HeroTone.PENDING)
        }

        // The clock is a *session* reading, so it only exists while there is a session. Before one,
        // it is not "pending" — it is the one metric the app is not even trying to measure yet.
        val uptime = if (connected && uptimeLabel.isNotBlank()) {
            HeroMetric(HeroMetricKind.UPTIME, uptimeLabel, "", HeroTone.LIVE)
        } else {
            HeroMetric(HeroMetricKind.UPTIME, ABSENT, "", HeroTone.IDLE)
        }

        // The exit address is the one metric that is *not* about the tunnel: a phone that has never
        // connected still has a public address, and the app reads it on arrival. So a missing address
        // is a pending lookup rather than an idle reading.
        val exit = if (exitAddress.isNotBlank()) {
            HeroMetric(HeroMetricKind.EXIT, exitAddress, "", HeroTone.LIVE)
        } else {
            HeroMetric(HeroMetricKind.EXIT, ABSENT, "", HeroTone.PENDING)
        }

        return listOf(ping, jitter, uptime, exit)
    }

    /**
     * The hero's one-sentence status line, under the state headline.
     *
     * Short on purpose: the hero already carries four numbers, and a fifth sentence about the same
     * four numbers is how the old page became unreadable. It answers the *one* question the numbers
     * do not — which server — and nothing else.
     */
    fun statusLine(serverName: String, fallbackKey: String): String =
        serverName.ifBlank { fallbackKey }

    /**
     * The uptime tile's caption. The clock is a duration; a reader needs to know since *when*, and
     * "since" is the only word that turns `00:42:11` into information.
     */
    const val UPTIME_CAPTION_KEY: String = "Since you connected"

    /** The exit tile's caption. */
    const val EXIT_CAPTION_KEY: String = "Exit IP"
}

/** Which of the four hero metrics a tile is drawing. */
enum class HeroMetricKind(val id: String) {
    PING("ping"),
    JITTER("jitter"),
    UPTIME("uptime"),
    EXIT("exit")
}

/** The five things a metric can be, which is the whole tone vocabulary of the hero. */
enum class HeroTone {
    /** Measured and good. */
    GOOD,

    /** Measured and unremarkable. */
    FAIR,

    /** Measured and bad. */
    POOR,

    /** Being measured right now. */
    WORKING,

    /** There is something to measure but nobody has measured it yet. */
    PENDING,

    /** There is nothing to measure — no route, no address, no session. */
    IDLE,

    /** A live, continuously-true value (the uptime clock, the exit address). */
    LIVE
}

/**
 * One tile's worth of data.
 *
 * [value] holds the digits only when the metric is numeric, so the composable can set it in display
 * type and let the unit sit beside it in label type — the pairing every good telemetry card uses, and
 * the reason `48 ms` in one `Text` never lines up with itself across rows.
 */
data class HeroMetric(
    val kind: HeroMetricKind,
    val value: String,
    val unit: String,
    val tone: HeroTone
) {
    /** A metric that is neither measured nor being measured: it renders quietly, without a spinner. */
    val isPlaceholder: Boolean
        get() = tone == HeroTone.PENDING || tone == HeroTone.IDLE

    /** The metric the hero should pulse. Exactly one at a time, by construction of the tone. */
    val isWorking: Boolean
        get() = tone == HeroTone.WORKING
}

/**
 * Everything the hero draws, as one value.
 *
 * The page builds this once and hands it to the stage, so the composable has no repository, no
 * formatters and no decisions: it lays out a value. That split is what lets the hero be tested on the
 * host JVM — the arithmetic and the wording live here, and the composition only paints.
 */
data class HomeHeroSnapshot(
    val stateLabel: String,
    val overline: String,
    val routeName: String,
    val routeDetail: String,
    val countryCode: String?,
    val metrics: List<HeroMetric>,
    val working: Boolean
) {
    val ping: HeroMetric? get() = metrics.firstOrNull { it.kind == HeroMetricKind.PING }
    val jitter: HeroMetric? get() = metrics.firstOrNull { it.kind == HeroMetricKind.JITTER }
    val uptime: HeroMetric? get() = metrics.firstOrNull { it.kind == HeroMetricKind.UPTIME }
    val exit: HeroMetric? get() = metrics.firstOrNull { it.kind == HeroMetricKind.EXIT }
}
