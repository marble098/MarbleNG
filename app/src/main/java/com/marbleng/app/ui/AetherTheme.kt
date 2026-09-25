package com.marbleng.app.ui

// MARBLE_KINETIC_GLASS_THEME_V34
// MARBLE_SOLID_WHITE_THEME_V35
// MARBLE_REFINED_PRODUCT_UI_V52
// MARBLE_M3_EXPRESSIVE_THEME_V53
// MARBLE_PRISM_THEME_V54
// MARBLE_NAVY_BRAND_THEME_V77
// MARBLE_MATERIAL_YOU_REFRESH_V185
// MARBLE_EXPRESSIVE_MOTION_V186
// The whole identity is re-anchored on the Marble navy/ice/electric blue ramp:
//   #000033 deep navy  •  #001144 dark navy  •  #0066CC electric  •  #3399FF bright
//   #ADD8E6 ice        •  #E0FFFF ice white   •  #F0F8FF alice     •  #FFFFFF white
// Light and Dark are the same formal color system; only the surface/ink roles swap.
//
// MARBLE_MATERIAL_YOU_REFRESH_V185 — the Material You / Android 16-17 visual refresh. The ramp
// above stays the brand; what changed is the *finish* of every role that holds content:
//   • cards and containers move from tinted glass steps to calm, cool, opaque
//     "surface container" tones (light) and navy-lifted steps over AMOLED black (dark);
//   • hairlines drop to quieter, cooler neutral strokes;
//   • the type ramp follows the Material 3 scale with clearer steps between display,
//     headline, title and body roles, so hierarchy reads at a glance;
//   • shapes round one step up everywhere, matching the newest Material corner language.
// minSdk 26 is untouched: every role still resolves through the same AetherPalette, and the
// Material You dynamic branch keeps its API 31 guard.

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import com.marbleng.app.R
import com.marbleng.app.model.AppFont
import com.marbleng.app.model.DarkOutlineStyle
import com.marbleng.app.model.parseAppFont
import com.marbleng.app.model.parseDarkOutlineStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Marble White — calm solid surfaces with vivid, state-driven accents.
 *
 * MARBLE_PHONE_DYNAMIC_THEME_V113 — besides following the system, MarbleNG can now borrow the
 * phone's own Material You palette (wallpaper colors) through [AppTheme.PHONE_DYNAMIC]. When the
 * device generates dynamic colors the whole Aether token set — surfaces, hairlines, the brand
 * accent ramp, ink — is rebuilt from the phone palette so every colored element of the product
 * moves with the wallpaper instead of a fixed brand hue.
 */
enum class AppTheme { SYSTEM, DARK, LIGHT, PHONE_DYNAMIC }

fun parseAppTheme(id: String): AppTheme = when {
    id.equals("dark", true) -> AppTheme.DARK
    id.equals("system", true) -> AppTheme.SYSTEM
    id.equals("phone", true) || id.equals("dynamic", true) -> AppTheme.PHONE_DYNAMIC
    else -> AppTheme.LIGHT
}

private data class AetherPalette(
    val void: Color,
    val voidElevated: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassBorderSoft: Color,
    // MARBLE_PRODUCT_FLOATING_GLASS_V117 — tokens for the truly floating bars (bottom dock,
    // Library source strip, Settings tabs). They are translucent so whatever scrolls
    // beneath them shows through, and both themes keep their own readable ink pair.
    val barGlass: Color,
    val barGlassBorder: Color,
    val barGlassHighlight: Color,
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
    val inkFaint: Color,
    // MARBLE_THEME_COHERENCE_V190 — true when the set was rebuilt from the phone's wallpaper
    // palette. Surfaces that used to hard-code the brand ice/sky values (the Home cloud cards,
    // the page gradient, the selection accent) read this and follow the palette instead, so a
    // dynamic theme can no longer paint a lavender dock over an ice-blue page with sky-blue
    // selections beside a violet connect button.
    val dynamic: Boolean = false
)

/**
 * The eight Marble brand colors. Every tint below is one of these exact hues composited with
 * alpha over a surface — never a foreign hue — so Light and Dark both stay inside the same
 * identity even where a role needs a softer or deeper step.
 */
