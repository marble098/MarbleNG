package com.marbleng.app.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.marbleng.app.AppRepository
import com.marbleng.app.BuildConfig
import com.marbleng.app.core.BugSeverity
import com.marbleng.app.core.ConnectionStatus
import com.marbleng.app.core.NotificationChannelKey
import com.marbleng.app.core.NotificationCopy
import com.marbleng.app.core.NotificationLanguage
import com.marbleng.app.core.NotificationRouteCycle
import com.marbleng.app.core.NotificationRoutePicker
import com.marbleng.app.core.ShadeLevel
import com.marbleng.app.model.ShadeControlDefaults
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

@Composable
fun SettingsSubscriptionsPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.REFRESH,
        tint = colors.primary,
        title = trx("Subscriptions & refresh"),
        onBack = onBack
    ) {
        item(key = "sub-refresh-card") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MarbleSectionHeader(title = trx("Automatic Updates"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Auto-Refresh Subscriptions"),
                        subtitle = trx("Keep remote HTTPS server lists fresh automatically"),
                        checked = s.subscriptionAutoRefresh,
                        glyph = MarbleGlyph.REFRESH,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(subscriptionAutoRefresh = it)) }
                    )
                    MarbleDivider()
                    MarbleNumberStepperRow(
                        title = trx("Refresh Interval"),
                        value = s.subscriptionRefreshHours,
                        range = 1..168,
                        suffix = "h",
                        glyph = MarbleGlyph.GAUGE,
                        iconTint = colors.secondary,
                        onValueChange = { repo.updateSettings(s.copy(subscriptionRefreshHours = it)) }
                    )
                }

                MarbleButton(
                    label = trx("Refresh All Subscriptions Now"),
                    glyph = MarbleGlyph.REFRESH,
                    enabled = repo.subscriptions.isNotEmpty() && !repo.busy,
                    onClick = { repo.refreshAll() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun SettingsBackupRestorePage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) repo.writeBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) repo.restoreBackup(uri)
    }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.VAULT,
        tint = colors.success,
        title = trx("Backup"),
        onBack = onBack
    ) {
        item(key = "backup-actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Encrypted-Ready JSON Archive"))
                MarbleCard {
                    MarbleListItem(
                        title = trx("Export Full Backup"),
                        subtitle = trx("Save subscriptions, manual servers, routing rules and settings to a JSON file"),
                        glyph = MarbleGlyph.UPLOAD,
                        iconTint = colors.success,
                        enabled = !repo.busy,
                        onClick = {
                            exportLauncher.launch("marbleng-backup-${System.currentTimeMillis() / 1000}.json")
                        }
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Restore from Backup File"),
                        subtitle = trx("Import a previously saved MarbleNG JSON backup"),
                        glyph = MarbleGlyph.DOWNLOAD,
                        iconTint = colors.primary,
                        enabled = !repo.busy,
                        onClick = {
                            importLauncher.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsNotificationsPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val s = repo.settings
    val activity = context as? Activity

    fun runtimeGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun appLevelEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun channelImportance(channelId: String): Int {
        if (Build.VERSION.SDK_INT < 26) return NotificationManager.IMPORTANCE_DEFAULT
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return NotificationManager.IMPORTANCE_UNSPECIFIED
        return manager.getNotificationChannel(channelId)?.importance
            ?: NotificationManager.IMPORTANCE_UNSPECIFIED
    }

    var notifGranted by remember { mutableStateOf(runtimeGranted() && appLevelEnabled()) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }
    var bgGranted by remember { mutableStateOf(repo.backgroundExemptionGranted()) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifGranted = granted && appLevelEnabled()
        if (!notifGranted) deniedOnce = true
    }
    val sysSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        notifGranted = runtimeGranted() && appLevelEnabled()
        bgGranted = repo.backgroundExemptionGranted()
    }

    val canAskAndroid = Build.VERSION.SDK_INT >= 33 && !runtimeGranted() && !deniedOnce &&
        activity?.let {
            androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } != false

    fun openAppNotificationSettings() {
        runCatching {
            sysSettingsLauncher.launch(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    fun openChannelSettings(channelId: String) {
        if (Build.VERSION.SDK_INT < 26) return
        runCatching {
            sysSettingsLauncher.launch(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            )
        }
    }

    // MARBLE_NOTIFICATION_COUNCIL_V231 — the page reads like the shade: permission, then what the
    // user will actually see, then the four channels Android owns, then the app's own switches.
    val permissionBadge = when {
        !runtimeGranted() -> "ASK"
        !appLevelEnabled() -> "BLOCKED"
        else -> "ON"
    }
    val permissionTint = when {
        !runtimeGranted() || !appLevelEnabled() -> colors.warning
        else -> colors.success
    }
    val permissionSubtitle = when {
        !runtimeGranted() ->
            trx("Android has not granted notifications to MarbleNG yet")
        !appLevelEnabled() ->
            trx("Notifications are switched off for MarbleNG in Android")
        else -> trx("Allowed by Android — the tunnel item and alerts can appear")
    }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.BELL,
        tint = colors.error,
        title = trx("Notifications & battery"),
        onBack = onBack
    ) {
        item(key = "permissions-status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("System Permissions"),
                    badge = permissionBadge,
                    badgeColor = permissionTint
                )
                MarbleCard {
                    MarbleListItem(
                        title = trx("Notification Permission"),
                        subtitle = permissionSubtitle,
                        glyph = MarbleGlyph.BELL,
                        iconTint = if (notifGranted) colors.success else colors.warning,
                        trailing = {
                            MarbleBadge(
                                text = permissionBadge,
                                color = permissionTint
                            )
                        },
                        onClick = {
                            if (canAskAndroid) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                openAppNotificationSettings()
                            }
                        }
                    )
                    if (!notifGranted) {
                        MarbleDivider()
                        MarbleListItem(
                            title = trx("Open Android notification settings"),
                            subtitle = trx("The only place a blocked permission can be restored"),
                            glyph = MarbleGlyph.INFO,
                            iconTint = colors.error,
                            onClick = { openAppNotificationSettings() }
                        )
                    }
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Unrestricted Background Activity"),
                        subtitle = if (bgGranted) {
                            trx("Granted — tunnel stays alive with screen off")
                        } else {
                            trx("Tap to exempt MarbleNG from battery optimization")
                        },
                        glyph = MarbleGlyph.POWER,
                        iconTint = if (bgGranted) colors.success else colors.error,
                        trailing = {
                            MarbleBadge(
                                text = trx(if (bgGranted) "ON" else "ACTION"),
                                color = if (bgGranted) colors.success else colors.error
                            )
                        },
                        onClick = {
                            repo.noteBackgroundAccessRequest()
                            val direct = repo.unrestrictedBackgroundDirectIntent()
                            val fallback = repo.unrestrictedBackgroundFallbackIntent()
                            runCatching { sysSettingsLauncher.launch(direct) }
                                .recoverCatching { sysSettingsLauncher.launch(fallback) }
                                .onFailure { repo.requestUnrestrictedBackgroundAccess(context) }
                        }
                    )
                }
            }
        }

        item(key = "notification-preview") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Live Preview"),
                    badge = if (notifGranted) trx("LIVE") else trx("OFF"),
                    badgeColor = if (notifGranted) colors.success else colors.error
                )
                NotificationPreviewCard(repo = repo)
            }
        }

        item(key = "shade-route-control") {
            // MARBLE_SHADE_STUDIO_V249 — the shade's route control, in the user's hands.
            //
            // The preview above shows what the item looks like; this card is what decides it. Every
            // row is a switch or a named choice, and nothing here is buried behind a long-press or a
            // diagnostic: the notification is the surface a user touches most often in a day, so the
            // settings that shape it belong on the page that describes it.
            val pickerOn = s.shadeControl.picker
            val studio = ShadeControlDefaults.styleId(s.shadeControl.style) ==
                ShadeControlDefaults.STYLE_STUDIO
            val rowChoices = ShadeControlDefaults.ROW_CHOICES
            val rowChoice = rowChoices.firstOrNull {
                it == ShadeControlDefaults.rowChoice(s.shadeControl.rows)
            } ?: ShadeControlDefaults.ROWS

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Route control in the notification"),
                    badge = if (pickerOn || s.shadeControl.cycle) trx("ON") else trx("OFF"),
                    badgeColor = if (pickerOn || s.shadeControl.cycle) {
                        colors.success
                    } else {
                        colors.onSurfaceVariant
                    }
                )
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Pick a server from the notification"),
                        subtitle = trx("Lists routes by name inside the ongoing item, so a change never needs the app"),
                        checked = pickerOn,
                        glyph = MarbleGlyph.SERVER,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(shadeControl = s.shadeControl.copy(picker = it))) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Next server and next subscription buttons"),
                        subtitle = trx("Two actions that move the live route in one tap, without a list"),
                        checked = s.shadeControl.cycle,
                        glyph = MarbleGlyph.REFRESH,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(shadeControl = s.shadeControl.copy(cycle = it))) }
                    )
                    if (pickerOn) {
                        MarbleDivider()
                        MarbleChoiceRow(
                            title = trx("Full control"),
                            subtitle = trx("Servers and subscriptions on two floors, paged, with the measured ping beside each"),
                            selected = studio,
                            glyph = MarbleGlyph.LAYERS,
                            iconTint = colors.tertiary,
                            onClick = {
                                repo.updateSettings(
                                    s.copy(shadeControl = s.shadeControl.copy(style = ShadeControlDefaults.STYLE_STUDIO))
                                )
                            }
                        )
                        MarbleDivider()
                        MarbleChoiceRow(
                            title = trx("Simple list"),
                            subtitle = trx("The live subscription's servers, then one row per other subscription"),
                            selected = !studio,
                            glyph = MarbleGlyph.MENU,
                            iconTint = colors.onSurfaceVariant,
                            onClick = {
                                repo.updateSettings(
                                    s.copy(shadeControl = s.shadeControl.copy(style = ShadeControlDefaults.STYLE_LIST))
                                )
                            }
                        )
                        if (studio) {
                            MarbleDivider()
                            MarbleSwitchRow(
                                title = trx("Measured ping beside each server"),
                                subtitle = trx("Reuses what the app already measured; nothing is pinged for the notification"),
                                checked = s.shadeControl.showPing,
                                glyph = MarbleGlyph.GAUGE,
                                iconTint = colors.primary,
                                onCheckedChange = {
                                    repo.updateSettings(s.copy(shadeControl = s.shadeControl.copy(showPing = it)))
                                }
                            )
                            MarbleDivider()
                            Column(
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = trx("Rows listed at once"),
                                    color = colors.onSurface,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = trx("A notification has a fixed height, so a longer library is paged instead of squeezed"),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                MarbleSegmentedControl(
                                    options = rowChoices.map { count -> count to faDigits("$count") },
                                    selected = rowChoice,
                                    onSelect = { count ->
                                        repo.updateSettings(s.copy(shadeControl = s.shadeControl.copy(rows = count)))
                                    }
                                )
                            }
                        }
                    }
                }
                MarbleSectionFooter(
                    text = if (!pickerOn && !s.shadeControl.cycle) {
                        trx("The notification will only show the state, the route and the disconnect action")
                    } else {
                        trx("Everything here happens inside the notification — the tunnel answers the tap and redraws itself")
                    }
                )
            }
        }

        item(key = "notification-channels") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Channels"),
                    badge = NotificationChannelKey.menuOrder.size.toString(),
                    badgeColor = colors.primary
                )
                MarbleCard {
                    NotificationChannelKey.menuOrder.forEachIndexed { index, key ->
                        if (index > 0) MarbleDivider()
                        val importance = channelImportance(key.id)
                        val state = when {
                            !notifGranted -> "OFF" to colors.error
                            importance == NotificationManager.IMPORTANCE_UNSPECIFIED ->
                                "—" to colors.onSurfaceVariant
                            importance == NotificationManager.IMPORTANCE_NONE ->
                                "MUTED" to colors.error
                            importance >= NotificationManager.IMPORTANCE_HIGH ->
                                "LOUD" to colors.success
                            importance >= NotificationManager.IMPORTANCE_DEFAULT ->
                                "ON" to colors.success
                            else -> "QUIET" to colors.warning
                        }
                        MarbleListItem(
                            title = trx(NotificationCopy.channelTitle(key, NotificationLanguage.EN)),
                            subtitle = trx(NotificationCopy.channelFocus(key, NotificationLanguage.EN)),
                            glyph = when (key) {
                                NotificationChannelKey.CONNECTION -> MarbleGlyph.TUNNEL
                                NotificationChannelKey.ALERTS -> MarbleGlyph.ROUTE
                                NotificationChannelKey.PRIVACY -> MarbleGlyph.SHIELD
                                NotificationChannelKey.UPDATES -> MarbleGlyph.DOWNLOAD
                            },
                            iconTint = when (key) {
                                NotificationChannelKey.CONNECTION -> colors.tertiary
                                NotificationChannelKey.ALERTS -> colors.primary
                                NotificationChannelKey.PRIVACY -> colors.error
                                NotificationChannelKey.UPDATES -> colors.secondary
                            },
                            trailing = {
                                MarbleBadge(text = state.first, color = state.second)
                            },
                            enabled = Build.VERSION.SDK_INT >= 26,
                            onClick = { openChannelSettings(key.id) }
                        )
                    }
                }
            }
        }

        item(key = "smart-notifications") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(
                    title = trx("Notification Preferences"),
                    badge = if (s.smartNotificationsEnabled) trx("SMART ALERTS ON") else trx("SMART ALERTS OFF"),
                    badgeColor = if (s.smartNotificationsEnabled) colors.success else colors.warning
                )
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Live Telemetry in Ongoing Notification"),
                        subtitle = trx("Show real-time ping and throughput in the status bar notification"),
                        checked = s.notificationLiveStats,
                        glyph = MarbleGlyph.GAUGE,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(notificationLiveStats = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Session Clock"),
                        subtitle = trx("A live chronometer in the ongoing item, drawn by Android, never reposted"),
                        checked = s.notificationSessionTimer,
                        glyph = MarbleGlyph.RADAR,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(notificationSessionTimer = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Smart Event Alerts"),
                        subtitle = trx("Notify on route recovery, privacy warnings or subscription updates"),
                        checked = s.smartNotificationsEnabled,
                        glyph = MarbleGlyph.BELL,
                        iconTint = colors.error,
                        onCheckedChange = { repo.updateSettings(s.copy(smartNotificationsEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Connection Events"),
                        enabled = s.smartNotificationsEnabled,
                        checked = s.notifyConnectionEvents,
                        glyph = MarbleGlyph.TUNNEL,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(notifyConnectionEvents = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Failover & Recovery"),
                        enabled = s.smartNotificationsEnabled,
                        checked = s.notifyRecoveryEvents,
                        glyph = MarbleGlyph.REFRESH,
                        iconTint = colors.warning,
                        onCheckedChange = { repo.updateSettings(s.copy(notifyRecoveryEvents = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Privacy & Kill Switch Warnings"),
                        enabled = s.smartNotificationsEnabled,
                        checked = s.notifyPrivacyWarnings,
                        glyph = MarbleGlyph.SHIELD,
                        iconTint = colors.error,
                        onCheckedChange = { repo.updateSettings(s.copy(notifyPrivacyWarnings = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Privacy Alarms Can Interrupt"),
                        subtitle = trx("Their own channel: sound, vibration and a lock-screen banner"),
                        enabled = s.smartNotificationsEnabled && s.notifyPrivacyWarnings,
                        checked = s.notifyPrivacyHeadsUp,
                        glyph = MarbleGlyph.RADAR,
                        iconTint = colors.error,
                        onCheckedChange = { repo.updateSettings(s.copy(notifyPrivacyHeadsUp = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Network Changes"),
                        enabled = s.smartNotificationsEnabled,
                        checked = s.notifyNetworkChanges,
                        glyph = MarbleGlyph.GLOBE,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(notifyNetworkChanges = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Subscription Refreshes"),
                        enabled = s.smartNotificationsEnabled,
                        checked = s.notifySubscriptionEvents,
                        glyph = MarbleGlyph.LAYERS,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(notifySubscriptionEvents = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Core & App Updates"),
                        subtitle = trx("A newer build or a core refresh worth installing"),
                        enabled = s.smartNotificationsEnabled,
                        checked = s.notifyCoreUpdates,
                        glyph = MarbleGlyph.DOWNLOAD,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(notifyCoreUpdates = it)) }
                    )
                    MarbleDivider()
                    MarbleNumberStepperRow(
                        title = trx("Alert Cooldown"),
                        subtitle = trx("Minimum gap between two alerts of the same kind"),
                        value = s.notificationCooldownSec,
                        range = 5..300,
                        step = 5,
                        suffix = "s",
                        glyph = MarbleGlyph.TUNERS,
                        iconTint = colors.secondary,
                        onValueChange = { repo.updateSettings(s.copy(notificationCooldownSec = it)) }
                    )
                }
            }
        }

        item(key = "notification-actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val canTest = notifGranted && s.smartNotificationsEnabled
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MarbleButton(
                        label = trx("Send Test Alert"),
                        style = MarbleButtonStyle.TINTED,
                        enabled = canTest,
                        onClick = { repo.testSmartNotification() },
                        modifier = Modifier.weight(1f)
                    )
                    MarbleButton(
                        label = trx("Clear Alerts"),
                        style = MarbleButtonStyle.SECONDARY,
                        onClick = { repo.clearSmartNotifications() },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!canTest) {
                    MarbleSectionFooter(
                        text = if (!notifGranted) {
                            trx("Android is blocking notifications, so no test can be delivered")
                        } else {
                            trx("Turn on Smart Event Alerts to send a test")
                        }
                    )
                }
            }
        }
    }
}

/**
 * MARBLE_NOTIFICATION_COUNCIL_V231 — a preview built from the same strings the shade receives.
 *
 * The page previously described the notification in prose; a user cannot proof-read
 * `"Protected • Frankfurt • 42 ms"` in their head. This renders the real vocabulary, the real
 * measurement formatting and the real subtext, so what is on screen is what will be posted.
 */
@Composable
private fun NotificationPreviewCard(repo: AppRepository) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val status = when (repo.state) {
        "CONNECTED" -> ConnectionStatus.PROTECTED
        "CONNECTING" -> ConnectionStatus.CONNECTING
        "BLOCKED" -> ConnectionStatus.BLOCKED
        else -> ConnectionStatus.DISCONNECTED
    }
    val selected = repo.profile(repo.selectedProfileId, repo.selectedProfileSourceId)
    val route = selected?.name.orEmpty()
    // MARBLE_NOTIFICATION_SWITCH_V234 — the preview shows the same second line the drawn view
    // shows: `route · subscription`, not a bare route name.
    val detail = if (route.isBlank()) {
        NotificationCopy.statusDetail(status, NotificationLanguage.EN)
    } else {
        NotificationRouteCycle.routeLine(route, selected?.subscriptionName.orEmpty())
    }
    val metrics = NotificationCopy.telemetryCompact(
        NotificationLanguage.EN,
        repo.livePingMs,
        repo.liveDownBps,
        repo.liveUpBps
    )

    MarbleCard {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MarbleRowIcon(
                    glyph = MarbleGlyph.BELL,
                    tint = colors.primary,
                    size = 30.dp,
                    glyphSize = 16.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trx(NotificationCopy.statusTitle(status, NotificationLanguage.EN)),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = trx(detail),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "MarbleNG",
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            MarbleFadeDivider()
            if (s.notificationLiveStats) {
                Text(
                    text = metrics,
                    color = colors.onSurface,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleBadge(
                    text = trx(NotificationCopy.statusSubText(status, NotificationLanguage.EN)),
                    color = when (status) {
                        ConnectionStatus.PROTECTED -> colors.success
                        ConnectionStatus.BLOCKED -> colors.error
                        ConnectionStatus.DISCONNECTED -> colors.onSurfaceVariant
                        else -> colors.warning
                    }
                )
                if (s.notificationSessionTimer) {
                    MarbleBadge(text = trx("SESSION TIMER"), color = colors.tertiary)
                }
                if (s.smartNotificationsEnabled) {
                    MarbleBadge(text = trx("ALERTS ON"), color = colors.secondary)
                }
            }
            // MARBLE_NOTIFICATION_SWITCH_V234 — the shade switches the route itself now, so the
            // preview names both verbs and says they act in place.
            if (s.shadeControl.cycle) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarbleBadge(
                        text = "↔ ${trx(NotificationCopy.actionNextServer(NotificationLanguage.EN))}",
                        color = colors.primary
                    )
                    MarbleBadge(
                        text = "⇄ ${trx(NotificationCopy.actionNextSubscription(NotificationLanguage.EN))}",
                        color = colors.secondary
                    )
                }
            }
            // MARBLE_SHADE_STUDIO_V249 — the route control, drawn by the same builder the shade
            // uses, so this page cannot describe an item the notification will not look like.
            //
            // Two rules the preview keeps that a hand-written mock would break: the chrome words
            // come from [NotificationCopy.shadeChrome] in the app's own language (the shade resolves
            // its language the same way), and the route names are drawn *raw* — a server is called
            // what the user called it, and the fallback translator would transliterate `Frankfurt`
            // into Persian letters that the real notification never shows.
            if (s.shadeControl.picker) {
                val shadeLanguage = if (Tr.now.language == MarbleLanguage.FA) {
                    NotificationLanguage.FA
                } else {
                    NotificationLanguage.EN
                }
                val previewRoutes = repo.notificationPickerRoutes()
                val previewRows = if (
                    ShadeControlDefaults.styleId(s.shadeControl.style) ==
                    ShadeControlDefaults.STYLE_STUDIO
                ) {
                    NotificationRoutePicker.studio(
                        NotificationRoutePicker.StudioInput(
                            routes = previewRoutes,
                            currentId = repo.selectedProfileId,
                            currentSourceId = repo.selectedProfileSourceId,
                            level = ShadeLevel.SERVERS,
                            page = 0,
                            rowBudget = s.shadeControl.rows,
                            showPing = s.shadeControl.showPing,
                            pingLabels = repo.notificationShadePingLabels(),
                            chrome = NotificationCopy.shadeChrome(shadeLanguage)
                        )
                    ).rows
                } else {
                    NotificationRoutePicker.build(
                        NotificationRoutePicker.Input(
                            routes = previewRoutes,
                            currentId = repo.selectedProfileId,
                            currentSourceId = repo.selectedProfileSourceId
                        )
                    )
                }
                MarbleFadeDivider()
                if (previewRows.isEmpty()) {
                    Text(
                        text = trx("Add a server and its name will be listed here, tappable from the notification"),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else {
                    previewRows.forEach { row ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = row.label,
                                modifier = Modifier.weight(1f),
                                color = when {
                                    row.active -> colors.primary
                                    row.kind == NotificationRoutePicker.RowKind.SECTION ||
                                        row.kind == NotificationRoutePicker.RowKind.CONTROL ||
                                        row.kind == NotificationRoutePicker.RowKind.OPEN_APP ->
                                        colors.onSurfaceVariant
                                    else -> colors.onSurface
                                },
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (row.meta.isNotEmpty()) {
                                Text(
                                    text = faDigits(row.meta),
                                    color = colors.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            if (row.active) {
                                MarbleBadge(text = trx("LIVE"), color = colors.success)
                            }
                        }
                    }
                }
            }
            if (s.shadeControl.cycle || s.shadeControl.picker) {
                Text(
                    text = trx("Every one of these works from the shade — no need to open the app"),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private enum class DiagnosticsTab {
    BUG_FINDER,
    PRIVACY,
    DOCTOR,
    LOGS,
    HISTORY
}

@Composable
fun SettingsDiagnosticsPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var tab by rememberSaveable { mutableStateOf(DiagnosticsTab.BUG_FINDER) }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.TUNERS,
        tint = colors.warning,
        title = trx("Fix & diagnose"),
        onBack = onBack
    ) {
        item(key = "diag-tabs") {
            MarbleSegmentedControl(
                options = listOf(
                    DiagnosticsTab.BUG_FINDER to trx("Scan"),
                    DiagnosticsTab.PRIVACY to trx("Privacy"),
                    DiagnosticsTab.DOCTOR to trx("Doctor"),
                    DiagnosticsTab.LOGS to trx("Logs"),
                    DiagnosticsTab.HISTORY to trx("History")
                ),
                selected = tab,
                onSelect = { tab = it }
            )
        }

        when (tab) {
            DiagnosticsTab.BUG_FINDER -> {
                val report = repo.bugReport
                item(key = "bug-finder-panel") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MarbleCard {
                            MarbleSwitchRow(
                                title = trx("Stream Debug TXT Log"),
                                subtitle = repo.debugReportLocation(),
                                checked = repo.settings.debugModeEnabled,
                                glyph = MarbleGlyph.FOLDER,
                                iconTint = colors.warning,
                                onCheckedChange = { repo.setDebugMode(it) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MarbleButton(
                                label = if (repo.busy) Tr.now.measuring else trx("Run Health Scan"),
                                glyph = MarbleGlyph.SPARK,
                                enabled = !repo.busy,
                                onClick = { repo.runBugFinder() },
                                modifier = Modifier.weight(1f)
                            )
                            MarbleButton(
                                label = Tr.now.copyAction,
                                glyph = MarbleGlyph.COPY,
                                style = MarbleButtonStyle.SECONDARY,
                                enabled = report != null,
                                onClick = {
                                    clipboard.setText(AnnotatedString(repo.bugFinderReportText()))
                                    repo.setRuntimeMessage("Report copied")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (report != null) {
                            MarbleCard {
                                MarbleListItem(
                                    title = trx(report.headline),
                                    subtitle = "${report.passed} pass · ${report.warnings} warn · ${report.failures} fail",
                                    glyph = MarbleGlyph.SHIELD,
                                    iconTint = when {
                                        report.failures > 0 -> colors.error
                                        report.warnings > 0 -> colors.warning
                                        else -> colors.success
                                    },
                                    showChevron = false
                                )
                                report.checks.forEach { check ->
                                    MarbleDivider()
                                    val tone = when (check.severity) {
                                        BugSeverity.PASS -> colors.success
                                        BugSeverity.INFO -> colors.primary
                                        BugSeverity.WARN -> colors.warning
                                        BugSeverity.FAIL -> colors.error
                                    }
                                    MarbleListItem(
                                        title = trx(check.title),
                                        subtitle = trx(check.detail),
                                        glyph = MarbleGlyph.INFO,
                                        iconTint = tone,
                                        showChevron = false,
                                        trailing = { MarbleBadge(text = check.severity.name, color = tone) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MarbleButton(
                                    label = trx("Save Snapshot"),
                                    style = MarbleButtonStyle.TINTED,
                                    enabled = !repo.busy,
                                    onClick = { repo.saveBugFinderReport() },
                                    modifier = Modifier.weight(1f)
                                )
                                if (report.failures > 0) {
                                    MarbleButton(
                                        label = trx("Safe Runtime Reset"),
                                        style = MarbleButtonStyle.DESTRUCTIVE,
                                        enabled = !repo.busy,
                                        onClick = { repo.safeRuntimeResetFromBugFinder() },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            DiagnosticsTab.PRIVACY -> {
                val p = repo.privacy
                item(key = "privacy-audit") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MarbleButton(
                            label = if (repo.busy) Tr.now.measuring else trx("Run Privacy & Leak Audit"),
                            glyph = MarbleGlyph.SHIELD,
                            enabled = !repo.busy,
                            onClick = { repo.audit() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (p != null) {
                            MarbleCard {
                                MarbleListItem(
                                    title = "${trx("Privacy Score")}: ${p.overallScore}/100",
                                    subtitle = trx(p.note),
                                    glyph = MarbleGlyph.SHIELD,
                                    iconTint = if (p.healthy) colors.success else colors.warning,
                                    showChevron = false
                                )
                                MarbleDivider()
                                MarbleListItem(
                                    title = "${trx("Tunnel Exit IP")}: ${p.proxyIp.ifBlank { "—" }}",
                                    subtitle = "${trx("Underlay IP")}: ${p.underlayIp.ifBlank { "—" }}",
                                    glyph = MarbleGlyph.GLOBE,
                                    iconTint = colors.primary,
                                    showChevron = false
                                )
                                MarbleDivider()
                                MarbleListItem(
                                    title = "${trx("Cloudflare POP")}: ${p.cloudflareLocation.ifBlank { "—" }}",
                                    subtitle = "${trx("DNS")}: ${p.dnsServers.ifBlank { "—" }}",
                                    glyph = MarbleGlyph.SERVER,
                                    iconTint = colors.secondary,
                                    showChevron = false
                                )
                            }
                        }
                    }
                }
            }

            DiagnosticsTab.DOCTOR -> {
                item(key = "system-doctor") {
                    val doctorText = remember(repo.state, repo.message, repo.busy) { repo.doctor() }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MarbleCard(contentPadding = PaddingValues(16.dp)) {
                            Text(
                                text = doctorText,
                                color = colors.onSurface,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                        MarbleButton(
                            label = trx("Copy Doctor Report"),
                            glyph = MarbleGlyph.COPY,
                            style = MarbleButtonStyle.TINTED,
                            onClick = {
                                clipboard.setText(AnnotatedString(doctorText))
                                repo.setRuntimeMessage("Doctor report copied")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            DiagnosticsTab.LOGS -> {
                item(key = "runtime-logs") {
                    var refreshTick by remember { mutableStateOf(0) }
                    val logs = remember(refreshTick, repo.state, repo.message) { repo.readLogs() }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MarbleButton(
                                label = trx("Refresh Logs"),
                                glyph = MarbleGlyph.REFRESH,
                                style = MarbleButtonStyle.TINTED,
                                onClick = { refreshTick++ },
                                modifier = Modifier.weight(1f)
                            )
                            MarbleButton(
                                label = Tr.now.copyAction,
                                glyph = MarbleGlyph.COPY,
                                style = MarbleButtonStyle.SECONDARY,
                                onClick = {
                                    clipboard.setText(AnnotatedString(logs))
                                    repo.setRuntimeMessage("Logs copied")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        MarbleCard(contentPadding = PaddingValues(16.dp)) {
                            Text(
                                text = logs.takeLast(8_000).ifBlank { trx("No runtime log entries yet.") },
                                color = colors.onSurface,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }

            DiagnosticsTab.HISTORY -> {
                item(key = "conn-history") {
                    val items = repo.history.asReversed()
                    if (items.isEmpty()) {
                        MarbleCard(contentPadding = PaddingValues(20.dp)) {
                            Text(
                                text = trx("No connection history yet."),
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        MarbleCard {
                            items.take(40).forEachIndexed { idx, rec ->
                                if (idx > 0) MarbleDivider()
                                MarbleListItem(
                                    title = cleanServerName(rec.name),
                                    subtitle = "${rec.reason} · ${trx(relativeTimeLabel(rec.at))}",
                                    glyph = MarbleGlyph.TUNNEL,
                                    iconTint = colors.primary,
                                    showChevron = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsAboutPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val context = LocalContext.current
    val s = repo.settings
    var confirmReset by remember { mutableStateOf(false) }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.INFO,
        tint = colors.onSurfaceVariant,
        title = strings.informationTitle,
        onBack = onBack
    ) {
        item(key = "about-versions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Versions & Cores"))
                MarbleCard {
                    MarbleListItem(
                        title = strings.appVersionLabel,
                        subtitle = "MarbleNG (${BuildConfig.VERSION_CODE})",
                        value = BuildConfig.VERSION_NAME,
                        glyph = MarbleGlyph.INFO,
                        iconTint = colors.primary,
                        showChevron = false
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = strings.xrayCoreLabel,
                        subtitle = BuildConfig.XRAY_CORE_REPO,
                        value = BuildConfig.XRAY_CORE_TAG,
                        glyph = MarbleGlyph.SERVER,
                        iconTint = colors.success,
                        showChevron = false
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = "sing-box extended",
                        subtitle = BuildConfig.SINGBOX_CORE_REPO,
                        value = BuildConfig.SINGBOX_CORE_TAG,
                        glyph = MarbleGlyph.LAYERS,
                        iconTint = colors.secondary,
                        showChevron = false
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = strings.hevCoreLabel,
                        subtitle = BuildConfig.HEV_CORE_REPO,
                        value = BuildConfig.HEV_CORE_TAG,
                        glyph = MarbleGlyph.TUNNEL,
                        iconTint = colors.tertiary,
                        showChevron = false
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = "tun2socks",
                        subtitle = BuildConfig.TUN2SOCKS_CORE_REPO,
                        value = BuildConfig.TUN2SOCKS_CORE_TAG,
                        glyph = MarbleGlyph.ROUTE,
                        iconTint = colors.secondary,
                        showChevron = false
                    )
                }
            }
        }

        item(key = "about-updates-links") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Updates & Open Source"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Automatic App Update Check"),
                        subtitle = trx("Check GitHub releases on startup"),
                        checked = s.appUpdateCheckEnabled,
                        glyph = MarbleGlyph.DOWNLOAD,
                        iconTint = colors.primary,
                        onCheckedChange = { repo.updateSettings(s.copy(appUpdateCheckEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Check for Updates Now"),
                        subtitle = trx("Query latest MarbleNG release on GitHub"),
                        glyph = MarbleGlyph.REFRESH,
                        iconTint = colors.success,
                        onClick = { repo.checkForAppUpdate(force = true) }
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = strings.sourceCodeLabel,
                        subtitle = BuildConfig.SOURCE_URL,
                        glyph = MarbleGlyph.LINK,
                        iconTint = colors.onSurfaceVariant,
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SOURCE_URL))
                                )
                            }
                        }
                    )
                }
            }
        }

        item(key = "about-reset") {
            MarbleCard {
                MarbleListItem(
                    title = trx("Reset All Settings to Defaults"),
                    subtitle = trx("Restores default settings while keeping your saved servers"),
                    titleColor = colors.error,
                    glyph = MarbleGlyph.TRASH,
                    iconTint = colors.error,
                    onClick = { confirmReset = true }
                )
            }
        }
    }

    if (confirmReset) {
        MarbleAlertDialog(
            title = trx("Reset Settings?"),
            onDismiss = { confirmReset = false },
            confirmLabel = Tr.now.reset,
            destructive = true,
            onConfirm = {
                repo.resetSettings()
                confirmReset = false
            }
        ) {
            Text(
                text = trx("Your saved servers and subscriptions will not be deleted."),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal)
            )
        }
    }
}
