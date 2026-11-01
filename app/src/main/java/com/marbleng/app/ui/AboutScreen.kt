@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.marbleng.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.BuildConfig
import com.marbleng.app.model.MarblePalette
import com.marbleng.app.model.parseMarblePalette
import com.marbleng.app.ui.theme.MarbleSpec
import com.marbleng.app.ui.theme.MarbleTheme
import com.marbleng.app.ui.theme.marbleMotion
import com.marbleng.app.ui.theme.marblePaletteSignature
import com.marbleng.app.ui.theme.resolveMarbleDarkMode
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

/**
 * MARBLE_ABOUT_PAGE_V258 — About, as a page of its own.
 *
 * It used to be the last tile of the Settings bento, which meant the one screen that answers "what
 * is this build, and what is inside it" was also a row in a settings list, drawn inside a settings
 * scaffold with a settings back arrow. It is now a route: the Settings hub's top-right corner opens
 * it full-screen, it owns its own history entry, and it draws like a product page rather than a
 * preference.
 *
 * The composition is Material 3 Expressive from top to bottom:
 *
 *  * a hero card whose ground is a two-stop palette gradient with a slowly turning, morphing
 *    `MaterialShapes` watermark behind the wordmark;
 *  * the version facts as one pill row rather than four label/value lines;
 *  * the four cores as expressive bento tiles, each with its own polygon and its own tag;
 *  * the update and source actions as tonal rows;
 *  * the destructive reset last, alone, where a destructive action belongs.
 */
@Composable
fun AboutScreen(
    repo: AppRepository,
    onBack: () -> Unit,
    applyStatusBarInset: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val s = repo.settings
    val palette = parseMarblePalette(s.themePalette)
    val dark = resolveMarbleDarkMode(s.theme, isSystemInDarkTheme())
    var confirmReset by remember { mutableStateOf(false) }

    val sourceLabel = trx("Open the GitHub repository")
    val checkLabel = trx("Check for updates")

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(if (applyStatusBarInset) Modifier.statusBarsPadding() else Modifier)
            .testTag("about-page"),
        contentPadding = PaddingValues(
            start = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            end = MarbleSpec.SCREEN_HORIZONTAL_PADDING_DP.dp,
            top = 8.dp,
            bottom = MarbleTheme.bottomClearance
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "about-top-bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MarbleAboutOrbitButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onBack()
                    },
                    glyph = MarbleGlyph.ARROW_BACK,
                    contentDescription = trx(strings.backLabel),
                    tag = "about-back"
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = trx(strings.informationTitle),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = trx("The whole build, in one place"),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        item(key = "about-hero") {
            AboutHero(
                palette = palette,
                dark = dark,
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE,
                xrayTag = BuildConfig.XRAY_CORE_TAG
            )
        }

        item(key = "about-cores-header") {
            MarbleSectionHeader(
                title = trx("Versions & Cores"),
                badge = faDigits("5")
            )
        }

        item(key = "about-cores") {
            val cores = aboutCores()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                cores.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { core ->
                            AboutCoreTile(
                                core = core,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        item(key = "about-updates") {
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
                            haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SOURCE_URL))
                                )
                            }
                        }
                    )
                }
                Spacer(Modifier.height(2.dp))
                MarbleButton(
                    label = checkLabel,
                    glyph = MarbleGlyph.REFRESH,
                    style = MarbleButtonStyle.TINTED,
                    onClick = { repo.checkForAppUpdate(force = true) },
                    modifier = Modifier.fillMaxWidth()
                )
                MarbleButton(
                    label = sourceLabel,
                    glyph = MarbleGlyph.LINK,
                    style = MarbleButtonStyle.SECONDARY,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.SOURCE_URL))
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
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

        item(key = "about-signature") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    marblePaletteSignature(palette, dark).take(3).forEach { tone ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tone)
                        )
                    }
                }
                Text(
                    text = trx("Built in the open. No account, no tracking, no telemetry."),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (confirmReset) {
        MarbleAlertDialog(
            title = trx("Reset Settings?"),
            onDismiss = { confirmReset = false },
            confirmLabel = strings.reset,
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

/**
 * MARBLE_ABOUT_PAGE_V258 — the rotating Material 3 Expressive mark, *merged into* the button.
 *
 * The hub used to put a plain INFO glyph in its top-right corner and the app drew a decorative
 * turning polygon elsewhere. Two objects answering two halves of one question became one: the
 * button's own ground is the morphing `MaterialShapes` polygon, turning on a long linear orbit and
 * blooming between two silhouettes, with the glyph sitting on top of it. Pressing it opens About —
 * so the decoration *is* the door.
 *
 * Motion obeys [marbleMotion]: a calm or system-reduced-motion reader gets the resting polygon at
 * its most legible state instead of a spinner.
 */
@Composable
internal fun MarbleAboutOrbitButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: MarbleGlyph = MarbleGlyph.INFO,
    contentDescription: String = "",
    tag: String = "settings-about-button"
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val motion = marbleMotion()
    val turning = motion.loops
    val transition = rememberInfiniteTransition(label = "aboutOrbit")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing)
        ),
        label = "aboutOrbitTurn"
    )
    val bloom by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7_000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aboutOrbitBloom"
    )
    val described = if (contentDescription.isNotBlank()) {
        modifier.semantics { this.contentDescription = contentDescription }
    } else {
        modifier
    }

    Box(
        modifier = described
            .size(52.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(colors.primary, colors.tertiary)
                )
            )
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            }
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        MarbleMorphingMark(
            motif = MarbleExpressiveMotif.FLOWER,
            targetMotif = MarbleExpressiveMotif.GEM,
            color = Color.White.copy(alpha = 0.42f),
            progress = { if (turning) bloom else 0.45f },
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
                .graphicsLayer { rotationZ = if (turning) turn else 0f }
        )
        MarbleGlyphIcon(
            glyph = glyph,
            color = Color.White,
            modifier = Modifier.size(21.dp),
            weight = 1.2f
        )
    }
}