private object Brand {
    val NavyDeep = Color(0xFF000033)   // deep navy
    val NavyDark = Color(0xFF001144)   // dark navy
    val Electric = Color(0xFF0066CC)   // electric blue
    val Bright = Color(0xFF3399FF)     // bright blue
    val Ice = Color(0xFFADD8E6)        // ice blue
    val IceWhite = Color(0xFFE0FFFF)   // ice white
    val Alice = Color(0xFFF0F8FF)      // alice blue
    val White = Color(0xFFFFFFFF)      // white
}

/*
 * Light surfaces are deliberately opaque. The previous translucent stack produced visible
 * rectangular compositing bands on several Android GPUs and weakened the information hierarchy.
 * Depth now comes from spacing, a single soft outline and state colour—not nested glass layers.
 *
 * MARBLE_NAVY_BRAND_THEME_V77
 * Background, surfaces, borders and ink are the Marble navy/ice ramp. The two accents are the
 * electric blue (primary) and deep navy (secondary), so the product reads as one continuous
 * blue system in both themes. Emerald/Amber/Danger stay as *functional* state colours only:
 * a VPN must never dress "blocked" or "connected" in the brand hue.
 */
// MARBLE_MATERIAL_YOU_REFRESH_V185 — light surfaces adopt Material You's calm container language:
// a cool near-white page, opaque white cards, soft blue-gray container steps and quiet neutral
// hairlines. Tint intensity now lives in the accents only, so content owns the contrast.
private val LightPalette = AetherPalette(
    void = Color(0xFFF4F8FD),
    voidElevated = Brand.White,
    glass = Color(0xFFEDF3FA),
    glassStrong = Color(0xFFE2ECF6),
    glassBorder = Brand.Electric.copy(alpha = .22f).compositeOver(Brand.White),
    glassBorderSoft = Brand.NavyDeep.copy(alpha = .10f).compositeOver(Brand.White),
    // MARBLE_DOCK_GLASS_VISIBILITY_V191 — the light glass values were tuned for the old flat
    // white page, where a 61% white bar still had its border to carry it. On the aurora backdrop
    // the bar reads as a hole while scrolling. The glass keeps more of its body (.94) and its
    // hairline doubles (.16), so the page shows *through* the bar instead of the bar vanishing
    // *into* the page.
    barGlass = Brand.White.copy(alpha = .94f),
    barGlassBorder = Brand.NavyDeep.copy(alpha = .16f).compositeOver(Brand.White),
    barGlassHighlight = Color.White.copy(alpha = .60f),
    amethyst = Brand.NavyDark,
    amethystBright = Brand.Electric,
    cyan = Brand.Electric,
    cyanBright = Brand.Bright,
    slate = Color(0xFFE8EFF7),
    slateBright = Brand.NavyDark.copy(alpha = .55f).compositeOver(Brand.White),
    danger = Color(0xFFE23D5B),
    dangerBright = Color(0xFFF26079),
    emerald = Color(0xFF009A74),
    amber = Color(0xFFD98200),
    ink = Brand.NavyDeep,
    inkMuted = Brand.NavyDeep.copy(alpha = .66f).compositeOver(Brand.White),
    inkFaint = Brand.NavyDeep.copy(alpha = .40f).compositeOver(Brand.White)
)

/* Dark remains an explicit accessibility/user choice with a true AMOLED black foundation.
 * MARBLE_MATERIAL_YOU_REFRESH_V185 — the elevated steps above the black floor gain a navy cast
 * (#0B111C / #0E141F / #121A28) so cards, sheets and wells separate from the void the way the
 * newest Material dark themes do, and hairlines cool down to slate-blue strokes. */
private val DarkPalette = AetherPalette(
    void = Color(0xFF000000),          // pure AMOLED black
    voidElevated = Color(0xFF0B111C),   // navy-lifted card step
    glass = Color(0xFF0E141F),          // container step
    glassStrong = Color(0xFF121A28),
    glassBorder = Brand.Electric.copy(alpha = .30f).compositeOver(Color(0xFF0B111C)),
    glassBorderSoft = Color(0xFF1E2836),
    // The dock is opaque while idle. Its translucent value is used only while content is moving.
    barGlass = Color(0xD9000000),
    barGlassBorder = Brand.Ice.copy(alpha = .14f),
    barGlassHighlight = Brand.Ice.copy(alpha = .08f),
    amethyst = Brand.Electric,
    amethystBright = Brand.Bright,
    cyan = Brand.Bright,
    cyanBright = Brand.Ice,
    slate = Color(0xFF101724),
    slateBright = Brand.Ice,
    danger = Color(0xFFFF718B),
    dangerBright = Color(0xFFFF99AA),
    emerald = Color(0xFF55D7B4),
    amber = Color(0xFFF2B45F),
    ink = Color(0xFFF0F8FF),           // bright ice white for max AMOLED contrast
    inkMuted = Brand.Ice.copy(alpha = .78f).compositeOver(Color(0xFF000000)),
    inkFaint = Brand.Ice.copy(alpha = .42f).compositeOver(Color(0xFF000000))
)

