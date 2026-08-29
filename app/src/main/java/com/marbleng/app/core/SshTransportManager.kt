package com.marbleng.app.core

// MARBLE_SSH_TRANSPORT_V25

import android.net.Uri
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

data class SshEndpoint(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val hostKeySha256: String = "",
    val name: String = ""
)

object SshProfileCodec {
    fun parse(profile: ProxyProfile): SshEndpoint = parseRaw(profile.raw)

    fun parseRaw(raw: String): SshEndpoint {
        val uri = Uri.parse(raw.trim())
        require(uri.scheme.equals("ssh", true)) { "Not an SSH URI" }
        val host = uri.host?.trim().orEmpty()
        require(host.isNotBlank()) { "SSH host is required" }
        val port = uri.port.takeIf { it in 1..65535 } ?: 22

        val conventional = uri.userInfo.orEmpty()
        val conventionalUser = conventional.substringBefore(':', "").trim()
        val conventionalPassword = if (conventional.contains(':')) conventional.substringAfter(':') else ""

        val username = uri.getQueryParameter("user")?.trim()?.takeIf(String::isNotBlank)
            ?: conventionalUser
        val password = uri.getQueryParameter("password")
            ?: uri.getQueryParameter("pass")
            ?: conventionalPassword
        require(username.isNotBlank()) { "SSH username is required" }
        require(password.isNotBlank()) { "SSH password is required" }

        val pin = (
            uri.getQueryParameter("fingerprint")
                ?: uri.getQueryParameter("hostKeySha256")
                ?: uri.getQueryParameter("fp")
                ?: ""
            ).trim()

        return SshEndpoint(
            host = host,
            port = port,
            username = username,
            password = password,
            hostKeySha256 = pin,
            name = uri.fragment.orEmpty().trim()
        )
    }

    fun encode(
        host: String,
        port: Int,
        username: String,
        password: String,
        hostKeySha256: String,
        name: String
    ): String {
        val authority = if (host.contains(':') && !host.startsWith("[")) "[$host]:$port" else "$host:$port"
        val builder = Uri.Builder()
            .scheme("ssh")
            .encodedAuthority(authority)
            .appendQueryParameter("user", username)
            .appendQueryParameter("password", password)
        hostKeySha256.trim().takeIf(String::isNotBlank)
            ?.let { builder.appendQueryParameter("fingerprint", it) }
        name.trim().takeIf(String::isNotBlank)?.let { builder.fragment(it) }
        return builder.build().toString()
    }

    /**
     * Placeholder Xray document. The SSH manager replaces localSocksPort at runtime before Xray
     * starts; this profile never asks Xray to pretend that SSH is a native outbound protocol.
     */
    fun xrayClientConfig(localSocksPort: Int): String {
        require(localSocksPort in 1..65535)
        return JSONObject()
            .put("log", JSONObject().put("loglevel", "warning"))
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject()
                        .put("tag", "socks-in")
                        .put("listen", "127.0.0.1")
                        .put("port", 10808)
                        .put("protocol", "socks")
                        .put("settings", JSONObject().put("udp", false))
                )
            )
            .put(
                "outbounds",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("tag", "ssh-proxy")
                            .put("protocol", "socks")
                            .put(
                                "settings",
                                JSONObject()
                                    .put("address", "127.0.0.1")
                                    .put("port", localSocksPort)
                            )
                    )
                    .put(JSONObject().put("tag", "block").put("protocol", "blackhole"))
            )
            .toString()
    }
}

/**
 * Exact SHA256 host-key repository. When a pin is supplied JSch consults this during KEX, before
 * user authentication, so a mismatched server never receives the SSH password.
 */
private class PinnedHostKeyRepository(expectedRaw: String) : HostKeyRepository {
    private val expected = expectedRaw.trim().removePrefix("SHA256:").trim()

    override fun check(host: String, key: ByteArray): Int {
        val actual = Base64.getEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(key)
        )
        return if (
            MessageDigest.isEqual(
                actual.toByteArray(Charsets.US_ASCII),
                expected.toByteArray(Charsets.US_ASCII)
            )
        ) HostKeyRepository.OK else HostKeyRepository.CHANGED
    }

    override fun add(hostkey: HostKey, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "MarbleNG pinned SSH host key"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
}

/**
 * Loopback SOCKS5 CONNECT adapter backed by one authenticated SSH session.
 *
 * It is deliberately one-shot. XrayManager creates a fresh instance for each generation so stale
 * or cancelled connection attempts can never kill or overwrite the bridge owned by a newer attempt.
 */
