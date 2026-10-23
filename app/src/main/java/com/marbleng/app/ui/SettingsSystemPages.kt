package com.marbleng.app.ui

import android.Manifest
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.marbleng.app.AppRepository
import com.marbleng.app.BuildConfig
import com.marbleng.app.core.BugSeverity

@Composable
fun SettingsSubscriptionsPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
    val s = repo.settings

    IosSubPageScaffold(
        title = trx("Subscriptions & Auto-Refresh"),
        onBack = onBack
    ) {
        item(key = "sub-refresh-card") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                IosSectionHeader(title = trx("Automatic Updates"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Auto-Refresh Subscriptions"),
                        subtitle = trx("Keep remote HTTPS server lists fresh automatically"),
                        checked = s.subscriptionAutoRefresh,
                        glyph = MarbleGlyph.REFRESH,
                        squircleColor = colors.blue,
                        onCheckedChange = { repo.updateSettings(s.copy(subscriptionAutoRefresh = it)) }
                    )
                    IosDivider()
                    IosNumberStepperRow(
                        title = trx("Refresh Interval"),
                        value = s.subscriptionRefreshHours,
                        range = 1..168,
                        suffix = "h",
                        glyph = MarbleGlyph.GAUGE,
                        squircleColor = colors.indigo,
                        onValueChange = { repo.updateSettings(s.copy(subscriptionRefreshHours = it)) }
                    )
                }

                IosButton(
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
    val colors = IosTheme.colors

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

    IosSubPageScaffold(
        title = trx("Backup & Restore"),
        onBack = onBack
    ) {
        item(key = "backup-actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Encrypted-Ready JSON Archive"))
                IosCard {
                    IosListItem(
                        title = trx("Export Full Backup"),
                        subtitle = trx("Save subscriptions, manual servers, routing rules and settings to a JSON file"),
                        glyph = MarbleGlyph.UPLOAD,
                        squircleColor = colors.green,
                        enabled = !repo.busy,
                        onClick = {
                            exportLauncher.launch("marbleng-backup-${System.currentTimeMillis() / 1000}.json")
                        }
                    )
                    IosDivider()
                    IosListItem(
                        title = trx("Restore from Backup File"),
                        subtitle = trx("Import a previously saved MarbleNG JSON backup"),
                        glyph = MarbleGlyph.DOWNLOAD,
                        squircleColor = colors.blue,
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
    val colors = IosTheme.colors
    val context = LocalContext.current
    val s = repo.settings

    fun runtimeGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun notificationsEnabled(): Boolean =
        runtimeGranted() && NotificationManagerCompat.from(context).areNotificationsEnabled()

    var notifGranted by remember { mutableStateOf(notificationsEnabled()) }
    var bgGranted by remember { mutableStateOf(repo.backgroundExemptionGranted()) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notifGranted = notificationsEnabled()
    }
    val sysSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        notifGranted = notificationsEnabled()
        bgGranted = repo.backgroundExemptionGranted()
    }

    IosSubPageScaffold(
        title = trx("Notifications & Background"),
        onBack = onBack
    ) {
        item(key = "permissions-status") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("System Permissions"))
                IosCard {
                    IosListItem(
                        title = trx("Notification Permission"),
                        subtitle = if (notifGranted) {
                            trx("Allowed by Android")
                        } else {
                            trx("Tap to allow live VPN status notifications")
                        },
                        glyph = MarbleGlyph.BELL,
                        squircleColor = if (notifGranted) colors.green else colors.orange,
                        trailing = {
                            IosBadge(
                                text = if (notifGranted) "ON" else "OFF",
                                color = if (notifGranted) colors.green else colors.orange
                            )
                        },
                        onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !runtimeGranted()) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                sysSettingsLauncher.launch(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                )
                            }
                        }
                    )
                    IosDivider()
                    IosListItem(
                        title = trx("Unrestricted Background Activity"),
                        subtitle = if (bgGranted) {
                            trx("Granted — tunnel stays alive with screen off")
                        } else {
                            trx("Tap to exempt MarbleNG from battery optimization")
                        },
                        glyph = MarbleGlyph.POWER,
                        squircleColor = if (bgGranted) colors.green else colors.red,
                        trailing = {
                            IosBadge(
                                text = if (bgGranted) "ON" else "ACTION",
                                color = if (bgGranted) colors.green else colors.red
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

        item(key = "smart-notifications") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Notification Preferences"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Live Telemetry in Ongoing Notification"),
                        subtitle = trx("Show real-time ping and throughput in the status bar notification"),
                        checked = s.notificationLiveStats,
                        glyph = MarbleGlyph.GAUGE,
                        squircleColor = colors.blue,
                        onCheckedChange = { repo.updateSettings(s.copy(notificationLiveStats = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Smart Event Alerts"),
                        subtitle = trx("Notify on route recovery, privacy warnings or subscription updates"),
                        checked = s.smartNotificationsEnabled,
                        glyph = MarbleGlyph.BELL,
                        squircleColor = colors.red,
                        onCheckedChange = { repo.updateSettings(s.copy(smartNotificationsEnabled = it)) }
                    )
                    if (s.smartNotificationsEnabled) {
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Connection Events"),
                            checked = s.notifyConnectionEvents,
                            glyph = MarbleGlyph.TUNNEL,
                            squircleColor = colors.teal,
                            onCheckedChange = { repo.updateSettings(s.copy(notifyConnectionEvents = it)) }
                        )
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Failover & Recovery"),
                            checked = s.notifyRecoveryEvents,
                            glyph = MarbleGlyph.REFRESH,
                            squircleColor = colors.orange,
                            onCheckedChange = { repo.updateSettings(s.copy(notifyRecoveryEvents = it)) }
                        )
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Privacy & Kill Switch Warnings"),
                            checked = s.notifyPrivacyWarnings,
                            glyph = MarbleGlyph.SHIELD,
                            squircleColor = colors.red,
                            onCheckedChange = { repo.updateSettings(s.copy(notifyPrivacyWarnings = it)) }
                        )
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Subscription Refreshes"),
                            checked = s.notifySubscriptionEvents,
                            glyph = MarbleGlyph.LAYERS,
                            squircleColor = colors.indigo,
                            onCheckedChange = { repo.updateSettings(s.copy(notifySubscriptionEvents = it)) }
                        )
                        IosDivider()
                        IosNumberStepperRow(
                            title = trx("Alert Cooldown"),
                            value = s.notificationCooldownSec,
                            range = 5..300,
                            step = 5,
                            suffix = "s",
                            glyph = MarbleGlyph.TUNERS,
                            squircleColor = colors.purple,
                            onValueChange = { repo.updateSettings(s.copy(notificationCooldownSec = it)) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IosButton(
                        label = trx("Send Test Alert"),
                        style = IosButtonStyle.TINTED,
                        enabled = notifGranted && s.smartNotificationsEnabled,
                        onClick = { repo.testSmartNotification() },
                        modifier = Modifier.weight(1f)
                    )
                    IosButton(
                        label = trx("Clear Alerts"),
                        style = IosButtonStyle.SECONDARY,
                        onClick = { repo.clearSmartNotifications() },
                        modifier = Modifier.weight(1f)
                    )
                }
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
    val colors = IosTheme.colors
    val clipboard = LocalClipboardManager.current
    var tab by rememberSaveable { mutableStateOf(DiagnosticsTab.BUG_FINDER) }

    IosSubPageScaffold(
        title = trx("Diagnostics, Logs & Bug Finder"),
        onBack = onBack
    ) {
        item(key = "diag-tabs") {
            IosSegmentedControl(
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
                        IosCard {
                            IosSwitchRow(
                                title = trx("Stream Debug TXT Log"),
                                subtitle = repo.debugReportLocation(),
                                checked = repo.settings.debugModeEnabled,
                                glyph = MarbleGlyph.FOLDER,
                                squircleColor = colors.orange,
                                onCheckedChange = { repo.setDebugMode(it) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            IosButton(
                                label = if (repo.busy) Tr.now.measuring else trx("Run Health Scan"),
                                glyph = MarbleGlyph.SPARK,
                                enabled = !repo.busy,
                                onClick = { repo.runBugFinder() },
                                modifier = Modifier.weight(1f)
                            )
                            IosButton(
                                label = Tr.now.copyAction,
                                glyph = MarbleGlyph.COPY,
                                style = IosButtonStyle.SECONDARY,
                                enabled = report != null,
                                onClick = {
                                    clipboard.setText(AnnotatedString(repo.bugFinderReportText()))
                                    repo.setRuntimeMessage("Report copied")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (report != null) {
                            IosCard {
                                IosListItem(
                                    title = trx(report.headline),
                                    subtitle = "${report.passed} pass · ${report.warnings} warn · ${report.failures} fail",
                                    glyph = MarbleGlyph.SHIELD,
                                    squircleColor = when {
                                        report.failures > 0 -> colors.red
                                        report.warnings > 0 -> colors.orange
                                        else -> colors.green
                                    },
                                    showChevron = false
                                )
                                report.checks.forEach { check ->
                                    IosDivider()
                                    val tone = when (check.severity) {
                                        BugSeverity.PASS -> colors.green
                                        BugSeverity.INFO -> colors.blue
                                        BugSeverity.WARN -> colors.orange
                                        BugSeverity.FAIL -> colors.red
                                    }
                                    IosListItem(
                                        title = trx(check.title),
                                        subtitle = trx(check.detail),
                                        glyph = MarbleGlyph.INFO,
                                        squircleColor = tone,
                                        showChevron = false,
                                        trailing = { IosBadge(text = check.severity.name, color = tone) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                IosButton(
                                    label = trx("Save Snapshot"),
                                    style = IosButtonStyle.TINTED,
                                    enabled = !repo.busy,
                                    onClick = { repo.saveBugFinderReport() },
                                    modifier = Modifier.weight(1f)
                                )
                                if (report.failures > 0) {
                                    IosButton(
                                        label = trx("Safe Runtime Reset"),
                                        style = IosButtonStyle.DESTRUCTIVE,
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
                        IosButton(
                            label = if (repo.busy) Tr.now.measuring else trx("Run Privacy & Leak Audit"),
                            glyph = MarbleGlyph.SHIELD,
                            enabled = !repo.busy,
                            onClick = { repo.audit() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (p != null) {
                            IosCard {
                                IosListItem(
                                    title = "${trx("Privacy Score")}: ${p.overallScore}/100",
                                    subtitle = trx(p.note),
                                    glyph = MarbleGlyph.SHIELD,
                                    squircleColor = if (p.healthy) colors.green else colors.orange,
                                    showChevron = false
                                )
                                IosDivider()
                                IosListItem(
                                    title = "${trx("Tunnel Exit IP")}: ${p.proxyIp.ifBlank { "—" }}",
                                    subtitle = "${trx("Underlay IP")}: ${p.underlayIp.ifBlank { "—" }}",
                                    glyph = MarbleGlyph.GLOBE,
                                    squircleColor = colors.blue,
                                    showChevron = false
                                )
                                IosDivider()
                                IosListItem(
                                    title = "${trx("Cloudflare POP")}: ${p.cloudflareLocation.ifBlank { "—" }}",
                                    subtitle = "${trx("DNS")}: ${p.dnsServers.ifBlank { "—" }}",
                                    glyph = MarbleGlyph.SERVER,
                                    squircleColor = colors.indigo,
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
                        IosCard(contentPadding = PaddingValues(16.dp)) {
                            Text(
                                text = doctorText,
                                color = colors.title,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                            )
                        }
                        IosButton(
                            label = trx("Copy Doctor Report"),
                            glyph = MarbleGlyph.COPY,
                            style = IosButtonStyle.TINTED,
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
                            IosButton(
                                label = trx("Refresh Logs"),
                                glyph = MarbleGlyph.REFRESH,
                                style = IosButtonStyle.TINTED,
                                onClick = { refreshTick++ },
                                modifier = Modifier.weight(1f)
                            )
                            IosButton(
                                label = Tr.now.copyAction,
                                glyph = MarbleGlyph.COPY,
                                style = IosButtonStyle.SECONDARY,
                                onClick = {
                                    clipboard.setText(AnnotatedString(logs))
                                    repo.setRuntimeMessage("Logs copied")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        IosCard(contentPadding = PaddingValues(16.dp)) {
                            Text(
                                text = logs.takeLast(8_000).ifBlank { trx("No runtime log entries yet.") },
                                color = colors.title,
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
                        IosCard(contentPadding = PaddingValues(20.dp)) {
                            Text(
                                text = trx("No connection history yet."),
                                color = colors.subtitle,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        IosCard {
                            items.take(40).forEachIndexed { idx, rec ->
                                if (idx > 0) IosDivider()
                                IosListItem(
                                    title = cleanServerName(rec.name),
                                    subtitle = "${rec.reason} · ${relativeTimeLabel(rec.at)}",
                                    glyph = MarbleGlyph.TUNNEL,
                                    squircleColor = colors.blue,
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
    val colors = IosTheme.colors
    val strings = Tr.now
    val context = LocalContext.current
    val s = repo.settings
    var confirmReset by remember { mutableStateOf(false) }

    IosSubPageScaffold(
        title = strings.informationTitle,
        onBack = onBack
    ) {
        item(key = "about-versions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Versions & Cores"))
                IosCard {
                    IosListItem(
                        title = strings.appVersionLabel,
                        subtitle = "MarbleNG (${BuildConfig.VERSION_CODE})",
                        value = BuildConfig.VERSION_NAME,
                        glyph = MarbleGlyph.INFO,
                        squircleColor = colors.blue,
                        showChevron = false
                    )
                    IosDivider()
                    IosListItem(
                        title = strings.xrayCoreLabel,
                        subtitle = BuildConfig.XRAY_CORE_REPO,
                        value = BuildConfig.XRAY_CORE_TAG,
                        glyph = MarbleGlyph.SERVER,
                        squircleColor = colors.green,
                        showChevron = false
                    )
                    IosDivider()
                    IosListItem(
                        title = "sing-box extended",
                        subtitle = BuildConfig.SINGBOX_CORE_REPO,
                        value = BuildConfig.SINGBOX_CORE_TAG,
                        glyph = MarbleGlyph.LAYERS,
                        squircleColor = colors.indigo,
                        showChevron = false
                    )
                    IosDivider()
                    IosListItem(
                        title = strings.hevCoreLabel,
                        subtitle = BuildConfig.HEV_CORE_REPO,
                        value = BuildConfig.HEV_CORE_TAG,
                        glyph = MarbleGlyph.TUNNEL,
                        squircleColor = colors.teal,
                        showChevron = false
                    )
                    IosDivider()
                    IosListItem(
                        title = "tun2socks",
                        subtitle = BuildConfig.TUN2SOCKS_CORE_REPO,
                        value = BuildConfig.TUN2SOCKS_CORE_TAG,
                        glyph = MarbleGlyph.ROUTE,
                        squircleColor = colors.purple,
                        showChevron = false
                    )
                }
            }
        }

        item(key = "about-updates-links") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Updates & Open Source"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Automatic App Update Check"),
                        subtitle = trx("Check GitHub releases on startup"),
                        checked = s.appUpdateCheckEnabled,
                        glyph = MarbleGlyph.DOWNLOAD,
                        squircleColor = colors.blue,
                        onCheckedChange = { repo.updateSettings(s.copy(appUpdateCheckEnabled = it)) }
                    )
                    IosDivider()
                    IosListItem(
                        title = trx("Check for Updates Now"),
                        subtitle = trx("Query latest MarbleNG release on GitHub"),
                        glyph = MarbleGlyph.REFRESH,
                        squircleColor = colors.green,
                        onClick = { repo.checkForAppUpdate(force = true) }
                    )
                    IosDivider()
                    IosListItem(
                        title = strings.sourceCodeLabel,
                        subtitle = BuildConfig.SOURCE_URL,
                        glyph = MarbleGlyph.LINK,
                        squircleColor = colors.gray,
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
            IosCard {
                IosListItem(
                    title = trx("Reset All Settings to Defaults"),
                    subtitle = trx("Restores default settings while keeping your saved servers"),
                    titleColor = colors.red,
                    glyph = MarbleGlyph.TRASH,
                    squircleColor = colors.red,
                    onClick = { confirmReset = true }
                )
            }
        }
    }

    if (confirmReset) {
        IosAlertDialog(
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
                color = colors.subtitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal)
            )
        }
    }
}