/**
 * MARBLE_PHONE_DYNAMIC_THEME_V113 — rebuild the whole Aether token set from the phone's Material
 * You palette. Every token keeps its semantic job: surfaces/ink/hairlines come from the dynamic
 * scheme's tonal roles and the two accent ramps follow the wallpaper's primary/secondary/tertiary
 * hues, while functional state colors (emerald/amber/danger) stay semantic so "connected" and
 * "blocked" are never confused with the brand accent.
 */
private fun dynamicPhonePalette(scheme: androidx.compose.material3.ColorScheme, dark: Boolean): AetherPalette {
    // MARBLE_THEME_COHERENCE_V190 — the surface ladder was flattened: `background` and `surface`
    // are the same tone in Material You, so cards sat invisibly on the page and every edge had
    // to be carried by a hairline. The page now takes a container step and the cards the
    // brightest (light) / a lifted (dark) step, exactly as the brand palette separates them.
    // Hairlines were also far too heavy: the dock and the page controls were framed with the
    // scheme's full `outline` (a mid grey), which read as a thick grey border on every control.
    // They now take the quiet `outlineVariant` family, like the brand palette's soft strokes.
    val card = if (dark) scheme.surfaceContainer else scheme.surfaceContainerLowest
    val surface = card
    fun over(fg: Color, alpha: Float): Color = fg.copy(alpha = alpha).compositeOver(surface)
    return AetherPalette(
        void = if (dark) scheme.background else scheme.surfaceContainerLow,
        voidElevated = card,
        glass = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainer,
        glassStrong = if (dark) scheme.surfaceContainerHighest else scheme.surfaceContainerHigh,
        glassBorder = over(scheme.primary, if (dark) .30f else .22f),
        glassBorderSoft = over(scheme.outlineVariant, if (dark) .80f else .70f),
        barGlass = card.copy(alpha = if (dark) .88f else .78f),
        barGlassBorder = over(scheme.outlineVariant, if (dark) .55f else .60f),
        barGlassHighlight = if (dark) {
            Color.White.copy(alpha = .06f).compositeOver(surface)
        } else {
            Color.White.copy(alpha = .55f).compositeOver(surface)
        },
        amethyst = scheme.secondary,
        amethystBright = scheme.tertiary,
        cyan = scheme.primary,
        cyanBright = scheme.tertiary,
        slate = scheme.surfaceVariant,
        slateBright = scheme.onSurfaceVariant,
        danger = Color(0xFFE23D5B),
        dangerBright = Color(0xFFF26079),
        emerald = Color(0xFF009A74),
        amber = Color(0xFFD98200),
        ink = scheme.onSurface,
        inkMuted = scheme.onSurfaceVariant,
        inkFaint = scheme.onSurfaceVariant.copy(alpha = .72f).compositeOver(surface),
        dynamic = true
    )
}

private val LocalAetherPalette = staticCompositionLocalOf { LightPalette }

