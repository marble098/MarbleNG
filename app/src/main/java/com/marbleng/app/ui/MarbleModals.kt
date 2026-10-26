package com.marbleng.app.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.AppUpdateInfo

@Composable
fun ExitIpDetailsSheet(
    repo: AppRepository,
    onDismiss: () -> Unit
) {
    val colors = IosTheme.colors
    val strings = Tr.now
    val clipboard = LocalClipboardManager.current
    val intel = repo.serverIntel

    IosBottomSheet(
        title = strings.ipAddress,
        subtitle = trx("Public exit identity & ASN intelligence"),
        onDismiss = onDismiss
    ) {
        if (intel == null) {
            IosCard {
                IosListItem(
                    title = if (repo.serverIntelLoading) strings.resolving else strings.unavailable,
                    subtitle = repo.serverIntelError.ifBlank {
                        trx("Connect to a server or tap Refresh below to resolve exit IP details")
                    },
                    glyph = MarbleGlyph.GLOBE,
                    squircleColor = colors.teal,
                    showChevron = false
                )
            }
        } else {
            IosCard {
                IosListItem(
                    title = intel.ip,
                    subtitle = intel.ipType.ifBlank { trx("Public Exit IP") },
                    glyph = MarbleGlyph.GLOBE,
                    squircleColor = colors.blue,
                    trailing = {
                        Text(
                            text = strings.copyAction,
                            color = colors.accent,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    showChevron = false,
                    onClick = {
                        clipboard.setText(AnnotatedString(intel.ip))
                        repo.setRuntimeMessage(strings.ipCopied)
                    }
                )
                IosDivider()
                IosListItem(
                    title = intel.locationLabel.ifBlank { strings.unavailable },
                    subtitle = trx("Location"),
                    leading = {
                        IosServerAvatar(profile = null, countryCode = intel.countryCode)
                    },
                    showChevron = false
                )
                IosDivider()
                IosListItem(
                    title = intel.datacenterLabel,
                    subtitle = listOf(intel.asn, intel.domain).filter { it.isNotBlank() }.joinToString(" · "),
                    glyph = MarbleGlyph.SERVER,
                    squircleColor = colors.indigo,
                    showChevron = false
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            IosButton(
                label = strings.refreshIp,
                glyph = MarbleGlyph.REFRESH,
                enabled = !repo.serverIntelLoading,
                onClick = { repo.refreshServerIntel(force = true) },
                modifier = Modifier.weight(1f)
            )
            if (intel != null && intel.ip.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                IosButton(
                    label = strings.copyIp,
                    glyph = MarbleGlyph.COPY,
                    style = IosButtonStyle.TINTED,
                    onClick = {
                        clipboard.setText(AnnotatedString(intel.ip))
                        repo.setRuntimeMessage(strings.ipCopied)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun QrImportSourceDialogController(
    open: Boolean,
    repo: AppRepository,
    onDismiss: () -> Unit
) {
    val targetId = repo.intakeTargetOrManual(repo.librarySourceFilter)

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            repo.importQrBitmap(bitmap, targetId)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            repo.importQrImage(uri, targetId)
        }
    }

    if (!open) return

    val colors = IosTheme.colors
    IosBottomSheet(
        title = trx("Import from QR Code"),
        subtitle = trx("Scan with your camera or choose a QR image from your library"),
        onDismiss = onDismiss
    ) {
        IosCard {
            IosListItem(
                title = trx("Scan with Camera"),
                subtitle = trx("Capture a QR code directly"),
                glyph = MarbleGlyph.QR,
                squircleColor = colors.green,
                onClick = {
                    onDismiss()
                    cameraLauncher.launch(null)
                }
            )
            IosDivider()
            IosListItem(
                title = trx("Choose from Photo Library"),
                subtitle = trx("Pick a screenshot or saved QR image"),
                glyph = MarbleGlyph.FOLDER,
                squircleColor = colors.indigo,
                onClick = {
                    onDismiss()
                    galleryLauncher.launch("image/*")
                }
            )
        }
    }
}

@Composable
fun AppUpdateAvailableDialog(
    update: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val colors = IosTheme.colors
    val context = LocalContext.current

    IosAlertDialog(
        title = update.title.ifBlank { "${trx("Update Available")}: ${update.version}" },
        onDismiss = onDismiss,
        confirmLabel = trx("Download"),
        onConfirm = {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.url)))
            }
            onDismiss()
        }
    ) {
        // MARBLE_LATEST_VERSION_RULE_V235 — the one rule offers the newest published
        // build whatever its label, so the dialog names the label: a pre-release is
        // offered openly as one, never dressed as a stable.
        if (update.prerelease) {
            Row {
                IosBadge(text = trx("Pre-release"), color = colors.orange)
            }
        }
        Text(
            text = update.notes.take(420).ifBlank { trx("A newer release of MarbleNG is available on GitHub.") },
            color = colors.subtitle,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
