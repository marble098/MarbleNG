package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PING_ENGINE_V122 — the rewritten ICMP parser must read real `/system/bin/ping` output
 * the same way on toybox, bsd-ping and busybox builds: per-reply `time=` lines win, the summary
 * table is only a fallback, and loss is the binary's own count.
 */
class RouteProbeTest {

    @Test
    fun parsesPerReplyTimesAndReportsMedian() {
        val output = """
            PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=41.3 ms
            64 bytes from 1.1.1.1: icmp_seq=2 ttl=57 time=180.4 ms
            64 bytes from 1.1.1.1: icmp_seq=3 ttl=57 time=42.9 ms
            64 bytes from 1.1.1.1: icmp_seq=4 ttl=57 time=40.8 ms

            --- 1.1.1.1 ping statistics ---
            4 packets transmitted, 4 received, 0% packet loss, time 3005ms
            rtt min/avg/max/mdev = 40.800/76.350/180.400/60.123 ms
        """.trimIndent()

        val sample = RouteProbe.parseIcmpOutput(output, requestedCount = 4)

        // Median of [40.8, 41.3, 42.9, 180.4] is between 41.3 and 42.9 — the one slow reply
        // must not drag the number toward the 76 ms summary average.
        assertEquals(100, sample.successPercent)
        assertEquals(4, sample.samples)
        assertEquals(4, sample.attempts)
        assertTrue("median was ${sample.latencyMs}", sample.latencyMs in 41.0..43.5)
    }

    @Test
    fun reportsLossFromMissingReplies() {
        val output = """
            PING cp.cloudflare.com (1.1.1.1) 56(84) bytes of data.
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=56 time=88.1 ms
            64 bytes from 1.1.1.1: icmp_seq=3 ttl=56 time=91.7 ms

            --- cp.cloudflare.com ping statistics ---
            4 packets transmitted, 2 received, 50% packet loss, time 3004ms
            rtt min/avg/max/mdev = 88.100/89.900/91.700/1.800 ms
        """.trimIndent()

        val sample = RouteProbe.parseIcmpOutput(output, requestedCount = 4)

        assertEquals(50, sample.successPercent)
        assertEquals(2, sample.samples)
        assertEquals(4, sample.attempts)
        // Median of [88.1, 91.7] is the upper-middle element.
        assertEquals(91.7, sample.latencyMs, 0.1)
    }

    @Test
    fun summaryTableFallsBackWhenReplyLinesMissing() {
        // Some Android builds print only the summary table under -q.
        val output = """
            PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.

            --- 8.8.8.8 ping statistics ---
            3 packets transmitted, 3 received, 0% packet loss, time 2003ms
            rtt min/avg/max/mdev = 12.1/13.4/14.9/0.9 ms
        """.trimIndent()

        val sample = RouteProbe.parseIcmpOutput(output, requestedCount = 3)

        assertEquals(100, sample.successPercent)
        assertEquals(1, sample.samples)
        assertEquals(13.4, sample.latencyMs, 0.1)
    }

    @Test
    fun fullyDroppedBatchIsUnreachable() {
        val output = """
            PING 10.0.0.1 (10.0.0.1) 56(84) bytes of data.

            --- 10.0.0.1 ping statistics ---
            4 packets transmitted, 0 received, 100% packet loss, time 3008ms
        """.trimIndent()

        val sample = RouteProbe.parseIcmpOutput(output, requestedCount = 4)

        assertEquals(0, sample.successPercent)
        assertEquals(RouteProbe.UNREACHABLE, sample.latencyMs, 0.0)
        assertEquals(0, sample.samples)
        assertEquals(4, sample.attempts)
    }

    @Test
    fun busyboxSeqSpellingCountsAsReplies() {
        // busybox prints `seq=`, iputils and toybox print `icmp_seq=`; both are one reply each.
        val output = """
            PING 1.1.1.1 (1.1.1.1): 56 data bytes
            64 bytes from 1.1.1.1: seq=0 ttl=57 time=31.2 ms
            64 bytes from 1.1.1.1: seq=1 ttl=57 time=29.8 ms

            --- 1.1.1.1 ping statistics ---
            2 packets transmitted, 2 packets received, 0% packet loss
            round-trip min/avg/max = 29.800/30.500/31.200 ms
        """.trimIndent()

        val sample = RouteProbe.parseIcmpOutput(output, requestedCount = 2)

        assertEquals(100, sample.successPercent)
        assertEquals(2, sample.samples)
        assertEquals(31.2, sample.latencyMs, 0.1)
    }

    @Test
    fun statisticsTableNeverCountsAsAReply() {
        // MARBLE_ICMP_REPLY_ANCHOR_V123 — the tally line ends with `time 3008ms`. It must not be
        // read as a fifth reply, which is what silently halved every reported loss figure.
        val output = """
            PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=44.0 ms

            --- 1.1.1.1 ping statistics ---
            4 packets transmitted, 1 received, 75% packet loss, time 3008ms
            rtt min/avg/max/mdev = 44.000/44.000/44.000/0.000 ms
        """.trimIndent()

        val sample = RouteProbe.parseIcmpOutput(output, requestedCount = 4)

        assertEquals(25, sample.successPercent)
        assertEquals(1, sample.samples)
        assertEquals(4, sample.attempts)
        assertEquals(44.0, sample.latencyMs, 0.1)
    }

    @Test
    fun subMillisecondReplyIsKept() {
        // A LAN echo can round below a millisecond; iputils then prints `time<1 ms`.
        val output = """
            64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time<1 ms
            64 bytes from 192.168.1.1: icmp_seq=2 ttl=64 time=1.40 ms
        """.trimIndent()

        val sample = RouteProbe.parseIcmpOutput(output, requestedCount = 2)

        assertEquals(2, sample.samples)
        assertEquals(1.4, sample.latencyMs, 0.1)
    }

    @Test
    fun rejectsImplausibleReplyTimes() {
        val output = """
            PING 1.1.1.1 (1.1.1.1) 56(84) bytes of data.
            64 bytes from 1.1.1.1: icmp_seq=1 ttl=57 time=42.0 ms
            64 bytes from 1.1.1.1: icmp_seq=2 ttl=57 time=99999.0 ms
        """.trimIndent()

        val sample = RouteProbe.parseIcmpOutput(output, requestedCount = 2)

        // The 100 s timeout artifact is filtered; only the honest 42 ms remains.
        assertEquals(1, sample.samples)
        assertEquals(42.0, sample.latencyMs, 0.1)
    }
}
