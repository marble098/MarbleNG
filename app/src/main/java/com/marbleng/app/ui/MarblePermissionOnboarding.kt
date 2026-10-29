package com.marbleng.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbleng.app.ui.theme.MarbleCardShape
import com.marbleng.app.ui.theme.warning

enum class ConnectionPermissionStep {
    VPN,
    NOTIFICATIONS
}

@Composable
fun ConnectionPermissionDialog(
    step: ConnectionPermissionStep,
    error: String = "",
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    MarbleExpressiveTheme {
        val colors = MaterialTheme.colorScheme
        val isVpn = step == ConnectionPermissionStep.VPN
        val iconTint = if (isVpn) colors.primary else colors.warning
        val glyph = if (isVpn) MarbleGlyph.SHIELD else MarbleGlyph.BELL
        val title = trx(if (isVpn) "Allow VPN connection" else "Allow notifications")
        val subtitle = trx(
            if (isVpn) {
                "MarbleNG needs Android's VPN permission to establish a protected tunnel on this device."
            } else {
                "Allow status notifications so Android keeps your active VPN session visible and reliable."
            }
        )
        val note = trx(
            if (isVpn) {
                "Your traffic stays private on-device and only leaves through the server you choose."
            } else {
                "You can customize or mute optional alerts anytime in Settings."
            }
        )
        val continueLabel = trx("Continue")
        val notNowLabel = trx("Not now")

        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = colors.surfaceContainerLow,
            shape = MarbleCardShape,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MarbleRowIcon(glyph = glyph, tint = iconTint)
                    Text(
                        text = title,
                        color = colors.onSurface,
                        // MARBLE_WEIGHT_LADDER_V235 — titleLarge carries HEADLINE (700) itself.
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = subtitle,
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = note,
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (error.isNotBlank()) {
                        Text(
                            text = trx(error),
                            color = colors.error,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            },
            confirmButton = {
                MarbleButton(
                    label = continueLabel,
                    onClick = onContinue,
                    style = MarbleButtonStyle.PRIMARY,
                    colorOverride = iconTint,
                    compact = true
                )
            },
            dismissButton = {
                MarbleButton(
                    label = notNowLabel,
                    onClick = onDismiss,
                    style = MarbleButtonStyle.SECONDARY,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(0.36f)
                )
            }
        )
    }
}
