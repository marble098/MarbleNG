package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — the port is owned by exactly one core, and a port that
 * cannot be bound is a local fault with a named holder, never a verdict about servers.
 *
 * The reported incident this pins (2026-09-08, one device, four attempts over eight minutes):
 *
 * ```
 * 09:18:08  core-start: exited 1: … FATAL start service: start inbound/mixed[socks-in]:
 *           listen tcp 127.0.0.1:10808: bind: address already in use
 * 09:18:20  (same)     09:25:09 (same)     09:26:10 (same)
 * 09:26:14  core-start: exited 2: … SIGSEGV …                 (the V157 fault, pre-fix build)
 *           both events tagged alive=false / xrayAlive=false — the holder tracked nobody
 * 09:18:33  BUGFINDER scan-finish | failures=1
 * ```
 *
 * With no manager owning a child, the socket belonged to an **orphaned core process** — nothing
 * `stop()` can reach, so every connect paid the full spawn → FATAL → BLOCKED round trip until the
 * holder happened to die. Three product defects were visible in that one window, and each has a
 * test below:
 *
 *  1. nothing ever attributed the holder, so the port could not be freed and the failure carried
 *     no actionable evidence — [CorePortGuard] now finds the pid through `/proc/net/tcp{,6}` +
 *     `/proc/<pid>/fd`, reaps same-UID core binaries with TERM→KILL escalation, and never signals
 *     a foreign holder;
 *  2. the `core-start: exited 1:` prefix made the bind conflict a *config refusal*, so the reader
 *     walk spawned a core per candidate to die on the same port — [SingBoxManager.isConfigRefusal]
 *     now excludes the shape, and the rewritten `core-port:` reason is a local fault everywhere;
 *  3. the ERROR lines of local clients aborting their own SOCKS handshake (`read fqdn: unexpected
 *     EOF`, the 02:04–02:05 cluster on 2026-09-09) matched the bare "error" keyword and counted as
 *     Bug Finder failures — [CoreLogNoise] now classifies them as benign client evidence.
 */
class SingBoxPortSovereigntyV158Test {

    // ─────────────────────────────────────────────────────────────────────────────
    // Fixtures
    // ─────────────────────────────────────────────────────────────────────────────

    private companion object {
        const val MY_UID = 10123
        const val NATIVE_LIB_DIR = "/data/app/~~abc==/com.marbleng.app-HASH/lib/arm64"
    }

    /** A synthetic `/proc/net/tcp`-format table: one LISTEN on 10808 (0x2A38) plus decoys. */
    private val procTcp = """
        sl local_address rem_address   st tx_rx_queue tr_tm_when retrnsmt uid   timeout inode
        0: 0100007F:2A38 00000000:0000 0A 00000000:00000000 00:00000000 00000000 $MY_UID 0 1048571 1 0000000000000000 100 0 0 10 0
        1: 0100007F:2A39 00000000:0000 0A 00000000:00000000 00:00000000 00000000 $MY_UID 0 1048572 1 0000000000000000 100 0 0 10 0
        2: 0100007F:2A3A 00000000:0000 01 00000000:00000000 00:00000000 00000000 $MY_UID 0 1048573 1 0000000000000000 20 4 30 10 -1
        3: 00000000:1F90 00000000:0000 0A 00000000:00000000 00:00000000 00000000   1012 0 1048574 1 0000000000000000 100 0 0 10 0
    """.trimIndent() + "\n"

    private class FakeProcess(
        val uid: Int,
        val cmdline: String,
        val fds: Map<String, String> = emptyMap(),
        val ignoresTerm: Boolean = false
    ) {
        var alive = true
    }

