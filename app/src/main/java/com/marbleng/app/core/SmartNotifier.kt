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
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.marbleng.app.MainActivity
import com.marbleng.app.R
import com.marbleng.app.model.AppSettings
import com.marbleng.app.vpn.MarbleVpnService
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class SmartNotificationKind {
    CONNECTION,
    RECOVERY,
    PRIVACY,
    NETWORK,
    SUBSCRIPTION,
    CORE,
    TEST
}

/**
 * Central notification policy for MarbleNG.
 *
 * The running VPN/proxy foreground status is deliberately separate from optional event alerts.
 * Optional alerts are permission-aware, category-aware and rate-limited so route probes cannot
 * spam the notification shade during a bad underlay.
 */
class SmartNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /**
     * MARBLE_NOTIFICATION_CHANNELS_ONCE_V206 — the channel table is a constant of this build.
     *
     * It was rebuilt on *every* settings write. [AppRepository.updateSettings] ends with a call
     * to this method, and `updateSettings` is what every control in Settings calls — so typing
     * `100-200` into the Fragment length field performed, on the main thread, nine times in
     * nine hundred milliseconds: three channel constructions, one group creation and one
     * `createNotificationChannels` binder call into `system_server`, each of which makes the
     * system process write its own XML. Dragging the Mux concurrency slider did the same at
     * frame rate. None of it could ever have changed anything: the channel set is fixed at
     * compile time, and re-creating an existing channel with identical parameters is a no-op
     * the framework still has to parse, persist and re-sort.
     *
     * The flag is set only on success, so a call that threw (a wedged NotificationManager, a
     * device still booting) is retried instead of being suppressed for the rest of the process.
     */
    private val channelsEnsured = java.util.concurrent.atomic.AtomicBoolean(false)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        if (channelsEnsured.get()) return
        runCatching {
            manager.createNotificationChannelGroup(
                NotificationChannelGroup(GROUP_ID, "MarbleNG")
            )
            // MARBLE_NATIVE_ANDROID_V213 — every channel is configured the way Android's own
            // settings screen reads it back.
            //
            // Until this chapter the channels carried a name, a description and an importance and
            // nothing else, so the system's per-channel page showed Android's *defaults* for them:
            // the ongoing tunnel channel buzzed and dinged on every live-stats update (a status
            // notification is not an interruption — Android's own "ongoing" channels are silent),
            // and the alert channel showed no badge. A channel is the user's contract with the app
            // and every field of it is visible in Settings → Apps → Notifications; leaving them
            // unset is not neutrality, it is the platform's defaults standing in for decisions.
            val connection = NotificationChannel(
                CHANNEL_CONNECTION,
                "Connection status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent VPN/proxy connection, route and live status"
                group = GROUP_ID
                setShowBadge(false)
                // The live-stats line updates while the tunnel runs; a status channel that makes a
                // sound per update is a status channel every user mutes.
                setSound(null, null)
                enableVibration(false)
                setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)
            }
            val smart = NotificationChannel(
                CHANNEL_SMART,
                "Alerts, privacy & recovery",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Optional connection alerts, privacy warnings and recovery events"
                group = GROUP_ID
                setShowBadge(true)
                setLockscreenVisibility(Notification.VISIBILITY_PRIVATE)
            }
            val updates = NotificationChannel(
                CHANNEL_UPDATES,
                "Subscriptions & app updates",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Subscription refresh results and app core updates"
                group = GROUP_ID
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
            }
            manager.createNotificationChannels(listOf(connection, smart, updates))
            channelsEnsured.set(true)
        }
    }

    fun optionalPermissionGranted(): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun connectionNotification(title: String, text: String, ongoing: Boolean): Notification {
        ensureChannels()
        val publicVersion = NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle("MarbleNG")
            .setContentText("Connection details hidden")
            .setSubText("Tunnel active")
            .setGroup(GROUP_ID)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

        // MARBLE_NATIVE_ANDROID_V213 — the ongoing notification is drawn the way the platform
        // draws one.
        //
        // Three platform features were unused, and each of them is the difference between a
        // notification that looks like Android's and one that looks hand-built:
        //
        //  • `setColor` — the accent the system uses to tint the small icon and the app name in
        //    the shade. Without it a brand product's notification renders in the system's default
        //    grey, which is the most visible "this app did not bother" signal in the shade.
        //  • `setSubText` — the platform's own second line, which is where a live status belongs
        //    (the body stays the route; the subtext carries the state).
        //  • `setGroup` / `setLocalOnly` / `setBadgeIconType` — the group is the one the channels
        //    already declare, the status of a tunnel is nobody's business on the user's other
        //    devices, and the badge uses the small icon the platform is designed around.
        //
        // The middle line is also where the live telemetry goes when the user asked for it, so the
        // shade's own hierarchy — subtext, title, body, action — is what carries the information,
        // instead of one long sentence in the body.
        return NotificationCompat.Builder(context, CHANNEL_CONNECTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(statusSubText(text))
            .setGroup(GROUP_ID)
            .setLocalOnly(true)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .addAction(R.drawable.ic_notification_stop, "Disconnect", stopServiceIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Server names and live network details belong behind the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun updateConnection(id: Int, title: String, text: String, ongoing: Boolean): Boolean {
        val note = connectionNotification(title, text, ongoing)
        return runCatching {
            manager.notify(id, note)
            true
        }.getOrDefault(false)
    }

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
        ensureChannels()

        val now = System.currentTimeMillis()
        val eventKey = "${kind.name}:$key"
        val cooldown = (
            minIntervalOverrideMs
                ?: settings.notificationCooldownSec.coerceIn(5, 300) * 1000L
            ).coerceAtLeast(0L)
        val previous = lastEventAt[eventKey] ?: 0L
        if (cooldown > 0L && now - previous < cooldown) return false
        lastEventAt[eventKey] = now

        val channel = when (kind) {
            SmartNotificationKind.SUBSCRIPTION,
            SmartNotificationKind.CORE -> CHANNEL_UPDATES
            else -> CHANNEL_SMART
        }
        val priority = when (kind) {
            SmartNotificationKind.PRIVACY,
            SmartNotificationKind.RECOVERY,
            SmartNotificationKind.TEST -> NotificationCompat.PRIORITY_DEFAULT
            else -> NotificationCompat.PRIORITY_LOW
        }
        val safeHash = eventKey.hashCode().ushr(1)
        val id = OPTIONAL_ID_BASE + (safeHash % OPTIONAL_ID_RANGE)
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ACCENT)
            .setContentTitle("MarbleNG")
            .setContentText("A new alert is waiting")
            .setSubText("Open the app for details")
            .setGroup(GROUP_ID)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .build()
        val category = if (kind == SmartNotificationKind.PRIVACY) {
            NotificationCompat.CATEGORY_ERROR
        } else {
            NotificationCompat.CATEGORY_STATUS
        }
        // MARBLE_NATIVE_ANDROID_V213 — an alert wears its own kind's colour.
        //
        // Every optional alert used to be the same grey rectangle: a privacy warning, a recovery
        // event and a subscription refresh were indistinguishable in the shade until the text was
        // read, and none of them carried the accent the platform uses to tint a notification's
        // icon. The kind now decides the colour and the subtext, both of which are platform roles
        // rather than decoration — `error` red for a privacy warning is the same red Android paints
        // its own permission alerts in, so the shade's colour language stays the system's.
        val kindTone = when (kind) {
            SmartNotificationKind.PRIVACY -> TONE_PRIVACY
            SmartNotificationKind.RECOVERY -> TONE_RECOVERY
            SmartNotificationKind.NETWORK -> TONE_NETWORK
            SmartNotificationKind.SUBSCRIPTION,
            SmartNotificationKind.CORE -> TONE_UPDATES
            SmartNotificationKind.CONNECTION,
            SmartNotificationKind.TEST -> ACCENT
        }
        val note = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(kindTone)
            .setContentTitle(title.take(80))
            .setContentText(text.take(240))
            .setSubText(kindSubText(kind))
            .setGroup(GROUP_ID)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.take(1000)))
            .setContentIntent(openAppIntent())
            .setCategory(category)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        return runCatching {
            manager.notify(id, note)
            optionalIds += id
            true
        }.getOrDefault(false)
    }

    fun cancelOptional() {
        optionalIds.toList().forEach { id -> runCatching { manager.cancel(id) } }
        optionalIds.clear()
        lastEventAt.clear()
    }

    private fun kindEnabled(kind: SmartNotificationKind, s: AppSettings): Boolean = when (kind) {
        SmartNotificationKind.CONNECTION -> s.notifyConnectionEvents
        SmartNotificationKind.RECOVERY -> s.notifyRecoveryEvents
        SmartNotificationKind.PRIVACY -> s.notifyPrivacyWarnings
        SmartNotificationKind.NETWORK -> s.notifyNetworkChanges
        SmartNotificationKind.SUBSCRIPTION -> s.notifySubscriptionEvents
        SmartNotificationKind.CORE -> s.notifyCoreUpdates
        SmartNotificationKind.TEST -> true
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            7302,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun stopServiceIntent(): PendingIntent {
        val intent = Intent(context, MarbleVpnService::class.java)
            .setAction(MarbleVpnService.ACTION_STOP)
        return PendingIntent.getService(
            context,
            7303,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        /**
         * MARBLE_NATIVE_ANDROID_V213 — the accent the platform tints the small icon, the app name
         * and (on Android 12+) the notification's own colour chip with. It is the brand's electric
         * blue in every theme: a notification is drawn by the system, on the system's surface, and
         * the one thing it must carry is which app it came from.
         */
        const val ACCENT = 0xFF0066CC.toInt()

        // The four kind tones, as packed ARGB. They are Android's own semantic reds/greens/blues,
        // not brand accents: an alert's colour is a category, and a category is the system's.
        private const val TONE_PRIVACY = 0xFFB3261E.toInt()
        private const val TONE_RECOVERY = 0xFF146C2E.toInt()
        private const val TONE_NETWORK = 0xFF0B57D0.toInt()
        private const val TONE_UPDATES = 0xFF6750A4.toInt()

        const val CHANNEL_CONNECTION = "marbleng-vpn"
        const val CHANNEL_SMART = "marbleng-smart"
        const val CHANNEL_UPDATES = "marbleng-updates"
        private const val GROUP_ID = "marbleng-notifications"
        private const val OPTIONAL_ID_BASE = 7600
        private const val OPTIONAL_ID_RANGE = 1200
        private val lastEventAt = ConcurrentHashMap<String, Long>()
        private val optionalIds = ConcurrentHashMap.newKeySet<Int>()

        fun formatRate(bytesPerSecond: Long): String = when {
            bytesPerSecond >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024L -> String.format(Locale.US, "%.0f KB/s", bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — the platform's *subtext* line, filled with the state.
 *
 * A status notification has three lines and Android gives each of them a job: the title names the
 * app, the subtext names the state, the body names the detail. MarbleNG was writing one long body
 * ("Connected • Frankfurt • 42 ms") into the second and third at once, so the shade showed the
 * state somewhere in the middle of a sentence. The body's first clause *is* the state — the service
 * builds it that way — so it is lifted into the platform's own line, and the body keeps the route.
 *
 * Pure, so the split is a unit test rather than a screenshot.
 */
internal fun statusSubText(text: String): String {
    val head = text.substringBefore('•').trim().trimEnd('.', ',', ':', '-', '·').trim()
    return when {
        head.isEmpty() -> "MarbleNG"
        head.length > 32 -> "MarbleNG"
        else -> head
    }
}

/**
 * MARBLE_NATIVE_ANDROID_V213 — the one word the platform's subtext line carries for an alert.
 *
 * The category is the user's own filing system: "Privacy", "Recovery", "Network" and "Update" are
 * the four things Android's notification settings will let them tune separately, so the shade says
 * which one this is before the sentence does.
 */
internal fun kindSubText(kind: SmartNotificationKind): String = when (kind) {
    SmartNotificationKind.CONNECTION -> "Connection"
    SmartNotificationKind.RECOVERY -> "Recovery"
    SmartNotificationKind.PRIVACY -> "Privacy"
    SmartNotificationKind.NETWORK -> "Network"
    SmartNotificationKind.SUBSCRIPTION -> "Subscription"
    SmartNotificationKind.CORE -> "Update"
    SmartNotificationKind.TEST -> "Test"
}
