package com.marbleng.app.ui

// MARBLE_ROUTE_ATELIER_V207 — the design contract, written down as code.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object MarbleTapTarget {
    val Floor: Dp = 48.dp
}

fun Modifier.marbleTapTarget(
    minimum: Dp = MarbleTapTarget.Floor,
    enabled: Boolean = true
): Modifier = if (!enabled) this else layout { measurable, constraints ->
    val floor = minimum.roundToPx()
    val maxW = constraints.maxWidth
    val maxH = constraints.maxHeight
    val minW = floor.coerceAtMost(maxW).coerceAtLeast(constraints.minWidth)
    val minH = floor.coerceAtMost(maxH).coerceAtLeast(constraints.minHeight)
    val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    val width = placeable.width.coerceIn(minW, maxW)
    val height = placeable.height.coerceIn(minH, maxH)
    layout(width, height) {
        placeable.placeRelative((width - placeable.width) / 2, (height - placeable.height) / 2)
    }
}

enum class MarbleRouteState {
    NO_ROUTE,

    READY,

    SECURING,

    CONNECTED,

    CLOSING,

    BLOCKED
}

fun marbleRouteStateOf(
    hasRoute: Boolean,
    connected: Boolean,
    connecting: Boolean,
    disconnecting: Boolean,
    blocked: Boolean
): MarbleRouteState = when {
    blocked -> MarbleRouteState.BLOCKED
    disconnecting -> MarbleRouteState.CLOSING
    connecting -> MarbleRouteState.SECURING
    connected -> MarbleRouteState.CONNECTED
    !hasRoute -> MarbleRouteState.NO_ROUTE
    else -> MarbleRouteState.READY
}

enum class MarbleConnectVerb { ADD_ROUTE, CONNECT, CANCEL, DISCONNECT, RESET, WAIT }

fun MarbleRouteState.connectVerb(): MarbleConnectVerb = when (this) {
    MarbleRouteState.NO_ROUTE -> MarbleConnectVerb.ADD_ROUTE
    MarbleRouteState.READY -> MarbleConnectVerb.CONNECT
    MarbleRouteState.SECURING -> MarbleConnectVerb.CANCEL
    MarbleRouteState.CONNECTED -> MarbleConnectVerb.DISCONNECT
    MarbleRouteState.CLOSING -> MarbleConnectVerb.WAIT
    MarbleRouteState.BLOCKED -> MarbleConnectVerb.RESET
}

fun MarbleRouteState.isActionable(): Boolean = this != MarbleRouteState.CLOSING

enum class MarbleStateHue { BRAND, SUCCESS, CAUTION, DANGER, SECURE, MUTED }

fun MarbleRouteState.stateHue(): MarbleStateHue = when (this) {
    MarbleRouteState.NO_ROUTE -> MarbleStateHue.MUTED
    MarbleRouteState.READY -> MarbleStateHue.BRAND
    MarbleRouteState.SECURING -> MarbleStateHue.SECURE
    MarbleRouteState.CONNECTED -> MarbleStateHue.SUCCESS
    MarbleRouteState.CLOSING -> MarbleStateHue.CAUTION
    MarbleRouteState.BLOCKED -> MarbleStateHue.DANGER
}

@Composable
internal fun MarbleStateHue.tone(): Color = when (this) {
    MarbleStateHue.BRAND -> Aether.Cyan
    MarbleStateHue.SUCCESS -> Aether.Emerald
    MarbleStateHue.CAUTION -> Aether.Amber
    MarbleStateHue.DANGER -> Aether.Danger
    MarbleStateHue.SECURE -> Aether.Amethyst
    MarbleStateHue.MUTED -> Aether.InkMuted
}

@Composable
internal fun marbleRouteTone(state: MarbleRouteState): Color = state.stateHue().tone()

enum class MarbleNodeState { UNKNOWN, PENDING, PROVEN, FAILED }

data class MarbleRoutePath(
    val device: MarbleNodeState,
    val tunnel: MarbleNodeState,
    val egress: MarbleNodeState
) {
    val provenCount: Int
        get() = listOf(device, tunnel, egress).count { it == MarbleNodeState.PROVEN }

    val isComplete: Boolean get() = provenCount == 3
}

fun marbleRoutePathOf(
    state: MarbleRouteState,
    egressResolved: Boolean,
    egressFailed: Boolean
): MarbleRoutePath {
    val device = if (state == MarbleRouteState.NO_ROUTE) MarbleNodeState.UNKNOWN else MarbleNodeState.PROVEN
    val tunnel = when (state) {
        MarbleRouteState.SECURING -> MarbleNodeState.PENDING
        MarbleRouteState.CONNECTED -> MarbleNodeState.PROVEN
        MarbleRouteState.CLOSING -> MarbleNodeState.PENDING
        MarbleRouteState.BLOCKED -> MarbleNodeState.FAILED
        MarbleRouteState.NO_ROUTE, MarbleRouteState.READY -> MarbleNodeState.UNKNOWN
    }
    val egress = when {
        egressFailed -> MarbleNodeState.FAILED
        state != MarbleRouteState.CONNECTED && state != MarbleRouteState.CLOSING ->
            MarbleNodeState.UNKNOWN
        egressResolved -> MarbleNodeState.PROVEN
        else -> MarbleNodeState.PENDING
    }
    return MarbleRoutePath(device = device, tunnel = tunnel, egress = egress)
}

