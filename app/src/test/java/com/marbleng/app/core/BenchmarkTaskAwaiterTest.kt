package com.marbleng.app.core

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

class BenchmarkTaskAwaiterTest {
    @Test
    fun aFailedCandidateDoesNotDiscardTheNextResult() {
        val pool = Executors.newSingleThreadExecutor()
        val failed = pool.submit { error("candidate failed") }
        val healthy = pool.submit(java.util.concurrent.Callable { 42 })
        BenchmarkTaskAwaiter.await(listOf(failed, healthy), pool)
        assertEquals(42, healthy.get().toInt())
        assertTrue(pool.isShutdown)
    }

    @Test
    fun interruptionCancelsRunningAndQueuedCandidatesAndClosesPool() {
        val pool = Executors.newSingleThreadExecutor()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val interrupted = AtomicBoolean(false)
        val running = pool.submit { started.countDown(); release.await() }
        val queued = pool.submit { release.await() }
        val waiter = Thread {
            try {
                BenchmarkTaskAwaiter.await(listOf(running, queued), pool)
            } catch (_: InterruptedException) {
                interrupted.set(true)
            }
        }
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            waiter.start()
            waiter.interrupt()
            waiter.join(5_000)
            assertFalse("cancellation must not wait for the next task", waiter.isAlive)
            assertTrue(interrupted.get())
            assertTrue(running.isCancelled)
            assertTrue(queued.isCancelled)
            assertTrue(pool.isShutdown)
        } finally {
            release.countDown()
            waiter.interrupt()
            pool.shutdownNow()
            waiter.join(5_000)
        }
    }
}
