package com.marbleng.app.core

import java.util.Locale
import kotlin.math.abs

/**
 * MARBLE_NOTIFICATION_COUNCIL_V231 — the notification app's text engine.
 *
 * `SmartNotifier` used to be both the copywriter and the platform plumbing: every string was an
 * English literal at a call site, every number was glued to a Persian sentence without any bidi
 * isolation, and every helper that decided *what the notification says* lived inside a class that
 * needs a `Context`, so none of it could be tested. This file is the copy half, pure Kotlin:
 *
 *  - the channel taxonomy (ids, localized names, localized descriptions) in one table;
 *  - the status vocabulary (one word per state, in both languages);
 *  - bidi-safe composition for a Persian UI (`FSI … PDI` around every Latin/numeric run, `RLM`
 *    in front of a Persian line) — the defect that made `↓ 1.2 MB/s · 42 ms` render backwards on
 *    an RTL device;
 *  - grapheme-safe ellipsis for titles and texts (`String.take()` can cut a surrogate pair in half
 *    and can leave a dangling word);
 *  - a bounded cooldown map and a stable notification-id allocator, both previously unbounded
 *    collections inside the notifier.
 *
 * No Android import on purpose: `NotificationCopyV231Test` runs it on the host JVM.
 */
enum class NotificationLanguage(val id: String) {
    EN("en"),
    FA("fa");

    companion object {
        fun fromId(raw: String?): NotificationLanguage =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: EN
    }
}

/**
 * Where a notification tap lands. The ids travel as an intent extra, so they are stable strings and
 * never an enum ordinal.
 */
enum class NotificationRoute(val id: String) {
    DASHBOARD("dashboard"),
    NOTIFICATIONS("notifications"),
    DIAGNOSTICS("diagnostics"),
    SUBSCRIPTIONS("subscriptions"),
    SERVERS("servers");

    companion object {
        fun fromId(raw: String?): NotificationRoute =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: DASHBOARD
    }
}

/**
 * The four channels Android's own notification screen shows. One importance story per channel:
 * the tunnel is silent and unbounded, an alert is a badge, a privacy alarm is allowed to speak,
 * and an update is quiet. The ids are the shipped ones (they are user-visible state on an installed
 * device, so they may never be renamed).
 */
enum class NotificationChannelKey(val id: String) {
    CONNECTION("marbleng-vpn"),
    ALERTS("marbleng-smart"),
    PRIVACY("marbleng-privacy"),
    UPDATES("marbleng-updates");

    companion object {
        fun fromId(raw: String?): NotificationChannelKey? =
            entries.firstOrNull { it.id == raw }

        /** The order the settings page lists them in: status first, then the three alert kinds. */
        val menuOrder: List<NotificationChannelKey> = listOf(CONNECTION, ALERTS, PRIVACY, UPDATES)
    }
}

object NotificationCopy {
    const val MARKER: String = "MARBLE_NOTIFICATION_COUNCIL_V231"

    const val FSI: Char = '\u2068'
    const val PDI: Char = '\u2069'
    const val RLM: Char = '\u200F'

    const val TITLE_MAX: Int = 64
    const val TEXT_MAX: Int = 180
    const val BIG_TEXT_MAX: Int = 640
    const val SUB_TEXT_MAX: Int = 32

    // ------------------------------------------------------------------------------------------
    // language
    // ------------------------------------------------------------------------------------------

    /**
     * The language the notification speaks, resolved from the app setting and the device locale —
     * the same rule `ProvideMarbleLanguage` applies to the UI, so a Persian app never posts an
     * English notification.
     */
    fun languageFor(appLanguageId: String?, deviceLanguage: String?): NotificationLanguage =
        when (appLanguageId?.trim()?.lowercase(Locale.ROOT)) {
            "fa", "persian" -> NotificationLanguage.FA
            "en", "english" -> NotificationLanguage.EN
            else -> if ((deviceLanguage ?: "").trim().lowercase(Locale.ROOT) in PERSIAN_LANGUAGES) {
                NotificationLanguage.FA
            } else {
                NotificationLanguage.EN
            }
        }