object Aether {
    val Void: Color @Composable get() = LocalAetherPalette.current.void
    val VoidElevated: Color @Composable get() = LocalAetherPalette.current.voidElevated
    val Glass: Color @Composable get() = LocalAetherPalette.current.glass
    val GlassStrong: Color @Composable get() = LocalAetherPalette.current.glassStrong
    val GlassBorder: Color @Composable get() = LocalAetherPalette.current.glassBorder
    val GlassBorderSoft: Color @Composable get() = LocalAetherPalette.current.glassBorderSoft
    val BarGlass: Color @Composable get() = LocalAetherPalette.current.barGlass
    val BarGlassBorder: Color @Composable get() = LocalAetherPalette.current.barGlassBorder
    val BarGlassHighlight: Color @Composable get() = LocalAetherPalette.current.barGlassHighlight
    val Amethyst: Color @Composable get() = LocalAetherPalette.current.amethyst
    val AmethystBright: Color @Composable get() = LocalAetherPalette.current.amethystBright
    val Cyan: Color @Composable get() = LocalAetherPalette.current.cyan
    val CyanBright: Color @Composable get() = LocalAetherPalette.current.cyanBright
    val Slate: Color @Composable get() = LocalAetherPalette.current.slate
    val SlateBright: Color @Composable get() = LocalAetherPalette.current.slateBright
    val Danger: Color @Composable get() = LocalAetherPalette.current.danger
    val DangerBright: Color @Composable get() = LocalAetherPalette.current.dangerBright
    val Emerald: Color @Composable get() = LocalAetherPalette.current.emerald
    val Amber: Color @Composable get() = LocalAetherPalette.current.amber
    val Ink: Color @Composable get() = LocalAetherPalette.current.ink
    val InkMuted: Color @Composable get() = LocalAetherPalette.current.inkMuted
    val InkFaint: Color @Composable get() = LocalAetherPalette.current.inkFaint
    /** True while the palette follows the phone's wallpaper instead of the Marble brand ramp. */
    val IsDynamic: Boolean @Composable get() = LocalAetherPalette.current.dynamic
}

// MARBLE_VAZIR_REAL_FONT_V111
// Vazirmatn is bundled as real TTF resources (OFL-licensed, res/font/vazirmatn_*.ttf) so
// Persian text genuinely shapes with Vazir instead of silently falling back to the platform
// sans face. When Persian is the active product language the whole typography ramp switches
// to Vazirmatn regardless of the selected Latin font, so no Persian glyph ever renders in a
// mismatched face.
// MARBLE_VAZIR_LANGUAGE_KEY_V114 — internal, not private: the Persian language choice in Settings
// must render in Vazir in *every* state (idle, selected, pressed, English UI, Persian UI), and that
// button lives in another file. Nothing else may reach for the face directly; the typography ramp
// below stays the only place a whole screen switches fonts.
internal val VazirFamily = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold),
    Font(R.font.vazirmatn_bold, FontWeight.Bold)
)

// MARBLE_SETTINGS_HUB_V114 — the Typeface page shows each candidate in its own face, which needs
// the resolver outside this file. It returns a family only; nothing here changes the active theme.
internal fun previewFontFamily(id: String): FontFamily = selectedFontFamily(id)

private fun selectedFontFamily(id: String): FontFamily = when (parseAppFont(id)) {
    // The real bundled Vazirmatn face — Persian and Latin both ship inside the TTF.
    AppFont.VAZIR -> VazirFamily
    // MARBLE_SYSTEM_FONT_V112 — the device's own default typeface, exactly as the OS renders it.
    AppFont.SYSTEM -> FontFamily.Default
    // Google Sans is the platform product sans fallback on Android; keeping it platform-backed
    // avoids a network font download during a VPN connection or first launch.
    AppFont.GOOGLE_SANS -> FontFamily.Default
    // Android's serif face is the closest bundled, offline-compatible Times New Roman treatment.
    AppFont.TIMES_NEW_ROMAN -> FontFamily.Serif
}

