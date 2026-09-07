package com.marbleng.app.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.DnsResolver
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.CancellationSignal
import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** The CLI's `local` resolver reads /etc/resolv.conf (absent on Android) and falls back to
 * localhost:53. It is NOT SFA's platform resolver. This loopback-only bridge uses Android's
 * resolver on the physical network, so Private DNS and network changes work without netlink.
 * Only bootstrap/explicit domestic DNS is sent here; browsing DNS stays inside the proxy. */
class AndroidDnsBridge(context: Context) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var users = 0
    private var server: Server? = null

    class Lease internal constructor(val port: Int, private val release: () -> Unit) : Closeable {
        private val closed = AtomicBoolean()
        override fun close() { if (closed.compareAndSet(false, true)) release() }
    }

    @Synchronized fun acquire(): Lease {
        val current = server ?: Server().also { server = it }
        users++
        return Lease(current.socket.localPort) { release() }
    }

    @Synchronized private fun release() {
        users--
        if (users == 0) {
            server?.close()
            server = null
        }
    }

    @Suppress("DEPRECATION")
    private fun physicalNetwork(): Network? {
        fun usable(network: Network): Boolean = connectivity.getNetworkCapabilities(network)?.let {
            it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        } == true
        return connectivity.activeNetwork?.takeIf(::usable)
            ?: connectivity.allNetworks.firstOrNull(::usable)
    }

    private inner class Server : Closeable {
        val socket = DatagramSocket(InetSocketAddress("127.0.0.1", 0))
        private val closed = AtomicBoolean()
        private val callbacks = Executor { command -> command.run() }
        private val receiver = Thread({ receive() }, "marble-dns-bridge").apply { isDaemon = true; start() }

        private fun receive() {
            while (!closed.get()) {
                val packet = DatagramPacket(ByteArray(4096), 4096)
                try {
                    socket.receive(packet)
                    if (!packet.address.isLoopbackAddress) continue
                    val query = packet.data.copyOf(packet.length)
                    val receivedAt = System.nanoTime()
                    val peer = packet.socketAddress
                    try {
                        workers.execute {
                            val expired = closed.get() || System.nanoTime() - receivedAt > TimeUnit.SECONDS.toNanos(3)
                            val answer = if (expired) DnsBootstrapCodec.error(query, 2)
                                else runCatching { resolve(query) }.getOrNull() ?: DnsBootstrapCodec.error(query, 2)
                            if (answer != null && !closed.get()) runCatching { socket.send(DatagramPacket(answer, answer.size, peer)) }
                        }
                    } catch (_: java.util.concurrent.RejectedExecutionException) {
                        DnsBootstrapCodec.error(query, 2)?.let { answer ->
                            runCatching { socket.send(DatagramPacket(answer, answer.size, peer)) }
                        }
                    }
                } catch (_: java.net.SocketException) {
                    break
                } catch (_: Exception) {
                    if (closed.get()) break
                }
            }
        }

        private fun resolve(query: ByteArray): ByteArray? {
            val question = DnsBootstrapCodec.question(query) ?: return null
            val network = physicalNetwork() ?: return DnsBootstrapCodec.error(query, 2)
            if (Build.VERSION.SDK_INT >= 29) {
                val answer = AtomicReference<ByteArray?>()
                val done = CountDownLatch(1)
                val cancel = CancellationSignal()
                try {
                    DnsResolver.getInstance().rawQuery(network, query, 0, callbacks, cancel,
                        object : DnsResolver.Callback<ByteArray> {
                            override fun onAnswer(response: ByteArray, rcode: Int) { answer.set(response); done.countDown() }
                            override fun onError(error: DnsResolver.DnsException) { done.countDown() }
                        })
                    if (!done.await(3, TimeUnit.SECONDS)) return DnsBootstrapCodec.error(query, 2)
                    return answer.get() ?: DnsBootstrapCodec.error(query, 2)
                } finally { cancel.cancel() }
            }
            if (question.type !in setOf(1, 28)) return DnsBootstrapCodec.error(query, 4)
            // API 26-28 have no rawQuery. Keep the OS lookup bounded in population (two workers,
            // sixteen queued requests); a core timeout must not create unlimited resolver threads.
            val addresses = network.getAllByName(question.host).filter {
                it.address.size == if (question.type == 1) 4 else 16
            }
            return DnsBootstrapCodec.answer(query, addresses)
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            socket.close() // unblocks receive immediately
            receiver.interrupt()
        }
    }
    companion object {
        // Global rather than per lease: an API 26 netd stall must not create another pair of
        // blocked worker threads each time a timed-out measurement opens a new bridge socket.
        private val workers = ThreadPoolExecutor(2, 2, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(16),
            { runnable -> Thread(runnable, "marble-dns-query").apply { isDaemon = true } },
            ThreadPoolExecutor.AbortPolicy())
    }
}
