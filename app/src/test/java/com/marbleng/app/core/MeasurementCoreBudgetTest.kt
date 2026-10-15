package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementCoreBudgetTest {
    @Test
    fun aSmallDeviceKeepsTheFloorItHasAlwaysHad() {
        assertEquals(MeasurementCoreBudget.BASE, MeasurementCoreBudget.ceiling(cpus = 4, memoryClassMb = 512))
        assertEquals(MeasurementCoreBudget.BASE, MeasurementCoreBudget.ceiling(cpus = 2, memoryClassMb = 256))

        assertEquals(
            MeasurementCoreBudget.BASE,
            MeasurementCoreBudget.ceiling(cpus = 8, memoryClassMb = 512, lowRam = true)
        )

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

                    assertTrue(
                        MeasurementCoreBudget.extraSlots(cpus, memoryMb, lowRam) >= 0
                    )
                }
            }
        }
    }

    @Test
    fun theShippedFloorIsTheDocumentedFour() {
        assertEquals(4, MeasurementCoreBudget.BASE)
        assertEquals(4, SingBoxManager.MAX_TEMPORARY_CORES)
    }
}
