package com.marbleng.app.core

import java.util.concurrent.atomic.AtomicBoolean

/**
 * MARBLE_PING_CANCEL_V156 — the cancel latch every bulk measurement in the product obeys.
 *
 * "Ping all", a group ping, the Home group ping, Smart and Rank all run as one probe batch, so
 * they need one cancel and one place that decides what a cancel means. That decision is the part
 * worth stating precisely, because the obvious implementation gets it wrong:
 *
 *  - **Cancel is not abort.** A candidate that has not started yet is abandoned; a measurement
 *    that already finished is kept and stays on screen. Throwing away what was measured would
 *    make cancelling a 200-node sweep cost more than letting it finish.
 *  - **Cancel is idempotent.** Tapping stop twice must not restart the unwind or re-publish the
 *    "cancelling" state, so [arm] reports whether *this* call is the one that armed it.
 *  - **Cancel is reusable.** The latch belongs to the sweep, not to the app: [reset] clears it
 *    when the batch ends so the next sweep starts un-cancelled. Forgetting that would make every
 *    sweep after the first one exit immediately.
 *
 * The repository publishes the flag to the UI and interrupts the worker thread; the engines poll
 * [mayStart] at every candidate boundary.
 */
class ProbeCancelGate {

    private val requested = AtomicBoolean(false)

    /** True while a cancel is in force. This is the predicate the engines poll. */
    val isRequested: Boolean get() = requested.get()

    /**
     * Arms the latch. Returns `true` only for the call that actually armed it, which is what lets
     * a double tap be a no-op instead of a second, competing cancel.
     */
    fun arm(): Boolean = requested.compareAndSet(false, true)

    /** Clears the latch for the next sweep. Safe to call when it was never armed. */
    fun reset() {
        requested.set(false)
    }

    /** True when a candidate that has not started yet may still be started. */
    fun mayStart(): Boolean = !requested.get()

    /** The predicate handed to a sweep: `() -> Boolean` = "stop starting new work". */
    val shouldStop: () -> Boolean = { requested.get() }

    companion object {
        /**
         * What a cancel keeps. Finished results are published as they land, so the honest answer
         * to "what did I learn before I stopped?" is every result already in hand — never an
         * empty list, and never a fabricated failure for a candidate nobody looked at.
         */
        fun <T> retainedAfterCancel(results: List<T>): List<T> = results.toList()
    }
}
