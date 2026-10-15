package com.marbleng.app.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlin.math.max

object MeasurementCoreBudget {
    const val BASE = 4

    const val MAX = 8

    private const val WIDE = 6

    private const val MIN_MEMORY_CLASS_MB = 192

    private const val WIDE_MEMORY_CLASS_MB = 256

    fun ceiling(cpus: Int, memoryClassMb: Int, lowRam: Boolean = false): Int = when {
        lowRam || cpus < 6 || memoryClassMb < MIN_MEMORY_CLASS_MB -> BASE
        cpus >= 8 && memoryClassMb >= WIDE_MEMORY_CLASS_MB -> MAX
        else -> WIDE
    }

    fun extraSlots(cpus: Int, memoryClassMb: Int, lowRam: Boolean = false): Int =
        max(0, ceiling(cpus, memoryClassMb, lowRam) - BASE)

    fun read(context: Context): Int = runCatching {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memoryClassMb = manager?.memoryClass ?: 0
        val lowRam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            manager?.isLowRamDevice == true
        } else {
            false
        }
        extraSlots(
            cpus = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            memoryClassMb = memoryClassMb,
            lowRam = lowRam
        )
    }.getOrDefault(0)
}
