package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

        assertEquals(CoreConfigSuperset.PLAINTEXT_PUBLIC_NOTE, verdict.note)
    }

    @Test
    fun cleartextPublicNodeIsRefusedWhenTheUserWithdrewConsent() {
        val profile = profile("vless", "45.76.12.34", security = "none")
        val verdict = CoreConfigSuperset.verdict(profile, CoreEngine.XRAY, closedSettings)
        assertFalse(verdict.runnable)
        assertTrue(verdict.refused)
        assertEquals("plaintext-prohibited", verdict.reason)

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
        assertTrue(
            CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.xorpub.0rtt." + "A".repeat(43))
        )
        assertTrue(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.random.1rtt.pad"))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus"))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.xorpub.0rtt"))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.native.2rtt.pad"))

        assertTrue(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.native.1rtt.pad"))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.native.1rtt." + "A".repeat(40)))
        assertTrue(CoreConfigSuperset.isPostQuantumEncryption("mlkem768x25519plus.native.1rtt." + "A".repeat(43)))
        assertFalse(CoreConfigSuperset.isPostQuantumEncryption("auto"))
    }

    @Test
    fun aVlessEncryptionClaimTheCoreCannotParseIsNamedInsteadOfKillingTheCore() {
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
        val params = ShareLinkParams.of("pbk=wwwABCdef&type=tcp&security=")
        assertTrue(params.first("pbk", "publicKey").isNotBlank())
        assertEquals("", params.get("security"))
    }

    @Test
    fun consentQueryIsTheSingleSourceForEveryCaller() {
        assertTrue(CoreConfigSuperset.dialsPlaintextPublicNodes(openSettings))
        assertFalse(CoreConfigSuperset.dialsPlaintextPublicNodes(closedSettings))

        assertTrue(CoreConfigSuperset.dialsPlaintextPublicNodes(AppSettings()))
    }

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
