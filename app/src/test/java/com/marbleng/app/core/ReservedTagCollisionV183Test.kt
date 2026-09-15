package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.RoutingMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_RESERVED_TAG_COLLISION_V183 — `Xray exited with code 23: … app/proxyman/outbound:
 * existing tag found: block`.
 *
 * Two serverless profiles (`Serverless-v50-fragA`/`-fragB`) failed identically at load because the
 * imported document already defined a `block` outbound and the hardener appended a second one.
 * These tests pin the whole contract: the emitted document has unique tags, imported hops that
 * collide are renamed with their references, the core's `direct`/`block` protocol aliases are read
 * as infrastructure, and the failure — should it ever return — is classified as a document fault.
 */
class ReservedTagCollisionV183Test {

    /** The upstream XTLS serverless shape, with the bare names hand-edited derivatives use. */
    private fun serverlessWithReservedTags(): String = JSONObject()
        .put(
            "outbounds",
            JSONArray()
                .put(JSONObject().put("tag", "block").put("protocol", "block"))
                .put(
                    JSONObject()
                        .put("tag", "direct")
                        .put("protocol", "direct")
                        .put(
                            "streamSettings",
                            JSONObject().put("sockopt", JSONObject().put("domainStrategy", "ForceIP"))
                        )
                )
                .put(
                    JSONObject()
                        .put("tag", "dns-out")
                        .put("protocol", "dns")
                        .put("settings", JSONObject().put("nonIPQuery", "reject"))
                )
                .put(
                    JSONObject()
                        .put("tag", "tls-fragment")
                        .put("protocol", "direct")
                        .put(
                            "settings",
                            JSONObject().put(
                                "fragment",
                                JSONObject().put("packets", "tlshello").put("length", "6").put("interval", "0")
                            )
                        )
                        .put(
                            "streamSettings",
                            JSONObject().put("sockopt", JSONObject().put("dialerProxy", "full-fragment"))
                        )
                )
                .put(
                    JSONObject()
                        .put("tag", "full-fragment")
                        .put("protocol", "direct")
                        .put(
                            "settings",
                            JSONObject().put(
                                "fragment",
                                JSONObject().put("packets", "1-1").put("length", "1").put("interval", "4").put("maxSplit", "517")
                            )
                        )
                        .put(
                            "streamSettings",
                            JSONObject().put("sockopt", JSONObject().put("domainStrategy", "ForceIP"))
                        )
                )
        )
        .toString()

    private fun tags(config: JSONObject): List<String> {
        val outbounds = config.getJSONArray("outbounds")
        return (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("tag") }
    }

    private fun outbound(config: JSONObject, tag: String): JSONObject? {
        val outbounds = config.getJSONArray("outbounds")
        return (0 until outbounds.length()).map { outbounds.getJSONObject(it) }.firstOrNull { it.getString("tag") == tag }
    }

    @Test
    fun `an imported block outbound no longer collides with the one the hardener adds`() {
        val settings = AppSettings(routingMode = RoutingMode.PROXY_ALL, fragmentEnabled = false)
        val config = JSONObject(XrayConfigHardener.harden(serverlessWithReservedTags(), 21080, settings))
        val emitted = tags(config)

        assertEquals("every emitted outbound tag must be unique: $emitted", emitted.size, emitted.toSet().size)
        assertEquals("exactly one block outbound", 1, emitted.count { it == "block" })
        assertEquals("blackhole", outbound(config, "block")!!.getString("protocol"))
        // The hardener's `direct` is the one the routing rules reference; the imported one is
        // renamed, not silently dropped or merged.
        assertEquals(1, emitted.count { it == "direct" })
        assertEquals("freedom", outbound(config, "direct")!!.getString("protocol"))
        assertFalse("the imported dns outbound must not shadow dns-out", emitted.count { it == "dns-out" } > 1)
    }

