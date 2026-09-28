package com.marbleng.app.ui

// MARBLE_IME_HYGIENE_V197 — one explicit policy for the software keyboard.
//
// The attached logcat is sixteen minutes long, contains no crash and no exception, and yet contains
// more than a hundred of these:
//
//   InsetsController.hide(ime())
//   ImeTracker: ... onCancelled at PHASE_CLIENT_ALREADY_HIDDEN
//
// Read literally, that is the platform saying: "somebody asked me to hide a keyboard that is not
// open", over and over — in one nine-second window, roughly every 500 ms. Nothing in this app ever
// called `hide(ime())`, so the requests come from the text-input machinery itself, and the only way
// to produce them in a loop is to keep tearing down and re-creating an input session that has
// nothing to type into.
//
// Two shapes in this product can do that, and both are fixed here:
//
//  1. **Surfaces that look like text fields but are not.** `ServersDropdownField` is a real
//     `OutlinedTextField` (so its label floats like its neighbours) wrapped in a click handler that
//     opens a menu. It is focusable, so the platform can hand it an input session and then take it
//     away again — once per pass, forever. [Modifier.marbleImeInert] takes focus away from any
//     surface that must never own a keyboard.
//  2. **Fields that never say when they are finished.** Not one text field in the app declared an
//     IME action, so the keyboard's own "Done" key had nothing to call and the session was only
//     ever released by the framework guessing. [marbleImeActions] gives every field a real event
//     to fire, and [MarbleImeReleaseOnDispose] releases a dialog's session exactly once, on the way
//     out, instead of leaving it behind for the screen underneath to inherit.
//
// The third piece is containment, and it matters because the failure mode is invisible until it is
// expensive: [MarbleImeHider] refuses to honour a second hide request that arrives inside the
// cooldown and was not preceded by a fresh focus. A genuine loop therefore costs one call instead
// of a hundred, and a user who really does press Done twice in one second loses nothing that
// matters — the focus is already gone.
//
// Everything here is plain Kotlin except the Compose entry points, so the guard itself is
// unit-tested on the JVM (MarbleImeHiderTest).

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

/**
 * How long a hide request has to be honoured before another one is believed.
 *
 * A user can only type, press Done, re-tap a field and press Done again so fast; a recomposition
 * loop cannot tell the difference between 8 ms and 800 ms, so the window is deliberately wide
 * enough to swallow a loop and narrow enough that a real second press is never swallowed.
 */
const val MARBLE_IME_HIDE_COOLDOWN_MS = 750L

/**
 * A hide request that is answered exactly once per focus session.
 *
 * Built by [rememberMarbleImeHider] so the release path is the framework's own
 * `FocusManager` — never a direct `hide(ime())` call, which is the thing the platform is being
 * asked to do a hundred times.
 */
class MarbleImeHider internal constructor(
    private val release: () -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * When the last honoured hide happened, or `null` when there has not been one yet.
     *
     * `null` rather than a sentinel timestamp: the obvious sentinel is `Long.MIN_VALUE`, and
     * `now - Long.MIN_VALUE` overflows to a *negative* elapsed time, which would silently
     * suppress the very first request the user ever makes.
     */
    private var lastHideAtMs: Long? = null
    private var suppressed = 0
    private var honoured = 0

    /** Hide requests that were dropped because the keyboard had only just been released. */
    val suppressedHides: Int get() = suppressed

    /** Hide requests that actually reached the focus manager. */
    val honouredHides: Int get() = honoured

    /**
     * Release the keyboard. Call from a real event — an IME action, a dismissal — never from a
     * composable body: a hide that rides a recomposition is the bug this file exists to remove.
     *
     * @return true when the request was honoured; false when it was the repeat inside a loop.
     */
    @Synchronized
    fun hide(): Boolean {
        val at = now()
        val previous = lastHideAtMs
        if (previous != null && at - previous < MARBLE_IME_HIDE_COOLDOWN_MS) {
            suppressed += 1
            return false
        }
        lastHideAtMs = at
        honoured += 1
        release()
        return true
    }

    /** A field was focused again, so the next hide is a fresh request and not a repeat. */
    @Synchronized
    fun noteFocusGained() {
        lastHideAtMs = null
    }
}

/**
 * The one IME handle a screen should hold. Remembered per composition, released through the
 * framework's focus manager, and rate-guarded by [MarbleImeHider].
 */
@Composable
fun rememberMarbleImeHider(): MarbleImeHider {
    val focusManager = LocalFocusManager.current
    return remember(focusManager) {
        MarbleImeHider(release = { focusManager.clearFocus(force = true) })
    }
}

/**
 * MARBLE_IME_HYGIENE_V197 — a surface that must never own a keyboard.
 *
 * Used by the read-only dropdown fields: they are painted as text fields so the label floats in
 * line with the real ones beside them, but the only thing a tap on them does is open a menu. Giving
 * them focus hands the platform an input session with nothing to type into, which is exactly the
 * state that produces `hide(ime())` → `PHASE_CLIENT_ALREADY_HIDDEN` on every pass.
 */
fun Modifier.marbleImeInert(): Modifier = this.focusProperties { canFocus = false }

/**
 * MARBLE_IME_HYGIENE_V197 — release the input session exactly once, when this host leaves.
 *
 * A dialog dismissed while its field is focused used to leave the session behind for the screen
 * underneath to inherit. This runs on disposal only — never on recomposition — so a screen that
 * recomposes on every telemetry tick cannot turn into one hide request per tick.
 */
@Composable
fun MarbleImeReleaseOnDispose(hider: MarbleImeHider) {
    DisposableEffect(hider) {
        onDispose {
            // Bypass the cooldown deliberately: disposal is a real event, and the field that
            // would have re-armed it is already gone.
            hider.noteFocusGained()
            hider.hide()
        }
    }
}

/**
 * The IME action a single-line field should end on. Without one, the keyboard's own Done key is
 * inert and the only way a session ends is for the framework to notice on its own.
 */
fun marbleImeOptions(action: ImeAction = ImeAction.Done): KeyboardOptions =
    KeyboardOptions(imeAction = action)

/**
 * The events that dismiss the keyboard.
 *
 * Plain function on purpose: it must be callable from a composable's parameter list without
 * becoming a composition of its own, and it hands the real work to [MarbleImeHider].
 */
fun marbleImeActions(
    hider: MarbleImeHider,
    onDone: (() -> Unit)? = null
): KeyboardActions {
    val finish: () -> Unit = {
        hider.hide()
        onDone?.invoke()
    }
    return KeyboardActions(
        onDone = { finish() },
        onGo = { finish() },
        onSearch = { finish() },
        onSend = { finish() }
    )
}
