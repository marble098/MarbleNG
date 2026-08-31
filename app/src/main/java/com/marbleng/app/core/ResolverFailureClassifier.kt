package com.marbleng.app.core

/**
 * Resolver / transport failure classifier for MarbleNG diagnostics.
 *
 * Problem addressed (MARBLE_DIAGNOSTICS_CONSISTENCY_V1):
 * The old Bug Finder counted DoH deadline and DNS EOF errors with a couple of narrow
 * hard-coded substrings. Raw Xray lines that said the same thing in a slightly different
 * wording (for example "unexpected EOF" on a DNS-over-HTTPS exchange) produced a summary of
 * `0 DNS EOF` while the raw evidence clearly contained EOF. The summary and the raw log then
 * disagreed, which made resolver failures look like they were "not reproduced" when they were.
 *
 * This classifier is the single source of truth for categorising a raw log line. The summary is
 * always derived from the same raw lines through this classifier, so a category can never report
 * zero while the raw text it was built from contains that category.
 *
 * It also separates shutdown-safe events (cancellations and closed-pipe reads that happen during
 * teardown) from real transport failures, so a clean reconnect no longer floods the "resolver is
 * broken" counter.
 */
enum class ResolverFailureKind(
    val code: String,
    /**
     * True when the event is an expected side effect of cancellation / teardown rather than a
     * genuine resolver outage. Cancelled and closed-pipe events must never be counted as normal
     * transport failures.
     */
    val isShutdownSafe: Boolean,
    /**
     * True when the event is evidence that the resolver endpoint itself is broken or hostile and
     * the endpoint should be eligible for quarantine.
     */
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

/**
 * Immutable, machine-readable summary of resolver failure categories. Total and the
 * transport-failure total are derived once from the raw counters so they can never drift apart.
 */
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
    /** Every resolver category, including shutdown-safe ones. */
    val total: Int
        get() = deadlineCount + eofCount + closedPipeCount + cancelledCount +
            tlsCount + certExpiredCount + poisonedCount + otherCount

    /**
     * Genuine transport failures only. Cancellations and closed-pipe events from a clean teardown
     * are deliberately excluded so they cannot look like resolver outages.
     */
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

    /** Increment a single category, returning a new summary. */
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

/**
 * Pure, deterministic resolver line classifier. Kept dependency-free so it can be unit tested on
 * the JVM and shared by Bug Finder, RuntimeDiagnostics and the live resolver telemetry.
 */
object ResolverFailureClassifier {

    private val CERT_EXPIRED_MARKERS = listOf(
        "certificate has expired",
        "certificate is not yet valid",
        "certificate expired",
        "x509: certificate has expired",
        "expired certificate",
        "certificate not yet valid",
        "is not valid for this name",
        "tls: failed to verify certificate"
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
        "timeout while"
    )

    private val EOF_MARKERS = listOf(
        "unexpected eof",
        "failed to read response length > eof",
        "failed to read response > eof",
        "read eof",
        "eof while reading",
        "transport closed early",
        "connection closed before message completed",
        "eof" // last resort: a bare EOF marker inside a resolver line
    )

    private val CLOSED_PIPE_MARKERS = listOf(
        "read/write on closed pipe",
        "use of closed network connection",
        "broken pipe",
        "write: broken pipe",
        "read: connection reset by peer"
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

    /** True when a raw line plausibly concerns DNS / resolver / upstream resolution traffic. */
    fun isDnsRelated(line: String): Boolean {
        if (line.isBlank()) return false
        val lower = line.lowercase()
        return lower.contains("dns") || lower.contains("doh") ||
            lower.contains("dns-query") || lower.contains("resolver") ||
            lower.contains("upstream") || lower.contains("app/dns") ||
            lower.contains("dns.go") || lower.contains("dnscrypt")
    }

    /**
     * Classify a raw log line into a resolver/transport failure kind, or null when the line is not
     * a resolver failure event. Works on both JVM tests and real Xray logs.
     *
     * Cancellation markers are checked first because a cancelled request frequently also surfaces a
     * closed-pipe / reset text; the intent of the line is what matters for quarantine.
     */
    fun classify(line: String): ResolverFailureKind? {
        if (line.isBlank()) return null
        val lower = line.lowercase()

        if (containsAny(lower, CANCELLED_MARKERS)) return ResolverFailureKind.CANCELLED
        if (containsAny(lower, CERT_EXPIRED_MARKERS)) return ResolverFailureKind.CERT_EXPIRED
        if (containsAny(lower, TLS_MARKERS)) return ResolverFailureKind.TLS
        if (containsAny(lower, CLOSED_PIPE_MARKERS)) return ResolverFailureKind.CLOSED_PIPE
        if (containsAny(lower, DEADLINE_MARKERS)) return ResolverFailureKind.DEADLINE
        if (containsAny(lower, POISON_MARKERS)) return ResolverFailureKind.POISON
        if (containsAny(lower, EOF_MARKERS)) return ResolverFailureKind.EOF
        return null
    }

    /**
     * Classify an exception (or a raw line) into a resolver failure kind. Convenience used by the
     * live resolver path so cancellation and transport failures are recorded with the same scheme
     * as the offline log classifier.
     */
    fun classifyThrowable(t: Throwable): ResolverFailureKind? {
        val text = (t.message ?: "").let { it + " " + (t::class.java.simpleName) }
        return classify(text) ?: classify(t::class.java.simpleName)
    }

    /**
     * Summarise a batch of raw log lines. Only DNS-related lines are considered; every other line
     * is ignored, matching what the summary claims to represent.
     */
    fun summarize(lines: List<String>): ResolverFailureSummary {
        var summary = ResolverFailureSummary()
        lines.forEach { line ->
            if (!isDnsRelated(line)) return@forEach
            val kind = classify(line) ?: return@forEach
            summary = summary.withIncrement(kind)
        }
        return summary
    }

    /** True when every raw line was found; used to verify the summary is consistent with raw. */
    fun rawConsistentWithSummary(lines: List<String>, summary: ResolverFailureSummary): Boolean {
        val recomputed = summarize(lines)
        return recomputed == summary
    }

    private fun containsAny(haystack: String, needles: List<String>): Boolean =
        needles.any { haystack.contains(it) }
}