    fun t(language: NotificationLanguage, english: String, persian: String): String =
        if (language == NotificationLanguage.FA) persian else english

    // ------------------------------------------------------------------------------------------
    // bidi
    // ------------------------------------------------------------------------------------------

    /** A Latin/numeric run, wrapped so an RTL paragraph cannot reorder it. */
    fun isolate(text: String): String =
        if (text.isEmpty()) "" else "$FSI$text$PDI"

    fun ltrSegment(text: String, language: NotificationLanguage): String =
        if (language == NotificationLanguage.FA) isolate(text) else text

    /** A Persian line, pinned to the RTL base direction even inside a left-aligned shade. */
    fun rtlLine(text: String, language: NotificationLanguage): String =
        if (language == NotificationLanguage.FA && text.isNotEmpty()) "$RLM$text" else text

    // ------------------------------------------------------------------------------------------
    // measurements
    // ------------------------------------------------------------------------------------------

    fun ellipsize(text: String, max: Int): String {
        if (max <= 0) return ""
        val normalized = text.replace('\n', ' ').replace('\r', ' ').trim()
        if (normalized.isEmpty()) return ""
        if (normalized.codePointCount(0, normalized.length) <= max) return normalized

        val hard = normalized.offsetByCodePoints(0, max)
        val space = normalized.lastIndexOf(' ', hard)
        val cut = if (space > 0 && space >= hard - 18) space else hard
        val head = normalized.substring(0, cut).trimEnd('.', ',', ';', ':', '-', '·', '•', ' ')
        if (head.isEmpty() || head == normalized) return normalized
        return "$head…"
    }

    fun rate(bytesPerSecond: Long): String = when {
        bytesPerSecond >= 1024L * 1024L ->
            String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        bytesPerSecond >= 1024L -> String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
        else -> "$bytesPerSecond B/s"
    }

    fun latency(pingMs: Int): String = if (pingMs > 0) "$pingMs ms" else "— ms"

    /** `42 ms · ↓ 1.2 MB/s · ↑ 320 KB/s` — one isolated segment, so RTL cannot reverse it. */
    fun telemetryCompact(
        language: NotificationLanguage,
        pingMs: Int,
        downBps: Long,
        upBps: Long,
        includePing: Boolean = true
    ): String {
        val parts = ArrayList<String>(3)
        if (includePing) parts += latency(pingMs)
        parts += "↓ ${rate(downBps)}"
        parts += "↑ ${rate(upBps)}"
        return ltrSegment(parts.joinToString(" · "), language)
    }

    /** The expanded (BigText) version, in words rather than symbols. */
    fun telemetryExpanded(
        language: NotificationLanguage,
        pingMs: Int,
        downBps: Long,
        upBps: Long,
        includePing: Boolean = true
    ): String {
        val fa = language == NotificationLanguage.FA
        val parts = ArrayList<String>(3)
        if (includePing) {
            parts += if (fa) {
                "تأخیر ${isolate(latency(pingMs))}"
            } else {
                "Latency ${latency(pingMs)}"
            }
        }
        parts += if (fa) {
            "دانلود ${isolate(rate(downBps))}"
        } else {
            "Download ${rate(downBps)}"
        }
        parts += if (fa) {
            "آپلود ${isolate(rate(upBps))}"
        } else {
            "Upload ${rate(upBps)}"
        }
        return parts.joinToString(" • ")
    }

    // ------------------------------------------------------------------------------------------
    // the status vocabulary — one word per state, both languages
    // ------------------------------------------------------------------------------------------

    fun statusTitle(status: ConnectionStatus, language: NotificationLanguage): String =
        when (status) {
            ConnectionStatus.CONNECTING -> t(language, "Connecting…", "در حال اتصال…")
            ConnectionStatus.PROTECTED -> t(language, "Protected", "محافظت‌شده")
            ConnectionStatus.RECOVERING -> t(language, "Reconnecting…", "اتصال دوباره…")
            ConnectionStatus.BLOCKED -> t(language, "Traffic blocked", "ترافیک مسدود شد")
            ConnectionStatus.DISCONNECTED -> t(language, "Disconnected", "قطع شد")
        }

