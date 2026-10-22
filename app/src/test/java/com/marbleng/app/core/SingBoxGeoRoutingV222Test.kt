package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * MARBLE_SINGBOX_GEO_ROUTING_V222 — the sing-box engine's geo routing contract.
 *
 * Before this chapter the builder mapped four hard-coded tags and dropped the rest, so every
 * preset except the Iranian one and every country rule a user could add routed through the proxy
 * on sing-box while the same profile routed correctly on Xray. These tests construct real
 * protobuf .dat files (the same wire format Chocolate4U and v2fly publish), run the production
 * index and the production materialiser over them, and then assert on the finished config the
 * core would actually be given: a rule set per tag, its file on disk in sing-box source format,
 * and the exact rule that references it.
 */
class SingBoxGeoRoutingV222Test {
    @get:Rule
    val temporary = TemporaryFolder()

    private val uuid = "11111111-2222-3333-4444-555555555555"

    private fun profile() = ProxyProfile(
        id = "node-1",
        name = "Node A",
        scheme = "vless",
        raw = "vless://$uuid@198.51.100.7:443?encryption=none&security=tls#Node A",
        configJson = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("protocol", "vless")
                        .put("tag", "proxy")
                        .put(
                            "settings",
                            JSONObject()
                                .put("address", "198.51.100.7")
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
            .toString(),
        host = "198.51.100.7",
        port = 443
    )

    private fun settings(
        mode: RoutingMode = RoutingMode.GEO_DIRECT,
        ipTags: String = "ir,private",
        siteTags: String = "ir",
        directDomains: String = "",
        rules: String = ""
    ) = AppSettings(
        coreEngineId = "singbox",
        routingMode = mode,
        routeGeoIpTags = ipTags,
        routeGeoSiteTags = siteTags,
        routeDirectDomains = directDomains,
        routeBlockDomains = "",
        routeBypassPrivate = true,
        routeBlockAds = true,
        routingRulesJson = rules,
        customRoutingEnabled = true,
        singBoxUnifiedDelay = false
    )

    // ------------------------------------------------------------------------------------------
    // protobuf fixture writer — the .dat wire format, byte for byte
    // ------------------------------------------------------------------------------------------

    private fun varint(value: Long): ByteArray {
        var remaining = value
        val out = ByteArrayOutputStream()
        while (true) {
            val byte = (remaining and 0x7F).toInt()
            remaining = remaining ushr 7
            if (remaining == 0L) {
                out.write(byte)
                break
            }
            out.write(byte or 0x80)
        }
        return out.toByteArray()
    }

    private fun field(number: Int, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(varint((number.toLong() shl 3) or 2L))
        out.write(varint(payload.size.toLong()))
        out.write(payload)
        return out.toByteArray()
    }

    private fun intField(number: Int, value: Long): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(varint((number.toLong() shl 3) or 0L))
        out.write(varint(value))
        return out.toByteArray()
    }

    private fun concat(parts: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach(out::write)
        return out.toByteArray()
    }

    private fun domain(type: Int, value: String) = concat(
        listOf(intField(1, type.toLong()), field(2, value.toByteArray()))
    )

    private fun siteEntry(code: String, domains: List<ByteArray>) = concat(
        listOf(field(1, code.toByteArray())) + domains.map { field(2, it) }
    )

    private fun cidr(ip: ByteArray, prefix: Long) = concat(
        listOf(field(1, ip), intField(2, prefix))
    )

    private fun ipEntry(code: String, ranges: List<ByteArray>, reverse: Boolean = false) = concat(
        listOf(field(1, code.toByteArray())) +
            ranges.map { field(2, it) } +
            (if (reverse) listOf(intField(3, 1L)) else emptyList())
    )

    private fun geositeFile(vararg entries: ByteArray): File =
        temporary.newFile("geosite-${entries.size}-${System.nanoTime()}.dat").apply {
            writeBytes(concat(entries.map { field(1, it) }))
        }

