package com.marbleng.app.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marbleng.app.MainActivity
import com.marbleng.app.R
import com.marbleng.app.model.AppSettings
import com.marbleng.app.vpn.MarbleVpnService

enum class SmartNotificationKind {
    CONNECTION,
    RECOVERY,
    PRIVACY,
    NETWORK,
    SUBSCRIPTION,
    CORE,
    TEST
}

/** The five states the ongoing tunnel item can be in. One of them is always true. */
enum class ConnectionStatus {
    CONNECTING,
    PROTECTED,
    RECOVERING,
    BLOCKED,
    DISCONNECTED
}

/**
 * MARBLE_NOTIFICATION_COUNCIL_V231 — everything the ongoing notification needs to say, and
 * nothing about how it says it.
 *
 * The old API took two pre-joined strings (`"Protected • Frankfurt"`, `"42 ms • ↓ …"`), so the
 * shade had no idea which part was a state, which part was a route and which part was a
 * measurement. The service now hands over the state itself; the presentation (title, subtext,
 * chronometer, progress, actions, bidi) is decided in one place and in the user's language.
 */
data class ConnectionNotificationState(
    val status: ConnectionStatus = ConnectionStatus.CONNECTING,
    val routeName: String = "",
    /**
     * MARBLE_NOTIFICATION_SWITCH_V234 — the subscription the live route came from.
     *
     * The shade now switches both the server and the subscription in place, so it has to *show*
     * which subscription is live; without this the user would cycle sources blind.
     */
    val subscriptionName: String = "",
    val pingMs: Int = 0,
    val downBps: Long = 0L,
    val upBps: Long = 0L,
    val showTelemetry: Boolean = true,
    val showTimer: Boolean = true,
    val sessionStartedAtMs: Long = 0L,
    val iranModeLabel: String = "",
    val detail: String = "",
    /**
     * MARBLE_NOTIFICATION_PICKER_V238 — the routes the shade's picker may offer. Empty when the
     * library is empty or the item is not the live one; the RemoteViews builder draws at most
     * [NotificationRoutePicker.DEFAULT_CAP] rows from it.
     */
    val routes: List<CycleRoute> = emptyList(),
    /** The live route's id and source id — the picker marks its row from these. */
    val routeId: String = "",
    val routeSourceId: String = ""
) {
    fun signature(language: NotificationLanguage): String = buildString {
        append(status.name).append('|')
        append(routeName).append('|')
        append(subscriptionName).append('|')
        if (showTelemetry) {
            append(pingMs).append('|').append(downBps).append('|').append(upBps)
        }
        append('|').append(showTimer).append('|').append(sessionStartedAtMs)
        append('|').append(iranModeLabel).append('|').append(detail)
        append('|').append(language.id)
        append('|').append(routes.map { it.id }.joinToString(",").hashCode())
    }

    val ongoing: Boolean
        get() = status != ConnectionStatus.DISCONNECTED
}

