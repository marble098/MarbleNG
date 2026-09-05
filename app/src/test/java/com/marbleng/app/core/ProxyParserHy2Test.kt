package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Hysteria2 URI regressions. The query grammar is shared by clients, but the emitted document is
 * Xray-native: Salamander must be a UDP mask and the QUIC/TLS contract must survive parsing.
 */
class ProxyParserHy2Test {
    @Test
    fun parsesTlsAlpnAndXraySalamanderMask() {
        val profile = ProxyParser.parseInput(
            "hysteria2://user%3Apass@example.com:443" +
                "?sni=cdn.example.com&alpn=h3&obfs=salamander&obfs-password=secret%2Bvalue" +
                "#edge"
        ).single()
        val root = JSONObject(profile.configJson)
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        val stream = outbound.getJSONObject("streamSettings")
        val tls = stream.getJSONObject("tlsSettings")

        assertEquals("hysteria2", profile.scheme)
        assertEquals("hysteria", outbound.getString("protocol"))
        assertEquals("hysteria", stream.getString("method"))
        assertEquals(2, stream.getJSONObject("hysteriaSettings").getInt("version"))
        assertEquals("user:pass", stream.getJSONObject("hysteriaSettings").getString("auth"))
        assertEquals("cdn.example.com", tls.getString("serverName"))
        assertEquals("h3", tls.getJSONArray("alpn").getString(0))

        val masks = stream.getJSONArray("udpmasks")
        assertEquals(1, masks.length())
        assertEquals("salamander", masks.getJSONObject(0).getString("type"))
        assertEquals(
            "secret+value",
            masks.getJSONObject(0).getJSONObject("settings").getString("password")
        )

        // Final hardening may add DNS/routing/sockopt policy, but it must not translate or drop the
        // transport-specific mask on the way to the runtime document.
        val hardened = JSONObject(
            XrayConfigHardener.harden(profile.configJson, 19_080, AppSettings())
        )
        val hardenedStream = hardened.getJSONArray("outbounds")
            .getJSONObject(0)
            .getJSONObject("streamSettings")
        assertEquals(
            "salamander",
            hardenedStream.getJSONArray("udpmasks").getJSONObject(0).getString("type")
        )
    }

    @Test
    fun defaultsHysteria2ToHttp3AlpnWithoutInventingObfs() {
        val profile = ProxyParser.parseInput("hy2://password@example.com:8443#plain").single()
        val stream = JSONObject(profile.configJson)
            .getJSONArray("outbounds")
            .getJSONObject(0)
            .getJSONObject("streamSettings")
        val tls = stream.getJSONObject("tlsSettings")

        assertEquals("h3", tls.getJSONArray("alpn").getString(0))
        assertFalse(stream.has("udpmasks"))
    }

    @Test
    fun rejectsUnsupportedOrIncompleteObfsInsteadOfEmittingAnotherClientsSchema() {
        assertTrue(
            runCatching {
                ProxyParser.parseInput("hysteria2://password@example.com:443?obfs=salamander")
            }.isFailure
        )
        assertTrue(
            runCatching {
                ProxyParser.parseInput("hysteria2://password@example.com:443?obfs=plain&obfs-password=x")
            }.isFailure
        )
    }
}
