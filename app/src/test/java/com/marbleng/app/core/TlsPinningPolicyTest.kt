package com.marbleng.app.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_TLS_PINNING_V149 — the acceptance criteria of the "connected but no Internet" fix.
 *
 * These tests pin the exact contract Xray-core v26.7.28 `infra/conf/transport_security.go`
 * imposes, so a future refactor cannot quietly reintroduce the failure:
 *
 *  1. `vcn` reaches `tlsSettings.verifyPeerCertByName` and `pcs` reaches
 *     `tlsSettings.pinnedPeerCertSha256` — both from the SHORT keys real share links use;
 *  2. fingerprints are emitted as lowercase HEX (Xray runs `hex.DecodeString`), never base64;
 *  3. `allowInsecure` is never emitted, because this core rejects the whole config when it is set.
 */
class TlsPinningPolicyTest {

    private val pinA = "c88234050d72a3e9430ec7738636806deaf85c3708fee0fd9202ebd917e2c843"
    private val pinB = "a2372d06431e9716365eeed47ec020351497d182fcc038e457e58168a03cac07"

    // ------------------------------------------------------------------ format normalization

    @Test
    fun `bare hex fingerprint is preserved as lowercase hex`() {
        assertEquals(pinA, TlsPinningPolicy.normalizeOneFingerprint(pinA.uppercase()))
    }

    @Test
    fun `openssl colon format is accepted and colons removed`() {
        val colons = pinA.chunked(2).joinToString(":")
        assertEquals(pinA, TlsPinningPolicy.normalizeOneFingerprint(colons))
    }

    @Test
    fun `base64 fingerprint is converted to the hex Xray parses`() {
        val bytes = ByteArray(32) { index -> index.toByte() }
        val base64 = java.util.Base64.getEncoder().encodeToString(bytes)
        val expected = bytes.joinToString("") { "%02x".format(it) }
        assertEquals(expected, TlsPinningPolicy.normalizeOneFingerprint(base64))
        assertEquals(expected, TlsPinningPolicy.normalizeOneFingerprint("sha256/$base64"))
    }

    @Test
    fun `a digest of the wrong length is rejected rather than passed through`() {
        // Xray fails the ENTIRE configuration on a bad pin, so one malformed element must never
        // be allowed to travel with the good ones.
        assertNull(TlsPinningPolicy.normalizeOneFingerprint("deadbeef"))
        assertNull(TlsPinningPolicy.normalizeOneFingerprint("not-a-hash-at-all"))
        assertTrue(TlsPinningPolicy.hasInvalidFingerprint("$pinA,deadbeef"))
        assertFalse(TlsPinningPolicy.hasInvalidFingerprint("$pinA,$pinB"))
        assertEquals(pinA, TlsPinningPolicy.normalizeFingerprints("$pinA,deadbeef"))
    }

    @Test
    fun `multiple pins are emitted comma separated and deduplicated`() {
        assertEquals("$pinA,$pinB", TlsPinningPolicy.normalizeFingerprints("$pinA, $pinB , $pinA"))
    }

    @Test
    fun `verify names are trimmed lowercased and comma separated`() {
        assertEquals(
            "vps1.maje.eu.org,alt.example.com",
            TlsPinningPolicy.normalizeVerifyNames(" VPS1.maje.eu.org ; alt.example.com \n")
        )
    }

    // ------------------------------------------------------------------ tlsSettings emission

    @Test
    fun `both pinning fields are written into tlsSettings`() {
        val tls = JSONObject().put("serverName", "spotify.com")
        TlsPinningPolicy.sanitizeTlsSettings(
            tls = tls,
            verifyPeerCertByName = "vps1.maje.eu.org",
            pinnedPeerCertSha256 = "$pinA,$pinB"
        )
        assertEquals("vps1.maje.eu.org", tls.getString("verifyPeerCertByName"))
        assertEquals("$pinA,$pinB", tls.getString("pinnedPeerCertSha256"))
        // The fronted SNI must survive untouched: it is what goes on the wire, while the pins are
        // what the certificate is judged against.
        assertEquals("spotify.com", tls.getString("serverName"))
    }

    @Test
    fun `allowInsecure is never emitted because this core removed it`() {
        val tls = JSONObject().put("serverName", "node.example").put("allowInsecure", true)
        TlsPinningPolicy.sanitizeTlsSettings(tls = tls, insecureFallbackName = "node.example")
        assertFalse("allowInsecure makes Xray reject the whole config", tls.has("allowInsecure"))
        // Translated into the documented replacement rather than silently dropped.
        assertEquals("node.example", tls.getString("verifyPeerCertByName"))
    }

    @Test
    fun `a real pin outranks a legacy allowInsecure request`() {
        val tls = JSONObject().put("serverName", "spotify.com")
        TlsPinningPolicy.sanitizeTlsSettings(
            tls = tls,
            pinnedPeerCertSha256 = pinA,
            allowInsecureRequested = true,
            insecureFallbackName = "node.example"
        )
        assertEquals(pinA, tls.getString("pinnedPeerCertSha256"))
        assertFalse(tls.has("allowInsecure"))
        assertFalse("a fingerprint pin must not be weakened by a name fallback", tls.has("verifyPeerCertByName"))
    }

    @Test
    fun `a whole config document is repaired recursively`() {
        val root = JSONObject(
            """
            {"outbounds":[{"protocol":"vless","streamSettings":{"security":"tls",
              "tlsSettings":{"serverName":"spotify.com","allowInsecure":true,
                "pinnedPeerCertSha256":"${pinA.chunked(2).joinToString(":")}"}}}]}
            """.trimIndent()
        )
        assertTrue(TlsPinningPolicy.sanitizeConfigDocument(root) > 0)
        val tls = root.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings").getJSONObject("tlsSettings")
        assertFalse(tls.has("allowInsecure"))
        assertEquals(pinA, tls.getString("pinnedPeerCertSha256"))
        assertTrue(TlsPinningPolicy.configIsPinned(root.toString()))
    }
}
