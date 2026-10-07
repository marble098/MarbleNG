package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CORE_OPTIONS_V211 — the contract of the two cores' custom options.
 *
 * Everything in [CoreOptions] is pure: settings in, one JSON object out, no policy calls. That is
 * what makes the promise of this release testable — the values below are the user's, they are
 * written verbatim, and the only thing that may refuse them is the escape hatch itself.
 */
class CoreOptionsV211Test {

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    // ── sniffing ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun destOverrideKeepsTheUsersOrderAndDropsWhatTheCoreDoesNotKnow() {
        val settings = AppSettings(xraySniffDestOverride = "quic,tls,nonsense,http")
        val array = CoreOptions.xrayDestOverride(settings, fakeIpArmed = false)
        assertEquals(listOf("quic", "tls", "http"), array.strings())
    }

    @Test
    fun anEmptyChoiceFallsBackToTheCoresOwnTriple() {
        val array = CoreOptions.xrayDestOverride(AppSettings(xraySniffDestOverride = ""), false)
        assertEquals(listOf("http", "tls", "quic"), array.strings())
    }

    @Test
    fun anArmedFakePoolAlwaysAddsFakednsOnce() {
        val settings = AppSettings(xraySniffDestOverride = "tls,fakedns")
        val armed = CoreOptions.xrayDestOverride(settings, fakeIpArmed = true).strings()
        assertEquals(listOf("tls", "fakedns"), armed)
        val alsoArmed = CoreOptions.xrayDestOverride(AppSettings(xraySniffDestOverride = "tls"), true).strings()
        assertEquals(listOf("tls", "fakedns"), alsoArmed)
    }

    @Test
    fun metadataOnlyAppearsOnlyWhenTheUserAsksForIt() {
        val plain = CoreOptions.xraySniffing(
            AppSettings(xraySniffingEnabled = true, xraySniffingRouteOnly = false),
            false
        )
        assertFalse(plain.has("metadataOnly"))
        assertEquals(true, plain.getBoolean("enabled"))
        assertEquals(false, plain.getBoolean("routeOnly"))

        val metadata = CoreOptions.xraySniffing(
            AppSettings(xraySniffingEnabled = true, xraySniffMetadataOnly = true),
            false
        )
        assertEquals(true, metadata.getBoolean("metadataOnly"))
    }

    // ── inbounds ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun socksUdpIsTheUsersBooleanAndNotAnOmission() {
        assertEquals(
            true,
            CoreOptions.xraySocksSettings(AppSettings(xraySocksUdpEnabled = true)).getBoolean("udp")
        )
        assertEquals(
            false,
            CoreOptions.xraySocksSettings(AppSettings(xraySocksUdpEnabled = false)).getBoolean("udp")
        )
    }

    // ── the socket ───────────────────────────────────────────────────────────────────────────

    @Test
    fun sockoptWritesOnlyTheValuesTheUserChanged() {
        val neutral = CoreOptions.xrayUserSockopt(AppSettings())
        assertEquals(0, neutral.length())

        val chosen = CoreOptions.xrayUserSockopt(
            AppSettings(
                xraySockoptDomainStrategy = "ForceIPv4",
                xrayTcpNoDelay = true,
                xrayTcpKeepAliveIntervalSec = 90,
                xrayTcpUserTimeoutMs = 120_000,
                xrayTcpCongestion = "bbr",
                xrayTcpMptcp = false,
                xrayTcpWindowClamp = 600_000
            )
        )
        assertEquals("ForceIPv4", chosen.getString("domainStrategy"))
        assertEquals(true, chosen.getBoolean("tcpNoDelay"))
        assertEquals(90, chosen.getInt("tcpKeepAliveInterval"))
        assertEquals(120_000, chosen.getInt("tcpUserTimeout"))
        assertEquals("bbr", chosen.getString("tcpCongestion"))
        assertEquals(false, chosen.getBoolean("tcpMptcp"))
        assertEquals(600_000, chosen.getInt("tcpWindowClamp"))
    }

    @Test
    fun applyingTheUserSockoptIsTheLastWriter() {
        val sockopt = JSONObject().put("tcpKeepAliveInterval", 22).put("tcpUserTimeout", 120_000)
        CoreOptions.applyXrayUserSockopt(sockopt, AppSettings(xrayTcpKeepAliveIntervalSec = 90))
        assertEquals("the user's number wins over the liveness profile", 90, sockopt.getInt("tcpKeepAliveInterval"))
        assertEquals("a field the user left alone survives", 120_000, sockopt.getInt("tcpUserTimeout"))
    }

    // ── routing, policy, log ─────────────────────────────────────────────────────────────────

    @Test
    fun routingStrategyIsWrittenAndABlankMatcherIsRemovedNotEmittedEmpty() {
        val routing = JSONObject().put("domainMatcher", "linear")
        CoreOptions.applyXrayRoutingStrategy(routing, AppSettings(xrayRoutingDomainStrategy = "IPIfNonMatch"))
        assertEquals("IPIfNonMatch", routing.getString("domainStrategy"))
        assertFalse("a blank matcher must leave the core's default", routing.has("domainMatcher"))

        CoreOptions.applyXrayRoutingStrategy(routing, AppSettings(xrayRoutingDomainMatcher = "hybrid"))
        assertEquals("hybrid", routing.getString("domainMatcher"))
    }

    @Test
    fun aNeutralPolicyIsAbsentRatherThanZero() {
        assertNull(CoreOptions.xrayPolicy(AppSettings()))
        val policy = CoreOptions.xrayPolicy(
            AppSettings(xrayPolicyHandshakeSec = 12, xrayPolicyBufferSizeKb = 512)
        )!!
        val level = policy.getJSONObject("levels").getJSONObject("0")
        assertEquals(12, level.getInt("handshake"))
        assertEquals(512, level.getInt("bufferSize"))
        assertFalse(level.has("connIdle"))
    }

    @Test
    fun logCarriesTheLevelAndTheDnsLogOnlyWhenAsked() {
        val plain = CoreOptions.xrayLog(AppSettings(xrayLogLevel = "warning"))
        assertEquals("warning", plain.getString("loglevel"))
        assertFalse(plain.has("dnsLog"))
        assertEquals(true, CoreOptions.xrayLog(AppSettings(xrayDnsLog = true)).getBoolean("dnsLog"))
        assertEquals("error", CoreOptions.xrayLog(AppSettings(xrayLogLevel = "not-a-level")).getString("loglevel"))
    }

    // ── the escape hatch ─────────────────────────────────────────────────────────────────────

    @Test
    fun objectsMergeKeyByKeyAndArraysReplace() {
        val target = JSONObject()
            .put("stats", JSONObject().put("enabled", false).put("keep", "mine"))
            .put("observatory", JSONObject().put("subjectSelector", JSONArray().put("proxy")))
        val merged = CoreOptions.merge(
            target,
            """
            {
              "stats": {"enabled": true},
              "observatory": {"subjectSelector": ["direct"]},
              "policy": {"levels": {"0": {"handshake": 9}}}
            }
            """.trimIndent()
        )
        assertTrue(merged.error.isBlank())
        assertTrue(merged.changed)
        assertEquals(true, target.getJSONObject("stats").getBoolean("enabled"))
        assertEquals("mine", target.getJSONObject("stats").getString("keep"))
        assertEquals(listOf("direct"), target.getJSONObject("observatory").getJSONArray("subjectSelector").strings())
        assertEquals(9, target.getJSONObject("policy").getJSONObject("levels").getJSONObject("0").getInt("handshake"))
        assertTrue("policy" in merged.applied)
    }

    @Test
    fun blankJsonIsANoOp() {
        val target = JSONObject().put("stats", JSONObject().put("enabled", true))
        val merged = CoreOptions.merge(target, "   ")
        assertFalse(merged.changed)
        assertTrue(merged.error.isBlank())
        assertEquals(1, target.length())
    }

    @Test
    fun invalidJsonIsRefusedAsAWhole() {
        val target = JSONObject().put("stats", JSONObject().put("enabled", true))
        val merged = CoreOptions.merge(target, "{stats: nope")
        assertTrue(merged.error.startsWith("Not a JSON object"))
        assertFalse(merged.changed)
        assertEquals("the document must be untouched after a refusal", 1, target.length())
    }

    @Test
    fun aProtectedKeyRefusesTheWholePatchAndSaysWhy() {
        val target = JSONObject().put("outbounds", JSONArray().put(JSONObject().put("tag", "proxy")))
        val merged = CoreOptions.merge(
            target,
            """{"outbounds": [], "stats": {"enabled": true}}""".trimIndent()
        )
        assertTrue(merged.error.contains("managed by Marble"))
        assertTrue(merged.error.contains("outbounds"))
        assertFalse(merged.changed)
        assertEquals(
            "a partially merged patch is forbidden",
            1,
            target.getJSONArray("outbounds").length()
        )
        assertFalse("nothing from the patch may survive a refusal", target.has("stats"))
    }

    @Test
    fun theProtectedSetIsTheFiveDocumentSubsystems() {
        assertEquals(
            setOf("inbounds", "outbounds", "routing", "dns", "log"),
            CoreOptions.PROTECTED_KEYS
        )
    }
}
