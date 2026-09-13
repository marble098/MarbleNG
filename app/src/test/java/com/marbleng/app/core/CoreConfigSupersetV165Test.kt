package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CORE_CONFIG_SUPERSET_V165 — one authority decides whether a profile may dial, and it
 * agrees with the pinned core instead of with a hand-written guess.
 *
 * The reported session had 42 imported nodes and zero usable ones. Two of the three vetoes were
 * invented at app level — a copy of Xray's plaintext rule in the VPN preflight, and a "VLESS without
 * TLS is deprecated" copy in the rank gate — and the third was the core's own rule, which Marble's
 * patched core no longer applies. This test pins the surviving behaviour: a cleartext node is
 * *labelled*, a private node is never second-guessed, post-quantum VLESS is its own protected class,
 * and the only shapes that are refused are the ones the pinned source proves cannot load, or whatever
 * the user opted out of.
 */
class CoreConfigSupersetV165Test {

    private val openSettings = AppSettings(allowUnencryptedPublicOutbound = true)
    private val closedSettings = AppSettings(allowUnencryptedPublicOutbound = false)

    @Test
    fun cleartextPublicNodeIsDialledAndLabelled() {
        val profile = profile("vless", "45.76.12.34", security = "none")
        val verdict = CoreConfigSuperset.verdict(profile, CoreEngine.XRAY, openSettings)
        assertTrue(verdict.runnable)
        assertFalse(verdict.refused)
        assertEquals(CoreConfigSuperset.Wire.PLAINTEXT_PUBLIC, verdict.wire)
        assertEquals("plaintext-accepted", verdict.reason)
        // The label is not optional: an unencrypted node that connects must still say so.
        assertEquals(CoreConfigSuperset.PLAINTEXT_PUBLIC_NOTE, verdict.note)
    }

    @Test
    fun cleartextPublicNodeIsRefusedWhenTheUserWithdrewConsent() {
        val profile = profile("vless", "45.76.12.34", security = "none")
        val verdict = CoreConfigSuperset.verdict(profile, CoreEngine.XRAY, closedSettings)
        assertFalse(verdict.runnable)
        assertTrue(verdict.refused)
        assertEquals("plaintext-prohibited", verdict.reason)
        // The refusal text is the one every existing translation, log reader and test already knows.
        assertTrue(verdict.detail.startsWith(CoreConfigSuperset.PLAINTEXT_REFUSAL))
        assertTrue(verdict.detail.contains("Dial unencrypted nodes"))
    }

    @Test
    fun tlsAndRealityNodesAreNeverTouchedByThisPolicy() {
        listOf("tls", "reality").forEach { security ->
            val verdict = CoreConfigSuperset.verdict(
                profile("vless", "45.76.12.34", security = security),
                CoreEngine.XRAY,
                closedSettings
            )
            assertTrue("$security must stay runnable even with consent off", verdict.runnable)
            assertEquals(CoreConfigSuperset.Wire.ENCRYPTED, verdict.wire)
            assertEquals("", verdict.note)
        }
    }

    @Test
    fun privateEndpointIsPrivateUnderTheCoreMatcherNotUnderAShorterAppList() {
        // Every one of these is private in `common/geodata` — and every one of them used to be a
        // public address as far as the app's own shorter list was concerned.
        listOf(
            "10.8.0.9", "127.0.0.1", "172.16.5.4", "192.168.17.1", "100.64.0.1", "169.254.1.1",
            "::1", "fe80::1", "fd7a:115c:a1e0::12", "::ffff:10.0.0.1"
        ).forEach { host ->
            val wire = CoreConfigSuperset.wire(profile("vless", host, security = "none"))
            assertEquals("$host is private to the core, so to Marble", CoreConfigSuperset.Wire.PLAINTEXT_PRIVATE, wire)
        }
        listOf("8.8.8.8", "45.76.12.34", "1.1.1.1", "203.0.114.1").forEach { host ->
            assertEquals(
                "$host is public",
                CoreConfigSuperset.Wire.PLAINTEXT_PUBLIC,
                CoreConfigSuperset.wire(profile("vless", host, security = "none"))
            )
        }
    }

    @Test
    fun privateCleartextNeedsNoConsent() {
        val verdict = CoreConfigSuperset.verdict(
            profile("vless", "10.8.0.9", security = "none"),
            CoreEngine.XRAY,
            closedSettings
        )
        assertTrue(verdict.runnable)
        assertEquals("private-cleartext", verdict.reason)
        assertEquals(CoreConfigSuperset.PLAINTEXT_PRIVATE_NOTE, verdict.note)
    }

