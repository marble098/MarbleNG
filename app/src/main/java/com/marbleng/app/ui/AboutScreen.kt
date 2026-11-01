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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * MARBLE_ABOUT_REVIEW_V260 — the page was reviewed against the thing it is for, and it was doing
 * three jobs at once: an identity band, a version table and a settings page with its own buttons.
 *
 * What a reader opens About to find is *what this build is*. So the page is now three blocks and a
 * footer, in that order:
 *
 *  1. **Identity** — one band: the mark, the wordmark, the tagline, and the three facts a build has
 *     (version, build number, Xray tag) as a single monospaced line, with the palette's own spectrum
 *     as a 3dp rule at the band's foot. It used to be five objects deep (icon block, two type
 *     sizes, three fact pills, a colour swatch pill) and every one of them was tinted differently.
 *  2. **The chain** — the four cores, as four tiles rather than five. The fifth tile was the app
 *     itself, whose version was already printed twice above it; four tiles also fill two rows
 *     exactly, which is why the grid no longer ends with a hole.
 *  3. **The switches** — auto-check, check now, and the repository. Each verb appears exactly once.
 *     V258 drew all three twice: as rows inside a card *and* as two full-width buttons underneath
 *     that did the same two things.
 *  4. **The destructive reset last**, alone, where a destructive action belongs.
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
        // MARBLE_ABOUT_REVIEW_V260 — the top bar is a back button and the page's name, and nothing
        // else. V258 drew the turning orbit glyph here *and* a 52dp icon block in the hero directly
        // under it: two decorated squares, one above the other, both meaning "MarbleNG". The orbit
        // mark keeps its real job — it is the Settings hub's corner door (`MarbleAboutOrbitButton` is
        // still what opens this page) — and the page itself opens with the band it is about.
        item(key = "about-top-bar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarbleIconButton(
                    glyph = MarbleGlyph.ARROW_BACK,
                    tint = colors.onSurface,
                    contentDescription = trx(strings.backLabel),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onBack()
                    },
                    modifier = Modifier.testTag("about-back")
                )
                Text(
                    text = trx(strings.informationTitle),
                    color = colors.onSurface,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
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
            // Four, not five: the fifth tile was this app, whose version the identity band above
            // already prints twice. Four also fills the 2-column grid exactly, so the section no
            // longer ends with a hole where a tile would have been.
            MarbleSectionHeader(
                title = trx("Built on"),
                badge = faDigits("4")
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

        // The page's promise, once, as the last line of prose on it. The three palette dots that used
        // to sit above this sentence are the hero's own spectrum now — the same colours, drawn once.
        item(key = "about-signature") {
            Text(
                text = trx("Built in the open. No account, no tracking, no telemetry."),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            )
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
 * MARBLE_ABOUT_REVIEW_V260 — the identity band.
 *
 * The V258 hero was five objects deep and every one of them was tinted differently: a gradient card,
 * a solid primary icon block, three fact pills (primary, tertiary, success), a palette pill with a
 * six-stop gradient, and a 188dp watermark turning behind all of it. Nothing in it was wrong and the
 * whole was unreadable — there was no first thing to look at.
 *
 * This band has one first thing. The ground is a palette wash that stays *inside* the surface's own
 * tone, the watermark is one polygon at 14 % behind the title, and the facts are a single monospaced
 * line instead of three boxes: `v1.0.2 · build 10500 · Xray v26.9.9` is a sentence about a build, and
 * it is read the way a version string is read — left to right, once. The palette's spectrum survives
 * as the 3dp rule at the foot of the band, where it is a signature rather than a fourth pill.
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(marbleAsymmetricShape(prominent = true))
            .background(colors.surfaceContainerLow)
            .background(
                Brush.linearGradient(
                    listOf(
                        signature.first().copy(alpha = 0.18f),
                        Color.Transparent,
                        signature.getOrElse(2) { signature.first() }.copy(alpha = 0.12f)
                    )
                )
            )
            .testTag("about-hero")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            MarbleMorphingMark(
                motif = MarbleExpressiveMotif.BURST,
                targetMotif = MarbleExpressiveMotif.FLOWER,
                color = signature.first().copy(alpha = 0.14f),
                progress = { if (motion.loops) breath else 0.5f },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(150.dp)
                    .graphicsLayer {
                        translationX = 34.dp.toPx()
                        translationY = (-30).dp.toPx()
                        rotationZ = if (motion.loops) turn else 0f
                    }
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(marbleExpressiveShape(MarbleExpressiveMotif.FLOWER))
                            .background(colors.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        MarbleGlyphIcon(
                            glyph = MarbleGlyph.SHIELD,
                            color = colors.onPrimary,
                            modifier = Modifier.size(25.dp),
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
                            style = MaterialTheme.typography.headlineSmallEmphasized,
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

                // One line, one weight: the three facts a build has, in the type a version string is
                // read in. The dots are the separator, so nothing has to be a pill to be a fact.
                val persian = Tr.now.language == MarbleLanguage.FA
                Text(
                    text = buildString {
                        append("v")
                        append(versionName)
                        append("  ·  ")
                        append(trx("build"))
                        append(" ")
                        append(faDigits("$versionCode"))
                        append("  ·  ")
                        append(trx("Xray"))
                        append(" ")
                        append(xrayTag)
                    },
                    color = colors.onSurfaceVariant,
                    // Monospace is the version-string voice, and a monospace face has no Persian
                    // glyphs — so it is the Latin reading that gets the tabular face.
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = if (persian) FontFamily.Default else FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2,
                    modifier = Modifier.testTag("about-build-line")
                )
            }
        }

        // The palette's spectrum, as the band's foot: the one place four to six palette colours can
        // sit together without becoming a second set of chips.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
        ) {
            signature.forEach { tone ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .background(tone)
                )
            }
        }
    }

    // The palette's name, under the spectrum it belongs to, as quiet punctuation.
    Text(
        text = trx(palette.label),
        color = colors.onSurfaceVariant,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        modifier = Modifier.padding(start = 6.dp, top = 2.dp)
    )
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
    )
}