    @Test
    fun `the imported fragment chain survives the rename with its dialerProxy intact`() {
        val settings = AppSettings(routingMode = RoutingMode.PROXY_ALL, fragmentEnabled = false)
        val config = JSONObject(XrayConfigHardener.harden(serverlessWithReservedTags(), 21080, settings))
        val selected = outbound(config, XrayConfigHardener.importedAliasFor("tls-fragment"))
        assertNotNull("the imported tls-fragment hop is the selected exit under its alias", selected)
        assertEquals(
            "full-fragment",
            selected!!.getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy")
        )
        assertNotNull(outbound(config, "full-fragment"))
        // The routing fallback rule sends unmatched traffic to the renamed selected hop.
        val rules = config.getJSONObject("routing").getJSONArray("rules")
        val last = rules.getJSONObject(rules.length() - 1)
        assertEquals(XrayConfigHardener.importedAliasFor("tls-fragment"), last.getString("outboundTag"))
    }

    @Test
    fun `renaming rewrites dialerProxy and proxySettings references`() {
        val outbounds = JSONArray()
            .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            .put(
                JSONObject().put("tag", "proxy").put("protocol", "vless")
                    .put("streamSettings", JSONObject().put("sockopt", JSONObject().put("dialerProxy", "direct")))
                    .put("proxySettings", JSONObject().put("tag", "direct"))
            )
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            .put(JSONObject().put("tag", "import-direct").put("protocol", "freedom"))
        val renames = XrayConfigHardener.renameReservedImportedTags(outbounds)
        assertEquals("import-block", renames["block"])
        // `import-direct` already exists, so the alias must step aside.
        assertEquals("import-direct-2", renames["direct"])
        val proxy = outbounds.getJSONObject(1)
        assertEquals("import-direct-2", proxy.getJSONObject("streamSettings").getJSONObject("sockopt").getString("dialerProxy"))
        assertEquals("import-direct-2", proxy.getJSONObject("proxySettings").getString("tag"))
        assertTrue(XrayConfigHardener.renameReservedImportedTags(JSONArray().put(JSONObject().put("tag", "proxy"))).isEmpty())
    }

    @Test
    fun `a document that repeats a tag itself is emitted once per tag`() {
        val source = JSONObject().put(
            "outbounds",
            JSONArray()
                .put(
                    JSONObject().put("tag", "proxy").put("protocol", "vless")
                        .put("settings", JSONObject().put("address", "1.2.3.4").put("port", 443).put("encryption", "none"))
                        .put("streamSettings", JSONObject().put("security", "tls").put("network", "tcp"))
                )
                .put(JSONObject().put("tag", "proxy").put("protocol", "freedom"))
        ).toString()
        val config = JSONObject(XrayConfigHardener.harden(source, 21080, AppSettings(routingMode = RoutingMode.PROXY_ALL)))
        val emitted = tags(config)
        assertEquals(emitted.size, emitted.toSet().size)
        assertEquals("vless", outbound(config, "proxy")!!.getString("protocol"))
    }

    @Test
    fun `the core protocol aliases are canonicalised by the repair pass`() {
        val report = XrayConfigRepairs.apply(serverlessWithReservedTags())
        assertTrue(report.repairs.contains("protocol-block-to-blackhole"))
        assertTrue(report.repairs.contains("protocol-direct-to-freedom"))
        val outbounds = JSONObject(report.document).getJSONArray("outbounds")
        val protocols = (0 until outbounds.length()).map { outbounds.getJSONObject(it).getString("protocol") }
        assertFalse(protocols.contains("block"))
        assertFalse(protocols.contains("direct"))
        assertTrue(protocols.contains("blackhole"))
        assertTrue(protocols.contains("freedom"))
    }

    @Test
    fun `a block-protocol outbound is not mistaken for the proxy`() {
        val source = JSONObject().put(
            "outbounds",
            JSONArray()
                .put(JSONObject().put("tag", "block-out").put("protocol", "block"))
                .put(
                    JSONObject().put("tag", "node").put("protocol", "vless")
                        .put("settings", JSONObject().put("address", "1.2.3.4").put("port", 443).put("encryption", "none"))
                        .put("streamSettings", JSONObject().put("security", "tls").put("network", "tcp"))
                )
        ).toString()
        val config = JSONObject(XrayConfigHardener.harden(source, 21080, AppSettings(routingMode = RoutingMode.PROXY_ALL)))
        val rules = config.getJSONObject("routing").getJSONArray("rules")
        assertEquals("node", rules.getJSONObject(rules.length() - 1).getString("outboundTag"))
        val profile = ProxyParser.parseInput(source).single()
        assertEquals("vless", profile.scheme)
        assertEquals("1.2.3.4", profile.host)
    }

