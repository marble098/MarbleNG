package com.marbleng.app.core

import java.util.concurrent.atomic.AtomicBoolean

class ProbeCancelGate {
    private val requested = AtomicBoolean(false)

    val isRequested: Boolean get() = requested.get()

    fun arm(): Boolean = requested.compareAndSet(false, true)

    fun reset() {
        requested.set(false)
    }

    fun mayStart(): Boolean = !requested.get()

    val shouldStop: () -> Boolean = { requested.get() }

    companion object {
        fun <T> retainedAfterCancel(results: List<T>): List<T> = results.toList()
    }
}
