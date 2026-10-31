package com.marbleng.app.ui

import androidx.compose.ui.graphics.Color

/**
 * MARBLE_EXPRESSIVE_STUDIO_V252 — consolidated Settings hub layout and umbrella taxonomy.
 *
 * Inspired by Lumen Android 1.5.0's clean Settings hub, the main Settings page replaces twenty-three
 * scattered micro-cards and repeated section headings with seven cohesive broader titles
 * ([SettingsUmbrellaGroup]). Every existing [SettingsDestination] belongs to exactly one broader
 * group, remains directly reachable via search and deep links, and switches inline through the
 * group's Material 3 Expressive topic bar.
 */
internal data class SettingsRowSpec(
    val destination: SettingsDestination,
    val title: String,
    val subtitle: String,
    val glyph: MarbleGlyph,
    val iconTint: Color,
    val motif: MarbleExpressiveMotif = MarbleExpressiveMotif.forGlyph(glyph),
    val indexBadge: String = "",
    val topicPills: List<String> = emptyList(),
    val containerOverride: Color? = null,
    val contentOverride: Color? = null
)

internal enum class SettingsHubCategory(val id: String, val titleKey: String) {
    ALL("all", "All"),
    CONNECTION("connection", "Connection & routing"),
    NETWORK("network", "Network & testing"),
    TESTS("tests", "Tests"),
    APPEARANCE("appearance", "Appearance"),
    SYSTEM("system", "App & system");

    companion object {
        val visible = listOf(ALL, CONNECTION, NETWORK, SYSTEM)

        fun fromId(raw: String?): SettingsHubCategory = when (raw) {
            TESTS.id -> NETWORK
            APPEARANCE.id -> SYSTEM
            else -> visible.firstOrNull { it.id == raw } ?: ALL
        }
    }
}

internal enum class SettingsUmbrellaGroup(
    val id: String,
    val titleKey: String,
    val summaryKey: String,
    val glyph: MarbleGlyph,
    val motif: MarbleExpressiveMotif,
    val category: SettingsHubCategory,
    val destinations: List<SettingsDestination>
) {
    // MARBLE_SETTINGS_POSITION_V253 — the seven hubs read in the order a person sets a VPN up:
    // how it connects, what it skips, how it is measured, where its DNS goes, how it looks, where
    // its data lives, and what maintains it. The first hub owns the featured tile.
    CONNECTION_CORE(
        id = "connection_core",
        titleKey = "Connection & core",
        summaryKey = "VPN mode, Xray / sing-box engine, tunnel & power",
        glyph = MarbleGlyph.TUNNEL,
        motif = MarbleExpressiveMotif.FAN,
        category = SettingsHubCategory.CONNECTION,
        destinations = listOf(
            SettingsDestination.CONNECTION_MODE,
            SettingsDestination.CORE_ENGINE,
            SettingsDestination.TUNNEL_DATAPATH,
            SettingsDestination.POWER_PROFILE
        )
    ),
    ROUTING_BYPASS(
        id = "routing_bypass",
        titleKey = "Routing & bypass",
        summaryKey = "Direct rules, per-app split tunnel & Iran mode",
        glyph = MarbleGlyph.ROUTE,
        motif = MarbleExpressiveMotif.ARCH,
        category = SettingsHubCategory.CONNECTION,
        destinations = listOf(
            SettingsDestination.ROUTING,
            SettingsDestination.SPLIT_TUNNEL,
            SettingsDestination.CENSORSHIP_LAB
        )
    ),
    PING_SMART(
        id = "ping_smart",
        titleKey = "Ping & smart auto",
        summaryKey = "Latency probes, one-tap modes, auto-pick & jitter",
        glyph = MarbleGlyph.GAUGE,
        motif = MarbleExpressiveMotif.SUNNY,
        category = SettingsHubCategory.NETWORK,
        destinations = listOf(
            SettingsDestination.PING_TESTING,
            SettingsDestination.SMART_PRESETS,
            SettingsDestination.AUTO_SELECTOR,
            SettingsDestination.SMART_JITTER
        )
    ),
    DNS_PRIVACY(
        id = "dns_privacy",
        titleKey = "DNS & privacy",
        summaryKey = "Encrypted resolvers, IPv4 / IPv6 & IP leak shield",
        glyph = MarbleGlyph.GLOBE,
        motif = MarbleExpressiveMotif.CLOVER,
        category = SettingsHubCategory.NETWORK,
        destinations = listOf(
            SettingsDestination.DNS_FAMILY,
            SettingsDestination.IDENTITY_LOCATION
        )
    ),
    APPEARANCE_UI(
        id = "appearance_ui",
        titleKey = "Appearance & interface",
        summaryKey = "Themes, accents, Persian typography, Home & dock",
        glyph = MarbleGlyph.PALETTE,
        motif = MarbleExpressiveMotif.FLOWER,
        category = SettingsHubCategory.SYSTEM,
        destinations = listOf(
            SettingsDestination.APPEARANCE,
            SettingsDestination.LANGUAGE_FONT,
            SettingsDestination.HOME_LAYOUT,
            SettingsDestination.TAB_BAR
        )
    ),
    SUBSCRIPTIONS_BACKUP(
        id = "subscriptions_backup",
        titleKey = "Subscriptions & backup",
        summaryKey = "Scheduled refresh, user-agent & full file backup",
        glyph = MarbleGlyph.REFRESH,
        motif = MarbleExpressiveMotif.DIAMOND,
        category = SettingsHubCategory.SYSTEM,
        destinations = listOf(
            SettingsDestination.SUBSCRIPTIONS_DATA,
            SettingsDestination.BACKUP_RESTORE
        )
    ),
    SYSTEM_DIAGNOSTICS(
        id = "system_diagnostics",
        titleKey = "System & diagnostics",
        summaryKey = "Network check, shade controls, battery, doctor & versions",
        glyph = MarbleGlyph.TUNERS,
        motif = MarbleExpressiveMotif.BURST,
        category = SettingsHubCategory.SYSTEM,
        destinations = listOf(
            SettingsDestination.NETWORK_COUNCIL,
            SettingsDestination.NOTIFICATIONS_BG,
            SettingsDestination.DIAGNOSTICS,
            SettingsDestination.ABOUT
        )
    );

    val primaryDestination: SettingsDestination
        get() = destinations.first()
}

