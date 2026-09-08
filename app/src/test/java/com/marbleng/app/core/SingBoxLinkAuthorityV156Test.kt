package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
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
 * MARBLE_SINGBOX_LINK_AUTHORITY_V156 — the share link is the authority; the stored Xray JSON is a
 * cache of it.
 *
 * Every node in MarbleNG carries both: the `vless://…` link it was created from and the Xray JSON
 * the importer derived from that link. The sing-box writer only ever looked at the JSON, so the
 * extended core ran a *translation of a translation* and every parameter Marble's translator does
 * not model was silently dropped from the node the user actually subscribed to. These tests pin
 * the corrected reading order, the fallback that keeps one reader's blind spot from killing a
 * node, and the fact that routing is written identically for every reader.
 */
class SingBoxLinkAuthorityV156Test {

    private val uuid = "11111111-2222-3333-4444-555555555555"
    private val host = "198.51.100.7"

    private val link =
        "vless://$uuid@$host:443?encryption=none&security=tls&type=tcp&sni=example.org#Node A"

    /** The Xray JSON Marble's own importer derives from [link]. */
    private fun xrayJson(): String = JSONObject()
        .put(
            "outbounds",
            JSONArray().put(
                JSONObject()
                    .put("protocol", "vless")
                    .put("tag", "proxy")
                    .put(
                        "settings",
                        JSONObject()
                            .put("address", host)
                            .put("port", 443)
                            .put("id", uuid)
                            .put("encryption", "none")
                    )
                    .put(
                        "streamSettings",
                        JSONObject()
                            .put("network", "tcp")
                            .put("security", "tls")
                            .put("tlsSettings", JSONObject().put("serverName", "example.org"))
                    )
            )
        )
        .toString()

    /** A node exactly as a subscription stores it: the link *and* the JSON derived from it. */
    private fun subscriptionProfile(
        raw: String = link,
        configJson: String = xrayJson()
    ) = ProxyProfile(
        id = "node-1",
        name = "Node A",
        scheme = raw.substringBefore("://"),
        raw = raw,
        configJson = configJson,
        host = host,
        port = 443
    )

    private fun candidates(
        profile: ProxyProfile,
        settings: AppSettings = AppSettings()
    ): List<SingBoxConfigBuilder.Build> = SingBoxConfigBuilder.candidateBuilds(
        profile = profile,
        settings = settings,
        socksPort = 10808,
        apiPort = 39090,
        apiSecret = "secret",
        logPath = "",
        cachePath = "cache.db"
    )

    private fun proxyOf(build: SingBoxConfigBuilder.Build): JSONObject {
        val outbounds = JSONObject(build.json).getJSONArray("outbounds")
        return NativeSingBoxConfig.objects(outbounds)
            .single { it.optString("tag") == SingBoxConfigBuilder.PROXY_TAG }
    }

    /**
     * The same node as the link says it is *today* — a port and an SNI the stored copy has since
     * drifted away from. This is the situation reader 2 exists for: the stored JSON is a cache,
     * and the cache can be stale.
     *
     * The reader is injected because [ProxyParser] parses URIs with `android.net.Uri`, which is a
     * stub that throws in a plain JVM unit test. What is under test here is the candidate *order*
     * and the fallback, not the link grammar.
     */
    private fun rereadJson(): String = JSONObject()
        .put(
            "outbounds",
            JSONArray().put(
                JSONObject()
                    .put("protocol", "vless")
                    .put("tag", "proxy")
                    .put(
                        "settings",
                        JSONObject()
                            .put("address", host)
                            .put("port", 8443)
                            .put("id", uuid)
                            .put("encryption", "none")
                    )
                    .put(
                        "streamSettings",
                        JSONObject()
                            .put("network", "tcp")
                            .put("security", "tls")
                            .put("tlsSettings", JSONObject().put("serverName", "current.example"))
                    )
            )
        )
        .toString()

    private fun <T> withLinkReader(reader: (String) -> JSONObject?, block: () -> T): T {
        val previous = SingBoxConfigBuilder.linkJson
        SingBoxConfigBuilder.linkJson = reader
        return try {
            block()
        } finally {
            SingBoxConfigBuilder.linkJson = previous
        }
    }

    /** Reader 2 as a pure function of the link: today's reading of the same node. */
    private fun readers(json: String = rereadJson()): (String) -> JSONObject = { JSONObject(json) }

    // ───────────────────────────────────────────────────────────────── reader preference

