package com.marbleng.app.core

internal class DnsStormGuard {
    private val events = ArrayDeque<Long>()

    @Synchronized
    fun recordDeadlineFailures(count: Int, nowMs: Long) {
        repeat(count.coerceIn(0, 64)) { events.addLast(nowMs) }
        trim(nowMs)
    }

    @Synchronized
    fun recordProvenAnswer(nowMs: Long) {
        trim(nowMs)
        if (events.isEmpty()) return
        repeat(events.size / 2 + 1) { if (events.isNotEmpty()) events.removeFirst() }
    }

    @Synchronized
    fun armMs(nowMs: Long): Long? {
        trim(nowMs)
        val recent = events.count { nowMs - it <= STORM_ARM_WINDOW_MS }
        return if (recent >= STORM_ARM_EVENTS) nowMs else null
    }

    @Synchronized
    fun active(nowMs: Long): Boolean {
        trim(nowMs)
        val recent = events.count { nowMs - it <= STAND_DOWN_WINDOW_MS }
        return recent > STORM_QUIET_EVENTS
    }

    @Synchronized
    fun eventsPerMinute(nowMs: Long): Double {
        trim(nowMs)
        val recent = events.count { nowMs - it <= STORM_ARM_WINDOW_MS }
        return recent / (STORM_ARM_WINDOW_MS / 60_000.0)
    }

    @Synchronized
    private fun trim(nowMs: Long) {
        val horizon = maxOf(STORM_ARM_WINDOW_MS, STAND_DOWN_WINDOW_MS)
        while (events.isNotEmpty() && nowMs - events.first() > horizon) events.removeFirst()
        while (events.size > 512) events.removeFirst()
    }

    companion object {
        const val STORM_ARM_WINDOW_MS = 5L * 60_000L
        const val STORM_ARM_EVENTS = 3
        const val STAND_DOWN_WINDOW_MS = 10L * 60_000L
        const val STORM_QUIET_EVENTS = 1
    }
}
