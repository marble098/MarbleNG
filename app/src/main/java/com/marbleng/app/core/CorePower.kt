package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.PingBudget
import com.marbleng.app.model.PowerProfile
import com.marbleng.app.model.parsePowerProfile
import kotlin.math.max
import kotlin.math.min

/**
 * MARBLE_CORE_POWER_V221 — one dial over the tuning surface the two cores actually read.
 *
 * The product had grown twenty expert switches (multiplex, fast open, socket buffers, handshake
 * budgets, measurement concurrency) and no answer to the simplest question a user can ask: *make it
 * faster*. Worse, every switch was device-blind: a value that is right on an eight-core flagship is
 * a battery fire on a four-core phone with 2 GB of RAM.
 *
 * A [PowerProfile] is that answer, and it is deliberately a *projection* rather than a second source
 * of truth. It reads the device — [Device.cores], [Device.memoryMb] — and the profile, and returns
 * an [AppSettings] value. It never mutates the user's stored settings: the repository applies the
 * projection to the effective settings of a connection only, so switching back to Balanced restores
 * exactly what the user configured.
 *
 * The projection is bounded by three rules that exist because the cores reject values outside them:
 *
 *  * Every number is clamped to the same range the config builders clamp to (`PingBudget`,
 *    `muxConcurrency` 1..128, `xudpConcurrency` 1..1024, policy buffer 0..1024 KiB).
 *  * A profile never enables multiplexing for a transport that cannot carry it; mux on REALITY or
 *    on XTLS-Vision is the documented "connects, then nothing loads" failure. The caller decides
 *    with [muxSafeFor]; this file refuses when told the route is unsafe.
 *  * No profile touches identity, DNS fail-closed behaviour, routing, certificate verification or
 *    address-family policy. Speed may never buy those back.
 */
object CorePower {
    const val MARKER: String = "MARBLE_CORE_POWER_V221"

    const val MIN_BUFFER_KB: Int = 0
    const val MAX_BUFFER_KB: Int = 1024

    const val MUX_MIN: Int = 1
    const val MUX_MAX: Int = 128
    const val XUDP_MIN: Int = 1
    const val XUDP_MAX: Int = 1024

    /** The device as the projectors see it. Both fields are already measured by the repository. */
    data class Device(val cores: Int, val memoryMb: Long) {
        val coresSafe: Int get() = cores.coerceIn(1, 32)

        /** 0 = unknown/small, 1 = 2 GB, 2 = 4 GB, 3 = 6 GB or more. */
        val ramTier: Int
            get() = when {
                memoryMb <= 0L -> 1
                memoryMb < 2_500L -> 1
                memoryMb < 5_000L -> 2
                memoryMb < 7_500L -> 3
                else -> 3
            }
    }

    /**
     * True when the selected route can carry the pinned cores' multiplexer.
     *
     * The policy lives in [SingBoxTransportPolicy] and [XrayConfigHardener] already; this is the
     * single question a power projector needs answered, and it is expressed here so a test can pin
     * it without constructing a whole profile.
     */
    fun muxSafeFor(profile: com.marbleng.app.model.ProxyProfile?): Boolean =
        !SingBoxTransportPolicy.muxIsUnsafeFor(profile)

    fun profileOf(settings: AppSettings): PowerProfile = parsePowerProfile(settings.powerProfile)

    /** The multiplier the measurement pools use, so a Turbo phone sweeps in parallel and an
     *  Efficient phone does not wake every core for a ping. */
    fun measurementConcurrency(profile: PowerProfile, device: Device, configured: Int): Int {
        val scaled = when (profile) {
            PowerProfile.EFFICIENT -> min(configured, max(2, device.coresSafe))
            PowerProfile.BALANCED -> configured
            PowerProfile.TURBO -> max(configured, device.coresSafe * 3)
            PowerProfile.EXTREME -> max(configured, device.coresSafe * 4)
        }
        return PingBudget.concurrency(scaled)
    }

    fun tcpWorkers(profile: PowerProfile, device: Device, configured: Int): Int {
        val floor = configured.coerceIn(4, 96)
        return when (profile) {
            PowerProfile.EFFICIENT -> min(floor, max(4, device.coresSafe * 2))
            PowerProfile.BALANCED -> floor
            PowerProfile.TURBO -> max(floor, device.coresSafe * 6).coerceAtMost(64)
            PowerProfile.EXTREME -> max(floor, device.coresSafe * 8).coerceAtMost(96)
        }
    }

    fun bufferKb(profile: PowerProfile, device: Device): Int = when (profile) {
        PowerProfile.EFFICIENT -> 0
        PowerProfile.BALANCED -> 0
        PowerProfile.TURBO -> if (device.ramTier >= 2) 64 else 32
        PowerProfile.EXTREME -> if (device.ramTier >= 2) 128 else 64
    }.coerceIn(MIN_BUFFER_KB, MAX_BUFFER_KB)

    fun muxConcurrency(profile: PowerProfile, device: Device, configured: Int): Int {
        val base = configured.coerceIn(MUX_MIN, MUX_MAX)
        return when (profile) {
            PowerProfile.EFFICIENT -> 1
            PowerProfile.BALANCED -> base
            PowerProfile.TURBO -> max(base, min(device.coresSafe, 8))
            PowerProfile.EXTREME -> max(base, min(device.coresSafe * 2, 16))
        }.coerceIn(MUX_MIN, MUX_MAX)
    }