private fun aetherTypography(fontId: String, persian: Boolean): Typography {
    // Persian always shapes with the bundled Vazirmatn: that is the whole point of shipping it.
    val family = if (persian) VazirFamily else selectedFontFamily(fontId)
    // The tight negative tracking tuned for Latin ruins Perso-Arabic joining; Persian runs at
    // neutral tracking so Vazir's own metrics decide the rhythm.
    fun track(latin: Double): TextUnit = if (persian) 0.sp else latin.sp
    // MARBLE_PERSIAN_TYPE_FLOOR_V184 — the smallest label size must stay legible for Vazirmatn's
    // hairline joins on mid-range panels. MARBLE_MATERIAL_YOU_REFRESH_V185 lifts the whole label
    // floor half a step with the rest of the Material 3 ramp: Latin labels render at 11.5 sp and
    // Persian labels one half-step above, at 12 sp, keeping the same +0.5 sp guard.
    fun labelSmallSize(): TextUnit = if (persian) 12.sp else 11.5.sp
    fun labelSmallLine(): TextUnit = if (persian) 17.sp else 16.sp
    // Vazirmatn Regular is a display cut; at body sizes its strokes read faint on AMOLED
    // and on washed-out light surfaces. UI body copy takes the Medium weight in Persian only —
    // the Latin faces keep the spec weight.
    val bodyWeight = if (persian) FontWeight.Medium else FontWeight.Normal
    // MARBLE_MATERIAL_YOU_REFRESH_V185 — the ramp follows the Material 3 scale with the compact
    // product calibration MarbleNG ships: display grows to 40 sp, headlines step 28/24/20 sp,
    // titles step 18/16/14 sp, bodyLarge joins the spec at 16/24, and the label ramp gains the
    // 14 sp labelLarge. Every step between roles is now at least 1.5 sp, so hierarchy reads at a
    // glance, and line heights gain ~1 dp of air for long-setting copy.
    return Typography(
    displayLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = track(-.50)
    ),
    displayMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = track(-.40)
    ),
    displaySmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = track(-.30)
    ),
    headlineLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = track(-.30)
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = track(-.20)
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = track(-.10)
    ),
    titleLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = track(-.10)
    ),
    titleMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    titleSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = track(.05)
    ),
    bodyLarge = TextStyle(
        fontFamily = family,
        fontWeight = bodyWeight,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = track(.15)
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontWeight = bodyWeight,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = track(.10)
    ),
    bodySmall = TextStyle(
        fontFamily = family,
        fontWeight = bodyWeight,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = track(.20)
    ),
    labelLarge = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = track(.10)
    ),
    labelMedium = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = track(.30)
    ),
    labelSmall = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = labelSmallSize(),
        lineHeight = labelSmallLine(),
        letterSpacing = track(.40)
    )
)
}

// MARBLE_MATERIAL_YOU_REFRESH_V185 — the shape ramp rounds one step up to match the newest
// Material corner language: 12 / 16 / 20 / 28 / 32 dp. Material components (sheets, menus,
// chips, text fields) pick these up through MaterialTheme.shapes across the whole app.
val AetherShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

/**
 * MARBLE_EXPRESSIVE_MOTION_V186 — the shape-morph endpoints of the expressive control language.
 *
 * Material 3 Expressive controls do not hold one shape: a button softens toward a pill under the
 * finger and springs back on release (see [rememberExpressiveMorphShape]). These tokens are the
 * two ends of that morph for each control family, so every pressable surface in the product
 * bends by the same amount instead of each call site inventing its own pressed radius:
 *
 *  • [ButtonPressRadius] — the pressed end of every standard control: a full pill. Compose
 *    clamps corner radii at half the box when drawing, so one large value reads as "fully round"
 *    on a 46 dp button and on a 54 dp detail button alike.
 *  • [CompactButtonPressRadius] — the pressed end of compact controls, one step short of the
 *    pill so dense rows morph without their buttons turning into circles.
 *  • [TilePressRadius] — selection tiles soften by two steps, never all the way: a tile is a
 *    surface you choose, not an action you fire, and the smaller bend keeps the two grammar.
 *
 * Resting radii stay wherever V185 put them ([PrismSurface], [AetherShapes], HomeCloud); this
 * object only names where a press takes them.
 */
internal object MarbleExpressiveShapes {
    val ButtonPressRadius = 999.dp
    val CompactButtonPressRadius = 24.dp
    val TilePressRadius = 22.dp
}

/**
 * MARBLE_NIGHT_OUTLINES_V112 — the user's dark-theme hairline personality.
 *
 * Every frame/card rim in the product flows through the palette border tokens below, so applying
 * the choice here restyles the whole app without touching a single call site. Light themes keep
 * their designed hairlines: this is explicitly the night-mode control the users asked for.
 */
