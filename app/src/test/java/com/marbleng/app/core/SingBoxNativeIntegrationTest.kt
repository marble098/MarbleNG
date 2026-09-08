package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingMode
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory

/** Acceptance tests against EXACT core-lock.json binaries, not JSON shape assertions. CI makes
 * these mandatory. All traffic stays on loopback: no user's credentials, public DNS or healthy
 * external server is required. A passing schema check alone never counts as connectivity. */
class SingBoxNativeIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var singbox: File
    private lateinit var xray: File
    private val cleanup = mutableListOf<Closeable>()
    private val plain = AppSettings(coreEngineId = "singbox", routingMode = RoutingMode.PROXY_ALL,
        routeBlockAds = false, routeBypassPrivate = false, singBoxUnifiedDelay = false)
    private val uuid = "11111111-2222-3333-4444-555555555555"
    private var sequence = 0

    @Before fun requirePinnedBinaries() {
        val sb = System.getenv("MARBLE_SINGBOX_BINARY")
        val xr = System.getenv("MARBLE_XRAY_BINARY")
        if (System.getenv("MARBLE_NATIVE_TESTS_REQUIRED") == "1") {
            check(!sb.isNullOrBlank() && !xr.isNullOrBlank()) { "Mandatory native-core acceptance binaries missing" }
        }
        assumeTrue("Run scripts/prepare-native-test-cores.sh to enable native acceptance tests", !sb.isNullOrBlank() && !xr.isNullOrBlank())
        singbox = File(sb!!)
        xray = File(xr!!)
        assertTrue(singbox.isFile)
        assertTrue(xray.isFile)
    }

    @After fun closeResources() { cleanup.asReversed().forEach { runCatching { it.close() } } }

    @Test fun exactPinnedCoreAcceptsTheTranslationMatrixWithoutLegacyEnvironmentFlags() {
        val lock = JSONObject(repositoryFile("core-lock.json").readText())
        val version = command(singbox, "version")
        assertTrue(version, version.contains(lock.getJSONObject("singbox").getString("tag").removePrefix("v")))
        val matrix = mutableListOf<ProxyProfile>()
        fun vless(stream: JSONObject = JSONObject()) = JSONObject().put("protocol", "vless").put("tag", "proxy")
            .put("settings", JSONObject().put("address", "192.0.2.1").put("port", 443).put("id", uuid).put("encryption", "none"))
            .put("streamSettings", stream)
        val tls = JSONObject().put("security", "tls").put("tlsSettings", JSONObject().put("serverName", "example.com"))
        matrix += profile(vless(tls))
        matrix += profile(vless(JSONObject(tls.toString()).put("network", "ws").put("wsSettings", JSONObject()
            .put("path", "/ws").put("headers", JSONObject().put("Host", "example.com").put("X-Test", "kept")))))
        matrix += profile(vless(JSONObject().put("network", "kcp").put("kcpSettings", JSONObject()
            .put("mtu", 1280).put("tti", 50).put("uplinkCapacity", 8).put("downlinkCapacity", 32)
            .put("seed", "fixture").put("header", JSONObject().put("type", "none")))))
        matrix += profile(vless(JSONObject().put("method", "xhttp").put("security", "reality")
            .put("realitySettings", JSONObject().put("serverName", "example.com").put("password", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").put("shortId", "1234"))
            .put("xhttpSettings", JSONObject().put("path", "/").put("mode", "auto").put("extra", JSONObject()
                .put("xPaddingBytes", "100-1000").put("xPaddingObfsMode", true).put("xmux", JSONObject().put("maxConcurrency", "16-32"))))))
        matrix += profile(JSONObject().put("protocol", "vmess").put("tag", "proxy").put("settings", JSONObject()
            .put("address", "192.0.2.1").put("port", 443).put("id", uuid).put("security", "auto")).put("streamSettings", tls))
        matrix += profile(JSONObject().put("protocol", "hysteria").put("tag", "proxy").put("settings", JSONObject()
            .put("version", 2).put("address", "192.0.2.1").put("port", 443)).put("streamSettings", JSONObject(tls.toString())
            .put("method", "hysteria").put("hysteriaSettings", JSONObject().put("version", 2).put("auth", "test"))))
        matrix += socksProfile(1080)
        matrix.forEachIndexed { index, node ->
            val config = config(node)
            checkConfig(config, "matrix-$index")
        }
        // Exercise the production offline routing assets, not just a proxy-only test shell.
        val directory = repositoryFile("app/src/main/assets/singbox")
        val paths = listOf("geoip-ir", "geosite-ir", "geosite-ads").associateWith { File(directory, "$it.srs").absolutePath }
        val full = JSONObject(SingBoxConfigBuilder.build(matrix.first(), AppSettings(), freePort(), freePort(), "fixture", "", "cache.db",
            ruleSetPaths = paths, bootstrapDnsPort = 53530).json)
        checkConfig(full, "offline-routing")
    }

    @Test fun reportedXhttpRealityPasswordShapeActuallyCarriesUrlTestAndRealDelayThroughXray() {
        val tls = origin()
        val decoy = decoy(tls.context)
        val keys = KeyPairGenerator.getInstance("X25519").generateKeyPair()
        val encode = Base64.getUrlEncoder().withoutPadding()
        val privateKey = encode.encodeToString(keys.private.encoded.takeLast(32).toByteArray())
        val publicKey = encode.encodeToString(keys.public.encoded.takeLast(32).toByteArray())
        val port = freePort()
        val transport = JSONObject().put("path", "/").put("mode", "auto").put("extra", JSONObject()
            .put("mode", "auto").put("xPaddingBytes", "100-1000").put("xPaddingObfsMode", true))
        val stream = JSONObject().put("network", "xhttp").put("security", "reality").put("xhttpSettings", transport)
            .put("realitySettings", JSONObject().put("target", "127.0.0.1:$decoy").put("serverNames", JSONArray().put("localhost"))
                .put("privateKey", privateKey).put("shortIds", JSONArray().put("1234")))
        val inbound = JSONObject().put("listen", "127.0.0.1").put("port", port).put("protocol", "vless")
            .put("settings", JSONObject().put("decryption", "none").put("clients", JSONArray().put(JSONObject().put("id", uuid))))
            .put("streamSettings", stream)
        val serverLog = startXray(inbound, port)
        val client = JSONObject().put("protocol", "vless").put("tag", "proxy")
            .put("settings", JSONObject().put("address", "127.0.0.1").put("port", port).put("id", uuid).put("encryption", "none"))
            .put("streamSettings", JSONObject().put("method", "xhttp").put("security", "reality").put("xhttpSettings", transport)
                .put("realitySettings", JSONObject().put("serverName", "localhost").put("fingerprint", "chrome")
                    .put("password", publicKey).put("shortId", "1234").put("spiderX", "/")))
        val configured = config(profile(client), certificate = tls.certificate)
        start(configured).use { active ->
            val tested = active.delay(tls.url, 6000)
            assertTrue("$tested\nCLIENT: ${SingBoxProcessSession.tail(active.logFile)}\nSERVER: ${SingBoxProcessSession.tail(serverLog)}", tested.ok)
            assertTrue(tested.delayMs > 0)
            val portIn = configured.getJSONArray("inbounds").getJSONObject(0).getInt("listen_port")
            val real = SocksHttpClient.tunnelRttBatch(portIn, "127.0.0.1", "/generate_204?token=retained", 1, 6000,
                targetPort = tls.port, tlsFactory = tls.context.socketFactory)
            assertTrue(real.samplesMs.single() > 0)
            assertTrue(tls.paths.contains("/generate_204?token=retained"))
            assertFalse("http must not be silently replaced with gstatic", active.delay("http://127.0.0.1/", 1000).ok)
        }
        // Control: a listening core with the WRONG account must not report success.
        client.getJSONObject("settings").put("id", "22222222-2222-3333-4444-555555555555")
        start(config(profile(client), certificate = tls.certificate)).use { active ->
            assertFalse(active.delay(tls.url, 2000).ok)
        }
    }

    @Test fun configuredDnsFallbackReallyReachesTheSecondEncryptedTransportWithoutPlaintextLeak() {
        val tls = origin()
        val port = freePort()
        startXray(JSONObject().put("listen", "127.0.0.1").put("port", port).put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth").put("udp", true)), port)
        val badTlsPort = freePort() // deliberately no listener
        val built = config(socksProfile(port), tls.certificate,
            listOf("tls://127.0.0.1:$badTlsPort", "https://127.0.0.1:${tls.port}/dns-query"))
        val socks = built.getJSONArray("inbounds").getJSONObject(0).getInt("listen_port")
        start(built).use {
            val response = queryThroughSocks(socks, DnsWireCodec.buildQuery("bootstrap-fixture.invalid"))
            assertEquals("127.0.0.42", DnsWireCodec.parseAnswers(response).single().hostAddress)
            assertTrue(tls.paths.contains("/dns-query"))
        }
        // Both encrypted endpoints unavailable: do not fall through to dns-local, which would
        // incorrectly turn an encrypted-DNS outage into a cleartext browsing leak.
        val remotes = NativeSingBoxConfig.objects(built.getJSONObject("dns").getJSONArray("servers"))
            .filter { it.optString("tag").startsWith("dns-remote-") }
        remotes.forEach { it.put("server_port", badTlsPort) }
        start(built).use {
            val response = runCatching { queryThroughSocks(socks, DnsWireCodec.buildQuery("bootstrap-fixture.invalid")) }.getOrNull()
            assertTrue("no encrypted peer available must fail closed", response == null || DnsWireCodec.parseAnswers(response).isEmpty())
        }
    }

    @Test fun simultaneousSessionsHaveIndependentConfigsSecretsAndLifetimes() {
        val tls = origin()
        val port = freePort()
        startXray(JSONObject().put("listen", "127.0.0.1").put("port", port).put("protocol", "socks")
            .put("settings", JSONObject().put("auth", "noauth")), port)
        val a = config(socksProfile(port), tls.certificate)
        val b = config(socksProfile(port), tls.certificate)
        // Allocate workspaces before concurrency; only process execution itself is parallel.
        val aDir = temporary.newFolder("concurrent-a")
        val bDir = temporary.newFolder("concurrent-b")
        val executor = Executors.newFixedThreadPool(2)
        try {
            val tasks = listOf(a to aDir, b to bDir).map { (cfg, dir) -> executor.submit<SingBoxProcessSession> { start(cfg, dir) } }
            val first = tasks[0].get(15, TimeUnit.SECONDS)
            val second = tasks[1].get(15, TimeUnit.SECONDS)
            try {
                assertNotEquals(first.logFile, second.logFile)
                assertTrue(first.delay(tls.url, 4000).ok)
                first.close()
                assertFalse(first.isAlive)
                assertTrue(second.isAlive)
                assertTrue(second.delay(tls.url, 4000).ok)
                assertFalse(File(aDir, "cache.db").exists())
                assertFalse(File(bDir, "cache.db").exists())
            } finally { first.close(); second.close() }
        } finally { executor.shutdownNow() }
    }

    @Test fun cancelledStartupKillsTheChildAndReleasesItsListener() {
        val cfg = config(socksProfile(1080))
        val socks = cfg.getJSONArray("inbounds").getJSONObject(0).getInt("listen_port")
        val api = cfg.getJSONObject("experimental").getJSONObject("clash_api").getString("external_controller").substringAfterLast(':').toInt()
        val dir = temporary.newFolder("cancelled")
        val finished = CountDownLatch(1)
        val thread = Thread {
            try {
                SingBoxProcessSession.open(singbox, cfg.toString(), File(dir, "config.json"), File(dir, "core.log"), dir,
                    socks, api, "wrong-secret", 12_000).use { fail("401 must not count as a ready controller") }
            } catch (_: InterruptedException) {
                // The API exists, but wrong auth must keep startup pending until cancellation.
            } finally { finished.countDown() }
        }
        thread.start()
        waitListening(socks)
        thread.interrupt()
        assertTrue("cancelled startup did not clean up", finished.await(3, TimeUnit.SECONDS))
        assertFalse(listening(socks))
    }

    private data class Origin(val context: SSLContext, val certificate: String, val port: Int,
                              val paths: MutableList<String>) {
        val url get() = "https://127.0.0.1:$port/generate_204?token=retained"
    }

    private fun origin(): Origin {
        val directory = temporary.newFolder("tls-${sequence++}")
        val keystore = File(directory, "test.p12")
        val keytool = File(System.getProperty("java.home"), "bin/keytool")
        command(keytool, "-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048", "-validity", "36500",
            "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1", "-storetype", "PKCS12", "-keystore", keystore.absolutePath,
            "-storepass", "fixture-password", "-keypass", "fixture-password", "-noprompt")
        val store = KeyStore.getInstance("PKCS12").apply { keystore.inputStream().use { load(it, "fixture-password".toCharArray()) } }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, "fixture-password".toCharArray()) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        val ssl = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, trust.trustManagers, null) }
        val pem = "-----BEGIN CERTIFICATE-----\n" + Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(store.getCertificate("fixture").encoded) + "\n-----END CERTIFICATE-----\n"
        val paths = java.util.Collections.synchronizedList(mutableListOf<String>())
        val server = HttpsServer.create(InetSocketAddress("127.0.0.1", 0), 8)
        server.httpsConfigurator = HttpsConfigurator(ssl)
        val workers = Executors.newFixedThreadPool(4)
        server.executor = workers
        server.createContext("/") { exchange ->
            try {
                val it = exchange
                paths += it.requestURI.toString()
                if (it.requestURI.path == "/dns-query") {
                    val query = it.requestBody.readBytes()
                    val answer = DnsBootstrapCodec.answer(query, listOf(java.net.InetAddress.getByName("127.0.0.42")))!!
                    it.responseHeaders.add("Content-Type", "application/dns-message")
                    it.sendResponseHeaders(200, answer.size.toLong())
                    it.responseBody.write(answer)
                } else {
                    Thread.sleep(20) // keep sub-millisecond CI timing from rounding to zero
                    it.sendResponseHeaders(204, -1)
                }
            } finally { exchange.close() }
        }
        server.start()
        cleanup += Closeable { server.stop(0); workers.shutdownNow() }
        return Origin(ssl, pem, server.address.port, paths)
    }

    /**
     * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — the canary must be a document the pinned core
     * actually runs, or the self-test would report "the core is broken" on healthy devices, which
     * is a worse bug than the one it was written for.
     *
     * This is the only place the canary is executed against a real binary in CI, and it runs on
     * the *host* core: on Linux an app-UID-style netlink ban does not exist, so the canary is
     * expected to pass here and its verdict is expected to be cacheable. The Android-specific half
     * of the contract — a `direct` outbound surviving a nil interface monitor — is proven by the
     * Go tests `scripts/inject-singbox-android-fix.py` writes into the core source, which
     * `.github/workflows/verify.yml` runs before any ABI is built.
     */
    @Test fun theCoreSelfTestCanaryStartsThePinnedCoreAndServesItsLocalInbound() {
        val directory = temporary.newFolder("singbox-selftest-${sequence++}")
        val verdict = SingBoxCoreSelfTest.probe(singbox, directory, freePort())
        assertTrue("canary failed against the pinned core: ${verdict.reason}", verdict.ok)
        assertEquals(SingBoxCoreSelfTest.fingerprint(singbox), verdict.fingerprint)
        assertFalse(verdict.coreFault)

        // The canary is the production shape in miniature: same inbound type, same `direct`
        // outbound, same `route.final` — and nothing that can reach a network.
        val canary = JSONObject(SingBoxCoreSelfTest.canaryConfig(freePort()))
        val inbound = canary.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("mixed", inbound.getString("type"))
        assertEquals(SingBoxConfigBuilder.INBOUND_TAG, inbound.getString("tag"))
        assertEquals("127.0.0.1", inbound.getString("listen"))
        val outbound = canary.getJSONArray("outbounds").getJSONObject(0)
        assertEquals("direct", outbound.getString("type"))
        assertEquals(SingBoxConfigBuilder.DIRECT_TAG, outbound.getString("tag"))
        assertEquals(SingBoxConfigBuilder.DIRECT_TAG, canary.getJSONObject("route").getString("final"))
        assertEquals(1, canary.getJSONArray("inbounds").length())
        assertEquals(1, canary.getJSONArray("outbounds").length())
        // No DNS transport, no controller, no cache file: nothing in the canary can reach a
        // network, so nothing it reports can be an observation about a server.
        listOf("dns", "experimental", "ntp", "tun").forEach { key ->
            assertFalse("the canary must stay offline: $key", canary.has(key))
        }
        assertFalse(
            "the canary must not ask for an interface monitor",
            SingBoxAndroidRuntime.ANDROID_FORBIDDEN_ROUTE_KEYS.any { "\"$it\"" in canary.toString() }
        )

        // The verdict is cached against the binary's identity, and only against that identity.
        val cache = File(directory, "verdict.json")
        SingBoxCoreSelfTest.write(cache, verdict)
        assertEquals(verdict, SingBoxCoreSelfTest.read(cache, singbox))
        assertNull(SingBoxCoreSelfTest.read(cache, File(directory, "some-other-binary")))
    }

    /**
     * A binary that cannot start must produce a *core fault* verdict, never a silent pass and
     * never an exception: the whole point of the canary is that the manager can act on the answer.
     */
    @Test fun theCoreSelfTestReportsAnUnusableBinaryAsAFaultInsteadOfThrowing() {
        val directory = temporary.newFolder("singbox-selftest-fault-${sequence++}")
        val notABinary = File(directory, "libsingbox.so").apply { writeText("#!/bin/sh\nexit 2\n") }
        val verdict = SingBoxCoreSelfTest.probe(notABinary, directory, freePort())
        assertFalse(verdict.ok)
        assertTrue(verdict.reason.startsWith("core-install:"))
        assertFalse("a too-small file is not a crash verdict", verdict.coreFault)

        val missing = SingBoxCoreSelfTest.probe(File(directory, "absent"), directory, freePort())
        assertFalse(missing.ok)
        assertTrue(missing.reason.startsWith("core-install:"))
    }

    private fun decoy(context: SSLContext): Int {
        val server = context.serverSocketFactory.createServerSocket(0, 8, java.net.InetAddress.getByName("127.0.0.1")) as SSLServerSocket
        server.sslParameters = server.sslParameters.apply { applicationProtocols = arrayOf("h2", "http/1.1") }
        val executor = Executors.newCachedThreadPool()
        val accept = Thread {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() as SSLSocket }.getOrNull() ?: break
                executor.execute { socket.use { runCatching { it.soTimeout = 4000; it.startHandshake(); it.inputStream.read() } } }
            }
        }.apply { isDaemon = true; start() }
        cleanup += Closeable { server.close(); executor.shutdownNow(); accept.join(1000) }
        return server.localPort
    }

    private fun startXray(inbound: JSONObject, port: Int): File {
        val directory = temporary.newFolder("xray-${sequence++}")
        val config = File(directory, "config.json").apply { writeText(JSONObject()
            .put("log", JSONObject().put("loglevel", "error"))
            .put("inbounds", JSONArray().put(inbound))
            .put("outbounds", JSONArray().put(JSONObject().put("protocol", "freedom").put("tag", "direct"))).toString()) }
        val log = File(directory, "core.log")
        val child = ProcessBuilder(xray.absolutePath, "run", "-c", config.absolutePath).redirectErrorStream(true).redirectOutput(log).start()
        cleanup += Closeable { SingBoxProcessSession.stop(child) }
        try { waitListening(port) } catch (error: Throwable) { throw AssertionError(SingBoxProcessSession.tail(log), error) }
        return log
    }

    private fun profile(outbound: JSONObject) = ProxyProfile("fixture", "Loopback fixture", outbound.optString("protocol"), "",
        JSONObject().put("outbounds", JSONArray().put(outbound)).toString(), "127.0.0.1", outbound.optJSONObject("settings")?.optInt("port") ?: 443)
    private fun socksProfile(port: Int) = profile(JSONObject().put("protocol", "socks").put("tag", "proxy")
        .put("settings", JSONObject().put("address", "127.0.0.1").put("port", port)))
    private fun config(profile: ProxyProfile, certificate: String? = null, pool: List<String> = emptyList()): JSONObject {
        val socks = freePort()
        var controller = freePort()
        while (controller == socks) controller = freePort()
        return JSONObject(SingBoxConfigBuilder.build(profile, plain, socks, controller, UUID.randomUUID().toString(), "", "cache.db",
            resolverPool = pool, bootstrapDnsPort = 53530, forTest = true).json).apply {
            if (certificate != null) put("certificate", JSONObject().put("store", "none").put("certificate", JSONArray().put(certificate)))
        }
    }
    private fun start(config: JSONObject, directory: File = temporary.newFolder("singbox-${sequence++}")): SingBoxProcessSession {
        val api = config.getJSONObject("experimental").getJSONObject("clash_api")
        return SingBoxProcessSession.open(singbox, config.toString(), File(directory, "config.json"), File(directory, "core.log"), directory,
            config.getJSONArray("inbounds").getJSONObject(0).getInt("listen_port"), api.getString("external_controller").substringAfterLast(':').toInt(), api.getString("secret"))
    }
    private fun checkConfig(config: JSONObject, name: String) {
        val file = temporary.newFile("$name.json").apply { writeText(SingBoxConfigDoctor.hardenForAndroid(config.toString()).json) }
        command(singbox, "check", "-c", file.absolutePath)
    }
    private fun command(binary: File, vararg arguments: String): String {
        val log = temporary.newFile("command-${sequence++}.log")
        val process = SingBoxAndroidRuntime.prepare(ProcessBuilder(listOf(binary.absolutePath) + arguments).redirectErrorStream(true)
            .redirectOutput(log), temporary.root, temporary.root).start()
        try {
            assertTrue("command timed out: ${binary.name}", process.waitFor(20, TimeUnit.SECONDS))
            val output = log.readText()
            assertEquals(output, 0, process.exitValue())
            return output
        } finally { SingBoxProcessSession.stop(process) }
    }
    private fun queryThroughSocks(port: Int, query: ByteArray): ByteArray = Socket("127.0.0.1", port).use { socket ->
        socket.soTimeout = 8000
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        output.write(byteArrayOf(5, 1, 0)); output.flush()
        assertEquals(5, input.readUnsignedByte()); assertEquals(0, input.readUnsignedByte())
        output.write(byteArrayOf(5, 1, 0, 1, 192.toByte(), 0, 2, 53, 0, 53)); output.flush()
        assertEquals(5, input.readUnsignedByte()); assertEquals(0, input.readUnsignedByte())
        input.readUnsignedByte()
        val type = input.readUnsignedByte()
        val length = when (type) { 1 -> 4; 4 -> 16; 3 -> input.readUnsignedByte(); else -> error("bad SOCKS reply") }
        input.readFully(ByteArray(length + 2))
        output.writeShort(query.size); output.write(query); output.flush()
        ByteArray(input.readUnsignedShort()).also { input.readFully(it) }
    }
    private fun freePort(): Int = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { it.localPort }
    private fun listening(port: Int) = runCatching { Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 100) }; true }.getOrDefault(false)
    private fun waitListening(port: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8)
        while (System.nanoTime() < deadline) {
            if (listening(port)) return
            Thread.sleep(25)
        }
        error("loopback listener $port not ready")
    }
    private fun repositoryFile(path: String): File = listOf(File(path), File("../$path")).first { it.exists() }
}