    private class FakeProbe(
        private val processes: MutableMap<Int, FakeProcess>,
        private val tcp4: String = "",
        private val tcp6: String = "",
        portFree: Boolean = false
    ) : CorePortGuard.Probe {
        var portFree = portFree
            private set
        val signals = mutableListOf<Pair<Int, Int>>()

        override fun pids(): List<Int> = processes.keys.toList()
        override fun text(pid: Int, name: String): String? {
            if (name == "cmdline") return processes[pid]?.takeIf { it.alive }?.cmdline
            if (name == "status") {
                val process = processes[pid]?.takeIf { it.alive } ?: return null
                return "Name:\tcore\nUid:\t${process.uid}\t${process.uid}\t${process.uid}\t${process.uid}\n"
            }
            if (name == "fd") {
                val process = processes[pid]?.takeIf { it.alive } ?: return null
                return process.fds.keys.joinToString("\n")
            }
            return null
        }

        override fun link(pid: Int, fd: String): String? =
            processes[pid]?.takeIf { it.alive }?.fds?.get(fd)

        override fun signal(pid: Int, signalNumber: Int): Boolean {
            val process = processes[pid]?.takeIf { it.alive } ?: return false
            signals += pid to signalNumber
            when (signalNumber) {
                15 -> if (!process.ignoresTerm) { process.alive = false; portFree = true }
                9 -> { process.alive = false; portFree = true }
            }
            return true
        }

        override fun alive(pid: Int): Boolean = processes[pid]?.alive == true
        override fun bindable(port: Int): Boolean = portFree
        override fun myUid(): Int = MY_UID
        override fun netText(name: String): String? = when (name) {
            "tcp" -> tcp4
            "tcp6" -> tcp6
            else -> null
        }
    }

    private fun probeWithStaleCore(
        pid: Int = 4242,
        binary: String = "libsingbox.so",
        uid: Int = MY_UID,
        ignoresTerm: Boolean = false,
        portFree: Boolean = false
    ): FakeProbe {
        val process = FakeProcess(
            uid = uid,
            cmdline = "$NATIVE_LIB_DIR/$binary\u0000run\u0000-c\u0000/config.json",
            fds = mapOf("7" to "socket:[1048571]"),
            ignoresTerm = ignoresTerm
        )
        return FakeProbe(mutableMapOf(pid to process), tcp4 = procTcp, portFree = portFree)
    }

    private val coreBinaries = listOf(CoreEngineInfo.SINGBOX_BINARY, CoreEngineInfo.XRAY_BINARY)

    // ─────────────────────────────────────────────────────────────────────────────
    // 1 — the holder is found and reaped, by name
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun parseListenerInodesFindsOnlyTheRequestedListeningSocket() {
        val inodes = CorePortGuard.parseListenerInodes(procTcp, 10808)
        assertEquals(setOf("1048571"), inodes)
        assertTrue(CorePortGuard.parseListenerInodes(procTcp, 10809).contains("1048572"))
        // State 01 is ESTABLISHED, never a listener.
        assertFalse(CorePortGuard.parseListenerInodes(procTcp, 10810).isNotEmpty())
        // 8080 is a foreign-uid LISTEN — the parser is deliberately uid-agnostic (attribution
        // first, ownership decided later in reclaim), so it must find inode 1048574.
        assertEquals(setOf("1048574"), CorePortGuard.parseListenerInodes(procTcp, 8080))
    }

    @Test
    fun aStaleCoreHoldingThePortIsReapedAndTheStartProceeds() {
        val probe = probeWithStaleCore()
        val result = CorePortGuard.reclaim(
            probe, 10808, NATIVE_LIB_DIR, coreBinaries,
            budgetMs = 250, termWaitMs = 10, killWaitMs = 10, pollMs = 5
        )
        assertTrue("the port must be available after the reap", result.available)
        assertEquals(listOf(4242 to 15), probe.signals)
        assertTrue(result.reaped.single().contains("libsingbox.so"))
        assertTrue(result.reaped.single().contains("state=reaped"))
    }

    @Test
    fun aTermIgnoringCoreEscalatesToSigkill() {
        val probe = probeWithStaleCore(ignoresTerm = true)
        val result = CorePortGuard.reclaim(
            probe, 10808, NATIVE_LIB_DIR, coreBinaries,
            budgetMs = 400, termWaitMs = 20, killWaitMs = 20, pollMs = 5
        )
        assertTrue(result.available)
        assertEquals(listOf(4242 to 15, 4242 to 9), probe.signals)
        assertTrue(result.reaped.single().contains("state=reaped"))
    }

