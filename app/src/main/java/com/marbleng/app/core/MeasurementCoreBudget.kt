package com.marbleng.app.core

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import kotlin.math.max

/**
 * MARBLE_PING_SPEED_V160 — how wide the measurement-core pool may be on this device.
 *
 * Every URL test and every sing-box Real delay owns a native core process: the measurement is
 * the core's own `GET /proxies/{tag}/delay` (or an HTTPS round trip through its SOCKS inbound),
 * so a node cannot be measured without one. The pool was fixed at four, which was right when a
 * measurement also paid a `sing-box check` spawn (V156 removed it) — but four also means a
 * hundred-node URL test runs in twenty-five waves, and every wave waits for the slowest of its
 * four spawns before the next one begins.
 *
 * The ceiling is a property of the device, not of the subscription: a phone with eight cores and
 * a 512 MB heap budget can start eight children while the first four are waiting on the network,
 * and a 2 GB handset cannot. So the number is computed from what Android reports about the
 * device, once, and the pool is widened by the difference — the four-core floor never moves, and
 * a device that cannot carry more runs exactly the pool it has always run.
 *
 * Pure policy in [ceiling] and one Android read in [read], so the rule is unit-testable and the
 * only platform call in this file is the one that has to be.
 */
object MeasurementCoreBudget {

    /** The pool every device gets: the ceiling that shipped with V156 and must never move. */
    const val BASE = 4

    /** The widest pool the policy may ever grant. */
    const val MAX = 8

    /** A pool of six: enough to overlap two waves on a modern mid-range phone. */
    private const val WIDE = 6

    /** Smallest heap budget (MB) a device may report before a widened pool is considered. */
    private const val MIN_MEMORY_CLASS_MB = 192

    /** Heap budget (MB) a device must report before the widest pool is granted. */
    private const val WIDE_MEMORY_CLASS_MB = 256

    /**
     * How many measurement cores may run at once on a device with [cpus] cores, a per-app heap
     * budget of [memoryClassMb] and [lowRam] as Android reports it.
     *
     * Deliberately conservative: widening the pool buys throughput for a sweep and costs memory
     * for the whole app, so the default answer is [BASE] and every step up has to be earned.
     */
    fun ceiling(cpus: Int, memoryClassMb: Int, lowRam: Boolean = false): Int = when {
        lowRam || cpus < 6 || memoryClassMb < MIN_MEMORY_CLASS_MB -> BASE
        cpus >= 8 && memoryClassMb >= WIDE_MEMORY_CLASS_MB -> MAX
        else -> WIDE
    }

    /** How many slots [ceiling] adds to the shipped [BASE] pool; never negative. */
    fun extraSlots(cpus: Int, memoryClassMb: Int, lowRam: Boolean = false): Int =
        max(0, ceiling(cpus, memoryClassMb, lowRam) - BASE)

    /**
     * [extraSlots] for the device this app is running on.
     *
     * Everything here is best effort: a device that refuses to describe itself gets the pool it
     * has always had, which is the same answer as a device that describes itself as small.
     */
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
