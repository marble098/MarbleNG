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

/** Marble Product UI v8 — Nordic Noir / Soft Minimalist. */
enum class AppTheme { DARK, LIGHT }
fun parseAppTheme(id: String): AppTheme = if (id.equals("light", true)) AppTheme.LIGHT else AppTheme.DARK

private data class AetherPalette(
    val void: Color, val voidElevated: Color, val glass: Color, val glassStrong: Color,
    val glassBorder: Color, val glassBorderSoft: Color, val amethyst: Color,
    val amethystBright: Color, val cyan: Color, val cyanBright: Color, val slate: Color,
    val slateBright: Color, val danger: Color, val dangerBright: Color, val emerald: Color,
    val amber: Color, val ink: Color, val inkMuted: Color, val inkFaint: Color
)
private val DarkPalette = AetherPalette(
    Color(0xFF0E1114), Color(0xFF15191D), Color(0xFF181D22), Color(0xFF1D2329),
    Color(0xFF2A3036), Color(0xFF22282E), Color(0xFF365FD9), Color(0xFF6682D8),
    Color(0xFF365FD9), Color(0xFF7890D8), Color(0xFF3B434B), Color(0xFF69747E),
    Color(0xFFC87171), Color(0xFFD98A8A), Color(0xFF7FA68B), Color(0xFFC3A36B),
    Color(0xFFF1F3F4), Color(0xFFB3BAC0), Color(0xFF7B858D)
)
private val LightPalette = AetherPalette(
    Color(0xFFF4F5F2), Color(0xFFFDFDFB), Color(0xFFF8F9F6), Color(0xFFF0F2EE),
    Color(0xFFD9DDD8), Color(0xFFE5E8E3), Color(0xFF244AC7), Color(0xFF4D67BD),
    Color(0xFF244AC7), Color(0xFF3F5DC4), Color(0xFFD3D8D3), Color(0xFF7E8881),
    Color(0xFFB65E5E), Color(0xFFC87575), Color(0xFF62876D), Color(0xFFA18452),
    Color(0xFF171A1D), Color(0xFF50585E), Color(0xFF7B8388)
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
    displayLarge=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.Bold,fontSize=38.sp,lineHeight=44.sp,letterSpacing=(-0.7).sp),
    headlineMedium=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.SemiBold,fontSize=25.sp,lineHeight=31.sp,letterSpacing=(-0.35).sp),
    titleMedium=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=23.sp,letterSpacing=(-0.08).sp),
    bodyMedium=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.Normal,fontSize=15.sp,lineHeight=22.sp),
    bodySmall=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.Normal,fontSize=13.sp,lineHeight=19.sp),
    labelLarge=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=19.sp,letterSpacing=0.05.sp),
    labelSmall=TextStyle(fontFamily=ProductSans,fontWeight=FontWeight.Medium,fontSize=12.sp,lineHeight=16.sp,letterSpacing=0.16.sp)
)
@Composable fun AetherFlowTheme(themeId:String="dark",content:@Composable()->Unit){
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
    if(!view.isInEditMode){ val window=(view.context as Activity).window; SideEffect{
        val c=WindowCompat.getInsetsController(window,view); c.isAppearanceLightStatusBars=light; c.isAppearanceLightNavigationBars=light
        window.statusBarColor=p.void.toArgb(); window.navigationBarColor=p.void.toArgb()
    }}
    CompositionLocalProvider(LocalAetherPalette provides p){ MaterialTheme(colorScheme=scheme,typography=AetherTypography,content=content) }
}
