package com.marbleng.app.core

/**
 * MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — the Quick Settings tile is part of the product too.
 *
 * It lives outside Compose, so `trx()` never sees it. Before this file the tile printed
 * "MarbleNG • Connecting", "Connect once in app" and "Disconnecting…" on a phone whose whole app
 * was Persian. This is the same copy engine idea as [NotificationCopy]: pure Kotlin, no Context,
 * one place for both languages, and a JVM test that can prove it.
 *
 * Server names are isolated with FSI/PDI so an RTL tile label cannot reorder a Latin hostname.
 */
object QuickTileCopy {
    const val MARKER: String = "MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233"

    /** The tile uses exactly the language rule the Compose UI and the shade use. */
    fun languageFor(appLanguageId: String?, deviceLanguage: String?): NotificationLanguage =
        NotificationCopy.languageFor(appLanguageId, deviceLanguage)

    private fun t(language: NotificationLanguage, english: String, persian: String): String =
        NotificationCopy.t(language, english, persian)

    private fun name(value: String, language: NotificationLanguage): String =
        if (language == NotificationLanguage.FA) NotificationCopy.isolate(value) else value

    fun label(state: String, language: NotificationLanguage): String = when (state) {
        "CONNECTED" -> t(language, "MarbleNG • On", "ماربل • روشنه")
        "CONNECTING" -> t(language, "MarbleNG • Connecting", "ماربل • داره وصل میشه")
        "DISCONNECTING" -> t(language, "MarbleNG • Disconnecting", "ماربل • داره قطع میشه")
        "BLOCKED" -> t(language, "MarbleNG • Reset", "ماربل • یه ریست بزن")
        else -> t(language, "MarbleNG", "ماربل")
    }

    fun transientLabel(transient: QuickTileTransient?, language: NotificationLanguage): String =
        when (transient) {
            QuickTileTransient.CONNECTING ->
                t(language, "Connecting…", "دارم وصلت میکنم…")
            QuickTileTransient.DISCONNECTING ->
                t(language, "Disconnecting…", "دارم قطعت میکنم…")
            null -> ""
        }

    /** Android 10+ subtitle: the live route, the remembered route, or a friendly nudge. */
    fun subtitle(
        state: String,
        stateDetail: String,
        lastName: String,
        language: NotificationLanguage
    ): String {
        val value = when {
            state == "CONNECTED" && stateDetail.isNotBlank() -> name(stateDetail, language)
            lastName.isNotBlank() -> name(lastName, language)
            else -> t(language, "Connect once in app", "یه بار از تو برنامه وصل شو")
        }
        return value.take(80)
    }

    /** The spoken description of the tile action. */
    fun contentDescription(
        state: String,
        stateDetail: String,
        lastName: String,
        language: NotificationLanguage
    ): String = when {
        state == "CONNECTED" -> t(
            language,
            "Disconnect MarbleNG from $stateDetail",
            "ماربل رو از ${name(stateDetail, language)} جدا کن"
        )
        lastName.isNotBlank() -> t(
            language,
            "Connect MarbleNG to $lastName",
            "ماربل رو به ${name(lastName, language)} وصل کن"
        )
        else -> t(
            language,
            "Open MarbleNG and choose a server",
            "ماربل رو باز کن و یه سرور انتخاب کن"
        )
    }
}

enum class QuickTileTransient {
    CONNECTING,
    DISCONNECTING
}
