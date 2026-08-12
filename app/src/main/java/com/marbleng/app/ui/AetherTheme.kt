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

object Aether {
    val Void = Color(0xFF050608)
    val VoidElevated = Color(0xFF0B0D12)
    val Glass = Color(0xFF11141B)
    val GlassStrong = Color(0xFF171B24)
    val GlassBorder = Color(0xFF292E3A)
    val GlassBorderSoft = Color(0xFF1E232D)
    val Amethyst = Color(0xFF9D7CFF)
    val AmethystBright = Color(0xFFB9A3FF)
    val Cyan = Color(0xFF31E7FF)
    val CyanBright = Color(0xFF7BF3FF)
    val Slate = Color(0xFF3B4150)
    val SlateBright = Color(0xFF667085)
    val Danger = Color(0xFFFF617D)
    val DangerBright = Color(0xFFFF8FA6)
    val Emerald = Color(0xFF4DFFB8)
    val Amber = Color(0xFFFFC857)
    val Ink = Color(0xFFF5F7FC)
    val InkMuted = Color(0xFFA7ADBC)
    val InkFaint = Color(0xFF687083)
}

private val AetherFontFamily = FontFamily.Default

val AetherTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.0).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 23.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp
    ),
    titleMedium = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.05.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.05.sp
    ),
    labelLarge = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AetherFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.45.sp
    )
)

private data class AccentPair(val primary: Color, val secondary: Color)

private fun accent(themeId: String): AccentPair = when (themeId.lowercase()) {
    "ocean" -> AccentPair(Color(0xFF5B8CFF), Color(0xFF44D7D1))
    "sunset" -> AccentPair(Color(0xFFFF7A8A), Color(0xFFFFB36B))
    "matrix" -> AccentPair(Color(0xFF58E38C), Color(0xFF66F2D5))
    "mono" -> AccentPair(Color(0xFFD5D9E2), Color(0xFF9BA8B9))
    else -> AccentPair(Aether.Amethyst, Aether.Cyan)
}

@Composable
fun AetherFlowTheme(themeId: String = "aurora", content: @Composable () -> Unit) {
    val a = accent(themeId)
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = a.primary,
            onPrimary = Color(0xFF06070A),
            primaryContainer = a.primary.copy(alpha = 0.16f),
            onPrimaryContainer = Aether.Ink,
            secondary = a.secondary,
            onSecondary = Color(0xFF06070A),
            secondaryContainer = a.secondary.copy(alpha = 0.14f),
            onSecondaryContainer = Aether.Ink,
            tertiary = Aether.AmethystBright,
            background = Aether.Void,
            onBackground = Aether.Ink,
            surface = Aether.VoidElevated,
            onSurface = Aether.Ink,
            surfaceVariant = Aether.GlassStrong,
            onSurfaceVariant = Aether.InkMuted,
            error = Aether.Danger,
            outline = Aether.GlassBorder
        ),
        typography = AetherTypography,
        content = content
    )
}
