package com.marbleng.app.core

import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind

/**
 * MARBLE_ROUTING_PRESETS_V136 — one-tap rule sets, the v2rayNG template idea done Marble-style.
 *
 * v2rayNG ships whitelist/blacklist templates as asset files (custom_routing_white, …_iran,
 * …_russia) and replaces the whole rule list from them. MarbleNG keeps that power but makes the
 * presets region-aware rather than assuming any single country: the Recommended set is the
 * balanced anti-censorship baseline, Privacy is blocking-only, Global is minimal, and the
 * country sets exist for the users who actually live there — including the ones outside Iran.
 *
 * A preset replaces the user's rule list (after confirmation) but never touches the routing
 * mode, the geo asset source or the expert text lists, so "try a preset" is always reversible.
 */
object RoutingPresets {

    enum class Preset(
        val id: String,
        val title: String,
        val summary: String,
        val rules: List<RoutingRule>
    ) {
        RECOMMENDED(
            "recommended",
            "Recommended",
            "Ads blocked, Iranian services direct, everything else proxied",
            RoutingEngine.DEFAULT_RULES
        ),
        PRIVACY(
            "privacy",
            "Privacy",
            "Ads, trackers and public BitTorrent blocked; nothing geo-specific",
            listOf(
                RoutingRule(
                    id = "preset-ads",
                    kind = RoutingRuleKind.GEOSITE,
                    matcher = "category-ads-all",
                    outbound = RoutingOutbound.BLOCK,
                    remark = "Block ads"
                ),
                RoutingRule(
                    id = "preset-trackers",
                    kind = RoutingRuleKind.GEOSITE,
                    matcher = "category-public-tracker",
                    outbound = RoutingOutbound.BLOCK,
                    remark = "Block public trackers"
                ),
                RoutingRule(
                    id = "preset-private",
                    kind = RoutingRuleKind.GEOIP,
                    matcher = "private",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Private/LAN ranges direct"
                )
            )
        ),
        GLOBAL_PROXY(
            "global",
            "Global proxy",
            "Minimal: LAN stays direct, everything else rides the tunnel",
            listOf(
                RoutingRule(
                    id = "preset-private",
                    kind = RoutingRuleKind.GEOIP,
                    matcher = "private",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Private/LAN ranges direct"
                )
            )
        ),
        IRAN_DIRECT(
            "iran",
            "Iran direct",
            "Iranian domains, IPs and banks on the direct underlay",
            listOf(
                RoutingRule(
                    id = "preset-ir-banks",
                    kind = RoutingRuleKind.GEOSITE,
                    matcher = "category-bank-ir",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Iranian banks direct"
                ),
                RoutingRule(
                    id = "preset-ir-gov",
                    kind = RoutingRuleKind.GEOSITE,
                    matcher = "category-gov-ir",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Iranian government direct"
                ),
                RoutingRule(
                    id = "preset-ir-domains",
                    kind = RoutingRuleKind.GEOSITE,
                    matcher = "ir",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Iranian domains direct"
                ),
                RoutingRule(
                    id = "preset-ir-ips",
                    kind = RoutingRuleKind.GEOIP,
                    matcher = "ir",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Iranian IPs direct"
                ),
                RoutingRule(
                    id = "preset-private",
                    kind = RoutingRuleKind.GEOIP,
                    matcher = "private",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Private/LAN ranges direct"
                )
            )
        ),
        RUSSIA_DIRECT(
            "russia",
            "Russia direct",
            "Russian services on the direct underlay",
            listOf(
                RoutingRule(
                    id = "preset-ru-domains",
                    kind = RoutingRuleKind.GEOSITE,
                    matcher = "ru",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Russian domains direct"
                ),
                RoutingRule(
                    id = "preset-ru-ips",
                    kind = RoutingRuleKind.GEOIP,
                    matcher = "ru",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Russian IPs direct"
                ),
                RoutingRule(
                    id = "preset-private",
                    kind = RoutingRuleKind.GEOIP,
                    matcher = "private",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Private/LAN ranges direct"
                )
            )
        ),
        CHINA_DIRECT(
            "china",
            "China direct",
            "Chinese services on the direct underlay",
            listOf(
                RoutingRule(
                    id = "preset-cn-domains",
                    kind = RoutingRuleKind.GEOSITE,
                    matcher = "cn",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Chinese domains direct"
                ),
                RoutingRule(
                    id = "preset-cn-ips",
                    kind = RoutingRuleKind.GEOIP,
                    matcher = "cn",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Chinese IPs direct"
                ),
                RoutingRule(
                    id = "preset-private",
                    kind = RoutingRuleKind.GEOIP,
                    matcher = "private",
                    outbound = RoutingOutbound.DIRECT,
                    remark = "Private/LAN ranges direct"
                )
            )
        )
    }

    fun byId(id: String): Preset? = Preset.entries.firstOrNull { it.id == id }

    /**
     * Preset rules carry stable ids derived from the preset so re-applying a preset never
     * duplicates ids inside the persisted list.
     */
    fun materialize(preset: Preset): List<RoutingRule> = preset.rules.map { it.copy(enabled = true) }
}