enum class MarbleLocationTrust {
    UNKNOWN,

    LABEL_GUESS,

    SESSION_REPORT,

    MEASURED_PROVISIONAL,

    MEASURED_VERIFIED
}

fun MarbleLocationTrust.mayDrawFlag(): Boolean = when (this) {
    MarbleLocationTrust.SESSION_REPORT,
    MarbleLocationTrust.MEASURED_PROVISIONAL,
    MarbleLocationTrust.MEASURED_VERIFIED -> true
    MarbleLocationTrust.UNKNOWN, MarbleLocationTrust.LABEL_GUESS -> false
}

fun marbleLocationTrustOf(
    hasSessionReport: Boolean,
    hasMeasuredCode: Boolean,
    measuredIsProvisional: Boolean,
    hasLabelGlyph: Boolean
): MarbleLocationTrust = when {
    hasSessionReport -> MarbleLocationTrust.SESSION_REPORT
    hasMeasuredCode && !measuredIsProvisional -> MarbleLocationTrust.MEASURED_VERIFIED
    hasMeasuredCode -> MarbleLocationTrust.MEASURED_PROVISIONAL
    hasLabelGlyph -> MarbleLocationTrust.LABEL_GUESS
    else -> MarbleLocationTrust.UNKNOWN
}

fun MarbleLocationTrust.isVerified(): Boolean = this == MarbleLocationTrust.MEASURED_VERIFIED

object MarbleMotionPolicy {
    fun entranceDelayMs(index: Int, motionEnabled: Boolean): Long =
        if (!motionEnabled) 0L else ExpressiveMath.staggerDelayMs(index)

    fun animates(motionEnabled: Boolean): Boolean = motionEnabled

    fun pressScale(control: MarbleControlKind, motionEnabled: Boolean): Float =
        if (!motionEnabled) 1f else control.pressScale
}

enum class MarbleControlKind(
    val pressScale: Float,
    val radius: Dp,
    val minHeight: Dp
) {
    Primary(.955f, 14.dp, 56.dp),
    Standard(.965f, 15.dp, 46.dp),
    Compact(.972f, 11.dp, 36.dp),
    Icon(.955f, 15.dp, 42.dp);
}

fun MarbleControlKind.acknowledgesPress(motionEnabled: Boolean): Boolean = motionEnabled

object MarbleConnectMotion {
    val statePopPeak: Float = 1.02f

    const val StateColorMs: Int = 200
}

internal val LocalMarbleAmbientField = staticCompositionLocalOf { true }

object MarbleFeedbackPolicy {
    const val OutcomeDwellMs: Long = 4_500L

    const val ActionRequiredDwellMs: Long = 0L

    fun dwellMillis(actionRequired: Boolean): Long =
        if (actionRequired) ActionRequiredDwellMs else OutcomeDwellMs

    fun isOutcome(message: String): Boolean = message.isNotBlank()
}

object MarbleCopy {
    const val MaxDescriptionChars: Int = 140

    const val Ellipsis: String = "…"

    private val WHITESPACE = Regex("\\s+")

    private val Abbreviations: Set<String> = setOf(
        "e.g", "i.e", "etc", "vs", "approx", "ca", "cf", "al", "Inc", "Ltd", "No", "Dr", "Mr", "Mrs"
    )

    fun flatten(raw: String): String = raw.replace(WHITESPACE, " ").trim()

    fun isOneSentence(raw: String): Boolean {
        val flat = flatten(raw)
        if (flat.isEmpty()) return true
        return flat.length <= MaxDescriptionChars && firstSentenceEnd(flat) >= flat.length
    }

    fun oneSentence(raw: String): String {
        val flat = flatten(raw)
        if (flat.isEmpty()) return ""
        val end = firstSentenceEnd(flat)
        val sentence = flat.substring(0, end).trim()
        return clamp(sentence)
    }

    fun firstSentenceEnd(text: String): Int {
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (char == '.' || char == '!' || char == '?' || char == '؛' || char == '؟' || char == '。') {
                val followsWord = index > 0 && text[index - 1].isLetterOrDigit()
                val precedesSpace = index + 1 >= text.length || text[index + 1].isWhitespace()
                if (char == '.') {
                    if (index + 1 < text.length && text[index + 1].isLetterOrDigit()) {
                        index++
                        continue
                    }
                    if (endsWithAbbreviation(text, index)) {
                        index++
                        continue
                    }
                }
                if (followsWord && precedesSpace) return index + 1
            }
            index++
        }
        return text.length
    }

    private fun endsWithAbbreviation(text: String, dotIndex: Int): Boolean {
        var start = dotIndex
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '.')) start--
        return text.substring(start, dotIndex) in Abbreviations
    }

    private fun clamp(sentence: String): String {
        if (sentence.length <= MaxDescriptionChars) return sentence
        val window = sentence.substring(0, MaxDescriptionChars)
        val lastSpace = window.lastIndexOf(' ')
        val cut = if (lastSpace > MaxDescriptionChars / 2) lastSpace else window.length
        return window.substring(0, cut).trimEnd(' ', '.', ',', ';', ':') + Ellipsis
    }
}