    // ------------------------------------------------------------------ failure classification

    private val exit23 =
        "Xray exited with code 23: 2026/09/15 10:05:27.212311 [Info] app/dns: DNS: created DOH client " +
            "for https://1.1.1.1/dns-query, with h2c false | 2026/09/15 10:05:27.212344 [Info] app/dns: " +
            "DNS: created DOH client for https://8.8.8.8/dns-query, with h2c false | 2026/09/15 " +
            "10:05:27.212349 [Info] app/dns: DNS: created DOH client for https://9.9.9.9/dns-query, with " +
            "h2c false | Failed to start: main: failed to create server > app/proxyman/outbound: existing tag found: block"

    @Test
    fun `exit code 23 with a duplicate tag is a document fault with the tag named`() {
        val fault = XrayStartFailure.classify(exit23)
        assertNotNull(fault)
        assertEquals(XrayStartFailure.Kind.DUPLICATE_OUTBOUND_TAG, fault!!.kind)
        assertTrue(fault.cause.startsWith("Failed to start"))
        assertFalse("the [Info] DNS prologue is not the cause", fault.cause.contains("[Info]"))
        assertTrue(fault.headline.contains("\"block\""))
        assertTrue(fault.headline.contains("exit code 23"))
        assertTrue(fault.headline.contains("not a server verdict"))
        assertTrue(fault.remediation.contains("import-block"))
        assertEquals("Configuration rejected by Xray", XrayStartFailure.faultClass(exit23, "Core/configuration error"))
    }

    @Test
    fun `the classifier reads the runtime timeline when the in-memory reason is gone`() {
        val runtime = "2026-09-15T10:05:27.376400Z | thread=pool-35-thread-1 | XRAY | start-result | " +
            "session=mu2iabil-c7cfad26 | ok=false | elapsedMs=223 | phase=failed | alive=false | " +
            "inbound=false | controller=true | reason=$exit23"
        val fault = XrayStartFailure.classify("", runtime)
        assertNotNull(fault)
        assertEquals(XrayStartFailure.Kind.DUPLICATE_OUTBOUND_TAG, fault!!.kind)
        // A later successful start makes the failure history, not a current fault.
        val recovered = runtime + "\n2026-09-15T10:06:00.000000Z | thread=pool-40-thread-1 | XRAY | start-result | " +
            "session=mu2iaz00-a4ac6e18 | ok=true | elapsedMs=410 | phase=ready | alive=true"
        assertNull(XrayStartFailure.classify("", recovered))
    }

    @Test
    fun `a runtime death or a clean stop is not a load failure`() {
        assertNull(XrayStartFailure.classify(""))
        assertNull(XrayStartFailure.classify("SOCKS listener did not open: no Xray error detail"))
        assertNull(XrayStartFailure.classify("Xray exited with code 137: killed"))
        assertEquals("Core/configuration error", XrayStartFailure.faultClass("HEV stopped", "Core/configuration error"))
        val port = XrayStartFailure.classify("Xray exited with code 23: Failed to start: main: failed to create server > listen tcp 127.0.0.1:10808: bind: address already in use")
        assertEquals(XrayStartFailure.Kind.PORT_IN_USE, port!!.kind)
        assertEquals("Local port in use", XrayStartFailure.faultClass("bind: address already in use", "x"))
    }

    @Test
    fun `the start-failure hint leads with the fatal line instead of the DNS prologue`() {
        val lines = listOf(
            "2026/09/15 10:05:27.212311 [Info] app/dns: DNS: created DOH client for https://1.1.1.1/dns-query, with h2c false",
            "2026/09/15 10:05:27.212344 [Info] app/dns: DNS: created DOH client for https://8.8.8.8/dns-query, with h2c false",
            "2026/09/15 10:05:27.212349 [Info] app/dns: DNS: created DOH client for https://9.9.9.9/dns-query, with h2c false",
            "Failed to start: main: failed to create server > app/proxyman/outbound: existing tag found: block"
        )
        val hint = XrayManager.summarizeStartFailure(lines)
        assertTrue(hint.startsWith("Failed to start"))
        assertTrue(hint.length <= 900)
        assertEquals("", XrayManager.summarizeStartFailure(emptyList()))
        assertEquals("a | b", XrayManager.summarizeStartFailure(listOf("a", "", "b")))
    }
}
