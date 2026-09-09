package com.marbleng.app.core

/**
 * MARBLE_PING_FALSE_FAILED_V159 — the target walk shared by the two core-measured ping methods.
 *
 * ## The symptom this exists for
 *
 * URL test and Real delay both *work* — most rows answer and the number is honest — but every
 * once in a while a server that is demonstrably alive (it connects, it carries traffic) came
 * back FAILED. Two budget defects produced exactly that:
 *
 *  1. **The URL test spent its whole budget on the first target.** The walk computed ONE deadline
 *     for all fallback targets and handed each of them only the milliseconds the previous target
 *     had left. The first target of a fresh throwaway core is the coldest request the product
 *     ever makes — core DNS bootstrap, TCP to the node, TLS, then the fetch itself — so on a
 *     ~1 s route it legitimately needs the entire configured timeout. When it expired, the
 *     fallback URLs (which exist precisely for "this origin is filtered right now", and which
 *     answer warm because the core's route is already up) received a budget of zero and the
 *     server was published FAILED. The fallback was dead code for the one failure mode it was
 *     built for; it only ever helped failures that were *fast* (an immediate controller error).
 *  2. **Real delay through a live tunnel — and through the throwaway tunnel the disconnected
 *     Home ping builds — fetched exactly one URL**, the primary, while the Rank sweep already
 *     walked every `DelayTest` candidate. A moment of SNI-throttling on that single origin (the
 *     documented behaviour of this network class, see the IRAN_AWARE_PING target-pool note in
 *     [RouteProbe]) failed every sample of an otherwise perfect route.
 *
 * ## The contract
 *
 *  - every candidate target gets the **full per-target budget**, the same mercy PattNG gives its
 *    primary and secondary delay URL;
 *  - the first target that answers wins — a healthy server never pays for the walk;
 *  - a target that answers nothing hands the identical budget to the next candidate origin,
 *    because a filtered origin is not a verdict about the route;
 *  - at most [MAX_TARGETS] distinct targets, so a dead node's worst case stays bounded;
 *  - the walk is interruptible between targets, so a cancelled sweep stops here too.
 *
 * Pure JVM, no sockets and no core: the walk is the policy, the callers own the measurement.
 */
internal object ProbeTargetWalk {

    /**
     * The most fallback origins one server may ever spend time on: the configured delay URL,
     * PattNG's secondary, and one CDN alternative. A fourth target cannot change a verdict that
     * three independent origins already failed to produce.
     */
    const val MAX_TARGETS = 3

    /**
     * The honest per-target budget, clamped to the same window the core's own delay endpoint
     * accepts (`SingBoxProcessSession.delay` clamps identically), so the walk can never ask a
     * target for more time than the measurement beneath it would honour anyway.
     */
    fun perTargetBudgetMs(timeoutMs: Int): Int = timeoutMs.coerceIn(500, 30_000)

    /**
     * The URL-test walk. [measure] receives one URL and the FULL budget for that URL — never a
     * leftover — and the first `ok` answer wins. When every target stayed silent the last honest
     * failure is returned, not a fabricated one.
     */
    fun urlTest(
        urls: List<String>,
        timeoutMs: Int,
        measure: (url: String, budgetMs: Int) -> CoreUrlTestResult
    ): CoreUrlTestResult {
        val budget = perTargetBudgetMs(timeoutMs)
        var last = CoreUrlTestResult(0, false, "urltest-no-target")
        for (url in urls.distinct().take(MAX_TARGETS)) {
            SingBoxProcessSession.checkInterrupted()
            last = measure(url, budget)
            if (last.ok) return last
        }
        return last
    }

    /**
     * The Real-delay walk. The first target that produced a measurement wins; a target that
     * produced nothing hands the same samples-and-timeout budget to the next origin, because a
     * filtered origin is not a verdict about the route. When every candidate stayed silent the
     * walk returns the last target's honest failure.
     */
    fun realDelay(
        urls: List<String>,
        measure: (url: String) -> RouteProbe.ProbeResult
    ): RouteProbe.ProbeResult {
        var last = RouteProbe.ProbeResult(
            method = RouteProbe.METHOD_REAL_DELAY,
            latencyMs = RouteProbe.UNREACHABLE,
            successPercent = 0,
            samples = 1,
            lossPercent = 100.0,
            failureReason = "delay-url-failed"
        )
        for (url in urls.distinct().take(MAX_TARGETS)) {
            SingBoxProcessSession.checkInterrupted()
            val measured = measure(url)
            if (measured.successPercent > 0) return measured
            last = measured
        }
        return last
    }
}
