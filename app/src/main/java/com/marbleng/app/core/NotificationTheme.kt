package com.marbleng.app.core

/**
 * MARBLE_NOTIFICATION_THEME_V276 — the ongoing tunnel item's own colour themes.
 *
 * Until now the shade was locked to one brand blue: the badge, the session tone and every
 * measurement chip read the same colour no matter who is holding the phone. This file is the
 * pure half of the answer — a small, fixed palette of named themes the user can choose from on
 * the Notifications settings page, and the accent each theme paints the item with.
 *
 * No Android import on purpose: the themes are data, and the one choice the settings page makes
 * is a persisted id, so nothing here needs a device.
 */
data class NotificationTheme(
    /** The persisted id — never renamed, only added to. */
    val id: String,
    /** Shown in the settings choice row, in each language the app speaks. */
    val nameEn: String,
    val nameFa: String,
    /** The brand accent the ongoing item, its actions and its measurement chips are painted in. */
    val accent: Int,
    /** Telemetry chips on a light surface. */
    val chipDay: Int,
    /** Telemetry chips on a dark surface. */
    val chipNight: Int
) {
    companion object {
        const val DEFAULT_ID = "brand"

        /** Every theme the product ships; the settings row draws them in this order. */
        val all: List<NotificationTheme> = listOf(
            NotificationTheme(
                id = "brand",
                nameEn = "Brand",
                nameFa = "برند",
                accent = 0xFF0066CC.toInt(),
                chipDay = 0xFF0B5BC4.toInt(),
                chipNight = 0xFF8FC0FF.toInt()
            ),
            NotificationTheme(
                id = "teal",
                nameEn = "Teal",
                nameFa = "فیروزی",
                accent = 0xFF007A7A.toInt(),
                chipDay = 0xFF0B8A8A.toInt(),
                chipNight = 0xFF8FD0D0.toInt()
            ),
            NotificationTheme(
                id = "amber",
                nameEn = "Amber",
                nameFa = "عنبری",
                accent = 0xFFB8860B.toInt(),
                chipDay = 0xFFC27C00.toInt(),
                chipNight = 0xFFE8A15C.toInt()
            ),
            NotificationTheme(
                id = "rose",
                nameEn = "Rose",
                nameFa = "گلابی",
                accent = 0xFFC2185B.toInt(),
                chipDay = 0xFFAD1457.toInt(),
                chipNight = 0xFFEC407A.toInt()
            ),
            NotificationTheme(
                id = "slate",
                nameEn = "Slate",
                nameFa = "سلتی",
                accent = 0xFF37474F.toInt(),
                chipDay = 0xFF455A64.toInt(),
                chipNight = 0xFF90A4AE.toInt()
            ),
            NotificationTheme(
                id = "violet",
                nameEn = "Violet",
                nameFa = "بنفش",
                accent = 0xFF6A1B9A.toInt(),
                chipDay = 0xFF8E24AA.toInt(),
                chipNight = 0xFFCE93D8.toInt()
            )
        )

        fun default(): NotificationTheme = all.first { it.id == DEFAULT_ID }

        /** Normalises a stored id: unknown values fall back to the brand theme instead of nothing. */
        fun fromId(raw: String?): NotificationTheme =
            all.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: default()
    }
}
