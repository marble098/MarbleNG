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
    // MARBLE_CONNECT_BUTTON_V121 — the tunnel takes real time to close, so the shutdown is a
    // first-class state of the connect control instead of an instant jump back to "ready".
    val disconnecting: String,
    val closingRoute: String,
    val slideToAct: String,

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
    // MARBLE_PING_FIXED_GEOMETRY_V114 — the ping readout is a fixed-size instrument: the value
    // slot only ever holds digit-shaped glyphs, and the words live in the reserved hint slot.
    val pingMeasuringValue: String,
    val pingIdleValue: String,
    val notMeasured: String,
    val pingFailed: String,
    val copyIp: String,
    val ipCopied: String,
    val refreshIp: String,
    val ipDetails: String,
    val library: String,
    // MARBLE_SERVERS_LANGUAGE_V114 — "Library" is now "Servers" and a "node" is a "server".
    val serversTitle: String,
    val serversSubtitle: String,
    val searchServersHint: String,
    val serversUnit: String,
    val subscriptionLabel: String,
    val addedLabel: String,
    val localLabel: String,
    val expandLabel: String,
    val collapseLabel: String,
    val sortServers: String,
    val addFromClipboard: String,
    val clipboardAdded: String,
    val clipboardNothingFound: String,
    val noServersTitle: String,
    val noServersBody: String,
    // MARBLE_SETTINGS_HUB_V114 — one settings page, every title opens its own page.
    val settingsTitle: String,
    val settingsSubtitle: String,
    val categoryAppearance: String,
    val categoryConnection: String,
    val categoryNetwork: String,
    val categoryEngine: String,
    val categoryTests: String,
    val categorySystem: String,
    val categoryInformation: String,
    val informationTitle: String,
    val informationDetail: String,
    val appVersionLabel: String,
    val xrayCoreLabel: String,
    val hevCoreLabel: String,
    val sourceCodeLabel: String,
    val sourceCodeDetail: String,
    val communityLabel: String,
    val openInBrowserLabel: String,
    val quickSettingsTitle: String,
    val quickSettingsDetail: String,
    val themeTitle: String,
    val themeDetail: String,
    val themeDynamic: String,
    val themeDynamicDetail: String,
    val backLabel: String,
    val previewLabel: String,
    val networkSpeed: String,
    val download: String,
    val upload: String,
    val dataFlow: String,

    // Settings — appearance / style / language
    val appearance: String,
    val homeStyleTitle: String,
    val homeStyleDetail: String,
    val styleCosmicOrbit: String,
    val styleCosmicOrbitDetail: String,
    val styleCosmicImmersion: String,
    val styleCosmicImmersionDetail: String,
    val languageTitle: String,
    val languageDetail: String,
    val languageSystem: String,
    val languageSystemDetail: String,
    val languageEnglish: String,
    val languagePersian: String,

    // MARBLE_SIGNATURE_HOME_V112 — the Signature studio surface
    val stylePro: String,
    val styleProDetail: String,
    val proStudioTitle: String,
    val proStudioDetail: String,
    val proFloatingButton: String,
    val proFloatingButtonDetail: String,
    val proStatusBanner: String,
    val proStatusBannerDetail: String,
    val proCornerActions: String,
    val proCornerActionsDetail: String,
    val proAccentColor: String,
    val proNightOutlines: String,
    val proNightOutlinesDetail: String,
    val proServers: String,
    val proServersDetail: String,
    val proAddRoute: String,
    val proGrabPing: String,
    val proMoreActions: String,
    val proShortcut: String,
    val proShortcutLibrary: String,
    val proShortcutRank: String,
    val proShortcutPrivacy: String,
    val proShortcutRouting: String,
    val proShortcutTests: String,

    // MARBLE_HOME_REDESIGN_V132 — the selected route card, the shortcut deck and the live meter.
    val selectedRoute: String,
    val changeRoute: String,
    val livePing: String,
    val livePingWaiting: String,
    val livePingHint: String,
    val livePingHide: String,
    val livePingShow: String,
    val pasteShortcut: String,
    val qrShortcut: String,
    // MARBLE_CONNECT_BUTTON_STYLES_V132 — the two docked connection controls.
    val styleStream: String,
    val styleStreamDetail: String,
    val styleFloating: String,
    val styleFloatingDetail: String,
    // MARBLE_HOME_V137 — the rebuilt Home deck: live-ping states, the server selector and the
    // status banner. Every word here has a Persian twin below.
    val pingChecking: String,
    val pingTimeout: String,
    val pingUnreachable: String,
    val pingFailedShort: String,
    val homeCurrentGroup: String,
    val homeAllGroups: String,
    val homeServersInGroup: String,
    val homeNoServers: String,
    val homeOpenServers: String,
    val homeConnectedBadge: String,
    val homeSelectedBadge: String,
    val homeProtocol: String
)