    private fun geoipFile(vararg entries: ByteArray): File =
        temporary.newFile("geoip-${entries.size}-${System.nanoTime()}.dat").apply {
            writeBytes(concat(entries.map { field(1, it) }))
        }

    private fun index(site: File, ip: File): GeoAssetIndex.Snapshot {
        GeoAssetIndex.resetForTests()
        val directory = temporary.newFolder("assets-${System.nanoTime()}")
        val siteCopy = File(directory, "geosite.dat").apply { writeBytes(site.readBytes()) }
        val ipCopy = File(directory, "geoip.dat").apply { writeBytes(ip.readBytes()) }
        val snapshot = GeoAssetIndex.update(
            directory,
            listOf(
                GeoAssetIndex.IndexedFile(siteCopy, GeoAssetIndex.Kind.GEOSITE),
                GeoAssetIndex.IndexedFile(ipCopy, GeoAssetIndex.Kind.GEOIP)
            )
        )
        assertNotNull("the synthetic .dat index must parse", snapshot)
        return snapshot!!
    }

    private fun fixtureSite() = geositeFile(
        siteEntry(
            "cn",
            listOf(
                domain(3, "example.cn"),
                domain(2, "test-cn"),
                domain(0, "cn-keyword"),
                domain(1, "^regex-cn\\.org$")
            )
        ),
        siteEntry("ir", listOf(domain(2, "ir-domain"))),
        siteEntry("category-bank-ir", listOf(domain(2, "bank.example.ir")))
    )

    private fun fixtureIp() = geoipFile(
        ipEntry(
            "cn",
            listOf(
                cidr(byteArrayOf(1, 2, 3, 0), 24),
                cidr(
                    byteArrayOf(
                        0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0
                    ),
                    32
                )
            )
        ),
        ipEntry("ir", listOf(cidr(byteArrayOf(5, 6, 7, 0), 24)))
    )

    // ------------------------------------------------------------------------------------------
    // extraction
    // ------------------------------------------------------------------------------------------

    @Test
    fun datPayloadsBecomeSingBoxRuleSetItemsOfTheSameKind() {
        val site = fixtureSite()
        val ip = fixtureIp()

        val cn = GeoAssetIndex.extractPayload(GeoAssetIndex.Kind.GEOSITE, "cn", listOf(site))
        assertNotNull(cn)
        assertEquals(listOf("example.cn"), cn!!.full)
        assertEquals(listOf("test-cn"), cn.suffix)
        assertEquals(listOf("cn-keyword"), cn.keywords)
        assertEquals(listOf("^regex-cn\\.org$"), cn.regexes)
        assertFalse(cn.truncated)

        val cnIp = GeoAssetIndex.extractPayload(GeoAssetIndex.Kind.GEOIP, "geoip:cn", listOf(ip))
        assertNotNull(cnIp)
        assertEquals(listOf("1.2.3.0/24", "2001:db8::/32"), cnIp!!.cidrs)

        val missing = GeoAssetIndex.extractPayload(GeoAssetIndex.Kind.GEOSITE, "ru", listOf(site))
        assertNotNull(missing)
        assertTrue(missing!!.isEmpty)
    }

    @Test
    fun everyReadableSourceContributesToTheSameTag() {
        val primary = geositeFile(siteEntry("cn", listOf(domain(2, "a-cn"))))
        val secondary = geositeFile(siteEntry("cn", listOf(domain(2, "b-cn"))))
        val payload = GeoAssetIndex.extractPayload(
            GeoAssetIndex.Kind.GEOSITE,
            "cn",
            listOf(primary, secondary)
        )
        assertEquals(listOf("a-cn", "b-cn"), payload!!.suffix)
    }

    // ------------------------------------------------------------------------------------------
    // the materialised plan
    // ------------------------------------------------------------------------------------------

