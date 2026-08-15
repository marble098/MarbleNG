package com.marbleng.app.ui

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Marble Product UI v9 — high-legibility Nordic minimal surface. */
enum class AppTheme { DARK, LIGHT }

fun parseAppTheme(id: String): AppTheme =
    if (id.equals("light", true)) AppTheme.LIGHT else AppTheme.DARK

private data class AetherPalette(
    val void: Color,
    val voidElevated: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassBorderSoft: Color,
    val amethyst: Color,
    val amethystBright: Color,
    val cyan: Color,
    val cyanBright: Color,
    val slate: Color,
    val slateBright: Color,
    val danger: Color,
    val dangerBright: Color,
    val emerald: Color,
    val amber: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color
)

private val DarkPalette = AetherPalette(
    Color(0xFF0D1013), Color(0xFF161A1E), Color(0xFF171C20), Color(0xFF20262B),
    Color(0xFF30373D), Color(0xFF252C31), Color(0xFF526AB5), Color(0xFF7184C3),
    Color(0xFF4166D5), Color(0xFF7790DD), Color(0xFF3A4249), Color(0xFF76818A),
    Color(0xFFC66A6A), Color(0xFFD98686), Color(0xFF79A287), Color(0xFFC19E65),
    Color(0xFFF3F5F5), Color(0xFFB5BCC1), Color(0xFF818B92)
)

private val LightPalette = AetherPalette(
    Color(0xFFF5F6F3), Color(0xFFFFFFFF), Color(0xFFF8F9F6), Color(0xFFEEF1ED),
    Color(0xFFD9DED9), Color(0xFFE6E9E5), Color(0xFF4C61A7), Color(0xFF6576B2),
    Color(0xFF3157C7), Color(0xFF4A68C5), Color(0xFFD5DAD5), Color(0xFF747F78),
    Color(0xFFB85E5E), Color(0xFFC67575), Color(0xFF628B70), Color(0xFF9B7D49),
    Color(0xFF15191C), Color(0xFF535C62), Color(0xFF7B8489)
)

private val LocalAetherPalette = staticCompositionLocalOf { DarkPalette }

object Aether {
    val Void: Color @Composable get()=LocalAetherPalette.current.void
    val VoidElevated: Color @Composable get()=LocalAetherPalette.current.voidElevated
    val Glass: Color @Composable get()=LocalAetherPalette.current.glass
    val GlassStrong: Color @Composable get()=LocalAetherPalette.current.glassStrong
    val GlassBorder: Color @Composable get()=LocalAetherPalette.current.glassBorder
    val GlassBorderSoft: Color @Composable get()=LocalAetherPalette.current.glassBorderSoft
    val Amethyst: Color @Composable get()=LocalAetherPalette.current.amethyst
    val AmethystBright: Color @Composable get()=LocalAetherPalette.current.amethystBright
    val Cyan: Color @Composable get()=LocalAetherPalette.current.cyan
    val CyanBright: Color @Composable get()=LocalAetherPalette.current.cyanBright
    val Slate: Color @Composable get()=LocalAetherPalette.current.slate
    val SlateBright: Color @Composable get()=LocalAetherPalette.current.slateBright
    val Danger: Color @Composable get()=LocalAetherPalette.current.danger
    val DangerBright: Color @Composable get()=LocalAetherPalette.current.dangerBright
    val Emerald: Color @Composable get()=LocalAetherPalette.current.emerald
    val Amber: Color @Composable get()=LocalAetherPalette.current.amber
    val Ink: Color @Composable get()=LocalAetherPalette.current.ink
    val InkMuted: Color @Composable get()=LocalAetherPalette.current.inkMuted
    val InkFaint: Color @Composable get()=LocalAetherPalette.current.inkFaint
}

private val ProductSans = FontFamily.SansSerif

val AetherTypography = Typography(
    displayLarge=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.Bold,fontSize=37.sp,lineHeight=43.sp,letterSpacing=(-0.6).sp),
    headlineMedium=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.SemiBold,fontSize=24.sp,lineHeight=30.sp,letterSpacing=(-0.30).sp),
    titleMedium=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.SemiBold,fontSize=16.sp,lineHeight=22.sp,letterSpacing=(-0.05).sp),
    bodyMedium=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.Normal,fontSize=15.sp,lineHeight=21.sp),
    bodySmall=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.Normal,fontSize=13.sp,lineHeight=18.sp),
    labelLarge=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=18.sp,letterSpacing=0.sp),
    labelSmall=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.Medium,fontSize=11.sp,lineHeight=15.sp,letterSpacing=0.08.sp)
)

@Composable
fun AetherFlowTheme(
    themeId: String = "dark",
    content: @Composable () -> Unit
) {
    val p=if(parseAppTheme(themeId)==AppTheme.LIGHT) LightPalette else DarkPalette
    val light=p===LightPalette
    val scheme=if(light) lightColorScheme(
        primary=p.cyan,onPrimary=Color.White,primaryContainer=p.cyan.copy(alpha=.10f),onPrimaryContainer=p.ink,
        secondary=p.emerald,onSecondary=Color.White,secondaryContainer=p.emerald.copy(alpha=.10f),onSecondaryContainer=p.ink,
        tertiary=p.slateBright,background=p.void,onBackground=p.ink,surface=p.voidElevated,onSurface=p.ink,
        surfaceVariant=p.glassStrong,onSurfaceVariant=p.inkMuted,error=p.danger,outline=p.glassBorder
    ) else darkColorScheme(
        primary=p.cyan,onPrimary=Color.White,primaryContainer=p.cyan.copy(alpha=.14f),onPrimaryContainer=p.ink,
        secondary=p.emerald,onSecondary=Color(0xFF101411),secondaryContainer=p.emerald.copy(alpha=.12f),onSecondaryContainer=p.ink,
        tertiary=p.slateBright,background=p.void,onBackground=p.ink,surface=p.voidElevated,onSurface=p.ink,
        surfaceVariant=p.glassStrong,onSurfaceVariant=p.inkMuted,error=p.danger,outline=p.glassBorder
    )
    val view=LocalView.current
    if(!view.isInEditMode){
        val window=(view.context as Activity).window
        SideEffect{
            val c=WindowCompat.getInsetsController(window,view)
            c.isAppearanceLightStatusBars=light
            c.isAppearanceLightNavigationBars=light
            window.statusBarColor=p.void.toArgb()
            window.navigationBarColor=p.void.toArgb()
        }
    }
    CompositionLocalProvider(LocalAetherPalette provides p){
        MaterialTheme(colorScheme=scheme,typography=AetherTypography,content=content)
    }
}
