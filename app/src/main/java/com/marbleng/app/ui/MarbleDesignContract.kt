package com.marbleng.app.ui

// MARBLE_ROUTE_ATELIER_V207 — the design contract, written down as code.
//
// The V207 review named one root cause behind everything else it found: the product has plenty of
// effects and no grammar. Shape, elevation, colour and motion rules were spread across the Prism,
// Holo, Cyber, HomeCloud and four Home presentation families, so nothing in the code could answer
// "what does this control mean, and what colour is the state?" — each surface answered for itself.
//
// This file is that single answer. It is deliberately made of pure Kotlin (plus two modifiers):
// every rule here is unit-testable off-device, and every family of controls reads the same numbers.
// A style may still change how a surface *looks*; it may no longer change what a state *means*,
// how large a touch target is, how far a press travels or how long an entrance takes.
//
// The four rules this file exists to enforce:
//
//  1. STATE IS ONE MAPPING. `marbleRouteStateOf` resolves the connection to one of six states and
//     every surface — the round shutter, the slide track, the docked switch, the floor bar, the
//     floating pill, the Atelier hero and the status card — derives its hue, its word and its
//     enabled-ness from that one value.
//  2. A TOUCH TARGET IS NOT A STYLE CHOICE. `Modifier.marbleTapTarget` gives any control the
//     platform floor (48 dp) without repainting or re-measuring the artwork the designer sized.
//  3. MOTION EXPLAINS A CHANGE OR IT DOES NOT RUN. `MarbleMotionPolicy` answers for entrance
//     delays, acknowledgement pops and the ambient field alike, and `ProvideMarbleMotion` feeds it
//     the *live* system setting instead of a value read once at startup.
//  4. A PICTURE CLAIMS ONLY WHAT WAS MEASURED. `MarbleLocationTrust` separates "geolocated",
//     "one provider said so" and "the name contains a flag emoji", and only the first two may be
//     drawn as a national flag.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------------
// 1 — The touch-target floor
// ---------------------------------------------------------------------------------------------

/**
 * The minimum size of anything the user can actuate.
 *
 * The critique this answers is exact: "visual refinement was bought by shrinking the touch area".
 * A 42 dp icon control, a 36 dp compact button and a 28 dp overflow menu are all comfortable to
 * look at and unreliable to hit with a thumb on a moving bus.
 *
 * The floor is enforced by [marbleTapTarget] rather than by inflating every painted control, so a
 * designer's artwork keeps its size and the *slot* absorbs the hit area — which is also what
 * Material 3 does for its own icon buttons.
 */
object MarbleTapTarget {
    /**
     * WCAG 2.5.8 / Material 3 / Apple HIG all agree on this number, and it is the only floor in the
     * product: a control in a dense row is promised the same hit area as one alone on a card, which
     * is why there is one name for it and not a "row" variant someone could tune down later.
     */
    val Floor: Dp = 48.dp
}

/**
 * Grows this node's hit box to at least [minimum] in both axes without repainting or resizing the
 * content it wraps.
 *
 * The content is measured with the incoming constraints relaxed on the min side, so a control stays
 * exactly as large as its artwork; the node then reports the larger of `content` and the floor, and
 * centres the content inside it. Growth is clamped to the incoming max constraints, which makes the
 * modifier safe inside a Row that has already spent its width: a tight slot keeps the control's own
 * size instead of forcing an overflow.
 *
 * Put it FIRST in the chain (before `clip`/`background`) and let the `clickable`/`kineticClickable`
 * node follow it: the pointer region belongs to the node's own bounds, so the hit area grows with it
 * while the painted surface — sized by the inner content — does not.
 */
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

// ---------------------------------------------------------------------------------------------
// 2 — The state grammar
// ---------------------------------------------------------------------------------------------

/** The six states the connection can honestly be in. */
enum class MarbleRouteState {
    /** Nothing is selected: the page's real verb is "add a server", not "connect". */
    NO_ROUTE,

    /** A route is selected and the tunnel is closed. */
    READY,

    /** The tunnel is being opened. */
    SECURING,

    /** Traffic is flowing. */
    CONNECTED,

