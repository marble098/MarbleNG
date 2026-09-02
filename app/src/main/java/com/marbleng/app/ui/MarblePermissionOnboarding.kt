package com.marbleng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Access is deliberately requested at the moment a user starts a connection, not at first launch.
 * Keeping the flow as a modal also makes the reason for each Android prompt visible immediately
 * before the operating system owns the next screen.
 */
enum class ConnectionPermissionStep {
    VPN,
    NOTIFICATIONS,
    BATTERY
}

private data class PermissionCopy(
    val title: String,
    val reason: String,
    val detail: String,
    val tone: Color
)

private fun permissionCopy(step: ConnectionPermissionStep): PermissionCopy = when (step) {
    ConnectionPermissionStep.VPN -> PermissionCopy(
        title = "Allow VPN access",
        reason = "MarbleNG needs Android's VPN permission to arm its protected connection service. " +
            "It only routes traffic through the node you choose; it cannot read your messages or files.",
        detail = "Required before protected connections start",
        tone = Aether.Cyan
    )
    ConnectionPermissionStep.NOTIFICATIONS -> PermissionCopy(
        title = "Allow connection notifications",
        reason = "Android requires notification access for MarbleNG to keep the active tunnel visible " +
            "and to tell you when a connection changes while the app is in the background.",
        detail = "Keeps tunnel status visible",
        tone = Aether.Amethyst
    )
    ConnectionPermissionStep.BATTERY -> PermissionCopy(
        title = "Allow background connection",
        reason = "Battery optimization can suspend a VPN when the screen turns off. MarbleNG asks to " +
            "be excluded so the connection remains stable; this does not disable battery saving for other apps.",
        detail = "Keeps the route alive when idle",
        tone = Aether.Amber
    )
}

@Composable
fun ConnectionPermissionDialog(
    step: ConnectionPermissionStep,
    error: String = "",
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    val copy = permissionCopy(step)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Aether.VoidElevated,
        tonalElevation = 0.dp,
        icon = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = copy.tone.copy(alpha = .12f),
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        when (step) {
                            ConnectionPermissionStep.VPN -> "VPN"
                            ConnectionPermissionStep.NOTIFICATIONS -> "•••"
                            ConnectionPermissionStep.BATTERY -> "↯"
                        },
                        color = copy.tone,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(copy.title, color = Aether.Ink)
                Text(
                    copy.detail,
                    color = copy.tone,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    copy.reason,
                    color = Aether.InkMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            copy.tone.copy(alpha = .07f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(
                        modifier = Modifier
                            .size(7.dp)
                            .background(copy.tone, CircleShape)
                    )
                    Text(
                        "Only requested when you tap Connect",
                        modifier = Modifier.padding(start = 9.dp),
                        color = Aether.InkMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (error.isNotBlank()) {
                    Text(
                        error,
                        color = Aether.Danger,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            PrismButton(
                label = "Continue",
                onClick = onContinue,
                tone = copy.tone,
                variant = PrismButtonVariant.Primary,
                compact = true
            )
        },
        dismissButton = {
            PrismButton(
                label = "Not now",
                onClick = onDismiss,
                tone = Aether.InkMuted,
                variant = PrismButtonVariant.Quiet,
                compact = true
            )
        }
    )
}
