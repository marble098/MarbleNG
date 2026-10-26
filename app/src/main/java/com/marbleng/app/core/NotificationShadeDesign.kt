package com.marbleng.app.core

/**
 * MARBLE_SHADE_ATELIER_V234 — the pure half of the new ongoing-notification design.
 *
 * V231 decided *what* the shade says. This file decides *how the custom view is shaped* without
 * touching Android, so a host-JVM test can pin the rules that used to live in a `when` block that
 * was only reachable on a real phone:
 *
 *  - which of the five states owns which badge;
 *  - when the clock, the progress rail and the telemetry row are drawn (a chronometer next to an
 *    indeterminate bar reads as two clocks — V231's own finding, now enforced in the view);
 *  - how the platform's wall-clock session start becomes a `Chronometer` base, which is measured in
 *    `elapsedRealtime()`, not `currentTimeMillis()`.
 *
 * `RemoteViews` itself cannot exist on the JVM, which is exactly why none of the above is decided
 * there.
 */
enum class ShadeBadge {
    PROTECTED,
    CONNECTING,
    RECOVERING,
    BLOCKED,
    IDLE
}

object NotificationShadeDesign {
    const val MARKER: String = "MARBLE_SHADE_ATELIER_V234"

    /** The badge is the first thing the eye lands on: one drawing per state, never one for all. */
    fun badge(status: ConnectionStatus): ShadeBadge = when (status) {
        ConnectionStatus.PROTECTED -> ShadeBadge.PROTECTED
        ConnectionStatus.CONNECTING -> ShadeBadge.CONNECTING
        ConnectionStatus.RECOVERING -> ShadeBadge.RECOVERING
        ConnectionStatus.BLOCKED -> ShadeBadge.BLOCKED
        ConnectionStatus.DISCONNECTED -> ShadeBadge.IDLE
    }

    fun badgeId(status: ConnectionStatus): String = badge(status).name.lowercase()

    /** Only a live session has a clock; a connecting item has a rail instead. */
    fun clockVisible(status: ConnectionStatus, showTimer: Boolean): Boolean =
        showTimer && status == ConnectionStatus.PROTECTED

    /** The rail is the platform's indeterminate bar, and only while the route is being built. */
    fun progressVisible(status: ConnectionStatus): Boolean =
        status == ConnectionStatus.CONNECTING || status == ConnectionStatus.RECOVERING

    /**
     * Rates are only meaningful once traffic is moving. A blocked item has numbers that are frozen
     * and a disconnected one has none — printing either would be a measurement of nothing.
     */
    fun telemetryVisible(status: ConnectionStatus, showTelemetry: Boolean): Boolean =
        showTelemetry && (status == ConnectionStatus.PROTECTED || status == ConnectionStatus.RECOVERING)

    /**
     * `Chronometer.setBase()` is in `elapsedRealtime()` while a session start is a wall clock
     * (`currentTimeMillis()`), because `setWhen()` — the platform's own field — wants the wall
     * clock. One subtraction bridges the two; a session that has not started yet yields `0`, which
     * the view hides rather than counting from the epoch.
     */
    fun chronometerBase(sessionStartedAtMs: Long, nowWallMs: Long, nowElapsedMs: Long): Long {
        if (sessionStartedAtMs <= 0L) return 0L
        val elapsed = (nowWallMs - sessionStartedAtMs).coerceAtLeast(0L)
        return (nowElapsedMs - elapsed).coerceAtLeast(1L)
    }
}
