package com.marbleng.app.core

internal object ProbeTargetWalk {
    const val MAX_TARGETS = 3

    fun perTargetBudgetMs(timeoutMs: Int): Int = timeoutMs.coerceIn(500, 30_000)

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
