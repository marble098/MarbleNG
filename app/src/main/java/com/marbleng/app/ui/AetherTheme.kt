package com.marbleng.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * "Aether Flow" design tokens.
 *
 * A single source of truth for the redesigned control deck: deep OLED blacks,
 * frosted-glass surfaces, and a context-aware amethyst -> cyan accent that
 * "breathes" while connected and collapses to elegant slate when idle.
 *
 * Everything here is intentionally palette-first so any composable can reach
 * for the exact same colour without threading it through MaterialTheme.
 */
object Aether {
    // --- Canvas ---------------------------------------------------------------
    val Void = Color(0xFF050505)          // true OLED black backdrop
    val VoidElevated = Color(0xFF0B0B0F)  // faint lift for large panels

    // --- Glassmorphism --------------------------------------------------------
    /** Frosted card fill (white lifted off the void at low alpha). */
    val Glass = Color(0x14FFFFFF)
    val GlassStrong = Color(0x1FFFFFFF)
    val GlassBorder = Color(0x1FFFFFFF)
    val GlassBorderSoft = Color(0x0FFFFFFF)

    // --- Live accent (connected) ---------------------------------------------
    val Amethyst = Color(0xFF9D7CFF)
    val AmethystBright = Color(0xFFB9A3FF)
    val Cyan = Color(0xFF31E7FF)
    val CyanBright = Color(0xFF7BF3FF)

    // --- Idle accent (disconnected) ------------------------------------------
    val Slate = Color(0xFF3B4150)
    val SlateBright = Color(0xFF667085)

    // --- Semantic -------------------------------------------------------------
    val Danger = Color(0xFFFF5C7A)
    val DangerBright = Color(0xFFFF8FA6)
    val Emerald = Color(0xFF4DFFB8)

    // --- Type ink -------------------------------------------------------------
    val Ink = Color(0xFFF4F5FB)
    val InkMuted = Color(0xFF9BA1B4)
    val InkFaint = Color(0xFF5D6273)
}

/**
 * Geometric, ultra-crisp type ramp.
 *
 * To ship the exact "Aether Flow" voice, drop Inter or Manrope into
 * `res/font/` and set `private val AetherFontFamily = FontFamily(Font(...))`.
 * We default to the platform grotesque so the module compiles with zero extra
 * assets while keeping the tuned weights, sizes and tight tracking.
 */
private val AetherFontFamily = FontFamily.Default

val AetherTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.2).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
    ),
)

private val AetherColorScheme = darkColorScheme(
    primary = Aether.Amethyst,
    onPrimary = Aether.Void,
    secondary = Aether.Cyan,
    tertiary = Aether.AmethystBright,
    background = Aether.Void,
    onBackground = Aether.Ink,
    surface = Aether.VoidElevated,
    onSurface = Aether.Ink,
    surfaceVariant = Aether.Glass,
    onSurfaceVariant = Aether.InkMuted,
    error = Aether.Danger,
    outline = Aether.GlassBorder,
)

/** MD3 wrapper that projects the Aether palette + type onto the whole subtree. */
@Composable
fun AetherFlowTheme(content: @Composable () -> Unit) =
    MaterialTheme(
        colorScheme = AetherColorScheme,
        typography = AetherTypography,
        content = content,
    )