    @Test
    fun everyRequestedTagGetsASourceRuleSetAndIsReusedOnTheNextStart() {
        val snapshot = index(fixtureSite(), fixtureIp())
        val out = temporary.newFolder("geo")
        val requests = listOf(
            SingBoxGeoRuleSets.Request(GeoAssetIndex.Kind.GEOIP, "cn"),
            SingBoxGeoRuleSets.Request(GeoAssetIndex.Kind.GEOSITE, "cn"),
            SingBoxGeoRuleSets.Request(GeoAssetIndex.Kind.GEOSITE, "category-bank-ir")
        )

        val plan = SingBoxGeoRuleSets.plan(requests, emptyMap(), snapshot, out)
        assertEquals(3, plan.rules)
        assertEquals(3, plan.generated)
        assertTrue(plan.notes.isEmpty())
        assertTrue(plan.paths.containsKey("geoip-cn"))
        assertTrue(plan.paths.containsKey("geosite-cn"))
        assertTrue(plan.paths.containsKey("geosite-category-bank-ir"))

        val file = File(plan.paths.getValue("geosite-cn"))
        assertTrue(file.isAbsolute)
        val payload = JSONObject(file.readText())
        assertEquals(3, payload.getInt("version"))
        val rules = payload.getJSONArray("rules")
        val domains = rules.getJSONObject(0)
        assertEquals("example.cn", domains.getJSONArray("domain").getString(0))
        assertEquals("test-cn", domains.getJSONArray("domain_suffix").getString(0))
        assertEquals("cn-keyword", domains.getJSONArray("domain_keyword").getString(0))
        assertEquals("^regex-cn\\.org$", domains.getJSONArray("domain_regex").getString(0))
        assertEquals("1.2.3.0/24", JSONObject(File(plan.paths.getValue("geoip-cn")).readText())
            .getJSONArray("rules").getJSONObject(0).getJSONArray("ip_cidr").getString(0))

        val again = SingBoxGeoRuleSets.plan(requests, emptyMap(), snapshot, out)
        assertEquals(0, again.generated)
        assertEquals(3, again.reused)
        assertEquals(plan.paths, again.paths)
    }

    @Test
    fun aBundledRuleSetAlwaysWinsOverAGeneratedOne() {
        val snapshot = index(fixtureSite(), fixtureIp())
        val out = temporary.newFolder("geo-bundled")
        val bundled = mapOf("geosite-ir" to "/data/app/shared/geosite-ir.srs")
        val plan = SingBoxGeoRuleSets.plan(
            listOf(SingBoxGeoRuleSets.Request(GeoAssetIndex.Kind.GEOSITE, "ir")),
            bundled,
            snapshot,
            out
        )
        assertFalse(plan.paths.containsKey("geosite-ir"))
        assertEquals(0, plan.generated)
    }

    @Test
    fun aReverseMatchTagIsReportedInsteadOfInvertedSilently() {
        val site = fixtureSite()
        val reverseIp = geoipFile(ipEntry("ir", listOf(cidr(byteArrayOf(5, 6, 7, 0), 24)), reverse = true))
        val snapshot = index(site, reverseIp)
        val out = temporary.newFolder("geo-reverse")
        val plan = SingBoxGeoRuleSets.plan(
            listOf(SingBoxGeoRuleSets.Request(GeoAssetIndex.Kind.GEOIP, "ir")),
            emptyMap(),
            snapshot,
            out
        )
        assertEquals(0, plan.generated)
        assertEquals(1, plan.skipped)
        assertTrue(plan.notes.any { it.contains("reverse-match") })
    }

    @Test
    fun anAbsentTagIsReportedAndNeverInvented() {
        val snapshot = index(fixtureSite(), fixtureIp())
        val out = temporary.newFolder("geo-missing")
        val plan = SingBoxGeoRuleSets.plan(
            listOf(SingBoxGeoRuleSets.Request(GeoAssetIndex.Kind.GEOSITE, "ru")),
            emptyMap(),
            snapshot,
            out
        )
        assertEquals(0, plan.rules)
        assertEquals(1, plan.skipped)
        assertTrue(plan.notes.isEmpty())
    }

    // ------------------------------------------------------------------------------------------
    // requests: everything the UI can configure
    // ------------------------------------------------------------------------------------------

