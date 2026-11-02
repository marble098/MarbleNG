package com.marbleng.app.model

/**
 * MARBLE_STEADY_CONNECT_V264 — the one truth behind all three connect controls.
 *
 * Before this pass the dock's centre trigger, the dock's power button and Home's connect pill each
 * resolved "what does a tap do?" from their own pair of booleans. They disagreed in three places:
 *
 *  - `CONNECTING` printed **Connect** on two of them, while a tap actually **cancelled** the dial;
 *  - `DISCONNECTING` stayed clickable on all three (the handler silently swallowed the tap);
 *  - `BLOCKED` (kill switch / startup failure) did not exist for any of them, so a reader saw an
 *    idle **Connect** control on a tunnel the core had already refused, with no way to reset it.
 *
 * A fourth problem was geometric rather than semantic: the centre trigger's *width* was an animated
 * state (a bloom for its state word and latency), and the island capsule's width was whatever its
 * reading happened to need. Both widths are measured inside the dock's `Row`, so connecting — or a
 * ping finishing — moved every tab beside them. [DockStudio.centerTriggerWidthDp] and
 * [DockStudio.islandWidthDp] now pin those widths; this file pins the *state*.
 *
 * Everything here is pure JVM: no Compose, no repository and no Android. The three controls can be
 * tested against one table in `ConnectControlV264Test`, and none of them can invent a fifth state.
 */
enum class ConnectPhase {
    /** No tunnel, no dial in flight: one tap connects. */
    IDLE,

    /** A dial is in flight: one tap cancels it. */
    CONNECTING,

    /** Traffic is protected: one tap disconnects. */
    CONNECTED,

    /** The tunnel is closing: no tap does anything until the state lands. */
    DISCONNECTING,

    /** Kill switch or a refused startup: one tap resets the runtime. */
    BLOCKED
}

/** The verb a tap on [ConnectPhase] performs. [NONE] means the control is not pressable. */
enum class ConnectAction { CONNECT, CANCEL, DISCONNECT, RESET, NONE }

object ConnectControl {

    /** Every state the repository can publish, including the two the old buttons forgot. */
    fun phaseOf(raw: String?): ConnectPhase = when (raw?.trim()?.uppercase()) {
        "DISCONNECTING" -> ConnectPhase.DISCONNECTING
        "CONNECTED" -> ConnectPhase.CONNECTED
        "CONNECTING" -> ConnectPhase.CONNECTING
        "BLOCKED" -> ConnectPhase.BLOCKED
        else -> ConnectPhase.IDLE
    }

    /** The boolean flavour used by callers that already own the four flags. */
    fun phaseOf(
        connected: Boolean,
        connecting: Boolean,
        disconnecting: Boolean,
        blocked: Boolean = false
    ): ConnectPhase = when {
        disconnecting -> ConnectPhase.DISCONNECTING
        connected -> ConnectPhase.CONNECTED
        connecting -> ConnectPhase.CONNECTING
        blocked -> ConnectPhase.BLOCKED
        else -> ConnectPhase.IDLE
    }

    fun actionOf(phase: ConnectPhase): ConnectAction = when (phase) {
        ConnectPhase.IDLE -> ConnectAction.CONNECT
        ConnectPhase.CONNECTING -> ConnectAction.CANCEL
        ConnectPhase.CONNECTED -> ConnectAction.DISCONNECT
        ConnectPhase.BLOCKED -> ConnectAction.RESET
        ConnectPhase.DISCONNECTING -> ConnectAction.NONE
    }

    /** A closing tunnel owns no verb: the button is visibly disabled, not a dead tap. */
    fun pressable(phase: ConnectPhase): Boolean = actionOf(phase) != ConnectAction.NONE

    /** Both ends of a transition show the same indeterminate activity. */
    fun working(phase: ConnectPhase): Boolean =
        phase == ConnectPhase.CONNECTING || phase == ConnectPhase.DISCONNECTING

    /**
     * English keys, resolved through `trx()` at the call site. Every key has a Persian answer in
     * the lexicon; the model holds the keys rather than translated strings so it stays pure.
     */
    fun actionKey(phase: ConnectPhase): String = when (phase) {
        ConnectPhase.IDLE -> "Connect"
        ConnectPhase.CONNECTING -> "Cancel connect"
        ConnectPhase.CONNECTED -> "Disconnect"
        ConnectPhase.BLOCKED -> "Reset"
        ConnectPhase.DISCONNECTING -> "Working\u2026"
    }

    /** The spoken state, used only as a `stateDescription`, never as a word drawn beside a glyph. */
    fun stateKey(phase: ConnectPhase): String = when (phase) {
        ConnectPhase.IDLE -> "Ready"
        ConnectPhase.CONNECTING -> "Connecting"
        ConnectPhase.CONNECTED -> "Connected"
        ConnectPhase.DISCONNECTING -> "Disconnecting"
        ConnectPhase.BLOCKED -> "Blocked"
    }
}