    fun xudpConcurrency(profile: PowerProfile, configured: Int): Int {
        val base = configured.coerceIn(XUDP_MIN, XUDP_MAX)
        return when (profile) {
            PowerProfile.EFFICIENT -> min(base, 4)
            PowerProfile.BALANCED -> base
            PowerProfile.TURBO -> max(base, 16)
            PowerProfile.EXTREME -> max(base, 32)
        }.coerceIn(XUDP_MIN, XUDP_MAX)
    }

    /**
     * Project [base] onto the chosen profile.
     *
     * Balanced is the identity function on purpose: the default install must be byte-for-byte the
     * settings the user configured, and a test pins that.
     */
    fun tune(
        base: AppSettings,
        profileId: String,
        device: Device,
        muxSafe: Boolean = true
    ): AppSettings {
        val profile = parsePowerProfile(profileId)
        if (profile == PowerProfile.BALANCED) return base

        var next = base
        val mux = when (profile) {
            PowerProfile.EFFICIENT -> false
            else -> muxSafe
        }
        val buffer = bufferKb(profile, device)

        next = next.copy(
            pingConcurrency = measurementConcurrency(profile, device, base.pingConcurrency),
            tcpWorkers = tcpWorkers(profile, device, base.tcpWorkers),
            muxEnabled = mux && (base.muxEnabled || profile == PowerProfile.TURBO || profile == PowerProfile.EXTREME),
            muxConcurrency = muxConcurrency(profile, device, base.muxConcurrency),
            muxXudpConcurrency = xudpConcurrency(profile, base.muxXudpConcurrency),
            tcpFastOpenEnabled = when (profile) {
                PowerProfile.EFFICIENT -> false
                else -> true
            },
            adaptiveBufferEnabled = profile != PowerProfile.EFFICIENT,
            thermalAwareEnabled = profile != PowerProfile.EXTREME,
            xrayPolicyBufferSizeKb = if (buffer > 0) buffer else base.xrayPolicyBufferSizeKb,
            xrayTcpNoDelay = profile == PowerProfile.TURBO || profile == PowerProfile.EXTREME,
            xrayTcpKeepAliveIntervalSec = when (profile) {
                PowerProfile.TURBO -> max(base.xrayTcpKeepAliveIntervalSec, 30)
                PowerProfile.EXTREME -> max(base.xrayTcpKeepAliveIntervalSec, 15)
                else -> base.xrayTcpKeepAliveIntervalSec
            },
            benchSamples = when (profile) {
                PowerProfile.EFFICIENT -> min(base.benchSamples, 2).coerceAtLeast(1)
                PowerProfile.TURBO -> max(base.benchSamples, 3)
                PowerProfile.EXTREME -> max(base.benchSamples, 3)
                PowerProfile.BALANCED -> base.benchSamples
            },
            singBoxMuxEnabled = mux && (base.singBoxMuxEnabled || profile == PowerProfile.TURBO || profile == PowerProfile.EXTREME),
            singBoxMuxMaxConnections = when (profile) {
                PowerProfile.EFFICIENT -> 2
                PowerProfile.TURBO -> max(base.singBoxMuxMaxConnections, min(device.coresSafe, 8))
                PowerProfile.EXTREME -> max(base.singBoxMuxMaxConnections, min(device.coresSafe * 2, 16))
                PowerProfile.BALANCED -> base.singBoxMuxMaxConnections
            },
            singBoxMuxMaxStreams = when (profile) {
                PowerProfile.EFFICIENT -> min(base.singBoxMuxMaxStreams, 16)
                PowerProfile.TURBO -> max(base.singBoxMuxMaxStreams, 64)
                PowerProfile.EXTREME -> max(base.singBoxMuxMaxStreams, 128)
                PowerProfile.BALANCED -> base.singBoxMuxMaxStreams
            },
            liveTuningPingTriggerMs = when (profile) {
                PowerProfile.EFFICIENT -> max(base.liveTuningPingTriggerMs, 300)
                PowerProfile.TURBO -> min(base.liveTuningPingTriggerMs, 180)
                PowerProfile.EXTREME -> min(base.liveTuningPingTriggerMs, 150)
                PowerProfile.BALANCED -> base.liveTuningPingTriggerMs
            }
        )

        // Multiplexing that the route cannot carry is removed, not merely disabled: a leftover
        // `muxEnabled = true` on a REALITY node is the exact shape the hardener strips at the last
        // moment, and doing it here keeps the effective settings honest for every reader.
        if (!mux) {
            next = next.copy(muxEnabled = false, singBoxMuxEnabled = false)
        }
        return next
    }

    fun describe(profileId: String): String = when (parsePowerProfile(profileId)) {
        PowerProfile.EFFICIENT ->
            "Fewest sockets, no multiplexing, measurements capped to the phone's own cores."
        PowerProfile.BALANCED ->
            "Your settings exactly as configured."
        PowerProfile.TURBO ->
            "Multiplexing, TCP fast open and larger socket buffers, sized to this device."
        PowerProfile.EXTREME ->
            "Every acceleration at once, including the largest buffers this device can hold."
    }

    /** One line for the Settings hub, e.g. "Turbo • 8 cores • 8 GB". */
    fun summary(profileId: String, device: Device): String {
        val profile = parsePowerProfile(profileId)
        val memory = if (device.memoryMb > 0L) {
            "${(device.memoryMb / 1024L).coerceAtLeast(1L)} GB"
        } else {
            "memory unknown"
        }
        return "${profile.label} • ${device.coresSafe} cores • $memory"
    }
}