    @Test
    fun dotlessHostNameIsPrivateBecauseTheCoreSaysSo() {
        // `parseCustomDomainRule` turns a bare `lan` into a *suffix* rule, so `vpn.lan` is private
        // while `example` does not match `example.com`. The app mirroring that exactly is the point.
        assertTrue(CorePrivateEndpoint.matches("vpn.lan"))
        assertTrue(CorePrivateEndpoint.matches("nas.home.arpa"))
        assertTrue(CorePrivateEndpoint.matches("router"))
        assertFalse(CorePrivateEndpoint.matches("example.com"))
        assertFalse(CorePrivateEndpoint.matches(""))
    }

    @Test
    fun postQuantumVlessIsItsOwnProtectedClass() {
        val keyMaterial = "A".repeat(43)
        val config = JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("tag", "proxy")
                        .put("protocol", "vless")
                        .put(
                            "settings",
                            JSONObject()
                                .put("address", "45.76.12.34")
                                .put("port", 443)
                                .put("id", "11111111-1111-1111-1111-111111111111")
                                .put("encryption", "mlkem768x25519plus.native.1rtt.$keyMaterial")
                        )
                        .put("streamSettings", JSONObject().put("security", "none"))
                )
            )
            .toString()
        val node = profile("vless", "45.76.12.34", configJson = config, security = "none")
        assertEquals(CoreConfigSuperset.Wire.POST_QUANTUM, CoreConfigSuperset.wire(node))
        assertTrue(CoreConfigSuperset.verdict(node, CoreEngine.XRAY, closedSettings).runnable)
    }

    @Test
    fun postQuantumDetectionIsStructuralNotSubstringBased() {
        // `infra/conf/vless.go` requires FOUR dot-separated parts: a name that merely starts with
        // the algorithm is not the algorithm, and a three-part string is a load error upstream too.
        assertTrue(
            CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.xorpub.0rtt." + "A".repeat(43))
        )
        assertTrue(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.random.1rtt.pad"))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus"))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.xorpub.0rtt"))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.native.2rtt.pad"))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.native.1rtt.not*base64!"))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("auto"))
    }

    @Test
    fun aVlessEncryptionClaimTheCoreCannotParseIsNamedInsteadOfKillingTheCore() {
        // `aes-128-gcm` on VLESS is a real fatal load error (`VLESS users: unsupported
        // "encryption"`), and the old behaviour was a core that died with that sentence in a log the
        // user cannot open. A token that means nothing on VLESS is NOT reported here, because
        // XrayConfigRepairs rewrites it before the core ever sees it.
        fun config(block: JSONObject.() -> Unit): String {
            val settings = JSONObject()
                .put("address", "45.76.12.34")
                .put("port", 443)
                .put("id", "11111111-1111-1111-1111-111111111111")
            settings.block()
            val outbound = JSONObject()
                .put("tag", "proxy")
                .put("protocol", "vless")
                .put("settings", settings)
                .put("streamSettings", JSONObject().put("method", "raw").put("security", "none"))
            return JSONObject().put("outbounds", JSONArray().put(outbound)).toString()
        }

        val unsupported = config { put("encryption", "aes-128-gcm") }
        assertEquals(
            CoreConfigSuperset.CORE_GAP_VLESS_ENCRYPTION,
            CoreConfigSuperset.coreGapIssue(profile("vless", "45.76.12.34", configJson = unsupported), CoreEngine.XRAY)
        )

        val meaningless = config {
            put(
                "vnext",
                JSONArray().put(
                    JSONObject()
                        .put("address", "45.76.12.34")
                        .put("port", 443)
                        .put("users", JSONArray().put(JSONObject().put("id", "11111111-1111-1111-1111-111111111111").put("encryption", "auto")))
                )
            )
        }
        assertEquals(
            null,
            CoreConfigSuperset.coreGapIssue(profile("vless", "45.76.12.34", configJson = meaningless), CoreEngine.XRAY)
        )
    }

    @Test
    fun vmessWithoutTlsIsNotTheCoreProblemThisFunctionDecides() {
        // Xray's plaintext check covers vless and trojan outbounds only. A VMess node has no forward
        // secrecy and [ProfileSecurityAuditor] still says so; pretending the wire is prohibited here
        // is how a working node became an unusable one.
        val verdict = CoreConfigSuperset.verdict(
            profile("vmess", "45.76.12.34", security = "none"),
            CoreEngine.XRAY,
            closedSettings
        )
        assertTrue(verdict.runnable)
        assertEquals(CoreConfigSuperset.Wire.UNKNOWN, verdict.wire)
        assertEquals("not-core-checked", verdict.reason)
    }

    @Test
    fun trojanCleartextIsCheckedLikeVless() {
        val node = profile("trojan", "45.76.12.34", security = "none")
        assertEquals(CoreConfigSuperset.Wire.PLAINTEXT_PUBLIC, CoreConfigSuperset.wire(node))
        assertFalse(CoreConfigSuperset.verdict(node, CoreEngine.XRAY, closedSettings).runnable)
        assertTrue(CoreConfigSuperset.verdict(node, CoreEngine.XRAY, openSettings).runnable)
    }

    @Test
    fun transportsThePinnedCoreDeletedAreNamedWithTheEngineThatHasThem() {
        val removed = profile(
            "vless",
            "45.76.12.34",
            configJson = configWithStream(JSONObject().put("network", "http").put("security", "none")),
            transport = "http"
        )
        assertEquals(
            CoreConfigSuperset.CORE_GAP_TRANSPORT_REMOVED,
            CoreConfigSuperset.coreGapIssue(removed, CoreEngine.XRAY)
        )
        // …and the same node is not a gap on the core that implements it.
        assertEquals(null, CoreConfigSuperset.coreGapIssue(removed, CoreEngine.SINGBOX))

        val camouflaged = profile(
            "vless",
            "45.76.12.34",
            configJson = configWithStream(
                JSONObject()
                    .put("method", "mkcp")
                    .put("security", "none")
                    .put("kcpSettings", JSONObject().put("header", JSONObject().put("type", "srtp")))
            ),
            transport = "kcp"
        )
        assertEquals(
            CoreConfigSuperset.CORE_GAP_KCP_CAMOUFLAGE,
            CoreConfigSuperset.coreGapIssue(camouflaged, CoreEngine.XRAY)
        )

        val plainTcp = profile("vless", "45.76.12.34", security = "none")
        assertEquals(null, CoreConfigSuperset.coreGapIssue(plainTcp, CoreEngine.XRAY))
    }

    @Test
    fun aLinkCarryingOnlyAPublicKeyIsRealityNotPlaintext() {
        // Panels and exporters drop `security=reality` far more often than they drop `pbk`. Reading
        // the key as the fact it is (only REALITY has one) is what stopped `&type=tcp&pbk=…` links
        // from being imported as cleartext and then refused.
        val params = ShareLinkParams.of("pbk=wwwABCdef&type=tcp&security=")
        assertTrue(params.first("pbk", "publicKey").isNotBlank())
        assertEquals("", params.get("security"))
    }

    @Test
    fun consentQueryIsTheSingleSourceForEveryCaller() {
        assertTrue(CoreConfigSuperset.dialsPlaintextPublicNodes(openSettings))
        assertFalse(CoreConfigSuperset.dialsPlaintextPublicNodes(closedSettings))
        // The shipped default is "usable", not "refused": the app's own core carries the patch.
        assertTrue(CoreConfigSuperset.dialsPlaintextPublicNodes(AppSettings()))
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun profile(
        scheme: String,
        host: String,
        port: Int = 443,
        security: String = "none",
        transport: String = "tcp",
        configJson: String = "",
        raw: String = ""
    ) = ProxyProfile(
        id = "profile-$host",
        name = "node $host",
        scheme = scheme,
        raw = raw,
        configJson = configJson.ifBlank { defaultConfig(scheme, host, port, security, transport) },
        host = host,
        port = port,
        transport = transport,
        security = security
    )

    private fun defaultConfig(
        scheme: String,
        host: String,
        port: Int,
        security: String,
        transport: String
    ): String = configWithStream(
        JSONObject()
            .put("method", transport)
            .put("security", security)
            .apply {
                if (security == "tls") put("tlsSettings", JSONObject().put("serverName", host))
                if (security == "reality") {
                    put(
                        "realitySettings",
                        JSONObject().put("serverName", host).put("password", "publickey")
                    )
                }
            },
        scheme = scheme,
        host = host,
        port = port
    )

    private fun configWithStream(
        stream: JSONObject,
        scheme: String = "vless",
        host: String = "45.76.12.34",
        port: Int = 443
    ): String {
        val settings = JSONObject().put("address", host).put("port", port)
        when (scheme) {
            "vless" -> settings.put("id", "11111111-1111-1111-1111-111111111111").put("encryption", "none")
            "trojan" -> settings.put("password", "pw")
            else -> Unit
        }
        return JSONObject()
            .put(
                "outbounds",
                JSONArray().put(
                    JSONObject()
                        .put("tag", "proxy")
                        .put("protocol", scheme)
                        .put("settings", settings)
                        .put("streamSettings", stream)
                )
            )
            .toString()
    }
}