@Composable
private fun applyNightOutline(
    palette: AetherPalette,
    styleId: String
): AetherPalette = when (parseDarkOutlineStyle(styleId)) {
    // The designed AMOLED rim: one quiet hairline that separates surfaces without lines.
    DarkOutlineStyle.SUBTLE -> palette
    // Doubled presence: the same hue, clearly visible, for users who want framed cards.
    // MARBLE_MATERIAL_YOU_REFRESH_V185 — the bold frame cools to the new slate hairline family.
    DarkOutlineStyle.BOLD -> palette.copy(
        glassBorder = palette.glassBorder.copy(alpha = (palette.glassBorder.alpha * 2.1f).coerceAtMost(.85f)),
        glassBorderSoft = Color(0xFF31405A),
        barGlassBorder = palette.barGlassBorder.copy(alpha = (palette.barGlassBorder.alpha * 2.4f).coerceAtMost(.75f))
    )
    // Brand-tinted frames: electric-blue rims that glow against the AMOLED black.
    DarkOutlineStyle.COLORED -> palette.copy(
        glassBorder = Brand.Electric.copy(alpha = .52f),
        glassBorderSoft = Brand.Electric.copy(alpha = .30f),
        barGlassBorder = Brand.Bright.copy(alpha = .40f)
    )
    // Dissolved: no frame lines anywhere in the dark theme — depth comes from surfaces only.
    DarkOutlineStyle.HIDDEN -> palette.copy(
        glassBorder = Color.Transparent,
        glassBorderSoft = Color.Transparent,
        barGlassBorder = Color.Transparent
    )
}

