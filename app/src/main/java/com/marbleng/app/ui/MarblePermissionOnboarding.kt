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
    MarbleIosTheme {
        val colors = IosTheme.colors
        val isVpn = step == ConnectionPermissionStep.VPN
        val squircleColor = if (isVpn) colors.blue else colors.orange
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
            containerColor = colors.card,
            shape = IosCardShape,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IosSquircleIcon(glyph = glyph, backgroundColor = squircleColor)
                    Text(
                        text = title,
                        color = colors.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
                        color = colors.title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = note,
                        color = colors.subtitle,
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (error.isNotBlank()) {
                        Text(
                            text = trx(error),
                            color = colors.red,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            },
            confirmButton = {
                IosButton(
                    label = continueLabel,
                    onClick = onContinue,
                    style = IosButtonStyle.PRIMARY,
                    colorOverride = squircleColor,
                    compact = true
                )
            },
            dismissButton = {
                IosButton(
                    label = notNowLabel,
                    onClick = onDismiss,
                    style = IosButtonStyle.SECONDARY,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(0.36f)
                )
            }
        )
    }
}