    fun statusDetail(status: ConnectionStatus, language: NotificationLanguage): String =
        when (status) {
            ConnectionStatus.CONNECTING -> t(
                language,
                "Negotiating the route — traffic waits until it is up",
                "در حال آماده‌سازی مسیر — ترافیک تا آماده‌شدن منتظر می‌ماند"
            )
            ConnectionStatus.PROTECTED -> t(
                language,
                "Your traffic leaves through this route",
                "ترافیک شما از این مسیر خارج می‌شود"
            )
            ConnectionStatus.RECOVERING -> t(
                language,
                "Moving traffic to another route",
                "انتقال ترافیک به مسیر دیگر"
            )
            ConnectionStatus.BLOCKED -> t(
                language,
                "Kill switch is holding traffic",
                "کلید اضطراری ترافیک را نگه داشته است"
            )
            ConnectionStatus.DISCONNECTED -> t(
                language,
                "No tunnel is active",
                "هیچ تونلی فعال نیست"
            )
        }

    /** The `subText` of the ongoing notification: the state, never the app name twice. */
    fun statusSubText(status: ConnectionStatus, language: NotificationLanguage): String =
        when (status) {
            ConnectionStatus.CONNECTING -> t(language, "Connecting", "اتصال")
            ConnectionStatus.PROTECTED -> t(language, "Protected", "محافظت")
            ConnectionStatus.RECOVERING -> t(language, "Recovering", "بازیابی")
            ConnectionStatus.BLOCKED -> t(language, "Blocked", "مسدود")
            ConnectionStatus.DISCONNECTED -> t(language, "Idle", "بی‌کار")
        }

    fun actionChangeServer(language: NotificationLanguage): String =
        t(language, "Server", "سرور")

    fun actionChangeSubscription(language: NotificationLanguage): String =
        t(language, "Subscription", "اشتراک")

    fun actionDisconnect(language: NotificationLanguage): String =
        t(language, "Disconnect", "قطع اتصال")

    fun actionReconnect(language: NotificationLanguage): String =
        t(language, "Reconnect", "اتصال دوباره")

    fun actionManageAlerts(language: NotificationLanguage): String =
        t(language, "Manage", "مدیریت")

    fun actionRestore(language: NotificationLanguage): String =
        t(language, "Restore", "بازیابی")

    // ------------------------------------------------------------------------------------------
    // MARBLE_NOTIFICATION_SWITCH_V234 — the two verbs that move the tunnel without opening the app
    // ------------------------------------------------------------------------------------------

    /**
     * Short on purpose: an action button on the shade shows roughly a dozen characters before it
     * ellipsizes, and a truncated verb is worse than a short one.
     */
    fun actionNextServer(language: NotificationLanguage): String =
        t(language, "Next server", "سرور بعدی")

    fun actionNextSubscription(language: NotificationLanguage): String =
        t(language, "Next sub", "اشتراک بعدی")

    /**
     * The detail lines the tunnel service hands to the shade. They are English constants on purpose:
     * the service is language-free (it publishes a state, not a translation), and every one of these
     * four strings is a key in [FaCopy], so the notifier can render it in the app's own language.
     */
    const val DETAIL_SWITCHING_SERVER: String = "Switching to the next server"
    const val DETAIL_SWITCHING_SUBSCRIPTION: String = "Switching to the next subscription"
    const val DETAIL_NO_OTHER_SERVER: String = "No other server to switch to"
    const val DETAIL_NO_OTHER_SUBSCRIPTION: String = "No other subscription to switch to"

    /** The detail line while the shade is switching: the badge keeps its promise, so does the text. */
    fun cycleBusyEnglish(kind: NotificationCycleKind): String = when (kind) {
        NotificationCycleKind.SERVER -> DETAIL_SWITCHING_SERVER
        NotificationCycleKind.SUBSCRIPTION -> DETAIL_SWITCHING_SUBSCRIPTION
    }

