package com.marbleng.app.jitter

/**
 * MARBLE_SMART_JITTER_V238 — the Smart Jitter Reducer's modes.
 *
 * The module is opt-in. OFF is the pre-module behaviour byte for byte: no probe thread, no
 * switches, no lock. BALANCED probes on a medium cadence and switches only on clear, repeated
 * evidence. AGGRESSIVE is for calls and games: a faster probe, a lower trigger threshold and the
 * low-latency Wi-Fi lock.
 *
 * Every knob the runtime needs is derived here, in pure Kotlin, so the tests and the settings
 * page read the same numbers the service runs with.
 */
enum class JitterMode(val id: String) {
    OFF("off"),
    BALANCED("balanced"),
    AGGRESSIVE("aggressive");

    companion object {
        fun fromId(raw: String?): JitterMode =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: OFF
    }
}

/**
 * The per-mode knobs. [probeIntervalMultiplier] scales the user's base probe interval,
 * [triggerScale] scales the jitter trigger threshold (lower = more sensitive), [wifiLowLatency]
 * asks the service to hold `WIFI_MODE_FULL_LOW_LATENCY` while the mode is active.
 */
data class JitterModeProfile(
    val mode: JitterMode,
    val probeIntervalMultiplier: Double,
    val triggerScale: Double,
    val wifiLowLatency: Boolean,
    val switchEnabled: Boolean
) {
    companion object {
        fun of(mode: JitterMode): JitterModeProfile = when (mode) {
            JitterMode.OFF -> JitterModeProfile(
                mode = mode,
                probeIntervalMultiplier = 1.0,
                triggerScale = 1.0,
                wifiLowLatency = false,
                switchEnabled = false
            )
            JitterMode.BALANCED -> JitterModeProfile(
                mode = mode,
                probeIntervalMultiplier = 1.0,
                triggerScale = 1.0,
                wifiLowLatency = false,
                switchEnabled = true
            )
            JitterMode.AGGRESSIVE -> JitterModeProfile(
                mode = mode,
                probeIntervalMultiplier = 0.5,
                triggerScale = 0.6,
                wifiLowLatency = true,
                switchEnabled = true
            )
        }
    }
}