private val EnglishStrings = MarbleStrings(
    language = MarbleLanguage.EN,
    tabHome = "Home",
    tabLibrary = "Servers",
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
    disconnecting = "Disconnecting",
    closingRoute = "Closing the route",
    slideToAct = "Slide to act",
    node = "Server",
    source = "Source",
    ipAddress = "IP address",
    resolving = "resolving…",
    unavailable = "unavailable",
    uptime = "Uptime",
    connectionPing = "Ping",
    testPing = "Test ping",
    retestPing = "Test again",
    measuring = "measuring…",
    pingMeasuringValue = "•••",
    pingIdleValue = "—",
    notMeasured = "not measured",
    pingFailed = "no response",
    copyIp = "Copy IP address",
    ipCopied = "IP address copied",
    refreshIp = "Refresh IP information",
    ipDetails = "Show complete IP information",
    library = "Servers",
    serversTitle = "Servers",
    serversSubtitle = "Subscriptions, manual servers and the local Freedom engine",
    searchServersHint = "Search servers, host or protocol",
    serversUnit = "servers",
    subscriptionLabel = "Subscription",
    addedLabel = "added",
    localLabel = "Local",
    expandLabel = "Expand",
    collapseLabel = "Collapse",
    sortServers = "Sort servers",
    addFromClipboard = "Add from clipboard",
    clipboardAdded = "Added from the clipboard",
    clipboardNothingFound = "No subscription link or server config on the clipboard",
    noServersTitle = "No servers yet",
    noServersBody = "Tap the clipboard button to import the subscription or config you copied — hold it for more options.",
    settingsTitle = "Settings",
    settingsSubtitle = "Everything in one page — tap a title to open it",
    categoryAppearance = "Appearance",
    categoryConnection = "Connection",
    categoryNetwork = "Network",
    categoryEngine = "Engine",
    categoryTests = "Tests",
    categorySystem = "System",
    categoryInformation = "Information",
    informationTitle = "About MarbleNG",
    informationDetail = "Versions, cores and the project on GitHub",
    appVersionLabel = "App version",
    xrayCoreLabel = "Xray core",
    hevCoreLabel = "HEV tunnel core",
    sourceCodeLabel = "GitHub repository",
    sourceCodeDetail = "Opens in your browser",
    communityLabel = "Telegram community",
    openInBrowserLabel = "Open in browser",
    quickSettingsTitle = "Most used",
    quickSettingsDetail = "The decisions you change most, right here",
    themeTitle = "Theme",
    themeDetail = "Light, AMOLED, system or your phone's own colors",
    themeDynamic = "Dynamic phone",
    themeDynamicDetail = "Wallpaper colors",
    backLabel = "Back",
    previewLabel = "Preview",
    networkSpeed = "Network speed",
    download = "Down",
    upload = "Up",
    dataFlow = "Data flow",
    appearance = "Appearance",
    homeStyleTitle = "Home style",
    homeStyleDetail = "Five presentations of the same connection surface",
    styleCosmicOrbit = "Cosmic orbit",
    styleCosmicOrbitDetail = "Dashboard",
    styleCosmicImmersion = "Cosmic immersion",
    styleCosmicImmersionDetail = "Full screen",
    languageTitle = "Language",
    languageDetail = "Follows your phone unless you choose one",
    languageSystem = "System",
    languageSystemDetail = "Device language",
    languageEnglish = "English",
    languagePersian = "فارسی",

    // MARBLE_SIGNATURE_HOME_V112
    stylePro = "Signature",
    styleProDetail = "Pro studio",
    proStudioTitle = "Signature studio",
    proStudioDetail = "Every layer of the professional Home, exactly the way you want it",
    proFloatingButton = "Floating connect button",
    proFloatingButtonDetail = "Drag anywhere, tap to connect",
    proStatusBanner = "Status banner",
    proStatusBannerDetail = "Connection state and selected server",
    proCornerActions = "Corner actions",
    proCornerActionsDetail = "Add, ping, shortcut and more",
    proAccentColor = "Accent color",
    proNightOutlines = "Night outlines",
    proNightOutlinesDetail = "Frame lines of the dark theme",
    proServers = "Servers",
    proServersDetail = "From your selected server source",
    proAddRoute = "Add server",
    proGrabPing = "Ping",
    proMoreActions = "More",
    proShortcut = "Shortcut",
    proShortcutLibrary = "Servers",
    proShortcutRank = "Rank",
    proShortcutPrivacy = "Privacy",
    proShortcutRouting = "Routing",
    proShortcutTests = "Tests",
    selectedRoute = "Selected server",
    changeRoute = "Change",
    livePing = "Live ping",
    livePingWaiting = "Waiting for the tunnel",
    livePingHint = "Only the server you are connected to",
    livePingHide = "Hide live ping",
    livePingShow = "Show live ping",
    pasteShortcut = "Paste",
    qrShortcut = "QR code",
    styleStream = "Stream bar",
    styleStreamDetail = "Full-width floor bar with a travelling light band",
    styleFloating = "Floating button",
    styleFloatingDetail = "Circular v2rayNG-style button pinned to the bottom corner",
    pingChecking = "Checking…",
    pingTimeout = "Timeout",
    pingUnreachable = "Unreachable",
    pingFailedShort = "Failed",
    homeCurrentGroup = "Current group",
    homeAllGroups = "All groups",
    homeServersInGroup = "Servers",
    homeNoServers = "No servers in this group",
    homeOpenServers = "Open Servers",
    homeConnectedBadge = "Connected",
    homeSelectedBadge = "Selected",
    homeProtocol = "Protocol"
)