class SshTransportManager : Closeable {
    private val running = AtomicBoolean(false)
    private val workers = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "marble-ssh-bridge").apply { isDaemon = true }
    }
    private val clients = ConcurrentHashMap.newKeySet<Socket>()

    @Volatile private var session: Session? = null
    @Volatile private var server: ServerSocket? = null
    @Volatile private var started = false

    @Synchronized
    fun start(profile: ProxyProfile, settings: AppSettings = AppSettings()): Int {
        check(!started) { "SSH bridge instances are one-shot" }
        started = true
        val endpoint = SshProfileCodec.parse(profile)
        val jsch = JSch()
        val pin = endpoint.hostKeySha256.trim()
        if (pin.isNotBlank()) {
            jsch.setHostKeyRepository(PinnedHostKeyRepository(pin))
        }
        // JSch resolves the host itself and dials whichever record the system resolver returned
        // first, so a dual-stack SSH endpoint was always reached over IPv4 no matter what the user
        // enabled. Hand it the address the family plan chose instead; the pinned-key check matches
        // the key material, never the host string, so the pin still verifies.
        val dialTarget = AddressFamilyPolicy
            .resolveCandidates(
                endpoint.host,
                AddressFamilyPolicy.plan(settings = settings)
            )
            .firstOrNull()
            ?.hostAddress
            ?.takeIf { it.isNotBlank() }
            ?: endpoint.host
        val nextSession = jsch.getSession(endpoint.username, dialTarget, endpoint.port)
        nextSession.setPassword(endpoint.password.toByteArray(Charsets.UTF_8))
        nextSession.setConfig("PreferredAuthentications", "password")
        nextSession.setConfig("StrictHostKeyChecking", if (pin.isBlank()) "no" else "yes")
        nextSession.setServerAliveInterval(15_000)
        nextSession.setServerAliveCountMax(2)

        var nextServer: ServerSocket? = null
        try {
            nextSession.connect(6_500)
            nextServer = ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 48)
            }
            session = nextSession
            server = nextServer
            running.set(true)
            workers.execute { acceptLoop(nextServer) }
            return nextServer.localPort
        } catch (error: Throwable) {
            running.set(false)
            runCatching { nextServer?.close() }
            runCatching { nextSession.disconnect() }
            workers.shutdownNow()
            throw error
        }
    }

    private fun acceptLoop(listener: ServerSocket) {
        while (running.get() && !listener.isClosed) {
            val socket = runCatching { listener.accept() }.getOrNull() ?: break
            if (!running.get()) {
                runCatching { socket.close() }
                break
            }
            clients += socket
            workers.execute {
                try {
                    handleClient(socket)
                } finally {
                    clients -= socket
                    runCatching { socket.close() }
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.tcpNoDelay = true
        client.soTimeout = 12_000
        val input = client.getInputStream()
        val output = client.getOutputStream()

        check(readU8(input) == 0x05) { "SOCKS version is not 5" }
        val methodCount = readU8(input)
        check(methodCount in 1..255) { "SOCKS method list is empty" }
        val methods = readExact(input, methodCount)
        if (methods.none { (it.toInt() and 0xff) == 0x00 }) {
            output.write(byteArrayOf(0x05, 0xff.toByte()))
            output.flush()
            return
        }
        output.write(byteArrayOf(0x05, 0x00))
        output.flush()

        check(readU8(input) == 0x05) { "Invalid SOCKS request version" }
        val command = readU8(input)
        readU8(input) // RSV
        val atyp = readU8(input)
        if (command != 0x01) {
            reply(output, 0x07)
            return
        }

        val destination = when (atyp) {
            0x01 -> InetAddress.getByAddress(readExact(input, 4)).hostAddress
            0x03 -> String(readExact(input, readU8(input)), Charsets.UTF_8)
            0x04 -> InetAddress.getByAddress(readExact(input, 16)).hostAddress
            else -> {
                reply(output, 0x08)
                return
            }
        }
        val destinationPort = (readU8(input) shl 8) or readU8(input)
        check(destinationPort in 1..65535) { "Invalid SOCKS destination port" }

        val active = session
        if (active == null || !active.isConnected) {
            reply(output, 0x01)
            return
        }

        var forwardedPort = -1
        var forwardedSocket: Socket? = null
        try {
            forwardedPort = active.setPortForwardingL("127.0.0.1", 0, destination, destinationPort)
            val tunnelSocket = Socket()
            forwardedSocket = tunnelSocket
            tunnelSocket.tcpNoDelay = true
            tunnelSocket.connect(InetSocketAddress("127.0.0.1", forwardedPort), 2_500)
            client.soTimeout = 0
            tunnelSocket.soTimeout = 0
            reply(output, 0x00, forwardedPort)

            val firstDone = CountDownLatch(1)
            val up = workers.submit { pump(input, tunnelSocket.getOutputStream(), firstDone) }
            val down = workers.submit { pump(tunnelSocket.getInputStream(), output, firstDone) }
            firstDone.await()
            runCatching { tunnelSocket.close() }
            runCatching { client.close() }
            up.cancel(true)
            down.cancel(true)
        } catch (_: Throwable) {
            runCatching { reply(output, 0x05) }
        } finally {
            runCatching { forwardedSocket?.close() }
            if (forwardedPort > 0) runCatching { active.delPortForwardingL(forwardedPort) }
        }
    }

    private fun pump(input: InputStream, output: OutputStream, done: CountDownLatch) {
        val buffer = ByteArray(32 * 1024)
        try {
            while (running.get()) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Throwable) {
        } finally {
            done.countDown()
        }
    }

    private fun reply(out: OutputStream, code: Int, port: Int = 0) {
        out.write(
            byteArrayOf(
                0x05, code.toByte(), 0x00, 0x01,
                127, 0, 0, 1,
                ((port ushr 8) and 0xff).toByte(),
                (port and 0xff).toByte()
            )
        )
        out.flush()
    }

    private fun readU8(input: InputStream): Int {
        val value = input.read()
        if (value < 0) error("Unexpected SOCKS EOF")
        return value
    }

    private fun readExact(input: InputStream, count: Int): ByteArray {
        require(count >= 0)
        val out = ByteArray(count)
        var offset = 0
        while (offset < count) {
            val n = input.read(out, offset, count - offset)
            if (n < 0) error("Unexpected SOCKS EOF")
            offset += n
        }
        return out
    }

    @Synchronized
    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        clients.toList().forEach { runCatching { it.close() } }
        clients.clear()
        runCatching { session?.disconnect() }
        session = null
        workers.shutdownNow()
    }

    override fun close() = stop()
}
