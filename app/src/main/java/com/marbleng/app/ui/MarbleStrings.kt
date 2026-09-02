package com.marbleng.app.ui

// MARBLE_BILINGUAL_V110
// MarbleNG speaks English and Persian. The active language follows the Android device locale by
// default and can be pinned from Settings → Appearance. Every user-visible Home/connection string
// lives here so a translation is a data change, never a layout change.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.marbleng.app.model.AppLanguage
import com.marbleng.app.model.parseAppLanguage
import java.util.Locale

/** The two product languages MarbleNG renders. */
enum class MarbleLanguage { EN, FA }

/**
 * Every translatable product string.
 *
 * It is a plain data class instead of Android resources on purpose: the language can be switched
 * inside the running app without recreating the Activity, and Compose recomposes the whole tree
 * from this single value.
 */
data class MarbleStrings(
    val language: MarbleLanguage,

    // Navigation
    val tabHome: String,
    val tabLibrary: String,
    val tabSettings: String,

    // Connection state
    val connect: String,
    val disconnect: String,
    val cancel: String,
    val reset: String,
    val statusProtected: String,
    val securingRoute: String,
    val connectionStopped: String,
    val readyToConnect: String,
    val chooseRoute: String,

    // Home evidence block
    val node: String,
    val source: String,
    val ipAddress: String,
    val resolving: String,
    val unavailable: String,
    val uptime: String,
    val connectionPing: String,
    val testPing: String,
    val retestPing: String,
    val measuring: String,
    val notMeasured: String,
    val pingFailed: String,
    val copyIp: String,
    val ipCopied: String,
    val refreshIp: String,
    val ipDetails: String,
    val library: String,
    val networkSpeed: String,
    val download: String,
    val upload: String,
    val dataFlow: String,

    // Settings — appearance / style / language
    val appearance: String,
    val homeStyleTitle: String,
    val homeStyleDetail: String,
    val styleBioluminescent: String,
    val styleBioluminescentDetail: String,
    val styleCosmicOrbit: String,
    val styleCosmicOrbitDetail: String,
    val styleCosmicImmersion: String,
    val styleCosmicImmersionDetail: String,
    val styleParametric: String,
    val styleParametricDetail: String,
    val languageTitle: String,
    val languageDetail: String,
    val languageSystem: String,
    val languageSystemDetail: String,
    val languageEnglish: String,
    val languagePersian: String
)

private val EnglishStrings = MarbleStrings(
    language = MarbleLanguage.EN,
    tabHome = "Home",
    tabLibrary = "Library",
    tabSettings = "Settings",
    connect = "Connect",
    disconnect = "Disconnect",
    cancel = "Cancel",
    reset = "Reset",
    statusProtected = "Protected",
    securingRoute = "Securing route",
    connectionStopped = "Connection stopped",
    readyToConnect = "Ready to connect",
    chooseRoute = "Choose a route",
    node = "Node",
    source = "Source",
    ipAddress = "IP address",
    resolving = "resolving…",
    unavailable = "unavailable",
    uptime = "Uptime",
    connectionPing = "Ping",
    testPing = "Test ping",
    retestPing = "Test again",
    measuring = "measuring…",
    notMeasured = "not measured",
    pingFailed = "no response",
    copyIp = "Copy IP address",
    ipCopied = "IP address copied",
    refreshIp = "Refresh IP information",
    ipDetails = "Show complete IP information",
    library = "Library",
    networkSpeed = "Network speed",
    download = "Down",
    upload = "Up",
    dataFlow = "Data flow",
    appearance = "Appearance",
    homeStyleTitle = "Home style",
    homeStyleDetail = "Four presentations of the same connection surface",
    styleBioluminescent = "Bioluminescent",
    styleBioluminescentDetail = "Organic glow",
    styleCosmicOrbit = "Cosmic orbit",
    styleCosmicOrbitDetail = "Dashboard",
    styleCosmicImmersion = "Cosmic immersion",
    styleCosmicImmersionDetail = "Full screen",
    styleParametric = "Parametric",
    styleParametricDetail = "Architectural",
    languageTitle = "Language",
    languageDetail = "Follows your phone unless you choose one",
    languageSystem = "System",
    languageSystemDetail = "Device language",
    languageEnglish = "English",
    languagePersian = "فارسی"
)

