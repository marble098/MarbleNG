package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTruthV278Test {
    @Test
    fun liveStreamEntersExistingLogsAtTheirEnd() {
        assertEquals(4_096L, NativeLogMirror.sessionStartOffset(4_096L))
        assertEquals(4_096L, NativeLogMirror.resumeOffset(4_096L, 4_096L))
        assertEquals(4_096L, NativeLogMirror.resumeOffset(4_096L, 5_000L))
    }

    @Test
    fun newAndRotatedLogsAreReadFromTheStart() {
        assertEquals(0L, NativeLogMirror.resumeOffset(null, 700L))
        assertEquals(0L, NativeLogMirror.resumeOffset(4_096L, 120L))
    }

    @Test
    fun protobufTombstoneYieldsItsStrings() {
        val bytes = byteArrayOf(0x0A, 0x05) + "arm64".toByteArray() +
            byteArrayOf(0x12, 0x0F, 0x01, 0x02) + "marble-vpn-data".toByteArray() +
            byteArrayOf(0x00, 0x7F, 0x03) + "ab".toByteArray() + byteArrayOf(0x00) +
            "stack pointer is not in a rw map".toByteArray() + byteArrayOf(0x1A, 0x00)
        assertFalse(NativeLogMirror.looksLikeText(bytes))
        assertEquals(
            listOf("arm64", "marble-vpn-data", "stack pointer is not in a rw map"),
            NativeLogMirror.printableRuns(bytes)
        )
    }

    @Test
    fun javaTraceIsStillText() {
        assertTrue(NativeLogMirror.looksLikeText("java.lang.IllegalStateException: x\n\tat a.b(C.kt:1)\n".toByteArray()))
    }

    @Test
    fun startupGateDropsOnlyTheRepeat() {
        val gate = RecentKeyGate()
        assertTrue(gate.tryEnter("iran|OFF|0|wifi", 1_000L, 2_000L))
        assertTrue(gate.tryEnter("warm|a,b", 1_000L, 2_000L))
        assertFalse(gate.tryEnter("iran|OFF|0|wifi", 1_004L, 2_000L))
        assertFalse(gate.tryEnter("warm|a,b", 1_001L, 2_000L))
        assertTrue(gate.tryEnter("iran|OFF|1|wifi", 1_005L, 2_000L))
        assertTrue(gate.tryEnter("iran|OFF|0|wifi", 3_100L, 2_000L))
    }

    @Test
    fun threadCensusNamesFamilies() {
        assertEquals("pool-thread", NamedThreads.family("pool-64-thread-3"))
        assertEquals("marble-vpn-mon", NamedThreads.family("marble-vpn-mon-2"))
        assertEquals("marble-vpn-data", NamedThreads.name("marble-vpn-data", 1))
        assertEquals(
            "pool-thread=2,marble-vpn-mon=1",
            NamedThreads.census(listOf("pool-1-thread-1", "marble-vpn-mon", "pool-9-thread-2"))
        )
    }

    @Test
    fun hevSessionsGetHeadroomAboveTheUpstreamFloor() {
        assertEquals(262_144, HevTunnelPolicy.taskStackSize(is64Bit = true))
        assertEquals(131_072, HevTunnelPolicy.taskStackSize(is64Bit = false))
        assertTrue(HevTunnelPolicy.taskStackSize(false) > HevTunnelPolicy.TASK_STACK_SIZE)
        assertEquals(0, HevTunnelPolicy.taskStackSize(true) % 4_096)
    }
}