    /** The tunnel is being closed; the control must not be re-triggered. */
    CLOSING,

    /** A policy or a failure stopped the session. */
    BLOCKED
}

/**
 * The one resolver of connection state. Precedence is a product decision, so it is written once:
 * a blocked session outranks a closing one, which outranks a securing one, which outranks the
 * "connected" flag the engine may still be holding a moment.
 */
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

/** The verb a connect control offers for a state. Status belongs to the title, action to the button. */
enum class MarbleConnectVerb { ADD_ROUTE, CONNECT, CANCEL, DISCONNECT, RESET, WAIT }

fun MarbleRouteState.connectVerb(): MarbleConnectVerb = when (this) {
    MarbleRouteState.NO_ROUTE -> MarbleConnectVerb.ADD_ROUTE
    MarbleRouteState.READY -> MarbleConnectVerb.CONNECT
    MarbleRouteState.SECURING -> MarbleConnectVerb.CANCEL
    MarbleRouteState.CONNECTED -> MarbleConnectVerb.DISCONNECT
    MarbleRouteState.CLOSING -> MarbleConnectVerb.WAIT
    MarbleRouteState.BLOCKED -> MarbleConnectVerb.RESET
}

/** A control that is waiting on the engine must say so, and must not answer the tap twice. */
fun MarbleRouteState.isActionable(): Boolean = this != MarbleRouteState.CLOSING

/**
 * The semantic hues of the product. Names describe a *role*, never a colour: the same hue means the
 * same fact on Home, on Servers, in Settings and in a notification.
 *
 * The old tokens (`Aether.Cyan`, `AmethystBright`, …) stay what they are — palette members with a
 * brand history — but state code must not read them directly any more, because in two of the three
 * brand palettes `amethystBright` and `cyan` hold the same value, which silently killed the
 * "securing" read on the V202 floating control.
 */
enum class MarbleStateHue { BRAND, SUCCESS, CAUTION, DANGER, SECURE, MUTED }

fun MarbleRouteState.stateHue(): MarbleStateHue = when (this) {
    MarbleRouteState.NO_ROUTE -> MarbleStateHue.MUTED
    MarbleRouteState.READY -> MarbleStateHue.BRAND
    MarbleRouteState.SECURING -> MarbleStateHue.SECURE
    MarbleRouteState.CONNECTED -> MarbleStateHue.SUCCESS
    MarbleRouteState.CLOSING -> MarbleStateHue.CAUTION
    MarbleRouteState.BLOCKED -> MarbleStateHue.DANGER
}

/** The palette implementation of a semantic hue. Styles may decorate around it; they may not redefine it. */
@Composable
internal fun MarbleStateHue.tone(): Color = when (this) {
    MarbleStateHue.BRAND -> Aether.Cyan
    MarbleStateHue.SUCCESS -> Aether.Emerald
    MarbleStateHue.CAUTION -> Aether.Amber
    MarbleStateHue.DANGER -> Aether.Danger
    MarbleStateHue.SECURE -> Aether.Amethyst
    MarbleStateHue.MUTED -> Aether.InkMuted
}

/** The state tone every connection surface uses. One function, six states, no per-style drift. */
@Composable
internal fun marbleRouteTone(state: MarbleRouteState): Color = state.stateHue().tone()

// ---------------------------------------------------------------------------------------------
// 3 — The route mark: three nodes, three provable facts
// ---------------------------------------------------------------------------------------------

/** How much proof a stage of the route has. `PROVEN` is only ever earned by a measurement. */
enum class MarbleNodeState { UNKNOWN, PENDING, PROVEN, FAILED }

/**
 * The product's signature: the route as three nodes — device, tunnel, egress.
 *
 * The mark is only honest if a node is lit when the engine can actually show the fact behind it, so
 * the geometry is derived here rather than in a draw call: an idle page draws one lit node (the
 * device is real), a session that has not resolved its exit address draws two, and the third lights
 * only on a measured endpoint. A stage is never painted successful because the animation finished.
 */
