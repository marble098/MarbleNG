package com.marbleng.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_EXPRESSIVE_SHELL_V241 — the decisions the redesigned shell is built on.
 *
 * The audit was about clutter, so these pins are about what replaced it: one mode chip with
 * three honest answers, one health scale with three calm states, one traffic window that can
 * be drawn at any speed, one log tail that keeps its story, and three presets that touch only
 * the knobs they name.
 */
class ExpressiveShellV241Test {

    // ------------------------------------------------------------------------------------
    // the mode chip
    // ------------------------------------------------------------------------------------

    @Test
    fun theModeChipHasExactlyThreeAnswersAndASafeDefault() {
        assertEquals(
            listOf(ConnectModeChip.SMART, ConnectModeChip.FULL, ConnectModeChip.LOCAL),
            ConnectModeChip.ordered
        )
        assertEquals(ConnectModeChip.SMART, ConnectModeChip.DEFAULT)
        assertEquals(ConnectModeChip.SMART, ConnectModeChip.fromId(null))
        assertEquals(ConnectModeChip.SMART, ConnectModeChip.fromId("  "))
        assertEquals(ConnectModeChip.SMART, ConnectModeChip.fromId("nonsense"))
        ConnectModeChip.ordered.forEach { chip ->
            assertEquals(chip, ConnectModeChip.fromId(chip.id))
            assertEquals(chip, ConnectModeChip.fromId(chip.id.uppercase()))
            assertTrue(chip.label.isNotBlank())
            assertTrue(chip.hint.isNotBlank())
        }
    }

    @Test
    fun theChipMirrorsThePersistedModeButSmartLeavesItAlone() {
        assertEquals(ConnectModeChip.FULL, connectModeChipOf(ConnectionMode.FULL_TUN))
        assertEquals(ConnectModeChip.LOCAL, connectModeChipOf(ConnectionMode.LOCAL_PROXY))

        val defaults = AppSettings()
        // SMART is a routing decision, not a transport decision: nothing is rewritten.
        assertEquals(defaults.connectionMode, ConnectModeChip.SMART.applyTo(defaults).connectionMode)
        assertEquals(
            ConnectionMode.FULL_TUN,
            ConnectModeChip.FULL.applyTo(defaults.copy(connectionMode = ConnectionMode.LOCAL_PROXY))
                .connectionMode
        )
        assertEquals(
            ConnectionMode.LOCAL_PROXY,
            ConnectModeChip.LOCAL.applyTo(defaults).connectionMode
        )
    }

    // ------------------------------------------------------------------------------------
    // server health
    // ------------------------------------------------------------------------------------

    @Test
    fun healthIsThreeCalmStatesNotFiveColours() {
        assertEquals(ServerHealth.GOOD, serverHealthOf(42.0, success = true))
        assertEquals(ServerHealth.GOOD, serverHealthOf(ServerHealth.SLOW_LINE_MS - 1.0, true))
        assertEquals(ServerHealth.SLOW, serverHealthOf(ServerHealth.SLOW_LINE_MS, true))
        assertEquals(ServerHealth.SLOW, serverHealthOf(9_000.0, true))
        // A failed probe is silence, never a "fast" zero.
        assertEquals(ServerHealth.DEAD, serverHealthOf(0.0, success = false))
        assertEquals(ServerHealth.DEAD, serverHealthOf(12.0, success = false))
        assertEquals(ServerHealth.UNTESTED, serverHealthOf(0.0, success = true))
        assertEquals(ServerHealth.UNTESTED, ServerHealth.fromId(null))
        assertEquals(ServerHealth.UNTESTED, ServerHealth.fromId("???"))
        ServerHealth.entries.forEach { assertEquals(it, ServerHealth.fromId(it.id)) }
    }

    // ------------------------------------------------------------------------------------
    // the traffic window
    // ------------------------------------------------------------------------------------

    @Test
    fun theWindowKeepsTheNewestSamplesAndForgetsTheRest() {
        val timeline = TrafficTimeline(capacity = 3)
        timeline.push(1L, 1L)
        timeline.push(2L, 0L)
        timeline.push(3L, 4L)
        assertEquals(3, timeline.size)
        timeline.push(10L, 0L)

        val snapshot = timeline.snapshot()
        assertEquals(3, snapshot.size)
        // Oldest first, newest last: the graph draws left to right.
        assertEquals(TrafficSample(2L, 0L), snapshot.first())
        assertEquals(TrafficSample(10L, 0L), snapshot.last())
        assertEquals(10L, timeline.peakBps())
        assertFalse(timeline.isQuiet())
        timeline.clear()
        assertEquals(0, timeline.size)
        assertTrue(timeline.isQuiet())
    }

