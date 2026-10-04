package com.marbleng.app.core

/** Resolves the source behind the route shown on Home; never inherits another page's filter. */
object HomePingScope {
    fun sourceId(routeSourceId: String?): String =
        routeSourceId?.trim()?.takeIf(String::isNotEmpty) ?: "manual"
}
