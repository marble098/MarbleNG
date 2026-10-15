package com.marbleng.app.core

enum class ResolverFailureKind(
    val code: String,
    val isShutdownSafe: Boolean,
    val quarantinable: Boolean
) {
    DEADLINE("deadline", isShutdownSafe = false, quarantinable = true),
    EOF("eof", isShutdownSafe = false, quarantinable = true),
    CLOSED_PIPE("closed-pipe", isShutdownSafe = true, quarantinable = false),
    CANCELLED("cancelled", isShutdownSafe = true, quarantinable = false),
    TLS("tls", isShutdownSafe = false, quarantinable = true),
    CERT_EXPIRED("cert-expired", isShutdownSafe = false, quarantinable = true),
    POISON("poisoned-answer", isShutdownSafe = false, quarantinable = true),
    OTHER("other", isShutdownSafe = false, quarantinable = true)
}

data class ResolverFailureSummary(
    val deadlineCount: Int = 0,
    val eofCount: Int = 0,
    val closedPipeCount: Int = 0,
    val cancelledCount: Int = 0,
    val tlsCount: Int = 0,
    val certExpiredCount: Int = 0,
    val poisonedCount: Int = 0,
    val otherCount: Int = 0
) {
    val total: Int
        get() = deadlineCount + eofCount + closedPipeCount + cancelledCount +
            tlsCount + certExpiredCount + poisonedCount + otherCount

    val transportFailures: Int
        get() = deadlineCount + eofCount + tlsCount + certExpiredCount + poisonedCount + otherCount

    fun countFor(kind: ResolverFailureKind): Int = when (kind) {
        ResolverFailureKind.DEADLINE -> deadlineCount
        ResolverFailureKind.EOF -> eofCount
        ResolverFailureKind.CLOSED_PIPE -> closedPipeCount
        ResolverFailureKind.CANCELLED -> cancelledCount
        ResolverFailureKind.TLS -> tlsCount
        ResolverFailureKind.CERT_EXPIRED -> certExpiredCount
        ResolverFailureKind.POISON -> poisonedCount
        ResolverFailureKind.OTHER -> otherCount
    }

    fun withIncrement(kind: ResolverFailureKind): ResolverFailureSummary = when (kind) {
        ResolverFailureKind.DEADLINE -> copy(deadlineCount = deadlineCount + 1)
        ResolverFailureKind.EOF -> copy(eofCount = eofCount + 1)
        ResolverFailureKind.CLOSED_PIPE -> copy(closedPipeCount = closedPipeCount + 1)
        ResolverFailureKind.CANCELLED -> copy(cancelledCount = cancelledCount + 1)
        ResolverFailureKind.TLS -> copy(tlsCount = tlsCount + 1)
        ResolverFailureKind.CERT_EXPIRED -> copy(certExpiredCount = certExpiredCount + 1)
        ResolverFailureKind.POISON -> copy(poisonedCount = poisonedCount + 1)
        ResolverFailureKind.OTHER -> copy(otherCount = otherCount + 1)
    }

    fun toMachineReadable(): String = buildString {
        append("resolver.deadline=").append(deadlineCount)
        append(";resolver.eof=").append(eofCount)
        append(";resolver.closed_pipe=").append(closedPipeCount)
        append(";resolver.cancelled=").append(cancelledCount)
        append(";resolver.tls=").append(tlsCount)
        append(";resolver.cert_expired=").append(certExpiredCount)
        append(";resolver.poisoned=").append(poisonedCount)
        append(";resolver.other=").append(otherCount)
        append(";resolver.transport_failures=").append(transportFailures)
    }
}

object ResolverFailureClassifier {
    private val CERT_EXPIRED_MARKERS = listOf(
        "certificate has expired",
        "certificate is not yet valid",
        "certificate expired",
        "x509: certificate has expired",
        "expired certificate",
        "certificate not yet valid"
    )

    private val TLS_MARKERS = listOf(
        "tls handshake",
        "remote error: tls",
        "x509:",
        "certificate signed by unknown authority",
        "tls: first record does not look like a tls handshake",
        "tls handshake timeout"
    )

    private val DEADLINE_MARKERS = listOf(
        "context deadline exceeded",
        "deadline exceeded",
        "i/o timeout",
        "read tcp: i/o timeout",
        "write tcp: i/o timeout",
        "timed out",
        "timeout while",
        "tls handshake timeout"
    )

    private val EOF_MARKERS = listOf(
        "unexpected eof",
        "failed to read response length > eof",
        "failed to read response > eof",
        "read eof",
        "eof while reading",
        "transport closed early",
        "connection closed before message completed",
        "eof"
    )

    private val CLOSED_PIPE_MARKERS = listOf(
        "read/write on closed pipe",
        "use of closed network connection",
        "broken pipe",
        "write: broken pipe"
    )

    private val CANCELLED_MARKERS = listOf(
        "context canceled",
        "context cancelled",
        "request canceled",
        "request cancelled",
        "operation was canceled",
        "context done"
    )

    private val POISON_MARKERS = listOf(
        "poisoned",
        "dns poisoning",
        "dns hijack",
        "blocked by dns"
    )

    fun isDnsRelated(line: String): Boolean {
        if (line.isBlank()) return false
        val lower = line.lowercase()
        return lower.contains("dns") || lower.contains("doh") ||
            lower.contains("dns-query") || lower.contains("resolver") ||
            lower.contains("upstream") || lower.contains("app/dns") ||
            lower.contains("dns.go") || lower.contains("dnscrypt") ||
            lower.contains("nameserver") || lower.contains("resolve") ||
            lower.contains("exchange failed")
    }

    fun classify(line: String): ResolverFailureKind? {
        if (line.isBlank()) return null
        val lower = line.lowercase()

        if (containsAny(lower, CANCELLED_MARKERS)) return ResolverFailureKind.CANCELLED
        if (containsAny(lower, CERT_EXPIRED_MARKERS)) return ResolverFailureKind.CERT_EXPIRED
        if (containsAny(lower, CLOSED_PIPE_MARKERS)) return ResolverFailureKind.CLOSED_PIPE
        if (containsAny(lower, DEADLINE_MARKERS)) return ResolverFailureKind.DEADLINE
        if (containsAny(lower, TLS_MARKERS) || "failed to verify certificate" in lower || "is not valid for this name" in lower) return ResolverFailureKind.TLS
        if ("connection reset by peer" in lower) return ResolverFailureKind.OTHER
        if (containsAny(lower, POISON_MARKERS)) return ResolverFailureKind.POISON
        if (containsAny(lower, EOF_MARKERS)) return ResolverFailureKind.EOF
        return null
    }

    fun classifyThrowable(t: Throwable): ResolverFailureKind? {
        val text = (t.message ?: "").let { it + " " + (t::class.java.simpleName) }
        return classify(text) ?: classify(t::class.java.simpleName)
    }

    fun summarize(lines: List<String>): ResolverFailureSummary {
        var summary = ResolverFailureSummary()
        lines.forEach { line ->
            if (!isDnsRelated(line)) return@forEach
            val kind = classify(line) ?: return@forEach
            summary = summary.withIncrement(kind)
        }
        return summary
    }

    fun rawConsistentWithSummary(lines: List<String>, summary: ResolverFailureSummary): Boolean {
        val recomputed = summarize(lines)
        return recomputed == summary
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { haystack.contains(it) }
}