    @Test
    fun aStoredNodeIsHandedToTheCoresOwnParserFirst() {
        val profile = subscriptionProfile()
        val support = SingBoxConfigBuilder.describe(profile, AppSettings())
        assertTrue(support.reason, support.supported)
        assertEquals(
            "the share link outranks the JSON derived from it",
            SingBoxConfigBuilder.STRATEGY_LINK,
            support.strategy
        )

        val proxy = proxyOf(candidates(profile).first())
        assertEquals("parser", proxy.getString("type"))
        assertEquals(link, proxy.getString("link"))
    }

    @Test
    fun readersThatProduceTheSameDocumentAreOfferedOnce() {
        // De-duplication is a promise, not an accident: offering the identical document twice
        // would make the manager spend a second core spawn proving the same refusal twice.
        val profile = subscriptionProfile()
        val strategies = withLinkReader({ JSONObject(xrayJson()) }) {
            candidates(profile).map { it.strategy }
        }
        assertEquals(
            listOf(SingBoxConfigBuilder.STRATEGY_LINK, SingBoxConfigBuilder.STRATEGY_LINK_TRANSLATED),
            strategies
        )
    }

    @Test
    fun everyReaderIsOfferedSoOneRefusalCannotKillTheNode() {
        val profile = subscriptionProfile()
        val strategies = withLinkReader(readers()) { candidates(profile).map { it.strategy } }
        assertEquals(
            "the link, the link re-read and the stored copy are three different documents here",
            listOf(
                SingBoxConfigBuilder.STRATEGY_LINK,
                SingBoxConfigBuilder.STRATEGY_LINK_TRANSLATED,
                SingBoxConfigBuilder.STRATEGY_TRANSLATED
            ),
            strategies
        )
        // The point of the list: the manager hands them over in order and moves on only when the
        // core itself refuses one, so a blind spot in any single reader cannot kill the node.
        val builds = withLinkReader(readers()) { candidates(profile) }
        assertEquals(8443, proxyOf(builds[1]).getInt("server_port"))
        assertEquals(443, proxyOf(builds[2]).getInt("server_port"))
    }

    @Test
    fun turningTheParserPreferenceOffFlipsTheOrderNotTheCoverage() {
        val off = withLinkReader(readers()) {
            candidates(subscriptionProfile(), AppSettings(singBoxPreferParser = false))
        }
        assertEquals(
            "the stored JSON leads when the user asked for translation",
            SingBoxConfigBuilder.STRATEGY_TRANSLATED,
            off.first().strategy
        )
        assertTrue(
            "the core parser stays available as a fallback either way",
            off.any { it.strategy == SingBoxConfigBuilder.STRATEGY_LINK }
        )
        assertEquals(3, off.size)
    }

    @Test
    fun marbleReadsTheLinkItselfWhenTheStoredJsonIsMissing() {
        // tuic/anytls and any node whose import predates a parser fix land here: no stored JSON,
        // but the link is still readable by Marble.
        val profile = subscriptionProfile(configJson = "")
        val builds = withLinkReader({ JSONObject(xrayJson()) }) {
            candidates(profile)
        }
        assertEquals(
            listOf(
                SingBoxConfigBuilder.STRATEGY_LINK,
                SingBoxConfigBuilder.STRATEGY_LINK_TRANSLATED
            ),
            builds.map { it.strategy }
        )
        val translated = proxyOf(builds.last())
        assertEquals("vless", translated.getString("type"))
        assertEquals(host, translated.getString("server"))
        assertEquals(443, translated.getInt("server_port"))
    }

    @Test
    fun aPastedJsonDocumentStillTranslatesAndSaysSo() {
        val pasted = ProxyProfile(
            id = "node-2",
            name = "Pasted",
            scheme = "vless",
            raw = "",
            configJson = xrayJson(),
            host = host,
            port = 443
        )
        val built = candidates(pasted)
        assertEquals(1, built.size)
        assertEquals(SingBoxConfigBuilder.STRATEGY_TRANSLATED, built.first().strategy)
        assertEquals("vless", proxyOf(built.first()).getString("type"))
    }