    /**
     * The only honest answer when there is nothing else to switch to. It is shown where the route
     * line would be, so a tap on a verb that cannot move anything explains itself instead of
     * leaving the shade exactly as it was.
     */
    fun cycleEmptyEnglish(kind: NotificationCycleKind): String = when (kind) {
        NotificationCycleKind.SERVER -> DETAIL_NO_OTHER_SERVER
        NotificationCycleKind.SUBSCRIPTION -> DETAIL_NO_OTHER_SUBSCRIPTION
    }

    fun cycleBusyDetail(kind: NotificationCycleKind, language: NotificationLanguage): String =
        translate(cycleBusyEnglish(kind), language)

    fun cycleEmptyDetail(kind: NotificationCycleKind, language: NotificationLanguage): String =
        translate(cycleEmptyEnglish(kind), language)

    // ------------------------------------------------------------------------------------------
    // channels
    // ------------------------------------------------------------------------------------------

    fun channelTitle(key: NotificationChannelKey, language: NotificationLanguage): String =
        when (key) {
            NotificationChannelKey.CONNECTION -> t(language, "Tunnel status", "وضعیت تونل")
            NotificationChannelKey.ALERTS -> t(language, "Route alerts", "هشدارهای مسیر")
            NotificationChannelKey.PRIVACY -> t(language, "Privacy alarms", "هشدارهای حریم خصوصی")
            NotificationChannelKey.UPDATES -> t(language, "Updates", "به‌روزرسانی‌ها")
        }

    fun channelDescription(key: NotificationChannelKey, language: NotificationLanguage): String =
        when (key) {
            NotificationChannelKey.CONNECTION -> t(
                language,
                "The ongoing tunnel item: state, route, live rates and session time",
                "آیتم همیشگی تونل: وضعیت، مسیر، سرعت زنده و زمان نشست"
            )
            NotificationChannelKey.ALERTS -> t(
                language,
                "Route changes, failover and recovery results",
                "تغییر مسیر، جایگزینی و نتیجه بازیابی"
            )
            NotificationChannelKey.PRIVACY -> t(
                language,
                "Kill switch, blocked traffic and identity holds",
                "کلید اضطراری، ترافیک مسدود و نگه‌داشتن هویت"
            )
            NotificationChannelKey.UPDATES -> t(
                language,
                "Subscription refreshes, core and app updates",
                "به‌روزرسانی اشتراک‌ها، هسته و برنامه"
            )
        }

    fun channelFocus(key: NotificationChannelKey, language: NotificationLanguage): String =
        when (key) {
            NotificationChannelKey.CONNECTION -> t(language, "Silent · no badge", "بی‌صدا · بدون نشان")
            NotificationChannelKey.ALERTS -> t(language, "Default · badge", "معمولی · با نشان")
            NotificationChannelKey.PRIVACY -> t(language, "Heads-up · sound", "نمایان · با صدا")
            NotificationChannelKey.UPDATES -> t(language, "Quiet · badge", "آرام · با نشان")
        }

    // ------------------------------------------------------------------------------------------
    // translation of call-site strings
    // ------------------------------------------------------------------------------------------

    /**
     * Persian for a notification string. Unlike the UI's fallback translator this one **never**
     * transliterates: a notification is read at a glance on a lock screen, and `Kill switch is
     * holding traffic` spelled in Persian letters is worse than the English original. Unknown copy
     * stays English until someone adds it to [FaCopy].
     */
    fun translate(text: String, language: NotificationLanguage): String {
        if (language != NotificationLanguage.FA) return text
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return text

        FaCopy[trimmed]?.let { return it }

        for ((regex, render) in FaPatterns) {
            val match = regex.matchEntire(trimmed) ?: continue
            return render(match)
        }

        // A sentence made of known clauses joined by the product's own separators: translate the
        // clauses that are known and keep the rest, so `Route recovered • 42 ms` still reads.
        if (trimmed.any { it in CLAUSE_SEPARATORS }) {
            val pieces = splitKeepingSeparators(trimmed)
            if (pieces.size > 1) {
                var translatedAny = false
                val rendered = StringBuilder()
                pieces.forEach { piece ->
                    if (piece.length == 1 && piece[0] in CLAUSE_SEPARATORS) {
                        rendered.append(piece)
                        return@forEach
                    }
                    val lead = piece.takeWhile { it.isWhitespace() }
                    val trail = piece.takeLastWhile { it.isWhitespace() }
                    val core = piece.trim()
                    val hit = if (core.isEmpty()) null else FaCopy[core] ?: faPattern(core)
                    if (hit != null) translatedAny = true
                    rendered.append(lead).append(hit ?: core).append(trail)
                }
                if (translatedAny) return rendered.toString()
            }
        }
        return text
    }