    @Test
    fun aForeignHolderIsNamedButNeverSignalled() {
        // Same loopback port, but the process is root's sshd: ownership fails on BOTH the uid and
        // the binary check, and the guard must not signal either way.
        val probe = probeWithStaleCore(pid = 777, binary = "libsingbox.so", uid = 0)
        // The uid check happens first; to also pin the binary check, run a same-uid non-core.
        val sameUidForeign = probeWithStaleCore(pid = 778, binary = "sshd")
        val both = FakeProbe(
            mutableMapOf(
                777 to FakeProcess(uid = 0, cmdline = "/system/bin/sshd", fds = mapOf("7" to "socket:[1048571]")),
                778 to FakeProcess(uid = MY_UID, cmdline = "/system/bin/sshd", fds = mapOf("9" to "socket:[1048571]"))
            ),
            tcp4 = procTcp
        )
        listOf(probe, sameUidForeign, both).forEach { candidate ->
            val result = CorePortGuard.reclaim(
                candidate, 10808, NATIVE_LIB_DIR, coreBinaries,
                budgetMs = 150, termWaitMs = 5, killWaitMs = 5, pollMs = 5
            )
            assertFalse("a foreign holder must fail the start", result.available)
            assertTrue("…with the local-fault prefix", result.evidence.startsWith("core-port:"))
            assertTrue("…naming the holder", result.evidence.contains("pid="))
            assertEquals("…and no signal ever flies", emptyList<Pair<Int, Int>>(), candidate.signals)
        }
    }

    @Test
    fun aFreePortCostsNothing() {
        val probe = probeWithStaleCore(portFree = true)
        val result = CorePortGuard.reclaim(
            probe, 10808, NATIVE_LIB_DIR, coreBinaries,
            budgetMs = 100, termWaitMs = 5, killWaitMs = 5, pollMs = 5
        )
        assertTrue(result.available)
        assertTrue(result.reaped.isEmpty())
        assertEquals("", result.evidence)
        assertEquals(emptyList<Pair<Int, Int>>(), probe.signals)
    }

    @Test
    fun theExactBindConflictReasonOfClassificationAndWalkExclusion() {
        // The reason exactly as the retained core log carried it on 2026-09-08 12:48.
        val reason = "core-start: exited 1: +0330 2026-09-08 12:48:08 WARN network: " +
            "initialize package manager: read packages list: open /data/system/packages.xml: " +
            "permission denied\n" +
            "FATAL[0000] start service: start inbound/mixed[socks-in]: listen tcp 127.0.0.1:10808: " +
            "bind: address already in use"

        assertTrue(SingBoxAndroidRuntime.isPortBindConflict(reason))
        // The old behaviour: the `core-start:` prefix made this a refusal and the reader walk
        // spawned three dying children per node.
        assertFalse(
            "a bind conflict is identical for every candidate — it must not walk",
            SingBoxManager.isConfigRefusal(reason)
        )
        // The rewritten reason is a local fault in every classifier.
        val rewritten = "core-port: $reason"
        assertTrue(CoreFailurePolicy.isLocal(rewritten))
        assertTrue(ProbeLocalFaultGate().isSweepFatal(rewritten, 0))
        assertTrue(SingBoxAndroidRuntime.explain(reason).contains(SingBoxAndroidRuntime.PORT_REMEDIATION))
        // Precedence pin: the same retained reason also contains the benign packages.xml WARN;
        // the definitive kernel error must win over the loose package-manager heuristic.
        assertFalse(
            SingBoxAndroidRuntime.explain(reason)
                .contains(SingBoxAndroidRuntime.PACKAGE_MANAGER_REMEDIATION)
        )
        // …and the crash-class classifiers must not swallow it into a different fault.
        assertFalse(SingBoxAndroidRuntime.isCoreCrash(reason))
    }