@Composable
fun AetherFlowTheme(
    themeId: String = "light",
    // MARBLE_GOOGLE_SANS_DEFAULT_V160 — previews and any caller that does not name a face render
    // with the typeface a fresh install opens with, so a preview never lies about the default.
    fontId: String = AppFont.DEFAULT.id,
    outlineStyleId: String = DarkOutlineStyle.SUBTLE.id,
    content: @Composable () -> Unit
) {
    val requested=parseAppTheme(themeId)
    val light=when(requested) {
        AppTheme.LIGHT -> true
        AppTheme.DARK -> false
        AppTheme.SYSTEM -> !isSystemInDarkTheme()
        AppTheme.PHONE_DYNAMIC -> !isSystemInDarkTheme()
    }

    val context=LocalContext.current
    // MARBLE_PHONE_DYNAMIC_THEME_V113 — an explicit "dynamic phone" theme (Settings → Theme)
    // rebuilds Aether tokens from the phone's Material You palette on Android 12+ in both light
    // and dark mode. Below Android 12 the phone cannot generate a palette, so it gracefully
    // falls back to the standard Light/Dark identity of the same brightness.
    val phoneDynamic =
        requested == AppTheme.PHONE_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicPalette: AetherPalette?
    val dynamicScheme: androidx.compose.material3.ColorScheme?
    if (phoneDynamic) {
        val generated =
            if (light) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
        val rebuilt = dynamicPhonePalette(generated, !light)
        dynamicPalette = rebuilt
        // Stock Material surfaces (menus, sheets, text fields) sit on the same page/card steps
        // as the rebuilt Aether palette instead of the scheme's flat background.
        dynamicScheme = generated.copy(
            background = rebuilt.void,
            surface = rebuilt.voidElevated,
            surfaceTint = Color.Transparent
        )
    } else {
        dynamicPalette = null
        dynamicScheme = null
    }

    // MARBLE_THEME_COHERENCE_V190 — "System" means *follow the system's light/dark switch* with
    // the Marble identity. It used to hand the stock Material components (search field, menus,
    // dialogs, sheets, the status-bar colour) the wallpaper scheme while every custom surface
    // kept the brand palette, so one screen mixed lavender, pink and electric blue. Wallpaper
    // colours now belong to the explicit "Phone colours" theme only, where the palette AND the
    // Material scheme are rebuilt from the same source.
    val systemDynamicColor = false

    val palette=dynamicPalette ?: if(light) LightPalette else applyNightOutline(DarkPalette, outlineStyleId)

    val fallback=if(light) {
        lightColorScheme(
            primary=palette.cyan,
            onPrimary=Color.White,
            primaryContainer=palette.cyan.copy(alpha=.10f),
            onPrimaryContainer=palette.ink,
            secondary=palette.emerald,
            onSecondary=Color.White,
            secondaryContainer=palette.emerald.copy(alpha=.10f),
            onSecondaryContainer=palette.ink,
            tertiary=palette.amethyst,
            background=palette.void,
            onBackground=palette.ink,
            surface=palette.voidElevated,
            onSurface=palette.ink,
            surfaceVariant=palette.glassStrong,
            onSurfaceVariant=palette.inkMuted,
            surfaceTint=Color.Transparent,
            error=palette.danger,
            outline=palette.glassBorderSoft,
            outlineVariant=palette.glassBorderSoft.copy(alpha=.55f),
            // MARBLE_MATERIAL_YOU_REFRESH_V185 — the tonal container ladder Material components
            // actually read (bottom sheets, menus, chips, filled cards) now resolves to the cool
            // Marble surface ramp instead of the stock gray baseline, so every stock M3 surface
            // sits inside the same identity as the custom Prism surfaces.
            surfaceContainerLowest=Brand.White,
            surfaceContainerLow=Color(0xFFF6F9FD),
            surfaceContainer=Color(0xFFF0F5FB),
            surfaceContainerHigh=Color(0xFFEAF1F8),
            surfaceContainerHighest=Color(0xFFE3ECF5),
            surfaceDim=Color(0xFFD9E3EF),
            surfaceBright=Color(0xFFF8FBFE),
            inverseSurface=Color(0xFF0D1420),
            inverseOnSurface=Color(0xFFEEF3FA),
            inversePrimary=Color(0xFFA9CBFF)
        )
    } else {
        darkColorScheme(
            primary=palette.cyan,
            onPrimary=Brand.NavyDeep,
            primaryContainer=palette.cyan.copy(alpha=.14f),
            onPrimaryContainer=palette.ink,
            secondary=palette.emerald,
            onSecondary=Color(0xFF061711),
            secondaryContainer=palette.emerald.copy(alpha=.12f),
            onSecondaryContainer=palette.ink,
            tertiary=palette.amethyst,
            background=palette.void,
            onBackground=palette.ink,
            surface=palette.voidElevated,
            onSurface=palette.ink,
            surfaceVariant=palette.glassStrong,
            onSurfaceVariant=palette.inkMuted,
            surfaceTint=Color.Transparent,
            error=palette.danger,
            outline=palette.glassBorder,
            outlineVariant=palette.glassBorderSoft,
            // MARBLE_MATERIAL_YOU_REFRESH_V185 — the dark container ladder lifts from AMOLED black
            // through navy-tinted steps, matching the refreshed Aether dark surfaces.
            surfaceContainerLowest=Color.Black,
            surfaceContainerLow=Color(0xFF070C13),
            surfaceContainer=Color(0xFF0F1622),
            surfaceContainerHigh=Color(0xFF18202D),
            surfaceContainerHighest=Color(0xFF1D2734),
            surfaceDim=Color.Black,
            surfaceBright=Color(0xFF141B27),
            inverseSurface=Color(0xFFEDF2FA),
            inverseOnSurface=Color(0xFF0D1420),
            inversePrimary=Color(0xFF9CC6FF)
        )
    }

    val scheme=dynamicScheme ?: when {
        systemDynamicColor && light -> dynamicLightColorScheme(context)
        systemDynamicColor && !light -> dynamicDarkColorScheme(context)
        else -> fallback
    }

    val view=LocalView.current
    if(!view.isInEditMode) {
        val window=(view.context as Activity).window
        SideEffect {
            val controller=WindowCompat.getInsetsController(window,view)
            controller.isAppearanceLightStatusBars=light
            controller.isAppearanceLightNavigationBars=light
            // The page under the status bar is the Aether page tone, not the stock scheme's.
            window.statusBarColor=palette.void.toArgb()
            // The gesture/navigation surface must not paint a second horizontal strip below the app.
            // Android still owns the gesture handle itself, but the app-controlled bar and divider
            // are fully transparent on every navigation mode.
            window.navigationBarColor=Color.Transparent.toArgb()
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.setNavigationBarDividerColor(Color.Transparent.toArgb())
            }
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced=false
                window.isNavigationBarContrastEnforced=false
            }
        }
    }

    CompositionLocalProvider(LocalAetherPalette provides palette) {
        // MARBLE_VAZIR_REAL_FONT_V111 — the resolved product language decides whether the
        // Vazirmatn ramp is forced; MarbleApp installs the language provider above the theme.
        val persianActive = LocalMarbleStrings.current.language == MarbleLanguage.FA
        MaterialTheme(
            colorScheme=scheme,
            typography=aetherTypography(fontId, persianActive),
            shapes=AetherShapes
        ) {
            ProvideMarbleMotion(content)
        }
    }
}
