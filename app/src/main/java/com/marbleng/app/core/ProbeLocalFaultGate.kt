package com.marbleng.app.core

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — the second latch a probe sweep obeys.
 *
 * [ProbeCancelGate] answers "did a person press stop?". This one answers the question nobody was
 * asking: **"is the device still able to measure anything at all?"**
 *
 * ## The failure it exists for
 *
 * An Android sing-box build without the nil-interface-monitor fix panics in its `direct` outbound
 * for *every* profile. A sweep over 17 servers therefore spawned 17 (× up to 3 candidate readers,
 * × retries) processes that each died in ~200 ms with the same SIGSEGV, and published:
 *
 * ```
 * URL test   → reachable = 0 of 17
 * Real delay → reachable = 0 of 17
 * ```
 *
 * Both lines are literally true and completely misleading: no server was ever reached because no
 * measurement ever started. The sweep kept going because every individual result looked like an
 * ordinary failed node, and nothing in the product compares result *N* with result *N−1*.
 *
 * ## What trips it, and what deliberately does not
 *
 * A trip means "the remaining candidates would fail for a reason that has nothing to do with
 * them", so the predicate is narrower than [CoreFailurePolicy.isLocal] — which is about *ranking*
 * (never punish a profile for a local fault) and is far broader.
 *
 * Trips: a crashed / unusable core ([SingBoxAndroidRuntime.isUnusableCore], the exact signature in
 * the report above), a missing or unstartable binary (`core-install:`, `core-assets:`,
 * `core-exit:`, `core-crash:`, `core-selftest:`), an exhausted loopback port pool (`core-port:`),
 * and a sweep asked to measure through a tunnel that is not up (`no-live-tunnel`,
 * `core-unavailable:`). None of those change while the sweep runs.
 *
 * Does not trip: a per-node refusal (`core-config:`, `config-unsupported:` — the *next* profile may
 * translate perfectly), measurement capacity (`core-busy:` — a slot frees up in milliseconds), a
 * start-up timeout (`core-start-timeout:`, `core-check-timeout:` — slow is not broken), and every
 * network-shaped reason (`urltest-*`), which is the observation the sweep exists to collect.
 *
 * ## Contract
 *
 *  - **Idempotent.** [trip] returns `true` only for the call that armed it, so the repository
 *    publishes one honest message and interrupts the worker exactly once per sweep.
 *  - **Reusable.** [reset] at both ends of a batch: a fault found this morning must not silently
 *    kill the sweep the user starts after fixing it.
 *  - **Thread-safe.** Sweeps run several workers over one batch; the flag and the reason are
 *    atomics, and a worker may observe a fault tripped by a sibling.
 *  - **Additive.** It never replaces the cancel latch. The repository ORs the two into the single
 *    `shouldStop` predicate the engines poll, so a fault-stopped sweep unwinds through exactly the
 *    same code path — and keeps the results already measured — as a cancelled one.
 */
class ProbeLocalFaultGate {

    private val tripped = AtomicBoolean(false)
    private val firstReason = AtomicReference("")

    /** True while a local fault is in force. Polled by the sweep alongside the cancel latch. */
    val isTripped: Boolean get() = tripped.get()

    /** The reason that tripped the gate, `""` when it never did. Cleared by [reset]. */
    val reason: String get() = firstReason.get()

    /**
     * Classifies one finished measurement and arms the gate when it proves the device cannot
     * measure. Returns `true` only for the call that armed it.
     *
     * @param success the result's own success count; anything above zero is an observation, and an
     *   observation never trips this gate whatever else its reason string says.
     */
    fun trip(reason: String, success: Int): Boolean {
        if (!isSweepFatal(reason, success)) return false
        firstReason.compareAndSet("", reason)
        return tripped.compareAndSet(false, true)
    }

    /** Arms the gate directly, for a fault found outside a per-node result (e.g. the self-test). */
    fun tripNow(reason: String): Boolean {
        firstReason.compareAndSet("", reason)
        return tripped.compareAndSet(false, true)
    }

    /** Clears the latch and its reason for the next sweep. Safe to call when never tripped. */
    fun reset() {
        tripped.set(false)
        firstReason.set("")
    }

    /** True when a candidate that has not started yet may still be started. */
    fun mayStart(): Boolean = !tripped.get()