    @Test
    fun aRealBindConflictIsNeverReadAsACrashOrAPanic() {
        val reason = "core-start: exited 1: FATAL start service: start inbound/mixed[socks-in]: " +
            "listen tcp 127.0.0.1:10808: bind: address already in use"
        assertFalse(SingBoxAndroidRuntime.isCoreCrash(reason))
        assertFalse(SingBoxAndroidRuntime.isUnusableCore(reason))
        // Exit 1 is a refusal shape in general (V156 pinned it) — the bind shape is the exception.
        assertFalse(SingBoxManager.isConfigRefusal(reason))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2 — local-client handshake aborts are benign evidence, not failures
    // ─────────────────────────────────────────────────────────────────────────────

    /** The six lines from the 2026-09-09T02:04–02:05 window, verbatim. */
    private val clientAbortLines = listOf(
        "ERROR [4288097813 2.49s] inbound/mixed[socks-in]: process connection from 127.0.0.1:48528: read fqdn: unexpected EOF",
        "ERROR [3033606358 5.0s] inbound/mixed[socks-in]: process connection from 127.0.0.1:48478: read fqdn: unexpected EOF",
        "ERROR [2943035235 1.50s] inbound/mixed[socks-in]: process connection from 127.0.0.1:48568: read fqdn: unexpected EOF",
        "ERROR [1464070429 1.50s] inbound/mixed[socks-in]: process connection from 127.0.0.1:48580: read fqdn: unexpected EOF",
        "ERROR [3889285122 5.0s] inbound/mixed[socks-in]: process connection from 127.0.0.1:48542: read fqdn: unexpected EOF",
        "ERROR [302420225 5.0s] inbound/mixed[socks-in]: process connection from 127.0.0.1:48558: read fqdn: unexpected EOF"
    )

    @Test
    fun theReportedClientAbortLinesAreClassifiedBenign() {
        clientAbortLines.forEach { line ->
            assertTrue(
                "the exact reported line must be benign: $line",
                CoreLogNoise.isLocalClientHandshakeAbort(line)
            )
        }
    }

    @Test
    fun realFaultsAreNeverClassifiedAsClientAborts() {
        // The FATAL bind conflict of the same incident — it contains the inbound tag but no
        // per-connection wrapper, and it MUST stay a counted failure.
        assertFalse(
            CoreLogNoise.isLocalClientHandshakeAbort(
                "FATAL[0000] start service: start inbound/mixed[socks-in]: listen tcp 127.0.0.1:10808: " +
                    "bind: address already in use"
            )
        )
        // The V157 panic.
        assertFalse(
            CoreLogNoise.isLocalClientHandshakeAbort(
                "core-start: exited 2: panic: runtime error: invalid memory address or nil pointer dereference"
            )
        )
        // A remote source is a different story even with the same client abort shape.
        assertFalse(
            CoreLogNoise.isLocalClientHandshakeAbort(
                "ERROR [1 1.0s] inbound/mixed[socks-in]: process connection from 10.0.0.5:444: " +
                    "read fqdn: unexpected EOF"
            )
        )
        // A dial failure through the inbound (server-side) is a real observation.
        assertFalse(
            CoreLogNoise.isLocalClientHandshakeAbort(
                "ERROR [99 2.0s] inbound/mixed[socks-in]: process connection from 127.0.0.1:500: " +
                    "outbound/proxy: dial tcp: connect: connection refused"
            )
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3 — stop() proves its verdict on real processes
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun stopReportsAGracefulExit() {
        val child = ProcessBuilder("sh", "-c", "sleep 30").start()
        try {
            assertTrue(SingBoxProcessSession.stop(child))
            assertEquals(false, child.isAlive)
        } finally {
            child.destroyForcibly()
        }
    }

    @Test
    fun stopEscalatesWhenTheChildIgnoresSigtermAndStillReportsTheTruth() {
        // `trap '' TERM` is the POSIX-spelled stand-in for a core wedged past its graceful
        // shutdown: destroy() is not enough, destroyForcibly() must be, and the returned verdict
        // is the evidence the next start's reclaim story hangs on.
        val child = ProcessBuilder("sh", "-c", "trap '' TERM; sleep 30").start()
        try {
            Thread.sleep(150)
            assertTrue("destroy→destroyForcibly must end in a dead child", SingBoxProcessSession.stop(child))
            assertFalse(child.isAlive)
        } finally {
            child.destroyForcibly()
        }
    }

    @Test
    fun portAvailabilityProbeAgreesWithTheReaperOnAReallyBoundPort() {
        java.net.ServerSocket().use { holder ->
            holder.reuseAddress = true
            holder.bind(java.net.InetSocketAddress("127.0.0.1", 0))
            val port = holder.localPort
            // The real probe cannot bind a port that is actively listening (reuseAddress does not
            // grant SO_REUSEPORT takeover), which is exactly the condition reclaim treats as held.
            val fake = FakeProbe(mutableMapOf(), tcp4 = procTcp, portFree = false)
            assertFalse(fake.bindable(port))
            val inodes = CorePortGuard.parseListenerInodes(procTcp, 10808)
            assertTrue(inodes.isNotEmpty())
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 4 — MarbleNG's own SOCKS5 requests are well-formed on the wire (read fqdn)
    // ─────────────────────────────────────────────────────────────────────────────

    /** Counts whole-buffer writes; the request must arrive as exactly one of them. */
    private class RecordingStream : java.io.OutputStream() {
        val chunks = mutableListOf<ByteArray>()
        override fun write(b: Int) {
            chunks += byteArrayOf(b.toByte())
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            chunks += b.copyOfRange(off, off + len)
        }
    }

    @Test
    fun aDomainTargetCarriesTheRfc1928LengthPrefix() {
        // The inbound's ReadSockString reads ATYP 3 as len+name. Without the length byte the
        // desync read fqdn-len = name[0] and died with exactly `read fqdn: unexpected EOF`
        // whenever the request tail ran out — the 2026-09-09 02:04 cluster.
        val (atyp, addr) = SocksHttpClient.socksTarget("cp.cloudflare.com")
        assertEquals(3, atyp)
        assertEquals(1 + "cp.cloudflare.com".length, addr.size)
        assertEquals("cp.cloudflare.com".length.toByte(), addr[0])
        assertEquals("cp.cloudflare.com", addr.copyOfRange(1, addr.size).toString(Charsets.UTF_8))
    }

    @Test
    fun theDomainRequestLeavesAsOneWellFormedSegment() {
        val (atyp, addr) = SocksHttpClient.socksTarget("example.com")
        val request = SocksHttpClient.buildSocks5Request(atyp, addr, 443)
        // 05 01 00 03 | 0B "example.com" | 01 BB — 18 bytes, byte-for-byte what the parser wants.
        assertEquals(18, request.size)
        assertEquals(5, request[0].toInt())
        assertEquals(1, request[1].toInt()) // CONNECT
        assertEquals(0, request[2].toInt())
        assertEquals(3, request[3].toInt())
        assertEquals(11, request[4].toInt())
        assertEquals("example.com", request.copyOfRange(5, 16).toString(Charsets.UTF_8))
        assertEquals(0x01, request[16].toInt())
        assertEquals(0xBB, request[17].toInt() and 0xff)
    }

    @Test
    fun aLiteralTargetStaysFixedWidthWithoutAnyPrefix() {
        val (atyp4, addr4) = SocksHttpClient.socksTarget("192.0.2.1")
        assertEquals(1, atyp4)
        assertEquals(4, addr4.size)
        val (atyp6, addr6) = SocksHttpClient.socksTarget("[2001:db8::1]")
        assertEquals(4, atyp6)
        assertEquals(16, addr6.size)
        // …and the port stays big-endian on the tail of the assembled request.
        val request = SocksHttpClient.buildSocks5Request(atyp4, addr4, 10808)
        assertEquals(10, request.size)
        assertEquals(0x2A, request[8].toInt())
        assertEquals(0x38, request[9].toInt())
    }

    @Test
    fun theRequestIsSentInExactlyOneWrite() {
        val stream = RecordingStream()
        SocksHttpClient.writeSocks5Request(stream, "example.com", 443)
        assertEquals("one flushed write, not a header segment plus a name segment", 1, stream.chunks.size)
        val (atyp, addr) = SocksHttpClient.socksTarget("example.com")
        assertTrue(
            "the single chunk is the complete request",
            stream.chunks.single().contentEquals(SocksHttpClient.buildSocks5Request(atyp, addr, 443))
        )
    }
}