    fun faPattern(text: String): String? {
        for ((regex, render) in FaPatterns) {
            val match = regex.matchEntire(text) ?: continue
            return render(match)
        }
        return null
    }

    private fun splitKeepingSeparators(text: String): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        text.forEach { char ->
            if (char in CLAUSE_SEPARATORS) {
                if (current.isNotEmpty()) {
                    out += current.toString()
                    current.setLength(0)
                }
                out += char.toString()
            } else {
                current.append(char)
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }

    private val PERSIAN_LANGUAGES = setOf("fa", "prs", "tg", "peo", "pes")
    private val CLAUSE_SEPARATORS = charArrayOf('•', '·', '|')

    /** The notification dictionary. Product copy that reaches a lock screen lives here. */
    private val FaCopy: Map<String, String> = mapOf(
        // states and the ongoing item
        "Protected" to "محافظت‌شده",
        "Connecting…" to "در حال اتصال…",
        "Reconnecting…" to "اتصال دوباره…",
        "Traffic blocked" to "ترافیک مسدود شد",
        "Disconnected" to "قطع شد",
        "MarbleNG" to "MarbleNG",
        "Tunnel active" to "تونل فعال است",
        "Connection details hidden" to "جزئیات اتصال پنهان است",
        "A new alert is waiting" to "هشدار تازه‌ای در انتظار است",
        "Open the app for details" to "برای جزئیات برنامه را باز کنید",
        "Your traffic leaves through this route" to "ترافیک شما از این مسیر خارج می‌شود",
        "Negotiating the route — traffic waits until it is up" to
            "در حال آماده‌سازی مسیر — ترافیک تا آماده‌شدن منتظر می‌ماند",
        "Moving traffic to another route" to "انتقال ترافیک به مسیر دیگر",
        "Kill switch is holding traffic" to "کلید اضطراری ترافیک را نگه داشته است",
        "No tunnel is active" to "هیچ تونلی فعال نیست",
        "Iran Mode" to "حالت ایران",
        "Session" to "نشست",
        "Live" to "زنده",

        // statuses and actions
        "Connecting" to "اتصال",
        "Recovering" to "بازیابی",
        "Blocked" to "مسدود",
        "Idle" to "بی‌کار",
        "Disconnect" to "قطع اتصال",
        "Reconnect" to "اتصال دوباره",
        "Manage" to "مدیریت",
        "Restore" to "بازیابی",
        "Connection" to "اتصال",
        "Recovery" to "بازیابی",
        "Privacy" to "حریم خصوصی",
        "Network" to "شبکه",
        "Subscription" to "اشتراک",
        "Update" to "به‌روزرسانی",
        "Test" to "آزمایش",

        // channels
        "Tunnel status" to "وضعیت تونل",
        "Route alerts" to "هشدارهای مسیر",
        "Privacy alarms" to "هشدارهای حریم خصوصی",
        "Updates" to "به‌روزرسانی‌ها",
        "The ongoing tunnel item: state, route, live rates and session time" to
            "آیتم همیشگی تونل: وضعیت، مسیر، سرعت زنده و زمان نشست",
        "Route changes, failover and recovery results" to "تغییر مسیر، جایگزینی و نتیجه بازیابی",
        "Kill switch, blocked traffic and identity holds" to
            "کلید اضطراری، ترافیک مسدود و نگه‌داشتن هویت",
        "Subscription refreshes, core and app updates" to "به‌روزرسانی اشتراک‌ها، هسته و برنامه",

        // alert titles raised by the tunnel service
        "VPN protected" to "اتصال محافظت‌شده برقرار شد",
        "Route recovered" to "مسیر بازیابی شد",
        "Marble Autopilot" to "خودگردان Marble",
        "Network changed" to "شبکه تغییر کرد",
        "Traffic blocked safely" to "ترافیک با قطع ایمن متوقف شد",
        "Identity Guard" to "نگهبان هویت",
        "Identity Guard is holding traffic" to "نگهبان هویت ترافیک را نگه داشته است",
        "Recovery paused" to "بازیابی متوقف شد",
        "Smart fallback" to "جایگزینی هوشمند",
        "Subscription refreshed" to "اشتراک به‌روزرسانی شد",
        "Subscription refresh issues" to "مشکل در به‌روزرسانی اشتراک",
        "App update available" to "نسخه تازه برنامه آماده است",
        "MarbleNG was updated" to "MarbleNG به‌روزرسانی شد",
        "Test notification" to "اعلان آزمایشی",

        // MARBLE_NOTIFICATION_SWITCH_V234 — the shade's own verbs and the two honest answers
        "Next server" to "سرور بعدی",
        "Next sub" to "اشتراک بعدی",
        "Switching to the next server" to "در حال رفتن به سرور بعدی",
        "Switching to the next subscription" to "در حال رفتن به اشتراک بعدی",
        "No other server to switch to" to "سرور دیگری برای جابه‌جایی نیست",
        "No other subscription to switch to" to "اشتراک دیگری برای جابه‌جایی نیست",

        // alert bodies
        "Local proxy ready" to "پروکسی محلی آماده است",
        "Smart alerts are working on this device." to "هشدارهای هوشمند روی این دستگاه کار می‌کنند.",
        "The tunnel was stopped by the update" to "تونل با به‌روزرسانی برنامه متوقف شد",
        "Tap to restore the last route" to "برای بازیابی آخرین مسیر بزنید",
        "Automatic reconnect is disabled" to
            "اتصال دوباره خودکار خاموش است؛ برای تلاش دوباره «اتصال» را بزنید",
        "Too many automatic reconnects in a short window" to
            "تلاش‌های خودکار بیش از حد در زمان کوتاه",
        "traffic stays blocked" to "ترافیک مسدود می‌ماند",
        "Pinned exit is unavailable; traffic stays blocked instead of switching to another public IP" to
            "خروجی پین‌شده در دسترس نیست؛ ترافیک به‌جای تغییر IP عمومی مسدود می‌ماند",
        "Pinned exit is unavailable" to "خروجی پین‌شده در دسترس نیست",
        "Traffic moved safely to another route" to "ترافیک با ایمنی به مسیر دیگری منتقل شد"
    )

    private val FaPatterns: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("""^Moving to (.+)$""") to { m -> "انتقال به ${m.groupValues[1]}" },
        Regex("""^Switching to (.+)$""") to { m -> "جابه‌جایی به ${m.groupValues[1]}" },
        Regex("""^Retrying the same route$""") to { _ -> "تلاش دوباره روی همان مسیر" },
        Regex("""^(\d+) servers$""") to { m -> "${m.groupValues[1]} سرور" },
        Regex("""^(\d+) ms$""") to { m -> "${m.groupValues[1]} میلی‌ثانیه" },
        Regex("""^(\d+) seconds?$""") to { m -> "${m.groupValues[1]} ثانیه" },
        Regex("""^(.+) • (\d+) servers$""") to { m ->
            "${m.groupValues[1]} • ${m.groupValues[2]} سرور"
        }
    )
}

