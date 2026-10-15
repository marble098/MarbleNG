package com.marbleng.app.core

import java.net.URL

data class CoreUrlTestResult(val delayMs: Long, val ok: Boolean, val detail: String = "", val live: Boolean = false)

internal object UrlTestTarget {
    const val INVALID = "urltest-url: an HTTPS URL without user info is required"

    fun validate(url: String): String? {
        val target = runCatching { URL(url) }.getOrNull() ?: return INVALID
        val acceptable = target.protocol == "https" &&
            target.host.orEmpty().isNotBlank() &&
            target.userInfo == null
        return if (acceptable) null else INVALID
    }
}
