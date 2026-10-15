package com.marbleng.app.model

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class PingParallelTest {
    @Test
    fun `one core device runs two at once`() {
        assertEquals(2, PingParallel.recommend(1, 4_096L, ProbeMethod.TCP_PING))
    }

    @Test
    fun `width rises monotonically with core count`() {
        val ladder = (1..16).map { cores ->
            PingParallel.recommend(cores, 8_192L, ProbeMethod.TCP_PING)
        }
        for (i in 1 until ladder.size) {
            assertTrue(
                "width must never shrink as cores grow: ${ladder[i - 1]} -> ${ladder[i]} at ${i + 1} cores",
                ladder[i] >= ladder[i - 1]
            )
        }
        assertTrue("a 16-core device must out-width a 1-core one", ladder.last() > ladder.first())
    }

    @Test
    fun `width is flat past the core ceiling`() {
        val at16 = PingParallel.recommend(16, 8_192L, ProbeMethod.TCP_PING)
        val at32 = PingParallel.recommend(32, 8_192L, ProbeMethod.TCP_PING)
        val at64 = PingParallel.recommend(64, 8_192L, ProbeMethod.TCP_PING)
        assertEquals("past the ceiling more cores stop buying width", at16, at32)
        assertEquals("past the ceiling more cores stop buying width", at16, at64)
    }

    @Test
    fun `low memory demotes exactly one class`() {
        val strongCores = 8
        val roomy = PingParallel.deviceClass(strongCores, 8_192L)
        val starving = PingParallel.deviceClass(strongCores, 1_024L)
        assertEquals(PingDeviceClass.STRONG, roomy)
        assertEquals(PingDeviceClass.CAPABLE, starving)
        assertTrue(
            PingParallel.recommend(strongCores, 1_024L, ProbeMethod.TCP_PING) <
                PingParallel.recommend(strongCores, 8_192L, ProbeMethod.TCP_PING)
        )
    }

    @Test
    fun `memory demotion can never reach below modest`() {
        assertEquals(PingDeviceClass.MODEST, PingParallel.deviceClass(1, 512L))
        assertEquals(PingDeviceClass.MODEST, PingParallel.deviceClass(2, 512L))
    }

    @Test
    fun `real delay runs narrower than the direct sweep on the same device`() {
        for (cores in 1..16) {
            val direct = PingParallel.recommend(cores, 8_192L, ProbeMethod.TCP_PING)
            val real = PingParallel.recommend(cores, 8_192L, ProbeMethod.REAL_DELAY)
            assertTrue(
                "real delay spawns a core per server, so it must never be wider: $cores cores",
                real <= direct
            )
            assertTrue("a real-delay sweep still needs at least one slot", real >= 1)
        }
    }

    @Test
    fun `every recommended width is inside the legal range`() {
        for (cores in 0..80) {
            for (memory in listOf(0L, 512L, 1_536L, 3_072L, 6_144L, 16_384L)) {
                for (method in ProbeMethod.entries) {
                    val width = PingParallel.recommend(cores, memory, method)
                    assertTrue(
                        "width out of range: $cores cores / $memory MB / $method -> $width",
                        width in PingBudget.CONCURRENCY_MIN..PingBudget.CONCURRENCY_MAX
                    )
                }
            }
        }
    }

    @Test
    fun `manual mode honours the chip and ignores the device`() {
        val settings = AppSettings(
            pingParallelMode = PingParallelMode.MANUAL,
            pingConcurrency = 3
        )
        assertEquals(3, PingParallel.resolve(settings, 16, 16_384L))
        assertEquals(3, PingParallel.resolve(settings, 1, 512L))
    }

    @Test
    fun `auto mode ignores the chip and follows the device`() {
        val settings = AppSettings(
            pingParallelMode = PingParallelMode.AUTO,
            pingConcurrency = 64
        )
        val wide = PingParallel.resolve(settings, 8, 8_192L)
        val narrow = PingParallel.resolve(settings, 2, 2_048L)
        assertTrue("the same settings must yield a wider sweep on a wider device", wide > narrow)
        assertTrue("the stale maximum chip must not leak into automatic mode", wide < 64)
    }

    @Test
    fun `a manual chip outside the legal range is clamped not rejected`() {
        val high = AppSettings(pingParallelMode = PingParallelMode.MANUAL, pingConcurrency = 999)
        val low = AppSettings(pingParallelMode = PingParallelMode.MANUAL, pingConcurrency = 0)
        assertEquals(PingBudget.CONCURRENCY_MAX, PingParallel.resolve(high, 8, 8_192L))
        assertEquals(PingBudget.CONCURRENCY_MIN, PingParallel.resolve(low, 8, 8_192L))
    }

    @Test
    fun `the summary names the device and the number it bought`() {
        val text = PingParallel.describe(8, 8_192L, ProbeMethod.TCP_PING)
        assertTrue(text, text.contains("8 cores"))
        assertTrue(text, text.contains("8 GB RAM"))
        assertTrue(
            text,
            text.contains("${PingParallel.recommend(8, 8_192L, ProbeMethod.TCP_PING)} at once")
        )
    }

    @Test
    fun `the manual chips can express every width the ladder recommends`() {
        val recommended = (1..16).flatMap { cores ->
            listOf(1_024L, 2_048L, 4_096L, 8_192L).flatMap { memory ->
                ProbeMethod.entries.map { method -> PingParallel.recommend(cores, memory, method) }
            }
        }.toSet()
        val missing = recommended.filter { it !in PingBudget.CONCURRENCY_CHOICES }
        assertTrue("chip list cannot express: $missing", missing.isEmpty())
    }

    @Test
    fun `the chip list is sorted and holds no duplicates`() {
        val chips = PingBudget.CONCURRENCY_CHOICES
        assertEquals("the chips are shown in order", chips.sorted(), chips)
        assertEquals("a duplicated chip is a bug in the row", chips.toSet().size, chips.size)
        assertTrue(chips.first() == PingBudget.CONCURRENCY_MIN)
        assertTrue(chips.last() == PingBudget.CONCURRENCY_MAX)
    }

    @Test
    fun `unknown mode strings fall back to automatic not to manual`() {
        assertEquals(PingParallelMode.AUTO, parsePingParallelMode(""))
        assertEquals(PingParallelMode.AUTO, parsePingParallelMode("something else"))
        assertEquals(PingParallelMode.MANUAL, parsePingParallelMode("manual"))
        assertEquals(PingParallelMode.AUTO, parsePingParallelMode("DEVICE"))
    }
}
