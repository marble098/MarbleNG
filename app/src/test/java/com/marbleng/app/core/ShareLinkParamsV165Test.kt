package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_CORE_CONFIG_SUPERSET_V165 — the parameters of a share link, read the way the ecosystem
 * writes them.
 *
 * Every case here is a link that used to import as something other than what it says. A capitalised
 * `SNI=`, a `net=ws`, a name placed before the parameters instead of after them, a `+` inside base64
 * key material: none of them is an "unsupported config", and all of them were silently becoming
 * plaintext (or nothing at all), which the pinned core then refused. This object is the whole reading
 * rule, so it is testable without `android.net.Uri` and without a device.
 */
class ShareLinkParamsV165Test {

    @Test
    fun keyLookupIsCaseInsensitiveBecausePanelsAreNotConsistent() {
        val params = ShareLinkParams.of("type=tcp&security=reality&SNI=www.microsoft.com&PublicKey=abc")
        assertEquals("reality", params.get("security"))
        assertEquals("www.microsoft.com", params.get("sni"))
        assertEquals("www.microsoft.com", params.get("SNI"))
        assertEquals("abc", params.first("pbk", "publicKey"))
        assertTrue(params.has("PublicKey"))
    }

    @Test
    fun transportAndSecurityAliasesResolveToTheSameAnswer() {
        mapOf(
            "type=ws" to "ws",
            "network=ws" to "ws",
            "net=ws" to "ws",
            "type=xhttp" to "xhttp",
            "network=splithttp&path=%2Fxhttp" to "splithttp"
        ).forEach { (query, expected) ->
            val params = ShareLinkParams.of(query)
            assertEquals(expected, params.first("type", "network", "net"))
        }
    }

    @Test
    fun parametersWrittenAfterTheFragmentAreStillFound() {
        // `vless://uuid@host:443#Germany 1?type=tcp&security=none` is a legal URI, and `Uri` reports
        // no query for it at all — the whole link used to import with every parameter invisible.
        val link = "vless://11111111-1111-1111-1111-111111111111@1.2.3.4:443#Germany 1?type=tcp&security=none&seed=7"
        val params = ShareLinkParams.ofRawLink(link)
        assertEquals("tcp", params.get("type"))
        assertEquals("none", params.get("security"))
        assertEquals("7", params.get("seed"))

        // …and the ordinary ordering keeps working, with the fragment left out of every value.
        val normal = ShareLinkParams.ofRawLink("vless://id@1.2.3.4:443?type=xhttp&security=tls#Name")
        assertEquals("xhttp", normal.get("type"))
        assertEquals("tls", normal.get("security"))
    }

    @Test
    fun percentDecodingKeepsUtf8AndLeavesPlusAlone() {
        val params = ShareLinkParams.of("sni=%77%77%2Eexample%2Ecom&name=%D9%86%D8%A7%D9%85&host=example.com%2Fa%2Bb")
        assertEquals("www.example.com", params.get("sni"))
        assertEquals("نام", params.get("name"))
        // `+` is data inside base64 material (`pbk`, `mldsa65Verify`), never a space.
        assertEquals("example.com/a+b", params.get("host"))

        val key = ShareLinkParams.of("pbk=abc+def/ghi=")
        assertEquals("abc+def/ghi=", key.get("pbk"))
    }

    @Test
    fun firstSpellingWinsBecauseTheAuthorWroteItFirst() {
        val params = ShareLinkParams.of("security=none&security=reality&type=tcp")
        assertEquals("none", params.get("security"))
        assertEquals(setOf("security", "type"), params.keys())
    }

    @Test
    fun truthinessIsTheSetThePanelsActuallyWrite() {
        assertTrue(ShareLinkParams.of("multiMode=true").isTruthy("multiMode"))
        assertTrue(ShareLinkParams.of("multiMode=1").isTruthy("multiMode"))
        assertTrue(ShareLinkParams.of("multiMode=YES").isTruthy("multiMode"))
        assertTrue(ShareLinkParams.of("multiMode=on").isTruthy("multiMode"))
        assertFalse(ShareLinkParams.of("multiMode=false").isTruthy("multiMode"))
        assertFalse(ShareLinkParams.of("multiMode=0").isTruthy("multiMode"))
        assertFalse(ShareLinkParams.EMPTY.isTruthy("multiMode"))
    }

    @Test
    fun blankValuesFallBackToTheDefaultAndNeverToTheNextAlias() {
        val params = ShareLinkParams.of("security=&sni=&host=example.com")
        assertEquals("none", params.get("security", "none"))
        assertEquals("", params.get("sni"))
        assertNull(params.raw("missing"))
        assertEquals("", params.raw("sni"))
        assertEquals("example.com", params.first("sni", "host"))
    }

    @Test
    fun anEmptyOrGarbledQueryIsNotAnError() {
        assertEquals(ShareLinkParams.EMPTY, ShareLinkParams.of(null))
        assertEquals(ShareLinkParams.EMPTY, ShareLinkParams.of(""))
        assertEquals(ShareLinkParams.EMPTY, ShareLinkParams.of("&&"))
        assertEquals(ShareLinkParams.EMPTY, ShareLinkParams.ofRawLink("vless://id@1.2.3.4:443#Name"))
        assertEquals(ShareLinkParams.EMPTY, ShareLinkParams.ofRawLink(""))
        assertEquals(emptySet<String>(), EMPTY.keys())
        assertEquals("", EMPTY.get("type"))
        assertEquals("tcp", EMPTY.get("type", "tcp"))
    }

    @Test
    fun aTokenWithoutEqualsSignsIsAFlagNotAMissingKey() {
        val params = ShareLinkParams.of("insecure&ed=none&type=ws")
        assertEquals("", params.get("insecure"))
        assertTrue(params.has("insecure"))
        assertEquals("none", params.get("ed"))
        assertEquals("ws", params.get("type"))
    }

}
