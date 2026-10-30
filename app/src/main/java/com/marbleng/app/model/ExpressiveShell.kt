package com.marbleng.app.model

// MARBLE_EXPRESSIVE_SHELL_V241 — the decisions behind the one connect control, the live
// traffic graph, the server health dot, the smart presets and the quick-log overlay.
//
// Everything here is deliberately plain Kotlin: no Compose, no Android, no clocks. The UI
// layer paints these decisions and nothing else, so the rules the screens are built on can
// be pinned by `ExpressiveShellV241Test` on the JVM instead of by eye on a phone.
//
// Three complaints produced this file:
//   * Home asked the user to choose between five ways to do one thing (a power orb, a
//     slide-to-connect track and three deck cards). One control is the answer; the *mode*
//     is a chip, and [ConnectModeChip] is that chip's whole vocabulary.
//   * The servers page showed traffic only as four dead strings. [TrafficTimeline] turns a
//     stream of rate samples into the polyline a sparkline draws.
//   * "Gaming mode" existed only as a pile of raw knobs. [SmartPreset] bundles the same
//     knobs into three named decisions that can be applied in one tap and undone by
//     reapplying another.

/** Which way the one Home button should come up. */
enum class ConnectModeChip(
    val id: String,
    val label: String,
    val hint: String
) {
    /** Measure first, then connect to the route the ranker trusts most. */
    SMART(
        id = "smart",
        label = "Smart",
        hint = "Picks the best measured route, then connects"
    ),

    /** Everything on the device through the tunnel. */
    FULL(
        id = "full",
        label = "Full TUN",
        hint = "Every app on this device goes through the tunnel"
    ),

    /** Only apps pointed at the local SOCKS5 port. */
    LOCAL(
        id = "local",
        label = "Local proxy",
        hint = "Apps you point at the SOCKS5 port use the tunnel"
    );

    companion object {
        val DEFAULT: ConnectModeChip get() = SMART

        val ordered: List<ConnectModeChip> = listOf(SMART, FULL, LOCAL)

        fun fromId(raw: String?): ConnectModeChip =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * MARBLE_CONNECT_PILL_CRASH_V242 — the connected pill gives its side insets back to the
 * layout. A spatial spring may overshoot its target, and an overshoot past zero is a negative
 * padding argument (Compose throws `Padding must be non-negative`). The inset therefore uses a
 * non-overshooting effects spring in the UI and this final, finite clamp at the layout boundary.
 * Height and corner keep the expressive spatial spring; only the padding needs this contract.
 */
object ConnectPillMotion {
    const val RESTING_INSET_DP: Float = 20f

    /** Safe for every frame of an interrupted/reversed animation, including a stray NaN. */
    fun insetDp(raw: Float): Float =
        if (raw.isFinite()) raw.coerceIn(0f, RESTING_INSET_DP) else 0f
}

/** The chip that represents a persisted connection mode — the one the tunnel is running. */
fun connectModeChipOf(mode: ConnectionMode): ConnectModeChip =
    if (mode == ConnectionMode.FULL_TUN) ConnectModeChip.FULL else ConnectModeChip.LOCAL

/** The settings a chip stands for. SMART leaves the user's persisted mode alone. */
fun ConnectModeChip.applyTo(settings: AppSettings): AppSettings = when (this) {
    ConnectModeChip.SMART -> settings
    ConnectModeChip.FULL -> settings.copy(connectionMode = ConnectionMode.FULL_TUN)
    ConnectModeChip.LOCAL -> settings.copy(connectionMode = ConnectionMode.LOCAL_PROXY)
}

/** How a server's last probe reads, as one of three calm states instead of five colours. */
enum class ServerHealth(val id: String, val label: String) {
    /** Answered well under the amber line. */
    GOOD("good", "Good"),

    /** Answered, but slowly — worth a second look, not an alarm. */
    SLOW("slow", "Slow"),

    /** Asked and never answered. */
    DEAD("dead", "No reply"),

    /** Nothing has been measured yet. */
    UNTESTED("untested", "Untested");

    companion object {
        /** The amber line, in milliseconds. Under it is good, over it is slow. */
        const val SLOW_LINE_MS: Double = 220.0

        fun fromId(raw: String?): ServerHealth =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: UNTESTED
    }
}

/**
 * Classify a measurement. A failed probe is [ServerHealth.DEAD] whatever latency it recorded,
 * because a zero latency is not a fast server — it is no answer at all.
 */
fun serverHealthOf(latencyMs: Double, success: Boolean): ServerHealth = when {
    !success -> ServerHealth.DEAD
    latencyMs <= 0.0 -> ServerHealth.UNTESTED
    latencyMs < ServerHealth.SLOW_LINE_MS -> ServerHealth.GOOD
    else -> ServerHealth.SLOW
}

/**
 * Scroll distance, in pixels, over which a page's display title collapses into its toolbar.
 * Pure data so the curve below can be asserted on the JVM.
 */
const val HEADER_COLLAPSE_DISTANCE_PX: Int = 120

/**
 * How far a list has scrolled towards a collapsed title, 0..1.
 *
 * The first screenful collapses linearly over [distancePx]; any page after it is already
 * fully collapsed, so the title never re-expands mid-list.
 */
fun headerCollapseFraction(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    distancePx: Int = HEADER_COLLAPSE_DISTANCE_PX
): Float {
    if (firstVisibleItemIndex > 0) return 1f
    if (distancePx <= 0) return 1f
    return (firstVisibleItemScrollOffset.toFloat() / distancePx.toFloat()).coerceIn(0f, 1f)
}

/** One moment of tunnel throughput. Both numbers are bytes per second. */
data class TrafficSample(
    val downBps: Long,
    val upBps: Long
) {
    val totalBps: Long get() = downBps.coerceAtLeast(0L) + upBps.coerceAtLeast(0L)
}

/**
 * A fixed-length window of throughput samples and the arithmetic a sparkline needs.
 *
 * The graph is drawn against the *window's own* peak rather than a fixed scale: a 40 KB/s
 * link and a 40 MB/s link both fill the same height, which is what "is data moving?" asks.
 * The floor keeps a single stray packet from flattening every other sample into the axis.
 */
class TrafficTimeline(val capacity: Int = DEFAULT_CAPACITY) {

    private val window = ArrayDeque<TrafficSample>()

    init {
        require(capacity > 1) { "a sparkline needs at least two slots" }
    }

    val size: Int get() = window.size

    fun push(sample: TrafficSample): TrafficTimeline {
        window.addLast(sample)
        while (window.size > capacity) window.removeFirst()
        return this
    }

    fun push(downBps: Long, upBps: Long): TrafficTimeline =
        push(TrafficSample(downBps.coerceAtLeast(0L), upBps.coerceAtLeast(0L)))

    fun snapshot(): List<TrafficSample> = window.toList()

    fun clear() = window.clear()

    fun peakBps(): Long = trafficPeak(snapshot().map { it.totalBps })

    /** True when the window holds nothing worth drawing. */
    fun isQuiet(): Boolean = peakBps() <= 0L

    companion object {
        /** One minute of one-second samples: long enough to see a burst, short enough to read. */
        const val DEFAULT_CAPACITY: Int = 60
    }
}

/** The largest sample in [values]; zero when there is nothing to show. */
fun trafficPeak(values: List<Long>): Long =
    values.maxOrNull()?.coerceAtLeast(0L) ?: 0L

/**
 * Normalise [values] to 0..1 against [peak]. An all-zero window stays on the floor, so the
 * graph reads as a quiet line rather than as a blank box.
 */
fun trafficSeries(values: List<Long>, peak: Long = trafficPeak(values)): List<Float> {
    if (values.isEmpty()) return emptyList()
    val ceiling = peak.coerceAtLeast(1L)
    return values.map { value ->
        (value.coerceAtLeast(0L).toFloat() / ceiling.toFloat()).coerceIn(MIN_FRACTION, 1f)
    }
}

/** The resting height of a quiet sample: a visible floor line, never the axis itself. */
const val MIN_FRACTION: Float = 0.06f

/**
 * Points for a polyline through [values], in a [width] × [height] box with the origin at the
 * top-left (the direction a Canvas draws in). One sample at the right edge, the oldest at the
 * left edge; a single sample is a point, not a division by zero.
 */
fun sparklinePoints(
    values: List<Float>,
    width: Float,
    height: Float
): List<Pair<Float, Float>> {
    if (values.isEmpty() || width <= 0f || height <= 0f) return emptyList()
    val step = if (values.size == 1) 0f else width / (values.size - 1).toFloat()
    val bottom = height
    return values.mapIndexed { index, raw ->
        val value = raw.coerceIn(0f, 1f)
        (index * step).coerceIn(0f, width) to (bottom - value * bottom)
    }
}

/** The area under a sparkline, as the closed path a filled gradient needs. */
fun sparklineAreaPoints(
    values: List<Float>,
    width: Float,
    height: Float
): List<Pair<Float, Float>> {
    val line = sparklinePoints(values, width, height)
    if (line.isEmpty()) return emptyList()
    return buildList {
        add(line.first().first to height)
        addAll(line)
        add(line.last().first to height)
    }
}

/** One line of the quick-log overlay, already classified. */
data class QuickLogLine(
    val level: LogLevel,
    val text: String
)

/** The four levels the overlay tints for, plus the neutral line everything else gets. */
enum class LogLevel(val id: String) {
    ERROR("error"),
    WARN("warn"),
    INFO("info"),
    DEBUG("debug"),
    PLAIN("plain");

    companion object {
        fun fromId(raw: String?): LogLevel =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: PLAIN
    }
}

private val ERROR_TOKENS = listOf("error", "fatal", "panic", "failed", "failure", "[err]")
private val WARN_TOKENS = listOf("warn", "warning", "[warn]", "deprecated", "timeout", "retry")
private val INFO_TOKENS = listOf("info", "[info]", "started", "connected", "listening", "ready")
private val DEBUG_TOKENS = listOf("debug", "trace", "[debug]")

/**
 * Classify one core-log line. The first decisive token wins, and the search is
 * case-insensitive because the two cores disagree about capitalisation.
 */
fun classifyLogLine(line: String): LogLevel {
    val lower = line.lowercase()
    return when {
        ERROR_TOKENS.any { lower.contains(it) } -> LogLevel.ERROR
        WARN_TOKENS.any { lower.contains(it) } -> LogLevel.WARN
        INFO_TOKENS.any { lower.contains(it) } -> LogLevel.INFO
        DEBUG_TOKENS.any { lower.contains(it) } -> LogLevel.DEBUG
        else -> LogLevel.PLAIN
    }
}

/**
 * The last [limit] non-blank lines of a raw log tail, oldest first, classified.
 *
 * A tail opened at an arbitrary offset usually begins mid-line; that partial first line is
 * dropped so the overlay never shows half a timestamp as if it were the whole story.
 */
fun quickLogLines(raw: String, limit: Int = DEFAULT_QUICK_LOG_LINES): List<QuickLogLine> {
    if (limit <= 0) return emptyList()
    val all = raw.split('\n')
    val body = if (all.size > 1) all.drop(1) else all
    val kept = body.asSequence()
        .map { it.trimEnd('\r').trimEnd() }
        .filter { it.isNotBlank() }
        .toList()
    return kept.takeLast(limit).map { QuickLogLine(level = classifyLogLine(it), text = it) }
}

/** How many lines the overlay keeps — one screen of scroll-back, not a file viewer. */
const val DEFAULT_QUICK_LOG_LINES: Int = 160

/** The three named decisions that replace a wall of raw knobs. */
enum class SmartPreset(
    val id: String,
    val title: String,
    val body: String
) {
    GAMING(
        id = "gaming",
        title = "Gaming",
        body = "Full power and quicker connects. Made for games, not downloads."
    ),
    STREAMING(
        id = "streaming",
        title = "Streaming",
        body = "Built for speed: bigger buffers and smoother video."
    ),
    EXTREME_BYPASS(
        id = "extreme_bypass",
        title = "Blocked network",
        body = "For strict filtering: Iran mode on, small packets and more retries."
    );

    /**
     * The settings this preset writes. It only touches tuning knobs — routing, DNS, the
     * split tunnel and the selected server keep their values, because a preset that moves
     * the user's identity is not a preset, it is a surprise.
     */
    fun apply(settings: AppSettings): AppSettings = when (this) {
        GAMING -> settings.copy(
            powerProfile = PowerProfile.TURBO.id,
            workloadProfile = WorkloadProfile.INTERACTIVE,
            smartJitter = settings.smartJitter.copy(mode = JitterSmartMode.AGGRESSIVE.id),
            fragmentStrategy = FragmentStrategy.AUTO.id,
            adaptiveBufferEnabled = true,
            raceConnectEnabled = true,
            raceWidth = settings.raceWidth.coerceAtLeast(4)
        )

        STREAMING -> settings.copy(
            powerProfile = PowerProfile.BALANCED.id,
            workloadProfile = WorkloadProfile.STREAMING,
            smartJitter = settings.smartJitter.copy(mode = JitterSmartMode.BALANCED.id),
            muxEnabled = true,
            muxConcurrency = settings.muxConcurrency.coerceAtLeast(8),
            adaptiveThroughputEnabled = true,
            adaptiveBufferEnabled = true
        )

        EXTREME_BYPASS -> settings.copy(
            powerProfile = PowerProfile.TURBO.id,
            workloadProfile = WorkloadProfile.STEALTH,
            smartJitter = settings.smartJitter.copy(mode = JitterSmartMode.AGGRESSIVE.id),
            iranModePolicy = IranModePolicy.ALWAYS_ON,
            iranModeCountermeasures = true,
            fragmentStrategy = FragmentStrategy.RECORD.id,
            adaptiveMtuEnabled = true,
            mtuMax = settings.mtuMax.coerceAtMost(1420).coerceAtLeast(settings.mtuMin),
            raceConnectEnabled = true,
            raceWidth = settings.raceWidth.coerceAtLeast(6)
        )
    }

    companion object {
        val ordered: List<SmartPreset> = listOf(GAMING, STREAMING, EXTREME_BYPASS)

        fun fromId(raw: String?): SmartPreset? =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) }
    }
}

/**
 * The preset the settings currently look like, or null when the user has tuned something
 * themselves. Only the knobs a preset writes are compared, so an unrelated change (a new
 * server, a different DNS) never makes a preset look "off".
 */
fun matchSmartPreset(settings: AppSettings): SmartPreset? =
    SmartPreset.entries.firstOrNull { preset -> signatureOf(preset.apply(settings)) == signatureOf(settings) }

private fun signatureOf(settings: AppSettings): List<Any> = listOf(
    settings.powerProfile,
    settings.workloadProfile.name,
    settings.smartJitter.mode.lowercase(),
    settings.fragmentStrategy.lowercase(),
    settings.muxEnabled,
    settings.muxConcurrency,
    settings.iranModePolicy.name,
    settings.iranModeCountermeasures,
    settings.mtuMax,
    settings.raceConnectEnabled,
    settings.raceWidth
)