    @Test
    fun requestsCoverPresetsRulesListsAndTheAdsTag() {
        val preset = AppSettings(
            coreEngineId = "singbox",
            routingMode = RoutingMode.CUSTOM,
            customRoutingEnabled = true,
            routingRulesJson = RoutingEngine.serializeRules(RoutingPresets.materialize(RoutingPresets.Preset.RUSSIA_DIRECT)),
            routeDirectDomains = "geosite:google,example.org",
            routeBlockIps = "geoip:cn"
        )
        val ids = SingBoxGeoRuleSets.requests(preset).map { it.id }
        assertTrue(ids.contains("geoip-ru"))
        assertTrue(ids.contains("geosite-ru"))
        assertTrue(ids.contains("geosite-google"))
        assertTrue(ids.contains("geoip-cn"))
        assertFalse("a plain domain is not a geo tag", ids.contains("geosite-example.org"))
    }

    @Test
    fun canonicalTagsAgreeWithEverySpellingTheUiAccepts() {
        assertEquals("cn", SingBoxGeoRuleSets.canonicalTag(" CN "))
        assertEquals("cn", SingBoxGeoRuleSets.canonicalTag("geosite:cn"))
        assertEquals("cn", SingBoxGeoRuleSets.canonicalTag("geoip:cn"))
        assertEquals("cn", SingBoxGeoRuleSets.canonicalTag("ext-site:geosite-loyalsoldier.dat:cn"))
        assertEquals("cn", SingBoxGeoRuleSets.canonicalTag("ext-ip:geoip-cn.dat:cn"))
        assertEquals("category-ads-all", SingBoxGeoRuleSets.canonicalTag("ads"))
        assertEquals("category-ads-all", SingBoxGeoRuleSets.canonicalTag("category-ads-all"))
        assertEquals("ir", SingBoxGeoRuleSets.canonicalTag("geosite-ir"))
        assertEquals("geosite-category-ads-all", SingBoxGeoRuleSets.idFor(GeoAssetIndex.Kind.GEOSITE, "ads"))
    }

    // ------------------------------------------------------------------------------------------
    // the finished config
    // ------------------------------------------------------------------------------------------

    private fun config(
        settings: AppSettings,
        paths: Map<String, String>
    ): JSONObject = JSONObject(
        SingBoxConfigBuilder.build(
            profile = profile(),
            settings = settings,
            socksPort = 10808,
            apiPort = 39090,
            apiSecret = "secret",
            logPath = "",
            cachePath = "cache.db",
            ruleSetPaths = paths
        ).json
    )