data class MarbleRoutePath(
    val device: MarbleNodeState,
    val tunnel: MarbleNodeState,
    val egress: MarbleNodeState
) {
    /** How many of the three nodes the evidence supports, for the caption "2 of 3". */
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
        // A failed exit is painted failed whatever else the page is doing: the state that reaches
        // here with a failure is precisely the state where a resolved address is no longer evidence
        // of anything, and answering "unknown" would hide the one fact the user needs.
        egressFailed -> MarbleNodeState.FAILED
        state != MarbleRouteState.CONNECTED && state != MarbleRouteState.CLOSING ->
            MarbleNodeState.UNKNOWN
        egressResolved -> MarbleNodeState.PROVEN
        else -> MarbleNodeState.PENDING
    }
    return MarbleRoutePath(device = device, tunnel = tunnel, egress = egress)
}

// ---------------------------------------------------------------------------------------------
// 4 — Evidence honesty: what a flag is allowed to claim
// ---------------------------------------------------------------------------------------------

/**
 * Where the country a row shows actually came from.
 *
 * A flag drawn at 32 dp reads as "this server is in Germany", which is a claim about the world, not
 * about a string. The product can only make that claim when the resolver measured it; the emoji a
 * subscription put into a node's name is part of the name.
 */
enum class MarbleLocationTrust {
    /** No usable answer yet. */
    UNKNOWN,

    /** Only the node's own label (a flag emoji inside the name) suggests a country. */
    LABEL_GUESS,

    /** The live server-intel report for the running session. */
    SESSION_REPORT,

    /** Measured by the location resolver; a lone provider, still being re-tested. */
    MEASURED_PROVISIONAL,

    /** Measured by the resolver with a quorum of providers. */
    MEASURED_VERIFIED
}

/** True when the answer may be painted as a national flag at all. A label emoji is not a measurement. */
fun MarbleLocationTrust.mayDrawFlag(): Boolean = when (this) {
    MarbleLocationTrust.SESSION_REPORT,
    MarbleLocationTrust.MEASURED_PROVISIONAL,
    MarbleLocationTrust.MEASURED_VERIFIED -> true
    MarbleLocationTrust.UNKNOWN, MarbleLocationTrust.LABEL_GUESS -> false
}

/** The one resolver a surface uses to decide what its location picture is allowed to say. */
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

/**
 * True when the answer is strong enough to stop re-testing, filter by, or quote in a report.
 *
 * The other two questions a surface can ask about a location are [mayDrawFlag] (may this be a national
 * flag at all) and [MarbleLocationTrust] itself (which tier am I looking at). There is deliberately no
 * third "should this be quiet" rule: the surfaces that hold still already ask [mayDrawFlag], and
 * re-testing is the resolver's business, not the tile's.
 */
fun MarbleLocationTrust.isVerified(): Boolean = this == MarbleLocationTrust.MEASURED_VERIFIED

// ---------------------------------------------------------------------------------------------
// 5 — Motion policy: one answer for "may this move?"
// ---------------------------------------------------------------------------------------------

/**
 * The single motion contract.
 *
 * Before V207 the animation toggle only reached *some* of the effects: the shared clock stopped, the
 * ripples calmed, but an entrance cascade still paid its `delay()` steps, because the delay lived in
 * a coroutine that never consulted the motion state. Reduced motion has to mean "nothing waits for
 * an effect", not "nothing spins forever".
 */
object MarbleMotionPolicy {
    /**
     * Entrance motion is for a surface arriving, never for a list under a scrolling thumb, and never
     * at all when the user asked for no motion. The step and the cap stay owned by
     * [ExpressiveMath.staggerDelayMs] so the ladder has one home; this only answers the switch.
     */
    fun entranceDelayMs(index: Int, motionEnabled: Boolean): Long =
        if (!motionEnabled) 0L else ExpressiveMath.staggerDelayMs(index)

    fun animates(motionEnabled: Boolean): Boolean = motionEnabled

    /** Press travel per control class: a small control must not move as far as a large one. */
    fun pressScale(control: MarbleControlKind, motionEnabled: Boolean): Float =
        if (!motionEnabled) 1f else control.pressScale
}