    @Test
    fun aPastedNativeSingBoxDocumentShortCircuitsEveryReader() {
        val native = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("type", "vless")
                        .put("tag", "proxy")
                        .put("server", host)
                        .put("server_port", 443)
                        .put("uuid", uuid)
                )
            )
            .toString()
        val profile = subscriptionProfile(configJson = native)
        assertEquals(
            SingBoxConfigBuilder.STRATEGY_NATIVE,
            SingBoxConfigBuilder.describe(profile, AppSettings()).strategy
        )
    }

    // ───────────────────────────────────────────────────────────────── routing parity

    @Test
    fun routingIsIdenticalForEveryReaderOfTheSameNode() {
        // The whole point of one scaffolder: the URL test, the DNS graph and the split rules mean
        // the same thing whichever reader produced the proxy hop.
        val builds = withLinkReader(readers()) {
            candidates(
                subscriptionProfile(),
                AppSettings(routeDirectDomains = "example.ir", routeBlockDomains = "ads.example")
            )
        }
        assertEquals(3, builds.size)
        val routes = builds.map { JSONObject(it.json).getJSONObject("route").toString() }
        assertEquals(
            "routing must not depend on which reader won",
            1,
            routes.distinct().size
        )
        val route = JSONObject(builds.first().json).getJSONObject("route")
        assertEquals(SingBoxConfigBuilder.PROXY_TAG, route.getString("final"))
        assertTrue(route.getJSONArray("rules").length() > 1)
    }

    @Test
    fun anUnbundledGeoRuleIsReportedInsteadOfKillingTheEngine() {
        // One `geoip:us` rule used to throw out of routeConfig, which made every node on the
        // sing-box engine unconnectable while the same profile worked on Xray.
        val built = SingBoxConfigBuilder.build(
            profile = subscriptionProfile(),
            settings = AppSettings(
                // geo tags only reach the writer in the split mode that reads them.
                routingMode = RoutingMode.GEO_DIRECT,
                routeGeoIpTags = "geoip:us",
                routeBlockAds = false
            ),
            socksPort = 10808,
            apiPort = 39090,
            apiSecret = "secret",
            logPath = "",
            cachePath = "cache.db"
        )
        assertNull(SingBoxConfigBuilder.geoTag(ip = true, raw = "geoip:us"))
        assertTrue(
            "the dropped rule has to be named, not silently absent",
            built.notes.any { it.contains("geoip:us") && it.contains("not applied") }
        )
        // Everything Marble does bundle is still honoured.
        assertEquals(SingBoxConfigBuilder.RULE_SET_GEOIP_IR, SingBoxConfigBuilder.geoTag(true, "geoip:ir"))
        assertEquals(SingBoxConfigBuilder.RULE_SET_GEOSITE_IR, SingBoxConfigBuilder.geoTag(false, "geosite:ir"))
        assertEquals(SingBoxConfigBuilder.RULE_SET_ADS, SingBoxConfigBuilder.geoTag(false, "geosite:category-ads-all"))
        assertEquals(SingBoxConfigBuilder.RULE_SET_GEOIP_PRIVATE, SingBoxConfigBuilder.geoTag(true, "geoip:private"))
    }

    // ───────────────────────────────────────────────────────────────── refusal honesty

    @Test
    fun aNodeNoReaderCanServeIsRefusedWithAReason() {
        val profile = ProxyProfile(
            id = "node-3",
            name = "Unknown",
            scheme = "dokodemo-door",
            raw = "",
            configJson = JSONObject()
                .put(
                    "outbounds",
                    JSONArray().put(
                        JSONObject().put("protocol", "dokodemo-door").put("tag", "proxy")
                            .put("settings", JSONObject().put("address", host).put("port", 443))
                    )
                )
                .toString(),
            host = host,
            port = 443
        )
        val support = SingBoxConfigBuilder.describe(profile, AppSettings())
        assertFalse(support.supported)
        assertEquals("", support.strategy)
        assertTrue(support.reason.isNotBlank())
    }

    @Test
    fun sshStaysOnTheXrayEngine() {
        val ssh = ProxyProfile("ssh", "SSH", "ssh", "", "{}", host, 22)
        val support = SingBoxConfigBuilder.describe(ssh, AppSettings())
        assertFalse(support.supported)
        assertTrue(support.reason.contains("Xray"))
    }

    // ───────────────────────────────────────────────────────────────── candidate walking

    @Test
    fun onlyADocumentRefusalMovesOnToTheNextReader() {
        assertTrue(SingBoxManager.isConfigRefusal("core-config: invalid DNS field"))
        assertTrue(SingBoxManager.isConfigRefusal("core-start: exited 1: FATAL parse config"))
        assertFalse(
            "a port or memory failure would repeat for every candidate",
            SingBoxManager.isConfigRefusal("core-port: unable to allocate distinct loopback ports")
        )
        assertFalse(SingBoxManager.isConfigRefusal("core-install: sing-box extended is missing"))
    }

    @Test
    fun measurementCoresAreBoundedAndTheBuilderAgreesWithTheManager() {
        assertEquals(4, SingBoxManager.MAX_TEMPORARY_CORES)
        assertNotNull(SingBoxConfigBuilder.STRATEGY_LINK_TRANSLATED)
    }
}
