package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingMode
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRule
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoutingEngineTest {

    @Before
    fun resetGeoIndex() {
        // Validation consults the process-wide geo index; tests must run without one so the
        // "unknown tag" verdicts stay deterministic regardless of execution order.
        GeoAssetIndex.resetForTests()
    }


    private fun proxyConfigSource(): String = JSONObject()
        .put(
            "outbounds",
            JSONArray().put(
                JSONObject()
                    .put("tag", "proxy")
                    .put("protocol", "vless")
                    .put(
                        "settings",
                        JSONObject().put(
                            "vnext",
                            JSONArray().put(
                                JSONObject()
                                    .put("address", "1.1.1.1")
                                    .put("port", 443)
                                    .put(
                                        "users",
                                        JSONArray().put(
                                            JSONObject()
                                                .put("id", "11111111-1111-1111-1111-111111111111")
                                                .put("encryption", "none")
                                        )
                                    )
                            )
                        )
                    )
            )
        )
        .toString()

    @Test
    fun `default rules are ads block plus iran ip and domain direct`() {
        val rules = RoutingEngine.DEFAULT_RULES
        assertEquals(3, rules.size)
        assertEquals(RoutingRuleKind.GEOSITE, rules[0].kind)
        assertEquals(RoutingOutbound.BLOCK, rules[0].outbound)
        assertEquals("ir", rules[1].matcher)
        assertEquals(RoutingOutbound.DIRECT, rules[1].outbound)
        assertEquals(RoutingRuleKind.GEOSITE, rules[2].kind)
        assertEquals("ir", rules[2].matcher)
    }

    @Test
    fun `an empty or deleted rule list stays empty - no silent resurrection`() {
        // The old parseRules turned "" and "[]" back into DEFAULT_RULES, so a user could never
        // actually delete the built-in rules. The stored list is now the truth.
        assertTrue(RoutingEngine.parseRules("").isEmpty())
        assertTrue(RoutingEngine.parseRules("[]").isEmpty())
        assertTrue(RoutingEngine.effectiveRules(AppSettings(routingRulesJson = "")).isEmpty())
        assertTrue(RoutingEngine.effectiveRules(AppSettings(routingRulesJson = "[]")).isEmpty())
    }

    @Test
    fun `geo direct mode emits implicit ads and geo rules plus user rules in order`() {
        val settings = AppSettings(
            routingMode = RoutingMode.GEO_DIRECT,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(
                    RoutingRule(
                        id = "yt",
                        kind = RoutingRuleKind.GEOSITE,
                        matcher = "youtube",
                        outbound = RoutingOutbound.PROXY
                    )
                )
            )
        )
        val hardened = JSONObject(
            XrayConfigHardener.harden(
                proxyConfigSource(), 10808,
                settings = settings,
                underlayHasIpv6 = false
            )
        )
        val text = hardened.getJSONObject("routing").getJSONArray("rules").toString()
        val adsIndex = text.indexOf("geosite:category-ads-all")
        val irIpIndex = text.indexOf("geoip:ir")
        val irSiteIndex = text.indexOf("geosite:ir")
        val userIndex = text.indexOf("geosite:youtube")
        assertTrue(adsIndex >= 0 && irIpIndex >= 0 && irSiteIndex >= 0 && userIndex >= 0)
        assertTrue(adsIndex < irIpIndex && irIpIndex < irSiteIndex && irSiteIndex < userIndex)
    }

    @Test
    fun `proxy all mode applies no implicit geo rules`() {
        val settings = AppSettings(
            routingMode = RoutingMode.PROXY_ALL,
            routingRulesJson = "[]"
        )
        val rulesOut = JSONArray()
        RoutingEngine.applyUserRules(rulesOut, settings, "proxy")
        val text = rulesOut.toString()
        assertFalse(text.contains("geoip:ir"))
        assertFalse(text.contains("geosite:ir"))
        // The ads switch is an explicit independent control the user owns in every mode.
        assertTrue(text.contains("geosite:category-ads-all"))
        // Private bypass stays an independent switch and is on by default.
        assertTrue(text.contains("10.0.0.0/8"))
    }

    @Test
    fun `custom mode is strictly user rules`() {
        val settings = AppSettings(
            routingMode = RoutingMode.CUSTOM,
            routeBlockAds = false,
            routeBypassPrivate = false,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(
                    RoutingRule(
                        id = "block-quic",
                        kind = RoutingRuleKind.PORT,
                        matcher = "443",
                        network = "udp",
                        outbound = RoutingOutbound.BLOCK,
                        remark = "No QUIC"
                    )
                )
            )
        )
        val rulesOut = JSONArray()
        RoutingEngine.applyUserRules(rulesOut, settings, "proxy")
        assertEquals(1, rulesOut.length())
        val rule = rulesOut.getJSONObject(0)
        assertEquals("443", rule.optString("port"))
        assertEquals("udp", rule.optString("network"))
        assertEquals("block", rule.optString("outboundTag"))
    }

    @Test
    fun `invalid rules are skipped at emission instead of killing the core`() {
        val settings = AppSettings(
            routingMode = RoutingMode.CUSTOM,
            routeBlockAds = false,
            routeBypassPrivate = false,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(
                    // A port token the engine rejects ("443 udp") used to fail the whole config.
                    RoutingRule(
                        id = "bad-port",
                        kind = RoutingRuleKind.PORT,
                        matcher = "443 udp",
                        outbound = RoutingOutbound.BLOCK
                    ),
                    RoutingRule(
                        id = "good-domain",
                        kind = RoutingRuleKind.DOMAIN,
                        matcher = "example.com",
                        outbound = RoutingOutbound.DIRECT
                    )
                )
            )
        )
        val rulesOut = JSONArray()
        RoutingEngine.applyUserRules(rulesOut, settings, "proxy")
        assertEquals(1, rulesOut.length())
        val text = rulesOut.toString()
        assertTrue(text.contains("domain:example.com"))
        assertFalse(text.contains("443 udp"))
    }

    @Test
    fun `port validation catches the shapes xray rejects`() {
        val bad = RoutingEngine.validateRule(
            RoutingRule(id = "p", kind = RoutingRuleKind.PORT, matcher = "443 udp", outbound = RoutingOutbound.BLOCK)
        )
        assertTrue(bad.any { it.severity == RoutingEngine.IssueSeverity.ERROR })

        val reversed = RoutingEngine.validateRule(
            RoutingRule(id = "p", kind = RoutingRuleKind.PORT, matcher = "900-800", outbound = RoutingOutbound.BLOCK)
        )
        assertTrue(reversed.any { it.severity == RoutingEngine.IssueSeverity.ERROR })

        val good = RoutingEngine.validateRule(
            RoutingRule(id = "p", kind = RoutingRuleKind.PORT, matcher = "443,80,8443,1000-2000", outbound = RoutingOutbound.BLOCK)
        )
        assertTrue(good.none { it.severity == RoutingEngine.IssueSeverity.ERROR })
    }

    @Test
    fun `domain validation compiles regexps and rejects broken shapes`() {
        val badRegexp = RoutingEngine.validateRule(
            RoutingRule(id = "d", kind = RoutingRuleKind.DOMAIN, matcher = "regexp:([unclosed", outbound = RoutingOutbound.PROXY)
        )
        assertTrue(badRegexp.any { it.severity == RoutingEngine.IssueSeverity.ERROR })

        val badDomain = RoutingEngine.validateRule(
            RoutingRule(id = "d", kind = RoutingRuleKind.DOMAIN, matcher = "exa mple..com", outbound = RoutingOutbound.PROXY)
        )
        assertTrue(badDomain.any { it.severity == RoutingEngine.IssueSeverity.ERROR })

        val good = RoutingEngine.validateRule(
            RoutingRule(
                id = "d", kind = RoutingRuleKind.DOMAIN,
                matcher = "example.com, domain:example.org, full:exact.example.net, keyword:video, regexp:(^|\\.)x\\.com$",
                outbound = RoutingOutbound.PROXY
            )
        )
        assertTrue(good.none { it.severity == RoutingEngine.IssueSeverity.ERROR })
    }

    @Test
    fun `cidr helpers parse literals and test containment without dns`() {
        val cidr = RoutingEngine.parseCidr("10.0.0.0/8")
        assertTrue(cidr != null)
        assertTrue(
            RoutingEngine.cidrContains(
                cidr!!,
                java.net.InetAddress.getByName("10.1.2.3")
            )
        )
        assertFalse(
            RoutingEngine.cidrContains(
                cidr,
                java.net.InetAddress.getByName("11.0.0.1")
            )
        )
        // Words are refused textually - a validator must never trigger a resolver lookup.
        assertNull(RoutingEngine.parseCidr("ir"))
        assertNull(RoutingEngine.parseCidr("1.2.3.4/99"))
        assertNull(RoutingEngine.parseCidr("1.2.3"))
        assertTrue(RoutingEngine.parseCidr("2001:db8::/32") != null)
        assertTrue(RoutingEngine.parseCidr("::1") != null)
    }

    @Test
    fun `simulator names the winning rule and the fail-closed fallback`() {
        val settings = AppSettings(
            routingMode = RoutingMode.CUSTOM,
            routeBlockAds = false,
            routeBypassPrivate = false,
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(
                    RoutingRule(
                        id = "block-one",
                        kind = RoutingRuleKind.DOMAIN,
                        matcher = "full:blocked.example",
                        outbound = RoutingOutbound.BLOCK,
                        remark = "one"
                    ),
                    RoutingRule(
                        id = "direct-suffix",
                        kind = RoutingRuleKind.DOMAIN,
                        matcher = "domain:direct.example",
                        outbound = RoutingOutbound.DIRECT,
                        remark = "two"
                    )
                )
            )
        )
        val blocked = RoutingEngine.simulate(settings, "blocked.example")
        assertEquals(RoutingOutbound.BLOCK, blocked.verdict)
        assertTrue(blocked.verdictReason.contains("rule 1"))

        val proxied = RoutingEngine.simulate(settings, "unrelated.example")
        assertEquals(RoutingOutbound.PROXY, proxied.verdict)
        assertTrue(proxied.steps.last().title == "Fallback")

        val suffix = RoutingEngine.simulate(settings, "host.direct.example")
        assertEquals(RoutingOutbound.DIRECT, suffix.verdict)
    }

    @Test
    fun `simulator never resolves domains - literals still match ip rules`() {
        val settings = AppSettings(
            routingMode = RoutingMode.CUSTOM,
            routeBlockAds = false,
            routeBypassPrivate = false,
            routingRulesJson = "[]"
        ).copy(
            routingRulesJson = RoutingEngine.serializeRules(
                listOf(
                    RoutingRule(
                        id = "rfc1918",
                        kind = RoutingRuleKind.IP,
                        matcher = "10.0.0.0/8",
                        outbound = RoutingOutbound.DIRECT
                    )
                )
            )
        )
        val sim = RoutingEngine.simulate(settings, "10.1.2.3")
        assertEquals(RoutingOutbound.DIRECT, sim.verdict)
        assertTrue(sim.isIpLiteral)
        // A geoip tag cannot be verified offline: the layer must report unverifiable, not "no".
        val geo = RoutingEngine.simulate(
            settings.copy(
                routingRulesJson = RoutingEngine.serializeRules(
                    listOf(
                        RoutingRule(
                            id = "ir", kind = RoutingRuleKind.GEOIP,
                            matcher = "ir", outbound = RoutingOutbound.DIRECT
                        )
                    )
                )
            ),
            "10.1.2.3"
        )
        assertTrue(geo.steps.any { it.matched == null })
        assertEquals(RoutingOutbound.PROXY, geo.verdict)
    }

    @Test
    fun `presets materialize and the iran baseline survives the engine`() {
        val recommended = RoutingPresets.materialize(RoutingPresets.Preset.RECOMMENDED)
        assertEquals(RoutingEngine.DEFAULT_RULES.size, recommended.size)
        assertTrue(RoutingEngine.needsGeoSite(AppSettings(routingRulesJson = RoutingEngine.serializeRules(recommended))))
        assertTrue(RoutingEngine.needsGeoIp(AppSettings(routingRulesJson = RoutingEngine.serializeRules(recommended))))
        assertTrue(RoutingEngine.needsDirectOutbound(AppSettings(routingRulesJson = RoutingEngine.serializeRules(recommended))))
        // Every preset's rules pass validation without a loaded index.
        RoutingPresets.Preset.entries.forEach { preset ->
            RoutingPresets.materialize(preset).forEach { rule ->
                assertTrue(
                    "${preset.id}/${rule.id}: " + RoutingEngine.validateRule(rule),
                    RoutingEngine.isEmittable(rule)
                )
            }
        }
    }

    @Test
    fun `harden emits ordered user rules`() {
        val settings = AppSettings(
            ipv6Enabled = false,
            routingRulesJson = RoutingEngine.serializeRules(RoutingEngine.DEFAULT_RULES)
        )
        val hardened = JSONObject(
            XrayConfigHardener.harden(proxyConfigSource(), 10808, settings, underlayHasIpv6 = false)
        )
        val rules = hardened.getJSONObject("routing").getJSONArray("rules")
        val text = rules.toString()
        assertTrue(text.contains("geosite:category-ads-all"))
        assertTrue(text.contains("geoip:ir"))
        assertTrue(text.contains("geosite:ir"))
        val adsIndex = text.indexOf("category-ads-all")
        val ipIndex = text.indexOf("geoip:ir")
        val siteIndex = text.indexOf("geosite:ir")
        assertTrue(adsIndex < ipIndex && ipIndex < siteIndex)
    }

    @Test
    fun `family switches default on - the app decides the family`() {
        val defaults = AppSettings()
        assertTrue("IPv6 ships enabled", defaults.ipv6Enabled)
        assertTrue("the v6 preference ships enabled", defaults.preferIpv6)
        // The decision is the app's: an IPv4-only underlay never gets a v6-first dial order.
        val v4Only = AddressFamilyPolicy.plan(settings = defaults, underlayHasIpv6 = false)
        assertEquals(IpFamilyPreference.DUAL, v4Only.preference)
        assertFalse(v4Only.prioritizeIpv6)
        assertTrue(v4Only.raceEnabled)
        assertFalse(v4Only.blockIpv6Traffic)
        // On a v6-capable underlay the same defaults prefer IPv6 with an armed race.
        val dual = AddressFamilyPolicy.plan(settings = defaults, underlayHasIpv6 = true)
        assertEquals(IpFamilyPreference.IPV6_FIRST, dual.preference)
        assertTrue(dual.prioritizeIpv6)
        assertTrue(dual.raceEnabled)
    }
}