private val PersianStrings = MarbleStrings(
    language = MarbleLanguage.FA,
    tabHome = "خانه",
    tabLibrary = "سرورها",
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
    disconnecting = "در حال قطع",
    closingRoute = "در حال بستن مسیر",
    slideToAct = "برای اجرا بکشید",
    node = "سرور",
    source = "ساب",
    ipAddress = "آدرس آی‌پی",
    resolving = "در حال دریافت…",
    unavailable = "در دسترس نیست",
    uptime = "مدت اتصال",
    connectionPing = "پینگ",
    testPing = "تست پینگ",
    retestPing = "تست دوباره",
    // Short on purpose: this string lives inside compact ping readouts where it must never
    // overflow its box (MARBLE_HOME_PING_AUTOFIT_V112).
    measuring = "اندازه‌گیری…",
    pingMeasuringValue = "•••",
    pingIdleValue = "—",
    notMeasured = "اندازه‌گیری نشده",
    pingFailed = "بدون پاسخ",
    copyIp = "کپی آدرس آی‌پی",
    ipCopied = "آدرس آی‌پی کپی شد",
    refreshIp = "به‌روزرسانی اطلاعات آی‌پی",
    ipDetails = "نمایش اطلاعات کامل آی‌پی",
    library = "سرورها",
    serversTitle = "سرورها",
    serversSubtitle = "ساب‌ها، سرورهای دستی و موتور آزادی محلی",
    searchServersHint = "جستجوی سرور، میزبان یا پروتکل",
    serversUnit = "سرور",
    subscriptionLabel = "ساب",
    addedLabel = "افزوده شده",
    localLabel = "محلی",
    expandLabel = "باز کردن",
    collapseLabel = "بستن",
    sortServers = "مرتب‌سازی سرورها",
    addFromClipboard = "افزودن از کلیپ‌بورد",
    clipboardAdded = "از کلیپ‌بورد اضافه شد",
    clipboardNothingFound = "لینک ساب یا پیکربندی سروری در کلیپ‌بورد نیست",
    noServersTitle = "هنوز سروری نیست",
    noServersBody = "دکمه کلیپ‌بورد را لمس کنید تا ساب یا پیکربندی کپی‌شده وارد شود؛ نگه‌داشتن، گزینه‌های بیشتری باز می‌کند.",
    settingsTitle = "تنظیمات",
    settingsSubtitle = "همه‌چیز در یک صفحه — روی هر عنوان بزنید تا باز شود",
    categoryAppearance = "ظاهر",
    categoryConnection = "اتصال",
    categoryNetwork = "شبکه",
    categoryEngine = "موتور",
    categoryTests = "تست‌ها",
    categorySystem = "سیستم",
    categoryInformation = "اطلاعات",
    informationTitle = "درباره MarbleNG",
    informationDetail = "نسخه‌ها، هسته‌ها و گیت‌هاب برنامه",
    appVersionLabel = "نسخه برنامه",
    xrayCoreLabel = "هسته Xray",
    hevCoreLabel = "هسته تونل HEV",
    sourceCodeLabel = "مخزن گیت‌هاب",
    sourceCodeDetail = "در مرورگر باز می‌شود",
    communityLabel = "کانال تلگرام",
    openInBrowserLabel = "بازکردن در مرورگر",
    quickSettingsTitle = "پرکاربردترین‌ها",
    quickSettingsDetail = "مهم‌ترین انتخاب‌ها، همین‌جا در دسترس",
    themeTitle = "پوسته",
    themeDetail = "روشن، آمولد، سیستم یا رنگ‌های خود گوشی",
    themeDynamic = "دینامیک گوشی",
    themeDynamicDetail = "رنگ‌های والپیپر",
    backLabel = "بازگشت",
    previewLabel = "پیش‌نمایش",
    networkSpeed = "سرعت شبکه",
    download = "دریافت",
    upload = "ارسال",
    dataFlow = "جریان داده",
    appearance = "ظاهر",
    homeStyleTitle = "حالت نمایش صفحه اصلی",
    homeStyleDetail = "پنج نمایش متفاوت از همان صفحه اتصال",
    styleCosmicOrbit = "مدار کیهانی",
    styleCosmicOrbitDetail = "داشبورد",
    styleCosmicImmersion = "غرق کیهانی",
    styleCosmicImmersionDetail = "تمام‌صفحه",
    languageTitle = "زبان",
    languageDetail = "به‌صورت پیش‌فرض از زبان گوشی پیروی می‌کند",
    languageSystem = "سیستم",
    languageSystemDetail = "زبان دستگاه",
    languageEnglish = "English",
    languagePersian = "فارسی",

    // MARBLE_SIGNATURE_HOME_V112
    stylePro = "سیگنچر",
    styleProDetail = "استودیو حرفه‌ای",
    proStudioTitle = "استودیوی سیگنچر",
    proStudioDetail = "هر لایه از صفحه حرفه‌ای، دقیقاً به سلیقه خودت",
    proFloatingButton = "دکمه اتصال شناور",
    proFloatingButtonDetail = "هرجا بکش، برای اتصال بزن",
    proStatusBanner = "بنر وضعیت",
    proStatusBannerDetail = "وضعیت اتصال و سرور انتخابی",
    proCornerActions = "دکمه‌های گوشه",
    proCornerActionsDetail = "افزودن، پینگ، میان‌بر و بیشتر",
    proAccentColor = "رنگ تاکید",
    proNightOutlines = "خطوط حالت شب",
    proNightOutlinesDetail = "خطوط و کادرهای تم تاریک",
    proServers = "سرورها",
    proServersDetail = "از ساب انتخابی سرورها",
    proAddRoute = "افزودن سرور",
    proGrabPing = "پینگ",
    proMoreActions = "بیشتر",
    proShortcut = "میان‌بر",
    proShortcutLibrary = "سرورها",
    proShortcutRank = "رتبه‌بندی",
    proShortcutPrivacy = "حریم خصوصی",
    proShortcutRouting = "مسیریابی",
    proShortcutTests = "تست‌ها",
    selectedRoute = "سرور انتخاب‌شده",
    changeRoute = "تغییر",
    livePing = "پینگ لحظه‌ای",
    livePingWaiting = "در انتظار تونل",
    livePingHint = "فقط سروری که به آن متصل هستید",
    livePingHide = "پنهان کردن پینگ لحظه‌ای",
    livePingShow = "نمایش پینگ لحظه‌ای",
    pasteShortcut = "چسباندن",
    qrShortcut = "کیوآر کد",
    styleStream = "نوار جریان",
    styleStreamDetail = "نوار تمام‌عرض پایین صفحه با نوری که از راست به چپ می‌رود",
    styleFloating = "دکمه شناور",
    styleFloatingDetail = "دکمه دایره‌ای به‌سبک v2rayNG در گوشه پایین صفحه",
    pingChecking = "در حال بررسی…",
    pingTimeout = "تایم‌اوت",
    pingUnreachable = "غیرقابل دسترس",
    pingFailedShort = "ناموفق",
    homeCurrentGroup = "گروه فعلی",
    homeAllGroups = "همه گروه‌ها",
    homeServersInGroup = "سرورها",
    homeNoServers = "سروری در این گروه نیست",
    homeOpenServers = "باز کردن سرورها",
    homeConnectedBadge = "متصل",
    homeSelectedBadge = "انتخاب‌شده",
    homeProtocol = "پروتکل"
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
