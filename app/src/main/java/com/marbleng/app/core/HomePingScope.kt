package com.marbleng.app.core

object HomePingScope {
    fun sourceId(routeSourceId: String?): String =
        routeSourceId?.trim()?.takeIf(String::isNotEmpty) ?: "manual"
}