    @Test
    fun aQuietWindowStaysOnTheFloorAndANoisyOneFillsTheHeight() {
        // Nothing to draw: no samples at all.
        assertTrue(trafficSeries(emptyList()).isEmpty())
        assertEquals(0L, trafficPeak(emptyList()))

        // An all-zero minute is a flat line on the floor, not a blank box.
        val quiet = trafficSeries(listOf(0L, 0L, 0L))
        assertEquals(3, quiet.size)
        quiet.forEach { assertEquals(MIN_FRACTION, it, 0.0001f) }

        // The window's own peak is 1.0, whatever the link speed is.
        val busy = trafficSeries(listOf(0L, 50L, 100L))
        assertEquals(MIN_FRACTION, busy[0], 0.0001f)
        assertEquals(0.5f, busy[1], 0.0001f)
        assertEquals(1f, busy[2], 0.0001f)

        // A slow link and a fast link have the same shape.
        assertEquals(
            trafficSeries(listOf(1L, 2L, 4L)),
            trafficSeries(listOf(1_000L, 2_000L, 4_000L))
        )
    }

    @Test
    fun thePolylineStartsAtTheLeftEdgeAndEndsAtTheRightOneWithYInverted() {
        val points = sparklinePoints(listOf(0f, 0.5f, 1f), width = 200f, height = 100f)
        assertEquals(3, points.size)
        assertEquals(0f, points[0].first, 0.001f)
        assertEquals(100f, points[0].second, 0.001f) // 0 rests on the bottom edge
        assertEquals(100f, points[1].first, 0.001f)
        assertEquals(50f, points[1].second, 0.001f)
        assertEquals(200f, points[2].first, 0.001f)
        assertEquals(0f, points[2].second, 0.001f) // 1 touches the top edge

        // Degenerate inputs are empty, never a division by zero.
        assertTrue(sparklinePoints(emptyList(), 200f, 100f).isEmpty())
        assertTrue(sparklinePoints(listOf(0.5f), 0f, 100f).isEmpty())
        // A single sample is a point on the left edge.
        val single = sparklinePoints(listOf(1f), 200f, 100f)
        assertEquals(1, single.size)
        assertEquals(0f, single[0].first, 0.001f)

        // The filled area is closed down to the bottom edge at both ends.
        val area = sparklineAreaPoints(listOf(0.2f, 0.8f), 100f, 50f)
        assertEquals(4, area.size)
        assertEquals(50f, area.first().second, 0.001f)
        assertEquals(50f, area.last().second, 0.001f)
        assertEquals(0f, area.first().first, 0.001f)
        assertEquals(100f, area.last().first, 0.001f)
    }

    // ------------------------------------------------------------------------------------
    // the quick-log overlay
    // ------------------------------------------------------------------------------------

    @Test
    fun theLogTailLosesItsHalfLineInsteadOfShowingItAsTruth() {
        val raw = "half a timestamp without a newline\n" +
            "2026-10-29 [INFO] core started\n" +
            "2026-10-29 [WARN] retry 1\n" +
            "2026-10-29 [ERROR] dial tcp: i/o timeout\n" +
            "\n"

        val lines = quickLogLines(raw, limit = 10)
        assertEquals(3, lines.size)
        // The partial first line is gone; the story starts at a whole line.
        assertTrue(lines.first().text.contains("core started"))
        assertEquals(LogLevel.INFO, lines[0].level)
        assertEquals(LogLevel.WARN, lines[1].level)
        assertEquals(LogLevel.ERROR, lines[2].level)

        // The limit keeps the newest lines, still oldest-first.
        val limited = quickLogLines(raw, limit = 2)
        assertEquals(2, limited.size)
        assertTrue(limited.first().text.contains("retry 1"))
        assertTrue(limited.last().text.contains("timeout"))

        assertTrue(quickLogLines("", limit = 10).isEmpty())
        assertTrue(quickLogLines(raw, limit = 0).isEmpty())
        assertTrue(quickLogLines("no newline at all", limit = 5).size == 1)
    }

