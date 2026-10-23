package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.marbleng.app.core.ServersQuery
import com.marbleng.app.model.BenchmarkResult
import com.marbleng.app.model.DockSlotAccent
import com.marbleng.app.model.DockSlotIcon
import com.marbleng.app.model.ProbeState
import com.marbleng.app.model.ProxyProfile
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

fun cleanServerName(raw: String): String {
    if (raw.isBlank()) return ""
    var idx = 0
    while (idx < raw.length) {
        val cp = raw.codePointAt(idx)
        val count = Character.charCount(cp)
        val isFlagOrSpace = (cp in 0x1F1E6..0x1F1FF) || Character.isWhitespace(cp) || cp == 0xFE0F
        if (isFlagOrSpace) {
            idx += count
        } else {
            break
        }
    }
    val trimmed = raw.substring(idx).trim()
    return trimmed.ifBlank { raw.trim() }
}

fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    return when {
        safe < 1_024L -> "$safe B"
        safe < 1_048_576L -> String.format(Locale.US, "%.1f KB", safe / 1_024.0)
        safe < 1_073_741_824L -> String.format(Locale.US, "%.1f MB", safe / 1_048_576.0)
        else -> String.format(Locale.US, "%.2f GB", safe / 1_073_741_824.0)
    }
}

fun formatRate(bytesPerSec: Long): String = "${formatBytes(bytesPerSec)}/s"

fun formatUptimeSeconds(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0L)
    val hours = safe / 3600L
    val minutes = (safe % 3600L) / 60L
    val seconds = safe % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}

@Composable
fun rememberUptimeLabel(connectedSinceMs: Long): String {
    var nowMs by remember(connectedSinceMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(connectedSinceMs) {
        if (connectedSinceMs <= 0L) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    if (connectedSinceMs <= 0L) return "00:00:00"
    val elapsedSec = ((nowMs - connectedSinceMs) / 1_000L).coerceAtLeast(0L)
    return formatUptimeSeconds(elapsedSec)
}

fun relativeTimeLabel(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (timestampMs <= 0L) return "Never"
    val diffSec = ((nowMs - timestampMs) / 1_000L).coerceAtLeast(0L)
    return when {
        diffSec < 60L -> "Just now"
        diffSec < 3_600L -> "${diffSec / 60L}m ago"
        diffSec < 86_400L -> "${diffSec / 3_600L}h ago"
        else -> "${diffSec / 86_400L}d ago"
    }
}

fun protocolShortLabel(profile: ProxyProfile): String =
    ServersQuery.badge(profile).ifBlank { profile.scheme.uppercase(Locale.US).ifBlank { "PROXY" } }

@Composable
fun protocolSquircleColor(scheme: String): Color {
    val colors = IosTheme.colors
    return when (scheme.trim().lowercase(Locale.US)) {
        "vless" -> colors.blue
        "vmess" -> colors.indigo
        "trojan" -> colors.purple
        "ss", "shadowsocks" -> colors.teal
        "hy2", "hysteria2", "hysteria" -> colors.orange
        "wireguard", "wg" -> colors.green
        "ssh" -> colors.pink
        "socks", "socks5", "http", "https" -> colors.cyan
        else -> colors.accent
    }
}

@Composable
fun pingBadgeColor(result: BenchmarkResult?, probeState: ProbeState): Color {
    val colors = IosTheme.colors
    return when {
        probeState == ProbeState.TESTING || probeState == ProbeState.QUEUED -> colors.accent
        result == null -> colors.subtitle
        result.success <= 0 || result.latencyMs <= 0.0 -> colors.red
        result.latencyMs < 180.0 -> colors.green
        result.latencyMs < 420.0 -> colors.orange
        else -> colors.red
    }
}

@Composable
fun pingBadgeText(result: BenchmarkResult?, probeState: ProbeState): String {
    val strings = Tr.now
    return when {
        probeState == ProbeState.TESTING -> strings.pingChecking
        probeState == ProbeState.QUEUED -> "…"
        result == null -> strings.pingIdleValue
        result.success <= 0 || result.latencyMs <= 0.0 -> strings.pingFailedShort
        else -> "${result.latencyMs.roundToInt()} ms"
    }
}

internal fun dockSlotMarbleGlyph(icon: DockSlotIcon): MarbleGlyph = when (icon) {
    DockSlotIcon.PULSE -> MarbleGlyph.GAUGE
    DockSlotIcon.SPARK -> MarbleGlyph.SPARK
    DockSlotIcon.LAYERS -> MarbleGlyph.LAYERS
    DockSlotIcon.BEARING -> MarbleGlyph.GLOBE
}

@Composable
fun dockSlotIosColor(accent: DockSlotAccent): Color {
    val colors = IosTheme.colors
    return when (accent) {
        DockSlotAccent.OCEAN -> colors.blue
        DockSlotAccent.MINT -> colors.green
        DockSlotAccent.VIOLET -> colors.indigo
        DockSlotAccent.AMBER -> colors.orange
    }
}

/**
 * 12dp-squircle server avatar: renders a crisp vector country flag when known,
 * or a colored 12dp squircle with a white globe/server glyph when unknown.
 */
@Composable
fun IosServerAvatar(
    profile: ProxyProfile?,
    countryCode: String?,
    modifier: Modifier = Modifier,
    size: Dp = IosSpec.SQUIRCLE_SIZE_DP.dp
) {
    val colors = IosTheme.colors
    if (CountryFlagSupported(countryCode)) {
        Box(
            modifier = modifier
                .size(size)
                .clip(IosSquircleShape)
                .background(colors.cardSecondary)
                .border(0.75.dp, colors.divider, IosSquircleShape),
            contentAlignment = Alignment.Center
        ) {
            CountryFlagCircle(
                countryCode = countryCode,
                size = size * 0.72f,
                rimmed = false
            )
        }
    } else {
        IosSquircleIcon(
            glyph = if (profile == null) MarbleGlyph.GLOBE else MarbleGlyph.SERVER,
            backgroundColor = if (profile == null) colors.gray else protocolSquircleColor(profile.scheme),
            size = size,
            glyphSize = size * 0.55f,
            modifier = modifier
        )
    }
}
