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

class SmartNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    private val channelsEnsured = java.util.concurrent.atomic.AtomicBoolean(false)

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        if (channelsEnsured.get()) return
        runCatching {
            manager.createNotificationChannelGroup(
                NotificationChannelGroup(GROUP_ID, "MarbleNG")
            )

            val connection = NotificationChannel(
                CHANNEL_CONNECTION,
                "Connection status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent VPN/proxy connection, route and live status"
                group = GROUP_ID
                setShowBadge(false)

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
        const val ACCENT = 0xFF0066CC.toInt()

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

internal fun statusSubText(text: String): String {
    val head = text.substringBefore('•').trim().trimEnd('.', ',', ':', '-', '·').trim()
    return when {
        head.isEmpty() -> "MarbleNG"
        head.length > 32 -> "MarbleNG"
        else -> head
    }
}

internal fun kindSubText(kind: SmartNotificationKind): String = when (kind) {
    SmartNotificationKind.CONNECTION -> "Connection"
    SmartNotificationKind.RECOVERY -> "Recovery"
    SmartNotificationKind.PRIVACY -> "Privacy"
    SmartNotificationKind.NETWORK -> "Network"
    SmartNotificationKind.SUBSCRIPTION -> "Subscription"
    SmartNotificationKind.CORE -> "Update"
    SmartNotificationKind.TEST -> "Test"
}
