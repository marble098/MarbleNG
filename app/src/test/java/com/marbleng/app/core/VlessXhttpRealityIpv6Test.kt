package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProbeMethod
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

/** Synthetic reproduction of an IPv6-only VLESS/XHTTP/REALITY link copied from rendered text.
 * No production endpoint, account, short ID or public key is included in this fixture. */
class VlessXhttpRealityIpv6Test {
    private val id = "11111111-2222-3333-4444-555555555555"
    private val key = "A".repeat(43)
    private val extra = "%7B%22mode%22%3A%22auto%22%2C%22xPaddingBytes%22%3A%22100-1000%22%7D"
    private val copied = "vless://$id@[2001:db8::7]:18443?mode=auto&amp;path=%2F&amp;security=reality" +
        "&amp;encryption=none&amp;extra=$extra&amp;pbk=$key&amp;fp=chrome&amp;spx=%2Fdecoy" +
        "&amp;type=xhttp&amp;sni=[example.org](http://example.org)&amp;sid=abcd" +
        "#%F0%9F%87%A9%F0%9F%87%AA%20Germany%203"

    private fun imported() = ProxyParser.vlessFromParts(copied, "[2001:db8::7]", 18443, id, "🇩🇪 Germany 3")
    private fun proxy(config: String): JSONObject = JSONObject(config).getJSONArray("outbounds").getJSONObject(0)

    @Test fun renderedTextIsNormalisedOnlyAtQueryBoundaries() {
        val clean = ShareLinkNormalizer.normalize(copied)
        assertTrue(clean.contains("?mode=auto&path=%2F&security=reality"))
        assertTrue(clean.contains("extra=$extra"))
        assertTrue(clean.contains("&sni=example.org&sid=abcd#"))
        assertFalse(clean.contains("&amp;"))
        assertEquals(clean, ShareLinkNormalizer.normalize(clean))
        assertEquals("reality", ShareLinkParams.ofRawLink(copied).get("security"))
        assertEquals("example.org", ShareLinkParams.ofRawLink(copied).get("sni"))
        assertEquals("abcd", ShareLinkParams.ofRawLink(copied).get("sid"))
        assertEquals(key, ShareLinkParams.ofRawLink(copied).get("pbk"))
    }

    @Test fun doNotReinterpretKeysFragmentsOrAmbiguousMarkdown() {
        val fragment = "vless://id@host:443?pbk=AB+%2F&amp;path=%2F%26amp%3Bsecret#Tom&amp;Jerry"
        val clean = ShareLinkNormalizer.normalize(fragment)
        assertEquals("vless://id@host:443?pbk=AB+%2F&path=%2F%26amp%3Bsecret#Tom&amp;Jerry", clean)
        assertEquals("AB+/", ShareLinkParams.ofRawLink(clean).get("pbk"))
        assertEquals("/&amp;secret", ShareLinkParams.ofRawLink(clean).get("path"))
        // A hyperlink to *another* host is not authority to replace the operator's SNI.
        val mismatch = "vless://id@host:443?SNI=[example.org](https://other.example/)"
        assertEquals(mismatch, ShareLinkNormalizer.normalize(mismatch))
        assertEquals("vless://id@host:443#Node?security=reality&sni=example.org",
            ShareLinkNormalizer.normalize("vless://id@host:443#Node?security=reality&amp;sni=[example.org](http://example.org)"))
    }

    @Test fun importedWireContainsBareIpv6EndpointAndExactRealityAndXhttpSettings() {
        val profile = imported()
        assertEquals("2001:db8::7", profile.host)
        assertEquals("🇩🇪 Germany 3", profile.name)
        assertEquals("xhttp", profile.transport)
        assertEquals("reality", profile.security)
        assertFalse(profile.raw.contains("&amp;"))
        val outbound = proxy(profile.configJson)
        val settings = outbound.getJSONObject("settings")
        assertEquals(id, settings.getString("id"))
        assertEquals("2001:db8::7", settings.getString("address"))
        assertEquals(18443, settings.getInt("port"))
        assertEquals("none", settings.getString("encryption"))
        val stream = outbound.getJSONObject("streamSettings")
        assertEquals("xhttp", stream.getString("method"))
        assertEquals("reality", stream.getString("security"))
        assertEquals("/", stream.getJSONObject("xhttpSettings").getString("path"))
        assertEquals("100-1000", stream.getJSONObject("xhttpSettings").getJSONObject("extra").getString("xPaddingBytes"))
        val reality = stream.getJSONObject("realitySettings")
        assertEquals("example.org", reality.getString("serverName"))
        assertEquals("chrome", reality.getString("fingerprint"))
        assertEquals(key, reality.getString("password"))
        assertEquals("abcd", reality.getString("shortId"))
        assertEquals("/decoy", reality.getString("spiderX"))
        assertEquals(CoreConfigSuperset.Wire.ENCRYPTED, CoreConfigSuperset.wire(profile))
        assertTrue(ProfilePreflightValidator.validate(profile).detail, ProfilePreflightValidator.validate(profile).valid)
    }