/**
 * The hero: palette-washed ground, a turning morphing watermark, the wordmark, and the three facts
 * a build has — version name, version code, core tag — as pills instead of a label/value table.
 */
@Composable
private fun AboutHero(
    palette: MarblePalette,
    dark: Boolean,
    versionName: String,
    versionCode: Int,
    xrayTag: String
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val signature = marblePaletteSignature(palette, dark)
    val transition = rememberInfiniteTransition(label = "aboutHero")
    val turn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 34_000, easing = LinearEasing)
        ),
        label = "aboutHeroTurn"
    )
    val breath by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aboutHeroBreath"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(colors.surfaceContainerLow)
            .background(
                Brush.linearGradient(
                    listOf(
                        signature.first().copy(alpha = 0.26f),
                        colors.surfaceContainerLow.copy(alpha = 0.05f),
                        signature.getOrElse(2) { signature.first() }.copy(alpha = 0.20f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = colors.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(32.dp)
            )
            .testTag("about-hero")
    ) {
        MarbleMorphingMark(
            motif = MarbleExpressiveMotif.BURST,
            targetMotif = MarbleExpressiveMotif.FLOWER,
            color = signature.first().copy(alpha = 0.16f),
            progress = { if (motion.loops) breath else 0.5f },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(188.dp)
                .graphicsLayer {
                    translationX = 44.dp.toPx()
                    translationY = 52.dp.toPx()
                    rotationZ = if (motion.loops) turn else 0f
                }
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(marbleExpressiveShape(MarbleExpressiveMotif.FLOWER))
                        .background(colors.primary),
                    contentAlignment = Alignment.Center
                ) {
                    MarbleGlyphIcon(
                        glyph = MarbleGlyph.SHIELD,
                        color = colors.onPrimary,
                        modifier = Modifier.size(26.dp),
                        weight = 1.2f
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "MarbleNG",
                        color = colors.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1
                    )
                    Text(
                        text = trx("A tunnel you can read."),
                        color = colors.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AboutFactPill(label = trx("Version"), value = "v$versionName", tint = colors.primary)
                AboutFactPill(
                    label = trx("Build"),
                    value = faDigits("$versionCode"),
                    tint = colors.tertiary
                )
                AboutFactPill(label = trx("Xray"), value = xrayTag, tint = colors.success)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 26.dp, height = 8.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Brush.linearGradient(signature))
                )
                Text(
                    text = trx(palette.label),
                    color = colors.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun AboutFactPill(
    label: String,
    value: String,
    tint: Color
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainerHighest.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
        Text(
            text = value,
            color = tint,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * One core as an expressive tile — a polygon badge, the name, and the pinned tag in monospace.
 *
 * It is deliberately *not* a button: these are facts about the binary, and a tile that accepts a
 * tap and does nothing teaches the reader that taps on this page are decoration.
 */
@Composable
private fun AboutCoreTile(
    core: AboutCore,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val motion = marbleMotion()
    val tint = core.tint()
    Box(
        modifier = modifier
            .clip(marbleExpressiveShape(core.motif))
            .background(colors.surfaceContainerLow)
            .background(tint.copy(alpha = 0.08f))
            .testTag("about-core-${core.id}")
            .padding(14.dp)
    ) {
        MarbleMorphingMark(
            motif = core.motif,
            targetMotif = MarbleExpressiveMotif.CIRCLE,
            color = tint.copy(alpha = 0.10f),
            progress = { if (motion.loops) 0.35f else 0f },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(84.dp)
                .graphicsLayer {
                    translationX = 18.dp.toPx()
                    translationY = 18.dp.toPx()
                }
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(marbleExpressiveShape(core.motif))
                    .background(tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                MarbleGlyphIcon(
                    glyph = core.glyph,
                    color = tint,
                    modifier = Modifier.size(19.dp),
                    weight = 1.1f
                )
            }
            Text(
                text = trx(core.title),
                color = colors.onSurface,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = core.tag,
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The five pieces this APK is actually made of. One tile each, with its own polygon and its tag
 * printed in the body — the same tags `build.gradle.kts` reads out of `core-lock.json`, so About can
 * never claim a core the binary does not carry.
 */
private data class AboutCore(
    val id: String,
    val title: String,
    val tag: String,
    val glyph: MarbleGlyph,
    val motif: MarbleExpressiveMotif,
    val tint: @Composable () -> Color
)

@Composable
private fun aboutCores(): List<AboutCore> {
    val colors = MaterialTheme.colorScheme
    return listOf(
        AboutCore(
            id = "xray",
            title = "Xray core",
            tag = BuildConfig.XRAY_CORE_TAG,
            glyph = MarbleGlyph.SERVER,
            motif = MarbleExpressiveMotif.FAN,
            tint = { colors.primary }
        ),
        AboutCore(
            id = "singbox",
            title = "sing-box extended",
            tag = BuildConfig.SINGBOX_CORE_TAG,
            glyph = MarbleGlyph.LAYERS,
            motif = MarbleExpressiveMotif.DIAMOND,
            tint = { colors.secondary }
        ),
        AboutCore(
            id = "hev",
            title = "HEV tunnel core",
            tag = BuildConfig.HEV_CORE_TAG,
            glyph = MarbleGlyph.TUNNEL,
            motif = MarbleExpressiveMotif.ARCH,
            tint = { colors.tertiary }
        ),
        AboutCore(
            id = "tun2socks",
            title = "tun2socks",
            tag = BuildConfig.TUN2SOCKS_CORE_TAG,
            glyph = MarbleGlyph.ROUTE,
            motif = MarbleExpressiveMotif.CLOVER,
            tint = { colors.warning }
        ),
        AboutCore(
            id = "app",
            title = "MarbleNG app",
            tag = "v" + BuildConfig.VERSION_NAME,
            glyph = MarbleGlyph.INFO,
            motif = MarbleExpressiveMotif.BURST,
            tint = { colors.success }
        )
    )
}
