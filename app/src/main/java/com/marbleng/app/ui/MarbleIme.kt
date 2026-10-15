package com.marbleng.app.ui

// MARBLE_IME_HYGIENE_V197 — one explicit policy for the software keyboard.

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions

const val MARBLE_IME_HIDE_COOLDOWN_MS = 750L

class MarbleImeHider internal constructor(
    private val release: () -> Unit,
    private val now: () -> Long = { System.currentTimeMillis() }
) {
    private var lastHideAtMs: Long? = null
    private var suppressed = 0
    private var honoured = 0

    val suppressedHides: Int get() = suppressed

    val honouredHides: Int get() = honoured

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

    @Synchronized
    fun noteFocusGained() {
        lastHideAtMs = null
    }
}

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
            hider.noteFocusGained()
            hider.hide()
        }
    }
}

fun marbleImeOptions(action: ImeAction = ImeAction.Done): KeyboardOptions =
    KeyboardOptions(imeAction = action)

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