    /** The predicate handed to a sweep, mirroring [ProbeCancelGate.shouldStop]. */
    val shouldStop: () -> Boolean = { tripped.get() }

    /**
     * The classification itself, kept public and pure so a test can pin the boundary: the reasons
     * that must stop a sweep, and — more importantly — the ones that must not.
     */
    fun isSweepFatal(reason: String, success: Int): Boolean {
        if (success > 0 || reason.isBlank()) return false
        val value = reason.lowercase()
        // Per-node refusals: the next profile is a different document and may be fine.
        if (value.startsWith("core-config:") || value.startsWith("config-unsupported:")) return false
        // Capacity and slowness are transient inside a sweep.
        if (value.startsWith("core-busy:") ||
            value.startsWith("core-start-timeout:") ||
            value.startsWith("core-check-timeout:")
        ) {
            return false
        }
        // A Go panic, the app-UID package-manager probe or the netlink ban: identical for every
        // candidate, and the exact fault that once printed "reachable = 0 of 17".
        if (SingBoxAndroidRuntime.isUnusableCore(reason)) return true
        return FATAL_PREFIXES.any { value.startsWith(it) }
    }

    companion object {
        /**
         * Local faults that cannot be fixed by trying another node. Deliberately excludes the
         * network-shaped `urltest-*` reasons: those are the sweep's actual subject matter.
         */
        private val FATAL_PREFIXES = listOf(
            "core-install:",
            "core-assets:",
            "core-crash:",
            "core-selftest:",
            "core-exit:",
            "core-port:",
            "core-unavailable:",
            "no-live-tunnel",
            "singbox-unavailable",
            "urltest-requires-singbox"
        )

        /**
         * The sentence a stopped sweep shows.
         *
         * Fixed wording per recognised fault, because the evidence itself is a Go traceback: the
         * first non-blank line of `core-start: exited 2: …` is the package-manager WARN that
         * *preceded* the panic, and a message bar that prints it invites exactly the misreading
         * this gate exists to prevent. The full reason stays available through [reason], the
         * diagnostics event and the retained core log.
         */
        fun summary(reason: String): String = when {
            SingBoxAndroidRuntime.isCoreCrash(reason) ->
                "the tunnel core crashed while starting"
            SingBoxAndroidRuntime.isPackageManagerFault(reason) ->
                "the tunnel core could not read Android's package list"
            SingBoxAndroidRuntime.isNetlinkBan(reason) ->
                "the tunnel core asked Android for a netlink monitor an app process may not open"
            reason.startsWith("core-install:") ->
                "the tunnel core binary is missing from this build"
            reason.startsWith("core-assets:") ->
                "the tunnel core's bundled rule sets are missing or corrupt"
            reason.startsWith("core-port:") ->
                "Android would not allocate loopback ports for a measurement core"
            reason.startsWith("no-live-tunnel") ||
                reason.startsWith("singbox-unavailable") ||
                reason.startsWith("core-unavailable:") ->
                "there is no live tunnel to measure through"
            else -> headline(reason)
        }

        /**
         * The evidence line, bounded: the fatal signature if the reason carries one, otherwise its
         * first non-blank line, with any remediation paragraph [SingBoxAndroidRuntime.explain]
         * appended stripped off (a headline is not the place for a paragraph).
         */
        fun headline(reason: String): String {
            val withoutRemedy = REMEDIATIONS.fold(reason) { text, remedy ->
                val index = text.indexOf(" — $remedy")
                if (index >= 0) text.substring(0, index) else text
            }
            val lines = withoutRemedy.lines()
            val fatal = lines.firstOrNull { line ->
                val value = line.trim().lowercase()
                value.startsWith("panic:") || value.startsWith("fatal error:") ||
                    value.startsWith("[signal ")
            }
            val line = (fatal ?: lines.firstOrNull { it.isNotBlank() } ?: withoutRemedy).trim()
            return if (line.length > 200) line.take(200).trimEnd() + "…" else line
        }

        /** The remediation paragraphs [SingBoxAndroidRuntime.explain] can append to a reason. */
        private val REMEDIATIONS = listOf(
            SingBoxAndroidRuntime.CRASH_REMEDIATION,
            SingBoxAndroidRuntime.PACKAGE_MANAGER_REMEDIATION,
            SingBoxAndroidRuntime.NETLINK_REMEDIATION
        )
    }
}
