package com.marbleng.app.ui

// MARBLE_FIRST_RUN_PERMISSIONS_V41
// MARBLE_FIRST_RUN_PERMISSIONS_V41_1

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

private fun vpnConsentGranted(context: Context): Boolean = VpnService.prepare(context) == null

private fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

private fun batteryExemptionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val power = context.getSystemService(PowerManager::class.java)
    return power?.isIgnoringBatteryOptimizations(context.packageName) == true
}

@Composable
fun MarblePermissionOnboarding(onComplete: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = context as LifecycleOwner
    var vpnReady by remember { mutableStateOf(vpnConsentGranted(context)) }
    var notificationReady by remember { mutableStateOf(notificationsGranted(context)) }
    var batteryReady by remember { mutableStateOf(batteryExemptionGranted(context)) }
    var notificationDenied by rememberSaveable { mutableStateOf(false) }

    fun refreshAccess() {
        vpnReady = vpnConsentGranted(context)
        notificationReady = notificationsGranted(context)
        batteryReady = batteryExemptionGranted(context)
    }

    val vpnPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        vpnReady = result.resultCode == Activity.RESULT_OK || vpnConsentGranted(context)
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationReady = granted || notificationsGranted(context)
        notificationDenied = !notificationReady
    }
    val systemSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshAccess() }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = true) { /* Required first-run setup cannot be bypassed accidentally. */ }

    val readyCount = listOf(vpnReady, notificationReady, batteryReady).count { it }
    val allReady = readyCount == 3
    val progress by animateFloatAsState(
        targetValue = readyCount / 3f,
        animationSpec = MarbleMotionSpecs.ProgressFloat,
        label = "onboarding-access-progress"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Aether.Void)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OnboardingHero(readyCount = readyCount, progress = progress)
        }
        item {
            AccessCard(
                step = "01",
                title = "VPN access",
                reason = "Android needs your approval to create MarbleNG's encrypted system tunnel. " +
                    "This permission only controls the VPN connection; it does not expose your files or messages.",
                accent = Aether.Cyan,
                ready = vpnReady,
                action = if (vpnReady) "VPN ready" else "Allow VPN access",
                onClick = {
                    val intent = VpnService.prepare(context)
                    if (intent == null) {
                        vpnReady = true
                    } else {
                        vpnPermission.launch(intent)
                    }
                }
            )
        }
        item {
            AccessCard(
                step = "02",
                title = "Notifications",
                reason = "Notifications keep the active VPN status visible and let MarbleNG report " +
                    "important connection changes while the tunnel runs in the background.",
                accent = Aether.Amethyst,
                ready = notificationReady,
                action = when {
                    notificationReady -> "Notifications ready"
                    notificationDenied -> "Open notification settings"
                    else -> "Allow notifications"
                },
                onClick = {
                    when {
                        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> notificationReady = true
                        notificationDenied -> systemSettings.launch(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                        else -> notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            )
        }
        item {
            AccessCard(
                step = "03",
                title = "Battery protection",
                reason = "Allow MarbleNG to ignore battery optimization so Android does not suspend " +
                    "the VPN when the screen is off or the app stays in the background.",
                accent = Aether.Amber,
                ready = batteryReady,
                action = if (batteryReady) "Battery protection ready" else "Open battery settings",
                onClick = {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                        batteryReady = true
                    } else {
                        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { systemSettings.launch(direct) }
                            .onFailure {
                                systemSettings.launch(
                                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                )
                            }
                    }
                }
            )
        }
        item {
            Button(
                onClick = onComplete,
                enabled = allReady,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Aether.Cyan,
                    contentColor = Color.White,
                    disabledContainerColor = Aether.GlassStrong,
                    disabledContentColor = Aether.InkFaint
                )
            ) {
                AnimatedContent(targetState = allReady, label = "onboarding-finish-label") { ready ->
                    Text(
                        if (ready) "Enter MarbleNG" else "Complete all 3 steps",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item {
            Text(
                "You stay in control. MarbleNG only asks for access required to keep the VPN " +
                    "visible, stable and connected.",
                modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                color = Aether.InkFaint,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OnboardingHero(readyCount: Int, progress: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Aether.VoidElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, Aether.GlassBorderSoft),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Aether.Cyan.copy(alpha = .11f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "M",
                        color = Aether.Cyan,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "WELCOME TO MARBLENG",
                        color = Aether.Cyan,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "$readyCount OF 3 READY",
                        color = Aether.InkFaint,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Text(
                "Set up a reliable connection.",
                color = Aether.Ink,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                "Three Android approvals help MarbleNG create the tunnel, keep you informed and " +
                    "stay connected when your phone is idle.",
                color = Aether.InkMuted,
                style = MaterialTheme.typography.bodyMedium
            )
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                color = if (readyCount == 3) Aether.Emerald else Aether.Cyan,
                trackColor = Aether.GlassStrong
            )
        }
    }
}

@Composable
private fun AccessCard(
    step: String,
    title: String,
    reason: String,
    accent: Color,
    ready: Boolean,
    action: String,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (ready) Aether.Emerald.copy(alpha = .42f) else accent.copy(alpha = .24f),
        animationSpec = MarbleMotionSpecs.Color,
        label = "onboarding-border-$step"
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Aether.VoidElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background((if (ready) Aether.Emerald else accent).copy(alpha = .11f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (ready) "✓" else step,
                        color = if (ready) Aether.Emerald else accent,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = Aether.Ink,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (ready) "READY" else "ACTION REQUIRED",
                        color = if (ready) Aether.Emerald else accent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(reason, color = Aether.InkMuted, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onClick,
                enabled = !ready,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(15.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent.copy(alpha = .12f),
                    contentColor = accent,
                    disabledContainerColor = Aether.Emerald.copy(alpha = .09f),
                    disabledContentColor = Aether.Emerald
                )
            ) {
                Text(
                    action,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