internal object SettingsHubLayout {
    const val WIDE_MIN_WIDTH_DP: Int = 600

    fun columns(widthDp: Int): Int = if (widthDp >= WIDE_MIN_WIDTH_DP) 3 else 2

    fun umbrellaFor(destination: SettingsDestination): SettingsUmbrellaGroup =
        SettingsUmbrellaGroup.entries.first { destination in it.destinations }

    fun umbrellasForCategory(category: SettingsHubCategory): List<SettingsUmbrellaGroup> =
        when (category) {
            SettingsHubCategory.ALL -> SettingsUmbrellaGroup.entries
            else -> SettingsUmbrellaGroup.entries.filter { it.category == category }
        }

    fun shortTopicTitleKey(destination: SettingsDestination): String = when (destination) {
        SettingsDestination.CONNECTION_MODE -> "Mode & ports"
        SettingsDestination.CORE_ENGINE -> "Proxy engine"
        SettingsDestination.TUNNEL_DATAPATH -> "Tunnel path"
        SettingsDestination.POWER_PROFILE -> "Power"
        SettingsDestination.ROUTING -> "Traffic rules"
        SettingsDestination.DNS_FAMILY -> "DNS & IP"
        SettingsDestination.SPLIT_TUNNEL -> "Per-app VPN"
        SettingsDestination.CENSORSHIP_LAB -> "Iran mode"
        SettingsDestination.PING_TESTING -> "Ping method"
        SettingsDestination.NETWORK_COUNCIL -> "Network check"
        SettingsDestination.AUTO_SELECTOR -> "Auto server"
        SettingsDestination.SMART_PRESETS -> "One-tap modes"
        SettingsDestination.SMART_JITTER -> "Jitter shield"
        SettingsDestination.IDENTITY_LOCATION -> "IP privacy"
        SettingsDestination.APPEARANCE -> "Theme & colour"
        SettingsDestination.LANGUAGE_FONT -> "Language & font"
        SettingsDestination.TAB_BAR -> "Bottom bar"
        SettingsDestination.HOME_LAYOUT -> "Home layout"
        SettingsDestination.SUBSCRIPTIONS_DATA -> "Subscriptions"
        SettingsDestination.BACKUP_RESTORE -> "Backup"
        SettingsDestination.NOTIFICATIONS_BG -> "Notifications"
        SettingsDestination.DIAGNOSTICS -> "Fix & diagnose"
        SettingsDestination.ABOUT -> "About"
    }

