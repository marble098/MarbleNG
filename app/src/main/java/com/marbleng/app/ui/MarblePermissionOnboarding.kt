package com.marbleng.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.marbleng.app.ui.theme.MarbleExpressiveTheme
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

        // MARBLE_ONE_LANGUAGE_V281 — the product's only alert dialog, asked for the two things this
        // one needs: a row icon in its title and its actions as buttons rather than text. The
        // container, the corner, the title voice and the body rhythm are the kit's now, so a
        // permission ask and a delete confirmation are the same object speaking the same way.
        MarbleAlertDialog(
            title = title,
            onDismiss = onDismiss,
            confirmLabel = continueLabel,
            onConfirm = onContinue,
            dismissLabel = notNowLabel,
            glyph = glyph,
            accent = iconTint,
            actionsAsButtons = true
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
    }
}