/**
 * A bounded, allocation-safe cooldown table.
 *
 * The previous `ConcurrentHashMap<String, Long>` was keyed by `kind:session:profileId`, never
 * pruned and cleared only when the user pressed "Clear alerts" — on a device that reconnects often
 * it grew for the whole process lifetime. This one keeps at most [maxEntries] keys and drops
 * anything older than [ttlMs] on every write.
 */
class NotificationCooldown(
    private val maxEntries: Int = 48,
    private val ttlMs: Long = 30 * 60 * 1000L
) {
    private val lastAt = LinkedHashMap<String, Long>()

    @Synchronized
    fun allow(key: String, now: Long, windowMs: Long): Boolean {
        // The entry a cooldown is about to be checked against must survive the prune, even when the
        // user configured a window longer than the table's TTL.
        prune(now, maxOf(ttlMs, windowMs))
        val previous = lastAt[key]
        if (previous != null && windowMs > 0L && now - previous < windowMs) return false
        lastAt[key] = now
        trim()
        return true
    }

    @Synchronized
    fun forget(key: String) {
        lastAt.remove(key)
    }

    @Synchronized
    fun clear() {
        lastAt.clear()
    }

    @Synchronized
    fun size(): Int = lastAt.size

    private fun prune(now: Long, thresholdMs: Long) {
        if (thresholdMs <= 0L) return
        val iterator = lastAt.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > thresholdMs) iterator.remove() else return
        }
    }

    private fun trim() {
        while (lastAt.size > maxEntries) {
            val eldest = lastAt.keys.firstOrNull() ?: return
            lastAt.remove(eldest)
        }
    }
}