    fun motifFor(destination: SettingsDestination): MarbleExpressiveMotif = when (destination) {
        SettingsDestination.APPEARANCE -> MarbleExpressiveMotif.FLOWER
        SettingsDestination.LANGUAGE_FONT -> MarbleExpressiveMotif.PENTAGON
        SettingsDestination.HOME_LAYOUT -> MarbleExpressiveMotif.COOKIE
        SettingsDestination.TAB_BAR -> MarbleExpressiveMotif.GEM
        SettingsDestination.CONNECTION_MODE -> MarbleExpressiveMotif.FAN
        SettingsDestination.CORE_ENGINE -> MarbleExpressiveMotif.DIAMOND
        SettingsDestination.TUNNEL_DATAPATH -> MarbleExpressiveMotif.ARCH
        SettingsDestination.POWER_PROFILE -> MarbleExpressiveMotif.SUNNY
        SettingsDestination.ROUTING -> MarbleExpressiveMotif.ARCH
        SettingsDestination.SPLIT_TUNNEL -> MarbleExpressiveMotif.CLOVER
        SettingsDestination.CENSORSHIP_LAB -> MarbleExpressiveMotif.BURST
        SettingsDestination.PING_TESTING -> MarbleExpressiveMotif.SUNNY
        SettingsDestination.SMART_PRESETS -> MarbleExpressiveMotif.FLOWER
        SettingsDestination.AUTO_SELECTOR -> MarbleExpressiveMotif.COOKIE
        SettingsDestination.SMART_JITTER -> MarbleExpressiveMotif.FAN
        SettingsDestination.NETWORK_COUNCIL -> MarbleExpressiveMotif.GEM
        SettingsDestination.DNS_FAMILY -> MarbleExpressiveMotif.CLOVER
        SettingsDestination.IDENTITY_LOCATION -> MarbleExpressiveMotif.ARCH
        SettingsDestination.SUBSCRIPTIONS_DATA -> MarbleExpressiveMotif.DIAMOND
        SettingsDestination.BACKUP_RESTORE -> MarbleExpressiveMotif.PENTAGON
        SettingsDestination.NOTIFICATIONS_BG -> MarbleExpressiveMotif.SUNNY
        SettingsDestination.DIAGNOSTICS -> MarbleExpressiveMotif.BURST
        SettingsDestination.ABOUT -> MarbleExpressiveMotif.COOKIE
    }

    fun featuredDestinations(
        rows: List<SettingsRowSpec>,
        searching: Boolean
    ): Set<SettingsDestination> =
        if (searching || rows.isEmpty() || rows.size == 2) {
            emptySet()
        } else {
            setOf(rows.first().destination)
        }

    /**
     * Returns rows padded with nulls to [columns]. A featured destination always occupies its
     * own row. Order is preserved and no destination is dropped or duplicated.
     */
    fun rows(
        items: List<SettingsRowSpec>,
        columns: Int,
        featured: Set<SettingsDestination> = emptySet()
    ): List<List<SettingsRowSpec?>> {
        val cols = columns.coerceAtLeast(1)
        if (items.isEmpty()) return emptyList()
        val result = mutableListOf<List<SettingsRowSpec?>>()
        val current = mutableListOf<SettingsRowSpec?>()

        fun flush() {
            while (current.size < cols) current.add(null)
            result.add(current.toList())
            current.clear()
        }

        items.forEach { item ->
            if (item.destination in featured) {
                if (current.isNotEmpty()) flush()
                result.add(listOf(item) + List(cols - 1) { null })
            } else {
                current.add(item)
                if (current.size == cols) flush()
            }
        }
        if (current.isNotEmpty()) flush()
        return result
    }
}
