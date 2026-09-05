package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingOutbound
import com.marbleng.app.model.RoutingRuleKind
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingEngineTest {
    @Test
    fun `default rules are ads block plus iran ip and domain direct`() {
        val rules = RoutingEngine.effectiveRules(AppSettings())
        assertEquals(3, rules.size)
        assertEquals(RoutingRuleKind.GEOSITE, rules[0].kind)
        assertEquals(RoutingOutbound.BLOCK, rules[0].outbound)
        assertEquals("ir", rules[1].matcher)
        assertEquals(RoutingOutbound.DIRECT, rules[1].outbound)
        assertEquals(RoutingRuleKind.GEOSITE, rules[2].kind)
        assertEquals("ir", rules[2].matcher)
    }

    @Test
    fun `harden emits ordered user rules`() {
        val source = JSONObject()
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
        val hardened = JSONObject(
            XrayConfigHardener.harden(source, 10808, AppSettings(ipv6Enabled = false), underlayHasIpv6 = false)
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
    fun `ipv6 defaults off`() {
        assertEquals(false, AppSettings().ipv6Enabled)
    }
}