class SmartNotifier(
    private val context: Context,
    private val languageSource: () -> String = { "" }
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Volatile
    private var channelsLanguage: NotificationLanguage? = null

    private val cooldowns = NotificationCooldown()
    private val ids = NotificationIdAllocator(OPTIONAL_ID_BASE, OPTIONAL_ID_RANGE)
    private val connectionGate = ConnectionSignatureGate()
    private val channelLock = Any()

    fun resolveLanguage(): NotificationLanguage {
        val locales = context.resources.configuration.locales
        val deviceLanguage = if (locales.isEmpty) "" else locales.get(0).language
        return NotificationCopy.languageFor(languageSource(), deviceLanguage)
    }

    /**
     * The four-channel table. Names and descriptions are rewritten whenever the app language
     * changes: Android keeps a user's per-channel choices, but it keeps the *first* name it was
     * given unless the app asks again, which is why a Persian app used to list English channels.
     */
    fun ensureChannels(language: NotificationLanguage = resolveLanguage()) {
        if (Build.VERSION.SDK_INT < 26) return
        if (channelsLanguage == language) return
        synchronized(channelLock) {
            if (channelsLanguage == language) return
            runCatching {
                manager.createNotificationChannelGroup(
                    NotificationChannelGroup(GROUP_ID, "MarbleNG")
                )

                val connection = NotificationChannel(
                    CHANNEL_CONNECTION,
                    NotificationCopy.channelTitle(NotificationChannelKey.CONNECTION, language),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description =
                        NotificationCopy.channelDescription(NotificationChannelKey.CONNECTION, language)
                    group = GROUP_ID
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)
                }

                val alerts = NotificationChannel(
                    CHANNEL_SMART,
                    NotificationCopy.channelTitle(NotificationChannelKey.ALERTS, language),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description =
                        NotificationCopy.channelDescription(NotificationChannelKey.ALERTS, language)
                    group = GROUP_ID
                    setShowBadge(true)
                    setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)
                }

                // A privacy alarm is the one alert that may interrupt: it is the difference
                // between "a route died" and "traffic is being held while you believe it is not".
                val privacy = NotificationChannel(
                    CHANNEL_PRIVACY,
                    NotificationCopy.channelTitle(NotificationChannelKey.PRIVACY, language),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description =
                        NotificationCopy.channelDescription(NotificationChannelKey.PRIVACY, language)
                    group = GROUP_ID
                    setShowBadge(true)
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 120L, 80L, 120L)
                    setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)
                }

                val updates = NotificationChannel(
                    CHANNEL_UPDATES,
                    NotificationCopy.channelTitle(NotificationChannelKey.UPDATES, language),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description =
                        NotificationCopy.channelDescription(NotificationChannelKey.UPDATES, language)
                    group = GROUP_ID
                    setShowBadge(true)
                    setSound(null, null)
                    enableVibration(false)
                    setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)
                }

                manager.createNotificationChannels(listOf(connection, alerts, privacy, updates))
                channelsLanguage = language
            }
        }
    }

    fun optionalPermissionGranted(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    // ------------------------------------------------------------------------------------------
    // the ongoing tunnel item
    // ------------------------------------------------------------------------------------------

    /**
     * The status notification: the state as the title, the route as the text, the measurement as
     * one bidi-isolated line, the session as the platform's own chronometer, and only the verbs
     * that are valid in that state.
     */
    fun connectionNotification(
        state: ConnectionNotificationState,
        language: NotificationLanguage = resolveLanguage()
    ): Notification {
        ensureChannels(language)
        val title = NotificationCopy.statusTitle(state.status, language)
        val route = state.routeName.trim()
        val source = state.subscriptionName.trim()
        val routeLine = NotificationRouteCycle.routeLine(route, source)
        val detail = NotificationCopy.translate(state.detail.trim(), language)

        // MARBLE_NOTIFICATION_SWITCH_V234 — the second line of the drawn view is one slot: the
        // live `route · source`, or the reason the state is what it is ("No other server to switch
        // to", "Kill switch is holding traffic"). A detail is never appended as a third line the
        // shade has no room for.
        val secondLine = if (detail.isNotEmpty()) detail else routeLine
        val contentText = if (secondLine.isNotEmpty()) {
            secondLine
        } else {
            NotificationCopy.statusDetail(state.status, language)
        }

        val subText = if (state.iranModeLabel.isBlank()) {
            "MarbleNG"
        } else {
            "MarbleNG · ${state.iranModeLabel.take(NotificationCopy.SUB_TEXT_MAX)}"
        }

        val shade = TunnelNotificationView.build(
            context = context,
            state = state,
            language = language,
            title = NotificationCopy.ellipsize(title, NotificationCopy.TITLE_MAX),
            routeLine = NotificationCopy.ellipsize(secondLine, NotificationCopy.TEXT_MAX),
            ping = NotificationCopy.latency(state.pingMs),
            down = "↓ ${NotificationCopy.rate(state.downBps)}",
            up = "↑ ${NotificationCopy.rate(state.upBps)}",
            picker = NotificationRoutePicker.build(
                NotificationRoutePicker.Input(
                    routes = state.routes,
                    currentId = state.routeId,
                    currentSourceId = state.routeSourceId,
                    cap = NotificationRoutePicker.DEFAULT_CAP
                )
            ),
            pickerIntent = { row -> pickerIntent(row) }
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            // The text fields stay: they are the accessibility stream, the fallback for every
            // surface that cannot inflate a RemoteViews layout, and what a screen reader reads.
            .setContentTitle(NotificationCopy.ellipsize(title, NotificationCopy.TITLE_MAX))
            .setContentText(NotificationCopy.ellipsize(contentText, NotificationCopy.TEXT_MAX))
            .setSubText(NotificationCopy.ellipsize(subText, NotificationCopy.SUB_TEXT_MAX))
            .setGroup(GROUP_ID)
            .setLocalOnly(true)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            // MARBLE_SHADE_ATELIER_V234 — one style, because `setStyle` is singular: a second
            // call (BigTextStyle, InboxStyle) silently replaces the decorator and the custom view
            // loses its header and its actions. The textual fields above are the fallback instead:
            // every surface that cannot inflate a RemoteViews layout still receives the state, the
            // route and the subscription as plain text.
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(shade.compact)
            .setCustomBigContentView(shade.expanded)
            .setContentIntent(openAppIntent(NotificationRoute.DASHBOARD, language))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(language, state.ongoing))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(state.ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setForegroundServiceBehavior(
                if (state.status == ConnectionStatus.CONNECTING) {
                    NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                } else {
                    NotificationCompat.FOREGROUND_SERVICE_DEFERRED
                }
            )

        // MARBLE_SHADE_ATELIER_V234 — the session clock moved into the drawn view.
        //
        // It is still the platform's own `Chronometer` (it costs zero notifications and keeps
        // counting while the app is dozing), but it is now a view inside `notification_tunnel.xml`
        // instead of the timestamp in the notification header: with a decorated custom view the
        // header would draw a second clock next to the row's, and V231's own finding is that two
        // clocks in one item read as a timer. The header keeps the app name and nothing else.
        builder.setShowWhen(false)
        builder.setUsesChronometer(false)

        if (state.status == ConnectionStatus.CONNECTING || state.status == ConnectionStatus.RECOVERING) {
            builder.setProgress(0, 0, true)
        }

        if (state.ongoing) {
            // MARBLE_NOTIFICATION_SWITCH_V234 — the two route verbs act on the live tunnel instead
            // of opening a page. V231's buttons opened Servers/Subscriptions, which meant a user
            // who wanted the next node had to unlock the phone and cross a screen to do it. A
            // `PendingIntent.getService` reaches the running foreground service directly, and the
            // service re-publishes the same item with the new route, so the shade stays the place
            // the decision is made *and* the place it is applied.
            builder.addAction(
                R.drawable.ic_notification_switch,
                NotificationCopy.actionNextServer(language),
                cycleIntent(NotificationCycleKind.SERVER)
            )
            builder.addAction(
                R.drawable.ic_notification_source,
                NotificationCopy.actionNextSubscription(language),
                cycleIntent(NotificationCycleKind.SUBSCRIPTION)
            )
            if (state.status == ConnectionStatus.BLOCKED) {
                builder.addAction(
                    R.drawable.ic_notification_reconnect,
                    NotificationCopy.actionReconnect(language),
                    reconnectIntent(language)
                )
            } else {
                builder.addAction(
                    R.drawable.ic_notification_stop,
                    NotificationCopy.actionDisconnect(language),
                    stopServiceIntent()
                )
            }
        }

        return builder.build()
    }

    /**
     * The legacy entry point, kept for the paths whose text is genuinely ad hoc (a launch failure
     * before there is a state to describe). Its subtext is the platform's own second line.
     */
    fun connectionNotification(title: String, text: String, ongoing: Boolean): Notification {
        val language = resolveLanguage()
        ensureChannels(language)
        return NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(NotificationCopy.ellipsize(title, NotificationCopy.TITLE_MAX))
            .setContentText(NotificationCopy.ellipsize(text, NotificationCopy.TEXT_MAX))
            .setSubText(statusSubText(text))
            .setGroup(GROUP_ID)
            .setLocalOnly(true)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(NotificationCopy.ellipsize(text, NotificationCopy.BIG_TEXT_MAX))
            )
            .setContentIntent(openAppIntent(NotificationRoute.DASHBOARD, language))
            .addAction(
                R.drawable.ic_notification_stop,
                NotificationCopy.actionDisconnect(language),
                stopServiceIntent()
            )
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(language, ongoing))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * Re-post the ongoing item only when the shade would actually look different. The telemetry
     * loop ticks once a second and used to rewrite the notification every five, even when a
     * tunnel was idle: twelve identical binder calls and twelve SystemUI relayouts a minute.
     */
    fun updateConnection(
        id: Int,
        state: ConnectionNotificationState,
        language: NotificationLanguage = resolveLanguage()
    ): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (!connectionGate.allow(state.signature(language), now)) return false
        val note = connectionNotification(state, language)
        return runCatching {
            manager.notify(id, note)
            true
        }.getOrDefault(false)
    }

    fun updateConnection(id: Int, title: String, text: String, ongoing: Boolean): Boolean {
        val note = connectionNotification(title, text, ongoing)
        return runCatching {
            manager.notify(id, note)
            true
        }.getOrDefault(false)
    }

    fun resetConnectionGate() {
        connectionGate.reset()
    }

    // ------------------------------------------------------------------------------------------
    // alerts
    // ------------------------------------------------------------------------------------------

    fun alert(
        kind: SmartNotificationKind,
        key: String,
        title: String,
        text: String,
        settings: AppSettings,
        minIntervalOverrideMs: Long? = null
    ): Boolean {
        if (!settings.smartNotificationsEnabled || !kindEnabled(kind, settings)) return false
        if (!optionalPermissionGranted()) return false
        val language = resolveLanguage()
        ensureChannels(language)

        val now = System.currentTimeMillis()
        val eventKey = "${kind.name}:$key"
        val cooldown = (
            minIntervalOverrideMs
                ?: settings.notificationCooldownSec.coerceIn(5, 300) * 1000L
            ).coerceAtLeast(0L)
        if (!cooldowns.allow(eventKey, now, cooldown)) return false

        val channel = channelFor(kind, settings)
        // The English label doubles as the dictionary key for the Persian one.
        val kindLabel = kindSubText(kind)
        val subText = NotificationCopy.translate(kindLabel, language)
        val localizedTitle = NotificationCopy.translate(title, language)
        val localizedText = NotificationCopy.translate(text, language)

        val priority = when (kind) {
            SmartNotificationKind.PRIVACY,
            SmartNotificationKind.RECOVERY,
            SmartNotificationKind.TEST -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
        val route = routeFor(kind)

        val note = NotificationCompat.Builder(context, channel)
            .setSmallIcon(iconFor(kind))
            .setColor(toneFor(kind))
            .setContentTitle(
                NotificationCopy.ellipsize(localizedTitle, NotificationCopy.TITLE_MAX)
            )
            .setContentText(NotificationCopy.ellipsize(localizedText, NotificationCopy.TEXT_MAX))
            .setSubText(subText)
            .setGroup(GROUP_ID)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(NotificationCopy.ellipsize(localizedText, NotificationCopy.BIG_TEXT_MAX))
            )
            .setContentIntent(openAppIntent(route, language))
            .addAction(
                R.drawable.ic_notification_manage,
                NotificationCopy.actionManageAlerts(language),
                channelSettingsIntent(channel)
            )
            .setCategory(
                if (kind == SmartNotificationKind.PRIVACY) {
                    NotificationCompat.CATEGORY_ERROR
                } else {
                    NotificationCompat.CATEGORY_STATUS
                }
            )
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion(language, false))
            .setPriority(priority)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setLocalOnly(true)
            .setAutoCancel(true)
            .build()

        val id = ids.idFor(eventKey)
        return runCatching {
            manager.notify(id, note)
            postSummary(language)
            true
        }.getOrDefault(false)
    }

    /** The bundle header Android shows above two or more alerts in the same group. */
    private fun postSummary(language: NotificationLanguage) {
        val count = ids.size()
        val title = NotificationCopy.t(language, "MarbleNG alerts", "هشدارهای MarbleNG")
        val text = if (count <= 1) {
            NotificationCopy.t(language, "1 alert waiting", "۱ هشدار در انتظار")
        } else {
            NotificationCopy.t(language, "$count alerts waiting", "$count هشدار در انتظار")
        }
        val summary = NotificationCompat.Builder(context, CHANNEL_SMART)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(NotificationCopy.t(language, "Grouped", "گروه‌شده"))
            .setGroup(GROUP_ID)
            .setGroupSummary(true)
            .setLocalOnly(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(NotificationRoute.NOTIFICATIONS, language))
            .build()
        runCatching { manager.notify(SUMMARY_ID, summary) }
    }

    /**
     * The post-update notice: an update stopped the tunnel, so this is a recovery event, not a
     * status line. It used to be posted on the silent ongoing channel with no action at all.
     */
    fun restoreNotice(language: NotificationLanguage = resolveLanguage()): Boolean {
        ensureChannels(language)
        if (!optionalPermissionGranted()) return false
        val title = NotificationCopy.translate("MarbleNG was updated", language)
        val text = NotificationCopy.translate(
            "The tunnel was stopped by the update • tap to restore the last route",
            language
        )
        val note = NotificationCompat.Builder(context, CHANNEL_SMART)
            .setSmallIcon(R.drawable.ic_notification_reconnect)
            .setColor(TONE_RECOVERY)
            .setContentTitle(NotificationCopy.ellipsize(title, NotificationCopy.TITLE_MAX))
            .setContentText(NotificationCopy.ellipsize(text, NotificationCopy.TEXT_MAX))
            .setSubText(kindSubText(SmartNotificationKind.RECOVERY, language))
            .setGroup(GROUP_ID)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(NotificationCopy.ellipsize(text, NotificationCopy.BIG_TEXT_MAX))
            )
            .setContentIntent(openAppIntent(NotificationRoute.DASHBOARD, language))
            .addAction(
                R.drawable.ic_notification_reconnect,
                NotificationCopy.actionRestore(language),
                reconnectIntent(language)
            )
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setLocalOnly(true)
            .build()
        val id = ids.idFor("RESTORE:update")
        return runCatching {
            manager.notify(id, note)
            postSummary(language)
            true
        }.getOrDefault(false)
    }

    fun cancelOptional() {
        ids.activeIds().forEach { id -> runCatching { manager.cancel(id) } }
        ids.clear()
        cooldowns.clear()
        runCatching { manager.cancel(SUMMARY_ID) }
    }

    fun activeAlertCount(): Int = ids.size()

    private fun kindEnabled(kind: SmartNotificationKind, s: AppSettings): Boolean = when (kind) {
        SmartNotificationKind.CONNECTION -> s.notifyConnectionEvents
        SmartNotificationKind.RECOVERY -> s.notifyRecoveryEvents
        SmartNotificationKind.PRIVACY -> s.notifyPrivacyWarnings
        SmartNotificationKind.NETWORK -> s.notifyNetworkChanges
        SmartNotificationKind.SUBSCRIPTION -> s.notifySubscriptionEvents
        SmartNotificationKind.CORE -> s.notifyCoreUpdates
        SmartNotificationKind.TEST -> true
    }

    private fun channelFor(kind: SmartNotificationKind, s: AppSettings): String = when (kind) {
        SmartNotificationKind.PRIVACY ->
            if (s.notifyPrivacyHeadsUp) CHANNEL_PRIVACY else CHANNEL_SMART
        SmartNotificationKind.SUBSCRIPTION,
        SmartNotificationKind.CORE -> CHANNEL_UPDATES
        else -> CHANNEL_SMART
    }

    private fun iconFor(kind: SmartNotificationKind): Int = when (kind) {
        SmartNotificationKind.PRIVACY -> R.drawable.ic_notification_privacy
        SmartNotificationKind.CORE,
        SmartNotificationKind.SUBSCRIPTION -> R.drawable.ic_notification_update
        SmartNotificationKind.RECOVERY -> R.drawable.ic_notification_reconnect
        else -> R.drawable.ic_notification
    }

    private fun toneFor(kind: SmartNotificationKind): Int = when (kind) {
        SmartNotificationKind.PRIVACY -> TONE_PRIVACY
        SmartNotificationKind.RECOVERY -> TONE_RECOVERY
        SmartNotificationKind.NETWORK -> TONE_NETWORK
        SmartNotificationKind.SUBSCRIPTION,
        SmartNotificationKind.CORE -> TONE_UPDATES
        SmartNotificationKind.CONNECTION,
        SmartNotificationKind.TEST -> ACCENT
    }

    /** Where the tap lands: the page that can act on the alert, not always the first tab. */
    private fun routeFor(kind: SmartNotificationKind): NotificationRoute = when (kind) {
        SmartNotificationKind.PRIVACY,
        SmartNotificationKind.RECOVERY,
        SmartNotificationKind.NETWORK -> NotificationRoute.DIAGNOSTICS
        SmartNotificationKind.SUBSCRIPTION -> NotificationRoute.SUBSCRIPTIONS
        SmartNotificationKind.CORE -> NotificationRoute.DASHBOARD
        SmartNotificationKind.CONNECTION -> NotificationRoute.DASHBOARD
        SmartNotificationKind.TEST -> NotificationRoute.NOTIFICATIONS
    }

    // ------------------------------------------------------------------------------------------
    // intents
    // ------------------------------------------------------------------------------------------

    private fun publicVersion(language: NotificationLanguage, ongoing: Boolean): Notification =
        NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle("MarbleNG")
            .setContentText(
                NotificationCopy.t(
                    language,
                    "Connection details hidden",
                    "جزئیات اتصال پنهان است"
                )
            )
            .setSubText(NotificationCopy.t(language, "Tunnel active", "تونل فعال است"))
            .setGroup(GROUP_ID)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .build()

    private fun openAppIntent(route: NotificationRoute, language: NotificationLanguage): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_PAGE, route.id)
            .setData(Uri.parse("marbleng://notify/${route.id}"))
        return PendingIntent.getActivity(
            context,
            REQUEST_OPEN_BASE + route.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * MARBLE_NOTIFICATION_SWITCH_V234 — the shade's own route switch.
     *
     * A distinct request code per verb, and a distinct data URI: two `PendingIntent`s that share a
     * request code are one `PendingIntent`, and `FLAG_UPDATE_CURRENT` would rewrite the first verb's
     * intent with the second's action (the exact defect V231 fixed for taps).
     */
    private fun cycleIntent(kind: NotificationCycleKind): PendingIntent {
        val action = when (kind) {
            NotificationCycleKind.SERVER -> MarbleVpnService.ACTION_CYCLE_SERVER
            NotificationCycleKind.SUBSCRIPTION -> MarbleVpnService.ACTION_CYCLE_SUBSCRIPTION
        }
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(action)
            .setData(Uri.parse("marbleng://cycle/${kind.id}"))
            .putExtra(EXTRA_CYCLE_KIND, kind.id)
        return PendingIntent.getService(
            context,
            REQUEST_CYCLE_BASE + kind.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * MARBLE_NOTIFICATION_PICKER_V238 — one picker row's tap: "make *this* route live".
     *
     * The row's identity travels in the data URI *and* the extras, so a re-delivered or
     * re-created intent always carries the exact profile it was built for. Request codes are
     * offset by the row's stable position in the picker (`actionId` hash kept positive), so two
     * rows can never share one `PendingIntent`.
     */
    private fun pickerIntent(row: NotificationRoutePicker.PickerRow): PendingIntent? = when {
        row.actionId.startsWith(NotificationRoutePicker.OPEN_APP_PREFIX) ->
            openAppIntent(NotificationRoute.DASHBOARD, resolveLanguage())
        row.actionId.startsWith(NotificationRoutePicker.SELECT_PREFIX) -> {
            val profileId = NotificationRoutePicker.selectProfileId(row.actionId)
            if (profileId.isBlank()) {
                null
            } else {
                val intent = Intent(context, MarbleVpnService::class.java)
                    .setAction(MarbleVpnService.ACTION_SELECT_ROUTE)
                    .setData(Uri.parse("marbleng://picker/${profileId.take(48)}"))
                    .putExtra(MarbleVpnService.EXTRA_SELECT_PROFILE, profileId)
                    .putExtra(MarbleVpnService.EXTRA_SELECT_SOURCE, row.payloadSourceId)
                PendingIntent.getService(
                    context,
                    REQUEST_PICKER_BASE + (row.actionId.hashCode() and 0x0FFFFFFF),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        }
        else -> null
    }

    private fun stopServiceIntent(): PendingIntent {
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(MarbleVpnService.ACTION_STOP)
        return PendingIntent.getService(
            context,
            REQUEST_STOP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun reconnectIntent(language: NotificationLanguage): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_RECONNECT_LAST)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_LANGUAGE, language.id)
        return PendingIntent.getActivity(
            context,
            REQUEST_RECONNECT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun channelSettingsIntent(channel: String): PendingIntent {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            REQUEST_MANAGE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val ACCENT = 0xFF0066CC.toInt()

        private const val TONE_PRIVACY = 0xFFB3261E.toInt()
        private const val TONE_RECOVERY = 0xFF146C2E.toInt()
        private const val TONE_NETWORK = 0xFF0B57D0.toInt()
        private const val TONE_UPDATES = 0xFF6750A4.toInt()

        // Mirrored by NotificationChannelKey; Android stores these ids on the device, so they are
        // never renamed, only added to.
        const val CHANNEL_CONNECTION = "marbleng-vpn"
        const val CHANNEL_SMART = "marbleng-smart"
        const val CHANNEL_PRIVACY = "marbleng-privacy"
        const val CHANNEL_UPDATES = "marbleng-updates"
        const val GROUP_ID = "marbleng-notifications"

        /** The page a notification tap opens; read by `MainActivity`. */
        const val EXTRA_PAGE = "marble.page"

        /** The language the notification was written in; read by `MainActivity`. */
        const val EXTRA_LANGUAGE = "marble.language"

        /** MARBLE_NOTIFICATION_SWITCH_V234 — which verb the shade's route switch carried. */
        const val EXTRA_CYCLE_KIND = "marble.cycle"

        /** "Connect the last route again", raised by the notification's own action. */
        const val ACTION_RECONNECT_LAST = "com.marbleng.RECONNECT_LAST"

        const val NOTIFICATION_ID = 7301
        private const val SUMMARY_ID = 7300
        private const val OPTIONAL_ID_BASE = 7600
        private const val OPTIONAL_ID_RANGE = 1200
        private const val REQUEST_OPEN_BASE = 7310
        private const val REQUEST_STOP = 7303
        private const val REQUEST_RECONNECT = 7304
        private const val REQUEST_MANAGE = 7305
        private const val REQUEST_CYCLE_BASE = 7312
        private const val REQUEST_PICKER_BASE = 7350

        fun formatRate(bytesPerSecond: Long): String = NotificationCopy.rate(bytesPerSecond)

        /** The channel ids the settings page lists, in reading order. */
        fun channelIds(): List<String> = NotificationChannelKey.menuOrder.map { it.id }
    }
}

/** The leading clause of a free-form status line — the legacy path's subtext. */
internal fun statusSubText(text: String): String {
    val head = text.substringBefore('•').trim().trimEnd('.', ',', ':', '-', '·').trim()
    return when {
        head.isEmpty() -> "MarbleNG"
        head.length > NotificationCopy.SUB_TEXT_MAX -> "MarbleNG"
        else -> head
    }
}

/** The English kind label; also the dictionary key for its Persian translation. */
internal fun kindSubText(kind: SmartNotificationKind): String = when (kind) {
    SmartNotificationKind.CONNECTION -> "Connection"
    SmartNotificationKind.RECOVERY -> "Recovery"
    SmartNotificationKind.PRIVACY -> "Privacy"
    SmartNotificationKind.NETWORK -> "Network"
    SmartNotificationKind.SUBSCRIPTION -> "Subscription"
    SmartNotificationKind.CORE -> "Update"
    SmartNotificationKind.TEST -> "Test"
}

internal fun kindSubText(kind: SmartNotificationKind, language: NotificationLanguage): String =
    NotificationCopy.translate(kindSubText(kind), language)
