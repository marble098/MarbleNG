package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_PING_SPEED_V160 — the width of the measurement-core pool.
 *
 * Every URL test and every sing-box Real delay owns a native core, so the pool's width *is* the
 * speed of a sweep: four slots means a hundred-node subscription runs in twenty-five waves, and
 * every wave waits for its slowest spawn. Widening it is also the one change here that can hurt
 * a device rather than help it, because eight Go processes cost memory the app shares with
 * everything else on the phone.
 *
 * So the rule is pinned in both directions: the floor never moves (a small device runs exactly
 * the pool it has always run), and every step up has to be earned by cores *and* heap.
 */
class MeasurementCoreBudgetTest {

    @Test
    fun aSmallDeviceKeepsTheFloorItHasAlwaysHad() {
        assertEquals(MeasurementCoreBudget.BASE, MeasurementCoreBudget.ceiling(cpus = 4, memoryClassMb = 512))
        assertEquals(MeasurementCoreBudget.BASE, MeasurementCoreBudget.ceiling(cpus = 2, memoryClassMb = 256))
        // A device Android itself calls low-RAM is never widened, whatever it reports.
        assertEquals(
            MeasurementCoreBudget.BASE,
            MeasurementCoreBudget.ceiling(cpus = 8, memoryClassMb = 512, lowRam = true)
        )
        // Plenty of cores, no heap: memory is the constraint that matters on a cheap tablet.
        assertEquals(MeasurementCoreBudget.BASE, MeasurementCoreBudget.ceiling(cpus = 8, memoryClassMb = 128))
    }

    @Test
    fun aCapableDeviceRunsAWiderPool() {
        val mid = MeasurementCoreBudget.ceiling(cpus = 6, memoryClassMb = 192)
        val wide = MeasurementCoreBudget.ceiling(cpus = 8, memoryClassMb = 256)

        assertTrue("a six-core device must beat the floor: $mid", mid > MeasurementCoreBudget.BASE)
        assertTrue("an eight-core device must beat the six-core one: $wide", wide > mid)
        assertTrue("the pool can never exceed the policy maximum: $wide", wide <= MeasurementCoreBudget.MAX)
    }

    @Test
    fun theFloorIsNeverReducedAndTheCeilingIsNeverExceeded() {
        for (cpus in 1..16) {
            for (memoryMb in listOf(64, 128, 192, 256, 512)) {
                for (lowRam in listOf(false, true)) {
                    val granted = MeasurementCoreBudget.ceiling(cpus, memoryMb, lowRam)
                    assertTrue(
                        "pool $granted out of range for cpus=$cpus mem=$memoryMb lowRam=$lowRam",
                        granted >= MeasurementCoreBudget.BASE && granted <= MeasurementCoreBudget.MAX
                    )
                    // extraSlots is what the pool is actually widened by, so it can never be
                    // negative — a negative grant would silently shrink the semaphore.
                    assertTrue(
                        MeasurementCoreBudget.extraSlots(cpus, memoryMb, lowRam) >= 0
                    )
                }
            }
        }
    }

    @Test
    fun theShippedFloorIsTheDocumentedFour() {
        // V156 chose four when it removed the `check` spawn; V160 widens from there but never
        // replaces it, because four is also the pool every pre-V160 test asserts on.
        assertEquals(4, MeasurementCoreBudget.BASE)
        assertEquals(4, SingBoxManager.MAX_TEMPORARY_CORES)
    }
}
