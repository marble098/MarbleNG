package com.marbleng.app.core

import com.marbleng.app.model.IpEgress
import com.marbleng.app.model.IpKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_IP_IDENTITY_V251 — what the echo service's answer is allowed to become.
 *
 * A page that prints "the address the internet sees" has exactly one job it cannot get wrong: never
 * print something that is not that address. These cases are the ways a real response can be wrong —
 * a banner, an HTML error page, a captive portal's private answer, the other family's literal — and
 * each one must end as "not discovered", never as a line of text on the hero card.
 */
class IpEchoProbeV251Test {

    @Test
    fun aPlainAnswerWithATrailingNewlineBecomesTheLiteral() {
        assertEquals("203.0.113.9", IpEchoProbe.literalOf("203.0.113.9\n", IpKind.IPV4))
        assertEquals("2606:4700:4700::1111", IpEchoProbe.literalOf("  2606:4700:4700::1111 \r\n", IpKind.IPV6))
        // A multi-line body is read from its first line that parses, which is how a `text/plain`
        // service with a banner still answers correctly.
        assertEquals("198.51.100.4", IpEchoProbe.literalOf("your ip:\n198.51.100.4\n", IpKind.IPV4))
    }

    @Test
    fun anAnswerOfTheWrongFamilyIsRefusedInsteadOfBeingPrinted() {
        assertNull(IpEchoProbe.literalOf("2606:4700:4700::1111\n", IpKind.IPV4))
        assertNull(IpEchoProbe.literalOf("203.0.113.9\n", IpKind.IPV6))
        // `OTHER` is the family the caller cannot name: either literal is acceptable there.
        assertEquals("203.0.113.9", IpEchoProbe.literalOf("203.0.113.9", IpKind.OTHER))
    }

    @Test
    fun aPrivateAnswerFromAPublicServiceIsTreatedAsNoAnswer() {
        listOf("10.0.0.7", "192.168.1.5", "127.0.0.1", "169.254.1.1", "fd00::1", "fe80::1", "0.0.0.0")
            .forEach { literal ->
                assertNull("$literal must not be reported as an exit", IpEchoProbe.literalOf(literal, IpKind.OTHER))
            }
    }

    @Test
    fun anHtmlErrorPageIsNotAnAddress() {
        assertNull(IpEchoProbe.literalOf("<html><body>502 Bad Gateway</body></html>", IpKind.IPV4))
        assertNull(IpEchoProbe.literalOf("", IpKind.IPV4))
        assertNull(IpEchoProbe.literalOf("  \n \n", IpKind.IPV4))
    }

    @Test
    fun aCarrierGradeAndANat64AnswerStayObservationsWithTheirOwnScope() {
        val cgnat = IpEchoProbe.observationOf("100.64.1.1", IpKind.IPV4, viaTunnel = true)
        assertEquals("100.64.1.1", cgnat?.address)
        assertFalse("a CGNAT literal is not a globally routable exit", cgnat!!.global)

        val nat64 = IpEchoProbe.observationOf("64:ff9b::102:304", IpKind.IPV6, viaTunnel = false)
        assertTrue(nat64 != null)
        assertFalse(nat64!!.global)

        val global = IpEchoProbe.observationOf("1.1.1.1", IpKind.IPV4, viaTunnel = false)
        assertTrue(global!!.global)
        assertFalse(global.viaTunnel)
        assertEquals(IpKind.IPV4, global.family)
    }

    @Test
    fun theTwoPlanesCanNeverAskTheWrongHost() {
        assertEquals("ipv4.icanhazip.com", IpEchoProbe.hostFor(IpKind.IPV4))
        assertEquals("ipv6.icanhazip.com", IpEchoProbe.hostFor(IpKind.IPV6))
        // An unknown family falls back to v4: every network this app runs on has one.
        assertEquals("ipv4.icanhazip.com", IpEchoProbe.hostFor(IpKind.OTHER))
    }

    @Test
    fun aTunnelPortOutsideTheValidRangeNeverDialsAnything() {
        assertEquals("", IpEchoProbe.viaTunnel(0, IpKind.IPV4))
        assertEquals("", IpEchoProbe.viaTunnel(-1, IpKind.IPV4))
        assertEquals("", IpEchoProbe.viaTunnel(70_000, IpKind.IPV4))
    }

    @Test
    fun theObservationTypeKeepsTheSentenceThePageNeeds() {
        val empty = IpEgress(IpKind.IPV4, detail = "no answer")
        assertFalse(empty.discovered)
        assertEquals("no answer", empty.detail)
        assertEquals(MARKER, "MARBLE_IP_IDENTITY_V251")
    }

    private companion object {
        const val MARKER = IpEchoProbe.MARKER
    }
}
