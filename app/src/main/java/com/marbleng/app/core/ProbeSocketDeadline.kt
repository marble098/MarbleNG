package com.marbleng.app.core

import java.io.Closeable
import java.net.Socket
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

internal class ProbeSocketDeadline(socket: Socket, timeoutMs: Long) : Closeable {
    private val owner = Thread.currentThread()
    private val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
    private val timer = scheduler.scheduleAtFixedRate({
        if (owner.isInterrupted || System.nanoTime() >= deadline) runCatching { socket.close() }
    }, 0, 50, TimeUnit.MILLISECONDS)
    override fun close() { timer.cancel(false) }

    companion object {
        private val scheduler = ScheduledThreadPoolExecutor(1) { runnable ->
            Thread(runnable, "marble-probe-deadline").apply { isDaemon = true }
        }.apply { removeOnCancelPolicy = true }
    }
}