/** The control classes whose press, radius and target rules are decided centrally. */
enum class MarbleControlKind(
    /** How far the face travels under the finger. Big faces can afford more than small ones. */
    val pressScale: Float,
    val radius: Dp,
    val minHeight: Dp
) {
    /** The one verb of a page: the connect control, the sheet's primary action. */
    Primary(.955f, 14.dp, 56.dp),
    /** A normal labelled button. */
    Standard(.965f, 15.dp, 46.dp),
    /** A dense labelled button inside a row. */
    Compact(.972f, 11.dp, 36.dp),
    /** An icon-only control: the smallest, so the shallowest press and the largest relative target. */
    Icon(.955f, 15.dp, 42.dp);
}

/** The acknowledgement beat of a press — an effect that may be switched off, not a second gesture. */
fun MarbleControlKind.acknowledgesPress(motionEnabled: Boolean): Boolean = motionEnabled

/**
 * The connect control's own motion numbers, one place for all six silhouettes.
 *
 * The review's complaint about the round shutter was not "the ring is ugly" but "six signals fire at
 * once for one tap": an icon resize, a five percent leap of the whole face, a coloured shadow, two
 * radial gradients, a touch echo and a busy orbit. Each is defensible; together they are a display
 * around a verb. The ladder below keeps ONE acknowledgement beat per event and lets the state colour
 * carry the meaning.
 */
object MarbleConnectMotion {
    /** A state-change acknowledgement is a nod. V186 used 1.05 of a 168 dp disc — a lurch. */
    val statePopPeak: Float = 1.02f

    /** How long a colour change between two states may take: enough to follow, never to wait for. */
    const val StateColorMs: Int = 200

    // There is deliberately no "commits the action before animating" flag here. A slide-to-confirm
    // may delay its own knob for the sake of the gesture; it may never delay the network command, and
    // that is a rule about the call order inside each control, not a number to tune. Both slide
    // controls dispatch first and animate afterwards: the finding is closed at the call site.
}

// ---------------------------------------------------------------------------------------------
// 6 — The ambient field is a preference, not a personality
// ---------------------------------------------------------------------------------------------

/**
 * Whether the page backdrop may breathe.
 *
 * A full-screen radial light that pulses on 11 and 15 second cycles says nothing about whether a
 * tunnel is up. It costs a redraw of the largest surface on screen — sampled at ~15 Hz instead of
 * 60 by V193, and skipped entirely by AMOLED, but still paid for. A tool that reports network state
 * should offer that cost rather than install it as a default, so one flag answers for the backdrop,
 * the status pip and every heartbeat trace at once.
 */
internal val LocalMarbleAmbientField = staticCompositionLocalOf { true }

// ---------------------------------------------------------------------------------------------
// 7 — Feedback that must not be lost by the no-toast rule
// ---------------------------------------------------------------------------------------------

/**
 * How long an operation's result stays readable when no other surface owns it.
 *
 * MARBLE_NO_IN_APP_NOTIFICATIONS_V121 removed the floating snackbar, which was right about the
 * interruption and wrong about the silence: a copy, an import, a backup or a clipboard rejection can
 * have no card to land on, and clearing the runtime message the moment the app goes idle deletes the
 * only feedback there is. A result now has a bounded dwell on an inline bar (see [HomeRuntimeNotice]),
 * and anything that does own a surface still clears itself out of it.
 */
object MarbleFeedbackPolicy {
    /** How long an outcome stays on the inline bar before it retires itself. */
    const val OutcomeDwellMs: Long = 4_500L

    /** An outcome the user must act on stays until it is dismissed. */
    const val ActionRequiredDwellMs: Long = 0L

    fun dwellMillis(actionRequired: Boolean): Long =
        if (actionRequired) ActionRequiredDwellMs else OutcomeDwellMs

    /**
     * True when a message deserves the bar at all.
     *
     * Connection noise ("reconnecting", a state word) is already painted by the control that owns
     * it; an outcome with no owner (clipboard empty, import rejected, backup written) is the case
     * that must survive.
     */
    fun isOutcome(message: String): Boolean = message.isNotBlank()
}
