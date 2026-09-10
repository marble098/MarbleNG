package com.marbleng.app.core

import com.marbleng.app.model.ProxyProfile

/**
 * MARBLE_HOME_PING_ROUTE_GROUP_V146 — the one decision that keeps the Home ping honest: which
 * group does the pulse icon measure?
 *
 * The answer is the subscription that the route shown on Home belongs to — never "all sources".
 * When the user taps the pulse their question is "how is the subscription I am looking at
 * doing?", so the sweep covers exactly that route's group; substituting `"all"` would silently
 * turn a scoped question into a whole-library sweep. A route that resolved without a source
 * (nothing selected yet, or a leftover reference) degrades to the Manual bucket instead of
 * every subscription.
 *
 * The resolution is pure so the contract is pinned in unit tests and the repository is the only
 * caller: the deck, the connect button and the top ping action all consult the same rule.
 */
object HomePingScope {

    /** The library scope id a group ping of [route] must sweep. `"manual"` when there is no route. */
    fun sourceIdFor(route: ProxyProfile?): String = when {
        route == null -> "manual"
        route.subscriptionId.isBlank() -> "manual"
        else -> route.subscriptionId
    }
}
