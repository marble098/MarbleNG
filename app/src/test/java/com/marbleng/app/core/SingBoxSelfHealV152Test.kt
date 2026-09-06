package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_ENGINE_SELF_HEAL_V152 — the sing-box config doctor, pinned by unit test.
 *
 * The shipped 8.0.6 log ends BLOCKED seventeen profiles deep because every start was refused
 * with `outbounds[3]: dns outbound is deprecated in sing-box 1.11.0 and removed in sing-box
 * 1.13.0, use rule actions instead`. The doctor is Marble Intelligence's automatic repair: the
 * known removals are migrated in place and the core re-checks the healed config. These tests
 * replay the exact fault from the log and the 1.12 DNS key rename, and pin the fault
 * classifier the VPN service uses to stop a futile 17-node failover and switch engines.
 */
class SingBoxSelfHealV152Test {

    /** The config shape that shipped in 8.0.6: a proxy hop, direct, block and the removed dns hop. */
    private fun legacyConfig(): String = JSONObject()
        .put(
            "log",
            JSONObject().put("level", "warn")
        )
        .put(
            "inbounds",
            JSONArray().put(
                JSONObject()
                    .put("type", "mixed")
                    .put("tag", "socks-in")
                    .put("listen", "127.0.0.1")
                    .put("listen_port", 10808)
            )
        )
        .put(
            "outbounds",
            JSONArray()
                .put(JSONObject().put("type", "direct").put("tag", "direct"))
                .put(JSONObject().put("type", "block").put("tag", "block"))
                .put(JSONObject().put("type", "dns").put("tag", "dns-out"))
        )
        .put(
            "dns",
            JSONObject()
                .put(
                    "servers",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put("type", "https")
                                .put("tag", "dns-remote")
                                .put("address", "https://1.1.1.1/dns-query")
                        )
                        .put(JSONObject().put("type", "hosts").put("tag", "dns-hosts"))
                )
                .put("final", "dns-remote")
        )
        .put(
            "route",
            JSONObject()
                .put(
                    "rules",
                    JSONArray()
                        .put(JSONObject().put("action", "sniff"))
                        .put(
                            JSONObject()
                                .put("protocol", JSONArray().put("dns"))
                                .put("outbound", "dns-out")
                        )
                        .put(
                            JSONObject()
                                .put("rule_set", JSONArray().put("geosite-ir"))
                                .put("outbound", "dns-out")
                        )
                )
                .put("final", "marble-proxy")
        )
        .toString()

    @Test
    fun theShipped860FaultIsRepairedAutomatically() {
        val repair = SingBoxConfigDoctor.repair(legacyConfig())
        assertTrue("the doctor must repair a config carrying the removed dns outbound", repair.repaired)

        val healed = JSONObject(repair.json)
        val outbounds = healed.getJSONArray("outbounds")
        for (i in 0 until outbounds.length()) {
            assertFalse(
                "the removed dns outbound must be gone after repair",
                outbounds.getJSONObject(i).optString("type").equals("dns", ignoreCase = true)
            )
        }
        // The replacement the error message itself names: a hijack-dns rule action.
        val rules = healed.getJSONObject("route").getJSONArray("rules")
        val hijack = (0 until rules.length()).any {
            rules.getJSONObject(it).optString("action") == "hijack-dns"
        }
        assertTrue("the dns route rule must become a hijack-dns action", hijack)
        // The non-DNS rule that pointed at the removed outbound loses only its dead target.
        val irRule = (0 until rules.length()).first {
            rules.getJSONObject(it).optJSONArray("rule_set") != null
        }.let { rules.getJSONObject(it) }
        assertFalse("no rule may target a removed outbound", irRule.has("outbound"))
        assertTrue(irRule.getJSONArray("rule_set").length() > 0)
    }

    @Test
    fun thePre112DnsAddressKeyIsMigrated() {
        val repair = SingBoxConfigDoctor.repair(legacyConfig())
        assertTrue(repair.repaired)
        val servers = JSONObject(repair.json).getJSONObject("dns").getJSONArray("servers")
        val remote = (0 until servers.length()).first {
            servers.getJSONObject(it).optString("tag") == "dns-remote"
        }.let { servers.getJSONObject(it) }
        assertEquals(
            "the address key must migrate to the 1.12 `server` key",
            "https://1.1.1.1/dns-query",
            remote.getString("server")
        )
        assertFalse("the legacy `address` key must not survive", remote.has("address"))
    }

    @Test
    fun anAlreadyModernConfigComesBackUntouched() {
        val modern = SingBoxConfigBuilder.build(
            profile = modernProfile(),
            settings = com.marbleng.app.model.AppSettings(),
            socksPort = 10808,
            apiPort = 39090,
            apiSecret = "secret",
            logPath = "/data/local/tmp/singbox.log",
            cachePath = "/data/local/tmp/singbox-cache.db"
        ).json
        val repair = SingBoxConfigDoctor.repair(modern)
        assertFalse(
            "a config the builder writes today must already be clean: " + repair.notes,
            repair.repaired
        )
        assertEquals(modern, repair.json)
    }

    @Test
    fun unreadableJsonIsNeverGuessedAt() {
        val repair = SingBoxConfigDoctor.repair("not json at all")
        assertFalse(repair.repaired)
        assertEquals("not json at all", repair.json)
        assertTrue(repair.notes.isEmpty())
    }

    @Test
    fun engineLevelFaultsAreRecognisedSoFailoverStopsWalkingNodes() {
        val shippedError = "sing-box rejected the config: outbounds[3]: dns outbound is " +
            "deprecated in sing-box 1.11.0 and removed in sing-box 1.13.0, use rule actions instead"
        assertTrue(
            "the exact 8.0.6 rejection is an engine-level fault",
            SingBoxConfigDoctor.isEngineLevelFault(shippedError)
        )
        assertTrue(SingBoxConfigDoctor.isEngineLevelFault("Config: decode config: unknown field"))
        // Node and network failures are NOT engine faults: those are exactly what failover exists
        // for, and misclassifying them would strand the user on one engine.
        assertFalse(SingBoxConfigDoctor.isEngineLevelFault("sing-box listener did not open: refused"))
        assertFalse(SingBoxConfigDoctor.isEngineLevelFault("spawn failed: no space left on device"))
        assertFalse(SingBoxConfigDoctor.isEngineLevelFault(""))
    }

    private fun modernProfile() = com.marbleng.app.model.ProxyProfile(
        id = "node-1",
        name = "Node 1",
        scheme = "vless",
        raw = "vless://11111111-2222-3333-4444-555555555555@198.51.100.7:443" +
            "?encryption=none&security=tls&type=tcp&sni=example.com#Node+1",
        configJson = "",
        host = "198.51.100.7",
        port = 443
    )
}
