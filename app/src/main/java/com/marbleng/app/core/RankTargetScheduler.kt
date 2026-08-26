package com.marbleng.app.core

/**
 * Fair batch target rotation. All nodes on one network/time epoch receive the same endpoint order,
 * eliminating profile-id target bias while still rotating the first origin over time.
 */
object RankTargetScheduler {
    fun <T> ordered(
        targets: List<T>,
        networkKey: String,
        nowMs: Long = System.currentTimeMillis(),
        epochMs: Long = 30L * 60L * 1000L
    ): List<T> {
        if (targets.size < 2) return targets.toList()
        require(epochMs > 0L) { "epochMs must be positive" }
        val epoch = nowMs.coerceAtLeast(0L) / epochMs
        val seed = networkKey.hashCode().toLong() xor epoch
        val start = Math.floorMod(seed, targets.size.toLong()).toInt()
        return targets.indices.map { offset -> targets[(start + offset) % targets.size] }
    }
}
