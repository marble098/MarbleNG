package com.marbleng.app.core

import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** Owns the worker lifetime; a stopped optimizer must not keep measurement cores queued. */
internal object BenchmarkTaskAwaiter {
    fun await(jobs: List<Future<*>>, pool: ExecutorService) {
        try {
            jobs.forEach { job ->
                try {
                    job.get()
                } catch (_: ExecutionException) {
                    // One failed candidate must not discard other measurements.
                }
            }
        } finally {
            // In particular, do not swallow InterruptedException and wait on the next core.
            jobs.forEach { if (!it.isDone) it.cancel(true) }
            pool.shutdownNow()
        }
    }
}