private val PersianStrings = MarbleStrings(
    language = MarbleLanguage.FA,
    tabHome = "خانه",
    tabLibrary = "کتابخانه",
    tabSettings = "تنظیمات",
    connect = "اتصال",
    disconnect = "قطع اتصال",
    cancel = "لغو",
    reset = "بازنشانی",
    statusProtected = "محافظت‌شده",
    securingRoute = "در حال برقراری مسیر امن",
    connectionStopped = "اتصال متوقف شد",
    readyToConnect = "آماده اتصال",
    chooseRoute = "یک مسیر انتخاب کنید",
    node = "نود",
    source = "ساب",
    ipAddress = "آدرس آی‌پی",
    resolving = "در حال دریافت…",
    unavailable = "در دسترس نیست",
    uptime = "مدت اتصال",
    connectionPing = "پینگ",
    testPing = "تست پینگ",
    retestPing = "تست دوباره",
    measuring = "در حال اندازه‌گیری…",
    notMeasured = "اندازه‌گیری نشده",
    pingFailed = "بدون پاسخ",
    copyIp = "کپی آدرس آی‌پی",
    ipCopied = "آدرس آی‌پی کپی شد",
    refreshIp = "به‌روزرسانی اطلاعات آی‌پی",
    ipDetails = "نمایش اطلاعات کامل آی‌پی",
    library = "کتابخانه",
    networkSpeed = "سرعت شبکه",
    download = "دریافت",
    upload = "ارسال",
    dataFlow = "جریان داده",
    appearance = "ظاهر",
    homeStyleTitle = "حالت نمایش صفحه اصلی",
    homeStyleDetail = "چهار نمایش متفاوت از همان صفحه اتصال",
    styleBioluminescent = "زیست‌نور",
    styleBioluminescentDetail = "درخشش ارگانیک",
    styleCosmicOrbit = "مدار کیهانی",
    styleCosmicOrbitDetail = "داشبورد",
    styleCosmicImmersion = "غرق کیهانی",
    styleCosmicImmersionDetail = "تمام‌صفحه",
    styleParametric = "پارامتریک",
    styleParametricDetail = "معماری",
    languageTitle = "زبان",
    languageDetail = "به‌صورت پیش‌فرض از زبان گوشی پیروی می‌کند",
    languageSystem = "سیستم",
    languageSystemDetail = "زبان دستگاه",
    languageEnglish = "English",
    languagePersian = "فارسی"
)

internal val LocalMarbleStrings = staticCompositionLocalOf { EnglishStrings }

/** `Tr.now.connect` reads the active translation from anywhere in the tree. */
object Tr {
    val now: MarbleStrings
        @Composable @ReadOnlyComposable get() = LocalMarbleStrings.current
}

private fun stringsFor(language: MarbleLanguage): MarbleStrings =
    if (language == MarbleLanguage.FA) PersianStrings else EnglishStrings

/** Persian, Dari and Tajik locales all read Persian copy; everything else falls back to English. */
internal fun languageForLocale(locale: Locale): MarbleLanguage =
    if (locale.language.lowercase(Locale.ROOT) in setOf("fa", "prs", "tg")) {
        MarbleLanguage.FA
    } else {
        MarbleLanguage.EN
    }

/**
 * Resolve the effective language: an explicit user choice always wins, otherwise the device locale
 * decides. Persian also flips the whole app to a right-to-left layout so every existing Row,
 * padding and alignment mirrors without per-screen work.
 */
@Composable
internal fun ProvideMarbleLanguage(
    languageId: String,
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val deviceLocale = configuration.locales
        .takeIf { !it.isEmpty() }
        ?.get(0)
        ?: Locale.getDefault()
    val resolved = when (parseAppLanguage(languageId)) {
        AppLanguage.SYSTEM -> languageForLocale(deviceLocale)
        AppLanguage.ENGLISH -> MarbleLanguage.EN
        AppLanguage.PERSIAN -> MarbleLanguage.FA
    }
    val direction = if (resolved == MarbleLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(
        LocalMarbleStrings provides stringsFor(resolved),
        LocalLayoutDirection provides direction,
        content = content
    )
}
