package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * MARBLE_REALITY_MLKEM_HANDSHAKE_V194 — the VLESS/REALITY node that connected in v2rayNG and
 * failed 55/55 with `reality verification failed` here.
 *
 * The report blamed the public-key parsing (a standard-base64 decode of a URL-safe key), but the
 * keys travel verbatim from the link to both cores — no base64 decode of `pbk` exists anywhere on
 * that path, and these tests pin that down with a key cut from the exact alphabet of the report
 * (`-` and `_`, no padding). The actual fault is the handshake shape: Xray >= v26.9.8 REALITY
 * servers require the X25519MLKEM768 key share, which the pinned sing-box core strips unless its
 * reality block sets `support_x25519mlkem768` — and neither its link parser nor MarbleNG's
 * translator used to set it.
 *
 * Synthetic fixtures only: no production endpoint, account, short ID or public key. Every byte
 * below is invented for the fixture; the *shape* (IPv6 literal, `&amp;` separators from rendered
 * text, XHTTP extra, spiderX) mirrors the reported link.
 */
class RealityMlkemHandshakeTest {
    private val uuid = "123e4567-e89b-12d3-a456-426614174000"

    /** 43-char raw-URL-base64 X25519 key *shape* (`-`/`_`, unpadded), fully synthetic. */
    private val key = "zK9-xQ2_mT7-bN4_pL8-dR3_fH6-jW1-cY5-aS0-eV9"
    private val sid = "0123456789abcdef"
    private val spx = "/9f8e7d6c5b4a"
    private val extra = "%7B%22mode%22%3A%22auto%22%2C%22xPaddingBytes%22%3A%22100-1000%22%7D"

    private val xhttpLink = "vless://$uuid@[2001:db8::a]:31778?mode=auto&amp;path=%2F&amp;security=reality" +
        "&amp;encryption=none&amp;extra=$extra&amp;pbk=$key&amp;fp=chrome&amp;spx=%2F9f8e7d6c5b4a" +
        "&amp;type=xhttp&amp;sni=cdn-example.net&amp;sid=$sid#Germany%203"

    private val tcpLink = "vless://$uuid@198.51.100.7:443?security=reality&type=tcp" +
        "&pbk=$key&sid=$sid&sni=cdn-example.net&fp=chrome#Tcp"

    private val impliedLink = "vless://$uuid@198.51.100.7:443?type=tcp" +
        "&pbk=$key&sid=$sid&sni=cdn-example.net&fp=chrome#Implied"

    private val tlsLink = "vless://$uuid@198.51.100.7:443?security=tls&type=tcp&sni=example.com#Normal"

    private fun xhttpProfile() = ProxyParser.vlessFromParts(xhttpLink, "[2001:db8::a]", 31778, uuid, "Germany 3")
    private fun tcpProfile() = ProxyParser.vlessFromParts(tcpLink, "198.51.100.7", 443, uuid, "Tcp")

    private fun streamOf(profile: ProxyProfile): JSONObject =
        JSONObject(profile.configJson).getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings")

    private fun <T> withLinkReader(profile: ProxyProfile, block: () -> T): T {
        val previous = SingBoxConfigBuilder.linkJson
        SingBoxConfigBuilder.linkJson = { JSONObject(profile.configJson) }
        return try {
            block()
        } finally {
            SingBoxConfigBuilder.linkJson = previous
        }
    }

    // ── the keys are innocent: verbatim from the link to both cores ──────────────────────────

    @Test fun urlSafeKeyAlphabetSurvivesImportByteIdentical() {
        assertEquals("fixture must be a 43-char unpadded key", 43, key.length)
        assertTrue(key.contains('-') && key.contains('_'))
        val params = ShareLinkParams.ofRawLink(xhttpLink)
        assertEquals(key, params.get("pbk"))
        assertEquals(sid, params.get("sid"))
        assertEquals(spx, params.get("spx"))
        assertEquals("cdn-example.net", params.get("sni"))
        assertEquals("chrome", params.get("fp"))

        val reality = streamOf(xhttpProfile()).getJSONObject("realitySettings")
        assertEquals("cdn-example.net", reality.getString("serverName"))
        assertEquals("chrome", reality.getString("fingerprint"))
        assertEquals(key, reality.getString("password"))
        assertEquals(sid, reality.getString("shortId"))
        assertEquals(spx, reality.getString("spiderX"))
    }

    @Test fun singboxTranslationEmitsMlkemFlagWithVerbatimKeys() {
        val tls = SingBoxTransportTranslator.tls(streamOf(xhttpProfile()), AppSettings(), mutableListOf())!!
        assertEquals("cdn-example.net", tls.getString("server_name"))
        assertEquals("chrome", tls.getJSONObject("utls").getString("fingerprint"))
        val reality = tls.getJSONObject("reality")
        assertTrue(reality.getBoolean("enabled"))
        assertEquals(key, reality.getString("public_key"))
        assertEquals(sid, reality.getString("short_id"))
        assertTrue(
            "translated REALITY must carry the hybrid-share flag current Xray servers require",
            reality.getBoolean("support_x25519mlkem768")
        )
    }

    // ── the core's link parser can never emit the flag, so REALITY skips it ──────────────────

    @Test fun realityLinksSkipTheCoreParserOnBothTransports() {
        listOf(xhttpProfile() to "xhttp", tcpProfile() to "tcp").forEach { (profile, label) ->
            withLinkReader(profile) {
                listOf(true, false).forEach { preferParser ->
                    val builds = SingBoxConfigBuilder.candidateBuilds(
                        profile, AppSettings(singBoxPreferParser = preferParser),
                        10808, 39090, "test", "", "cache.db"
                    )
                    assertTrue(
                        "$label REALITY must never be handed to the core parser",
                        builds.none { it.strategy == SingBoxConfigBuilder.STRATEGY_LINK }
                    )
                    val reality = JSONObject(builds.first().json).getJSONArray("outbounds")
                        .getJSONObject(0).getJSONObject("tls").getJSONObject("reality")
                    assertEquals(key, reality.getString("public_key"))
                    assertTrue(reality.getBoolean("support_x25519mlkem768"))
                }
            }
        }
    }