/**
 * One id per event key, allocated inside a fixed range, collision-free and bounded.
 *
 * The old `OPTIONAL_ID_BASE + (key.hashCode().ushr(1) % OPTIONAL_ID_RANGE)` had two defects: two
 * keys could collide and silently overwrite each other's notification, and `optionalIds` grew with
 * every alert ever posted. Here a key keeps its id for as long as it is tracked, a collision walks
 * to the next free slot, and the oldest keys are evicted with their ids.
 */
class NotificationIdAllocator(
    private val base: Int,
    private val range: Int,
    private val maxKeys: Int = 64
) {
    private val byKey = LinkedHashMap<String, Int>()
    private val used = HashSet<Int>()

    init {
        require(range > 0) { "range must be positive" }
    }

    @Synchronized
    fun idFor(key: String): Int {
        byKey[key]?.let { return it }
        val start = abs(key.hashCode()) % range
        var step = 0
        while (step < range) {
            val candidate = base + ((start + step) % range)
            if (used.add(candidate)) {
                byKey[key] = candidate
                trim()
                return candidate
            }
            step++
        }
        // Every slot is taken: reuse the hash slot rather than refusing to notify at all.
        val fallback = base + start
        byKey[key] = fallback
        trim()
        return fallback
    }

    @Synchronized
    fun forget(key: String) {
        val id = byKey.remove(key) ?: return
        used.remove(id)
    }

    @Synchronized
    fun activeIds(): List<Int> = byKey.values.toList()

    @Synchronized
    fun size(): Int = byKey.size

    @Synchronized
    fun clear() {
        byKey.clear()
        used.clear()
    }

    private fun trim() {
        while (byKey.size > maxKeys) {
            val eldest = byKey.keys.firstOrNull() ?: return
            val id = byKey.remove(eldest) ?: return
            used.remove(id)
        }
    }
}

/**
 * "Has the ongoing notification actually changed?" — the guard that stops the shade from being
 * rewritten every five seconds with identical bytes. A changed signature is still rate-limited, so
 * a fluctuating ping cannot turn the status bar into a timer.
 */
class ConnectionSignatureGate(private val minIntervalMs: Long = 1_000L) {
    private var lastSignature: String? = null
    private var lastAt: Long = 0L
    private var hasPosted = false

    @Synchronized
    fun allow(signature: String, now: Long): Boolean {
        if (hasPosted) {
            // Identical bytes never reach SystemUI again, whatever the clock says.
            if (lastSignature == signature) return false
            if (minIntervalMs > 0L && now - lastAt < minIntervalMs) return false
        }
        lastSignature = signature
        lastAt = now
        hasPosted = true
        return true
    }

    @Synchronized
    fun reset() {
        lastSignature = null
        lastAt = 0L
        hasPosted = false
    }
}