    @Test fun xhttpReadersKeepExtraAndSkipTheCoreParserThatDropsIt() {
        val profile = imported()
        val old = SingBoxConfigBuilder.linkJson
        SingBoxConfigBuilder.linkJson = { JSONObject(profile.configJson) }
        try {
            // Even with the parser preference OFF, a current link must beat a stale cached
            // outbound: the core's parser ignores JSON extra and a cached outbound may have lost
            // REALITY while still passing `sing-box check`.
            listOf(true, false).forEach { preferParser ->
                val builds = SingBoxConfigBuilder.candidateBuilds(profile, AppSettings(
                    singBoxPreferParser = preferParser), 10808, 39090, "test", "", "cache.db")
                assertEquals(SingBoxConfigBuilder.STRATEGY_LINK_TRANSLATED, builds.first().strategy)
                assertTrue(builds.none { it.strategy == SingBoxConfigBuilder.STRATEGY_LINK })
                val outbound = JSONObject(builds.first().json).getJSONArray("outbounds").getJSONObject(0)
                assertEquals("2001:db8::7", outbound.getString("server"))
                assertEquals("vless", outbound.getString("type"))
                assertEquals("example.org", outbound.getJSONObject("tls").getString("server_name"))
                assertEquals(key, outbound.getJSONObject("tls").getJSONObject("reality").getString("public_key"))
                assertEquals("abcd", outbound.getJSONObject("tls").getJSONObject("reality").getString("short_id"))
                assertEquals("100-1000", outbound.getJSONObject("transport").getString("x_padding_bytes"))
                assertEquals("/", outbound.getJSONObject("transport").getString("path"))
            }
        } finally { SingBoxConfigBuilder.linkJson = old }
    }

    @Test fun base64ExtraAndTopLevelTuningArePreservedNotSilentlyOverwritten() {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"mode":"auto","xPaddingBytes":"100-1000","xPaddingObfsMode":true}""".toByteArray())
        val link = "vless://$id@[2001:db8::7]:18443?type=xhttp&security=reality&extra=$encoded&pbk=$key&sni=example.org"
        val xhttp = proxy(ProxyParser.vlessFromParts(link, "2001:db8::7", 18443, id, null).configJson)
            .getJSONObject("streamSettings").getJSONObject("xhttpSettings")
        assertEquals("100-1000", xhttp.getJSONObject("extra").getString("xPaddingBytes"))
        val merged = JSONObject().put("method", "xhttp").put("xhttpSettings", JSONObject()
            .put("mode", "stream-up").put("path", "/main")
            .put("xPaddingBytes", "200-300").put("headers", JSONObject().put("X-Test", "present"))
            .put("extra", JSONObject().put("mode", "auto").put("xPaddingBytes", "100-1000")
                .put("xPaddingObfsMode", true)))
        val translated = SingBoxTransportTranslator.transport(merged, AppSettings(), mutableListOf())!!
        assertEquals("stream-up", translated.getString("mode"))
        assertEquals("200-300", translated.getString("x_padding_bytes"))
        assertEquals("present", translated.getJSONObject("headers").getString("X-Test"))
        assertTrue(translated.getBoolean("x_padding_obfs_mode"))
        assertThrows(Exception::class.java) { XhttpExtra.parse("not-base64-json") }
    }

    @Test fun aCachedNodeOnTheSameAddressIsRepairedWhenItsWireFieldsAreWrong() {
        val current = imported()
        assertTrue(LinkWireParity.matches(current, current))
        val logOnly = JSONObject(current.configJson).put("log", JSONObject().put("loglevel", "debug"))
        assertTrue(LinkWireParity.matches(current.copy(configJson = logOnly.toString()), current))

        fun changed(change: (JSONObject) -> Unit) {
            val root = JSONObject(current.configJson)
            change(root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("streamSettings"))
            assertFalse(LinkWireParity.matches(current.copy(configJson = root.toString()), current))
        }
        changed { it.put("security", "none").remove("realitySettings") }
        changed { it.getJSONObject("realitySettings").put("serverName", "[example.org](http://example.org)") }
        changed { it.getJSONObject("realitySettings").remove("password") }
        changed { it.getJSONObject("xhttpSettings").getJSONObject("extra").remove("xPaddingBytes") }
        changed { it.getJSONObject("xhttpSettings").put("path", "/wrong") }
    }

    @Test fun ipv6OnlyNodesExplainDisabledFamilyInsteadOfCallingTheServerDead() {
        val profile = imported()
        val disabled = AppSettings(ipv6Enabled = false)
        val verdict = ProfilePreflightValidator.validate(profile, settings = disabled)
        assertFalse(verdict.valid)
        assertEquals("ipv6-disabled", verdict.reason)
        assertEquals(AddressFamilyPolicy.IPV6_LITERAL_DISABLED, verdict.detail)
        val ping = RouteProbe.measureUnified(profile, ProbeMethod.TCP_PING, settings = disabled)
        assertEquals("ipv6-disabled", ping.failureReason)
        assertEquals(0, ping.successPercent)
        assertEquals("ipv6-disabled", RouteProbe.measureUnified(profile, ProbeMethod.REAL_DELAY, settings = disabled).failureReason)
        assertTrue(ProfilePreflightValidator.validate(profile, settings = AppSettings()).valid)
        assertFalse(AddressFamilyPolicy.excludedIpv6Endpoint("example.org", disabled))
        assertTrue(AddressFamilyPolicy.excludedIpv6Endpoint("[2001:db8::7]", disabled))
    }
}