    @Test fun plainTlsLinksStillReachTheCoreParser() {
        val profile = ProxyParser.vlessFromParts(tlsLink, "198.51.100.7", 443, uuid, "Normal")
        withLinkReader(profile) {
            val builds = SingBoxConfigBuilder.candidateBuilds(
                profile, AppSettings(singBoxPreferParser = true), 10808, 39090, "test", "", "cache.db"
            )
            assertEquals(SingBoxConfigBuilder.STRATEGY_LINK, builds.first().strategy)
            assertEquals("parser", JSONObject(builds.first().json).getJSONArray("outbounds")
                .getJSONObject(0).getString("type"))
        }
    }

    @Test fun linkCarriesRealityMirrorsParserInference() {
        assertTrue(SingBoxConfigBuilder.linkCarriesReality(tcpLink))
        assertTrue(SingBoxConfigBuilder.linkCarriesReality(impliedLink))
        assertTrue(SingBoxConfigBuilder.linkCarriesReality("vless://id@h:443?Security=REALITY#n"))
        assertFalse(SingBoxConfigBuilder.linkCarriesReality(tlsLink))
        assertFalse(SingBoxConfigBuilder.linkCarriesReality("vless://id@h:443?security=none&type=tcp#n"))
        assertFalse(SingBoxConfigBuilder.linkCarriesReality("trojan://p@h:443?security=tls#n"))
        // The importer and the candidate set must agree: a `pbk` turns the stored security into
        // reality, and the stored security is what preflight and rank read.
        assertEquals(
            "reality",
            ProxyParser.vlessFromParts(impliedLink, "198.51.100.7", 443, uuid, null).security
        )
    }

    // ── residual rejections are named, not retried blindly ───────────────────────────────────

    private val singboxLine =
        "ERROR connection: open connection to 203.0.113.9:443 using outbound/vless[proxy]: reality verification failed"

    @Test fun policyCountsBothMarkersWithoutFalsePositives() {
        val noise = listOf(
            "INFO inbound/mixed[socks-in]: inbound connection from 127.0.0.1:5001",
            "ERROR connection: TLS handshake timeout",
            "WARN app/dns: exchange failed for www.example.com"
        ).joinToString("\n")
        assertTrue(RealityHandshakePolicy.scan(noise).empty)

        val evidence = RealityHandshakePolicy.scan(
            listOf(singboxLine, singboxLine.uppercase(), noise,
                "REALITY: received real certificate (potential MITM or redirection)").joinToString("\n")
        )
        assertEquals(2, evidence.singboxFailures)
        assertEquals(1, evidence.xrayFallbacks)
        assertEquals(3, evidence.total)
        assertTrue(RealityHandshakePolicy.isSingboxVerificationFailure(singboxLine))
        assertTrue(RealityHandshakePolicy.isXrayRealityFallback("REALITY: processed invalid connection"))
        assertFalse(RealityHandshakePolicy.isSingboxVerificationFailure("REALITY: received real certificate"))
    }

    @Test fun policyCheckEscalatesBurstsAndNamesTheFingerprintCause() {
        assertNull(RealityHandshakePolicy.check(RealityHandshakePolicy.Evidence(0, 0), null))

        fun profileWith(fingerprint: String) = ProxyProfile(
            "p", "P", "vless", tcpLink,
            """{"outbounds":[{"protocol":"vless","settings":{},"streamSettings":{"security":"reality","realitySettings":{"serverName":"cdn-example.net","fingerprint":"$fingerprint"}}}]}""",
            "198.51.100.7", 443, "tcp", "reality"
        )
        assertEquals("firefox", RealityHandshakePolicy.fingerprintOf(profileWith("firefox")))
        assertEquals("", RealityHandshakePolicy.fingerprintOf(null))
        assertEquals("", RealityHandshakePolicy.fingerprintOf(profileWith("chrome").copy(configJson = "not json")))

        val warn = RealityHandshakePolicy.check(
            RealityHandshakePolicy.Evidence(2, 0), profileWith("firefox"))!!
        assertEquals(BugSeverity.WARN, warn.severity)
        assertTrue(warn.detail.contains("firefox"))
        assertTrue(warn.detail.contains("Xray >= v26.9.8"))
        assertTrue(warn.action.contains("Xray core"))

        val fail = RealityHandshakePolicy.check(
            RealityHandshakePolicy.Evidence(2, 1), profileWith("firefox"))!!
        assertEquals(BugSeverity.FAIL, fail.severity)

        val chrome = RealityHandshakePolicy.check(
            RealityHandshakePolicy.Evidence(3, 0), profileWith("chrome"))!!
        assertEquals(BugSeverity.FAIL, chrome.severity)
        assertTrue(chrome.detail.contains("support_x25519mlkem768"))
    }

    @Test fun lacksMlkemShareFlagsOnlyCertainlyIncapablePresets() {
        listOf("", "chrome", "Chrome", "chrome_pq", "chrome_pq_psk", "chrome_psk_shuffle", "random", "randomized")
            .forEach { assertFalse(it, RealityHandshakePolicy.lacksMlkemShare(it)) }
        listOf("firefox", "safari", "edge", "ios", "android", "360", "qq")
            .forEach { assertTrue(it, RealityHandshakePolicy.lacksMlkemShare(it)) }
    }
}