    @Test
    fun theConfigReferencesEveryGeneratedSetWithItsOwnFormatAndPath() {
        val snapshot = index(fixtureSite(), fixtureIp())
        val out = temporary.newFolder("geo-config")
        val node = settings(
            mode = RoutingMode.CUSTOM,
            ipTags = "ir,private",
            siteTags = "ir",
            rules = RoutingEngine.serializeRules(
                listOf(
                    com.marbleng.app.model.RoutingRule(
                        id = "cn-direct",
                        kind = com.marbleng.app.model.RoutingRuleKind.GEOSITE,
                        matcher = "cn",
                        outbound = com.marbleng.app.model.RoutingOutbound.DIRECT
                    ),
                    com.marbleng.app.model.RoutingRule(
                        id = "cn-ip-direct",
                        kind = com.marbleng.app.model.RoutingRuleKind.GEOIP,
                        matcher = "cn",
                        outbound = com.marbleng.app.model.RoutingOutbound.DIRECT
                    )
                )
            )
        ).copy(routeDirectIps = "geoip:ir")
        val bundled = mapOf(
            "geoip-ir" to "/data/app/geop-ip-ir.srs",
            "geosite-ir" to "/data/app/geosite-ir.srs",
            "geosite-category-ads-all" to "/data/app/geosite-ads.srs"
        )
        val plan = SingBoxGeoRuleSets.plan(
            SingBoxGeoRuleSets.requests(node),
            bundled,
            snapshot,
            out
        )
        val paths = LinkedHashMap<String, String>().apply {
            putAll(bundled)
            putAll(plan.paths)
        }

        val root = config(node, paths)
        val route = root.getJSONObject("route")
        val definitions = route.getJSONArray("rule_set")
        val byTag = (0 until definitions.length()).map { definitions.getJSONObject(it) }
            .associateBy { it.getString("tag") }

        val cnDomains = byTag.getValue("geosite-cn")
        assertEquals("source", cnDomains.getString("format"))
        assertEquals(plan.paths.getValue("geosite-cn"), cnDomains.getString("path"))
        val cnIps = byTag.getValue("geoip-cn")
        assertEquals("source", cnIps.getString("format"))
        assertEquals("binary", byTag.getValue("geoip-ir").getString("format"))

        val rules = route.getJSONArray("rules")
        val text = (0 until rules.length()).joinToString("|") { rules.getJSONObject(it).toString() }
        assertTrue("the geosite rule must reference the generated set", text.contains("\"rule_set\":[\"geosite-cn\"]"))
        assertTrue("the geoip rule must reference the generated set", text.contains("\"rule_set\":[\"geoip-cn\"]"))
        assertFalse(
            "no rule may reference a rule set the document does not define",
            Regex("\"rule_set\":\\[\"([a-z0-9-]+)\"\\]").findAll(text)
                .map { it.groupValues[1] }
                .any { it !in byTag }
        )
        assertTrue(
            "every defined rule set is either a bundled file or a generated one",
            byTag.keys.all { it in bundled || it in plan.paths }
        )
    }

    @Test
    fun dnsRulesNeverCarryAnUndefinedOrNullRuleSet() {
        val settings = settings(
            mode = RoutingMode.GEO_DIRECT,
            ipTags = "ir",
            siteTags = "cn,ir"
        )
        val root = config(
            settings,
            mapOf("geosite-ir" to "/data/app/geosite-ir.srs", "geoip-ir" to "/data/app/geoip-ir.srs")
        )
        val dnsRules = root.getJSONObject("dns").getJSONArray("rules")
        val routeSets = root.getJSONObject("route").getJSONArray("rule_set")
        val defined = (0 until routeSets.length()).map { routeSets.getJSONObject(it).getString("tag") }.toSet()
        for (index in 0 until dnsRules.length()) {
            val rule = dnsRules.getJSONObject(index)
            if (!rule.has("rule_set")) continue
            val array = rule.getJSONArray("rule_set")
            for (slot in 0 until array.length()) {
                val value = array.opt(slot)
                assertFalse("a DNS rule may not carry a null rule set", value == JSONObject.NULL || value == null)
                assertTrue("DNS references $value, which is not defined", value.toString() in defined)
            }
        }
    }

    @Test
    fun geoipTokensInsideTheIpListRouteInsteadOfBecomingLiteralAddresses() {
        val settings = settings(mode = RoutingMode.CUSTOM).copy(routeBlockIps = "geoip:cn,203.0.113.0/24")
        val bundled = mapOf("geoip-ir" to "/data/app/geoip-ir.srs")
        val plan = SingBoxGeoRuleSets.plan(
            SingBoxGeoRuleSets.requests(settings),
            bundled,
            index(fixtureSite(), fixtureIp()),
            temporary.newFolder("geo-tokens")
        )
        val paths = LinkedHashMap<String, String>().apply {
            putAll(bundled)
            putAll(plan.paths)
        }
        val route = config(settings, paths).getJSONObject("route")
        val rules = route.getJSONArray("rules")
        val text = (0 until rules.length()).joinToString("|") { rules.getJSONObject(it).toString() }
        assertTrue(text.contains("\"rule_set\":[\"geoip-cn\"]"))
        assertTrue(text.contains("203.0.113.0/24"))
        assertFalse("geoip:cn must not be emitted as a literal CIDR", text.contains("\"geoip:cn\""))
    }
}
