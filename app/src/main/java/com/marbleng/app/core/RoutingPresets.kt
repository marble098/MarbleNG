package com.marbleng.app.core

import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind

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

    fun materialize(preset: Preset): List<RoutingRule> = preset.rules.map { it.copy(enabled = true) }
}