    @Test
    fun everyLevelIsClassifiedFromTheCoresOwnWords() {
        assertEquals(LogLevel.ERROR, classifyLogLine("FATAL: inbound closed"))
        assertEquals(LogLevel.ERROR, classifyLogLine("dial failed"))
        assertEquals(LogLevel.WARN, classifyLogLine("WARNING: dns demoted"))
        assertEquals(LogLevel.WARN, classifyLogLine("connection timeout, will retry"))
        assertEquals(LogLevel.INFO, classifyLogLine("started xray 26.1.0"))
        assertEquals(LogLevel.DEBUG, classifyLogLine("debug: udp session"))
        assertEquals(LogLevel.PLAIN, classifyLogLine("hello"))
        assertEquals(LogLevel.DEBUG, LogLevel.fromId("debug"))
        assertEquals(LogLevel.PLAIN, LogLevel.fromId(null))
    }

    // ------------------------------------------------------------------------------------
    // the presets
    // ------------------------------------------------------------------------------------

    @Test
    fun aPresetMovesOnlyTheKnobsItNames() {
        val before = AppSettings()

        val gaming = SmartPreset.GAMING.apply(before)
        assertEquals(PowerProfile.TURBO.id, gaming.powerProfile)
        assertEquals(WorkloadProfile.INTERACTIVE, gaming.workloadProfile)
        assertEquals(JitterSmartMode.AGGRESSIVE.id, gaming.smartJitter.mode)
        assertTrue(gaming.raceConnectEnabled)

        val streaming = SmartPreset.STREAMING.apply(before)
        assertEquals(WorkloadProfile.STREAMING, streaming.workloadProfile)
        assertTrue(streaming.muxEnabled)
        assertEquals(JitterSmartMode.BALANCED.id, streaming.smartJitter.mode)
        assertTrue(streaming.adaptiveThroughputEnabled)

        val bypass = SmartPreset.EXTREME_BYPASS.apply(before)
        assertEquals(IranModePolicy.ALWAYS_ON, bypass.iranModePolicy)
        assertEquals(FragmentStrategy.RECORD.id, bypass.fragmentStrategy)
        assertTrue(bypass.mtuMax <= 1420)
        assertTrue(bypass.mtuMax >= bypass.mtuMin)
        assertTrue(bypass.raceWidth >= 6)

        // Identity and routing are never a preset's business.
        SmartPreset.ordered.forEach { preset ->
            val after = preset.apply(before)
            assertEquals(before.connectionMode, after.connectionMode)
            assertEquals(before.routingMode, after.routingMode)
            assertEquals(before.dnsPrimaryIp, after.dnsPrimaryIp)
            assertEquals(before.splitTunnelMode, after.splitTunnelMode)
            assertEquals(before.localProxyPort, after.localProxyPort)
            assertEquals(before.appLanguage, after.appLanguage)
            assertEquals(before.theme, after.theme)
            assertTrue(preset.title.isNotBlank())
            assertTrue(preset.body.isNotBlank())
            assertEquals(preset, SmartPreset.fromId(preset.id))
            assertEquals(preset, SmartPreset.fromId(preset.id.uppercase()))
        }
    }

    @Test
    fun theActivePresetIsDerivedFromTheSettingsNotRemembered() {
        val defaults = AppSettings()
        assertNull(matchSmartPreset(defaults))

        SmartPreset.ordered.forEach { preset ->
            val applied = preset.apply(defaults)
            assertEquals(preset, matchSmartPreset(applied))
            // Applying it twice changes nothing: the preset is a fixed point.
            assertEquals(applied, preset.apply(applied))
        }

        // A hand-tuned setting stops matching every preset, which is the honest answer.
        val tuned = SmartPreset.GAMING.apply(defaults).copy(workloadProfile = WorkloadProfile.STEALTH)
        assertNull(matchSmartPreset(tuned))

        assertNull(SmartPreset.fromId(null))
        assertNull(SmartPreset.fromId("nope"))
    }

    // ------------------------------------------------------------------------------------
    // the collapsing title
    // ------------------------------------------------------------------------------------

    @Test
    fun theTitleCollapsesOverTheFirstScreenfulAndNeverReExpands() {
        assertEquals(0f, headerCollapseFraction(0, 0), 0.0001f)
        assertEquals(0.5f, headerCollapseFraction(0, 60), 0.0001f)
        assertEquals(1f, headerCollapseFraction(0, 120), 0.0001f)
        assertEquals(1f, headerCollapseFraction(0, 9_999), 0.0001f)
        // Past the first item the header is already collapsed and stays there.
        assertEquals(1f, headerCollapseFraction(2, 0), 0.0001f)
        assertEquals(1f, headerCollapseFraction(0, 10, distancePx = 0), 0.0001f)
    }
}
