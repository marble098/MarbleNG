package com.marbleng.app.core

import java.io.File

/**
 * MARBLE_SINGBOX_PORT_SOVEREIGNTY_V158 — the local SOCKS port is owned by exactly one process:
 * the core this connect is about to start.
 *
 * The reported incident (2026-09-08, one device):
 *
 * ```
 * 09:18:08  core-start: exited 1: … FATAL start service: start inbound/mixed[socks-in]:
 *           listen tcp 127.0.0.1:10808: bind: address already in use
 * 09:18:20  (same, retried)
 * 09:25:09  (same, retried)
 * 09:26:10  (same, retried — four refusals over eight minutes)
 * 09:26:14  core-start: exited 2: … SIGSEGV …          ← unpatched build, separate fault (V157)
 * ```
 *
 * During all eight minutes neither manager owned a live child (`xrayAlive=false`,
 * `alive=false`), so the socket was held by a **core process nothing tracked**: an orphan from an
 * earlier app process that Android had killed (package update, LMK) while its native child kept
 * running, or a child whose handle a superseded start ticket dropped. No amount of stopping the
 * *managed* session can free such a port, and every connect attempt pays the full
 * spawn → FATAL → BLOCKED round trip, with Bug Finder recording `failures=1` after each one.
 *
 * The fix has three layers, in the order they are needed:
 *
 *  1. **Find who holds the port.** `/proc/net/tcp{,6}` maps a LISTEN port to its socket inode;
 *     `/proc/<pid>/fd/<fd>` symlinks (`socket:[inode]`) map the inode back to the owning pid. Both
 *     are readable by an app for its own UID — no root, no shell, same technique AOSP netstat
 *     uses. Ownership is checked twice before any signal: the holder's `/proc/<pid>/status` UID
 *     must be ours, and its `cmdline[0]` must be one of MarbleNG's core binaries. A port held by
 *     any *other* process is reported, never touched.
 *  2. **Reap it, escalating.** SIGTERM (the core's graceful shutdown), a bounded wait, then
 *     SIGKILL. Same-UID processes can always be signalled by the app.
 *  3. **Fail honestly if the port is still not ours.** The reason carries the `core-port:` prefix
 *     — a local fault (`CoreFailurePolicy.isLocal`), never a per-server verdict, never a reason to
 *     walk the reader candidates ([SingBoxManager.isConfigRefusal]).
 *
 * Every step is pure against a [Probe], so the whole lifecycle is provable on the JVM — the same
 * rule V157 used for the nil-monitor fix: a device-only code path with no device-only test is a
 * bug that ships.
 */
object CorePortGuard {

    /** A core process found on this device. [cmdline] is the raw NUL-split command line. */
    data class CoreProcess(val pid: Int, val uid: Int, val binaryPath: String, val cmdline: String)

    /**
     * What [reclaim] found. [available] is the only decision input; [evidence] travels to the
     * user inside a `core-port:` reason when [available] is false.
     */
    data class ReclaimResult(
        val port: Int,
        val available: Boolean,
        val reaped: List<String>,
        val evidence: String
    )

    /**
     * The device surface, narrow enough to fake completely. The production implementation reads
     * `/proc` and uses `Os.kill`; tests provide in-memory fakes.
     */
    interface Probe {
        /** Pids visible to us (candidates; further checks happen per-pid). */
        fun pids(): List<Int>

        /** `/proc/<pid>/<name>` as text, or null when unreadable/absent. */
        fun text(pid: Int, name: String): String?

        /** `/proc/<pid>/fd/<fd>` link target, or null. */
        fun link(pid: Int, fd: String): String?

        /** `/proc/net/<name>` (e.g. `tcp`, `tcp6`) as text, or null when unreadable. */
        fun netText(name: String): String?

        /** Send [signalNumber] (POSIX numbers) to [pid]; true when delivered without error. */
        fun signal(pid: Int, signalNumber: Int): Boolean

        /** True when the process still exists. */
        fun alive(pid: Int): Boolean

        /** True when a listener can bind 127.0.0.1:[port] right now. */
        fun bindable(port: Int): Boolean

        /** This app's UID. */
        fun myUid(): Int
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Pure /proc parsing
    // ─────────────────────────────────────────────────────────────────────────────

    private const val TCP_LISTEN_STATE = "0A"

    /**
     * Inodes of sockets in LISTEN state on [port], from `/proc/net/tcp` or `/proc/net/tcp6` text.
     *
     * Column layout (checked against the kernel's `tcp4/6_seq_file_seq`): `sl local_address
     * rem_address st tx:rx tr:tm->when retrnsmt uid timeout inode …` — local port is the hex
     * suffix of field 1, state field 3, inode field 9.
     */
    fun parseListenerInodes(procTcp: String, port: Int): Set<String> {
        require(port in 1..65535)
        val wanted = "%04X".format(port)
        val inodes = mutableSetOf<String>()
        procTcp.lineSequence().forEach { line ->
            val fields = line.trim().split(Regex("\\s+"))
            if (fields.size < 10) return@forEach
            val local = fields[1]
            val state = fields[3]
            if (!state.equals(TCP_LISTEN_STATE, ignoreCase = true)) return@forEach
            val localPort = local.substringAfterLast(':')
            if (!localPort.equals(wanted, ignoreCase = true)) return@forEach
            val inode = fields[9]
            if (inode.isNotEmpty() && inode != "0") inodes += inode
        }
        return inodes
    }

    /** The binary a pid runs — `cmdline[0]`, NUL-separated, or "" when unreadable/zombie. */
    fun readBinaryPath(probe: Probe, pid: Int): String {
        val cmdline = probe.text(pid, "cmdline") ?: return ""
        val first = cmdline.substringBefore('\u0000').trim()
        return first
    }

    /** The real uid of [pid] from `/proc/<pid>/status`, or -1 when unreadable. */
    fun readUid(probe: Probe, pid: Int): Int {
        val status = probe.text(pid, "status") ?: return -1
        val line = status.lineSequence().firstOrNull { it.startsWith("Uid:") } ?: return -1
        return line.substringAfter("Uid:").trim().split(Regex("\\s+")).firstOrNull()
            ?.toIntOrNull() ?: -1
    }

    /** True when [binaryPath] is one of MarbleNG's own core binaries in [nativeLibDir]. */
    fun isCoreBinary(binaryPath: String, nativeLibDir: String, coreBinaries: Collection<String>): Boolean {
        if (binaryPath.isEmpty()) return false
        return coreBinaries.any { name ->
            File(nativeLibDir, name).absolutePath == binaryPath
        }
    }

    /**
     * Pids that own one of [listenerInodes]. Scans the `/proc/<pid>/fd` links; every lookup failure

     * is skipped — a fd directory that became unreadable mid-scan belongs to a process we could
     * not attribute, and an unattributed process is never signalled.
     */
    fun findHolderPids(probe: Probe, listenerInodes: Set<String>): List<Int> {
        if (listenerInodes.isEmpty()) return emptyList()
        val holders = mutableListOf<Int>()
        for (pid in probe.pids()) {
            val fds = probe.text(pid, "fd") ?: continue
            val holds = fds.lineSequence()
                .map { it.trim().substringBefore(' ').ifBlank { it.trim() } }
                .filter { it.isNotEmpty() }
                .mapNotNull { fdName -> probe.link(pid, fdName) }
                .any { link ->
                    listenerInodes.any { inode -> link == "socket:[$inode]" }
                }
            if (holds) holders += pid
        }
        return holders
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // The reclaim decision
    // ─────────────────────────────────────────────────────────────────────────────

    private const val SIGTERM = 15
    private const val SIGKILL = 9

    /**
     * Makes `127.0.0.1:[port]` bindable for the core this connect is about to start.
     *
     * Order: probe → attribute the holder → reap only same-UID core binaries (TERM, wait, KILL)
     * → re-probe within [budgetMs]. A port that was free all along costs one bind probe. A port
     * held by a foreign process is never signalled; the result fails with a `core-port:` reason
     * that names the holder, which [com.marbleng.app.core.CoreFailurePolicy.isLocal] classifies
     * as local and every measurement surface already treats as a device fault.
     *
     * [termWaitMs]/[killWaitMs] are bounded and injectable so the JVM tests prove both escalation
     * steps without paying real wall-clock for it.
     */
    fun reclaim(
        probe: Probe,
        port: Int,
        nativeLibDir: String,
        coreBinaries: Collection<String>,
        budgetMs: Long = 4_000L,
        termWaitMs: Long = 800L,
        killWaitMs: Long = 1_500L,
        pollMs: Long = 100L
    ): ReclaimResult {
        require(port in 1..65535)
        val startedAt = System.currentTimeMillis()
        fun leftMs(): Long = budgetMs - (System.currentTimeMillis() - startedAt)

        if (probe.bindable(port)) {
            return ReclaimResult(port, available = true, reaped = emptyList(), evidence = "")
        }

        val reaped = mutableListOf<String>()
        val inodes = parseListenerInodes(
            (probe.netText("tcp") ?: "") + "\n" + (probe.netText("tcp6") ?: ""), port
        )
        val holders = findHolderPids(probe, inodes)
        for (pid in holders) {
            val uid = readUid(probe, pid)
            if (uid != probe.myUid()) continue
            val binaryPath = readBinaryPath(probe, pid)
            if (!isCoreBinary(binaryPath, nativeLibDir, coreBinaries)) continue

            // Ours, stale, and squatting on the port the live core needs. TERM first so a healthy
            // core shuts its sockets down cleanly; KILL only what survives the grace window.
            probe.signal(pid, SIGTERM)
            val termDeadline = System.currentTimeMillis() + termWaitMs
            while (probe.alive(pid) && System.currentTimeMillis() < termDeadline) {
                Thread.sleep(minOf(20L, termWaitMs).coerceAtLeast(1L))
            }
            if (probe.alive(pid)) {
                probe.signal(pid, SIGKILL)
                val killDeadline = System.currentTimeMillis() + killWaitMs
                while (probe.alive(pid) && System.currentTimeMillis() < killDeadline) {
                    Thread.sleep(minOf(20L, killWaitMs).coerceAtLeast(1L))
                }
            }
            reaped += "pid=$pid binary=${File(binaryPath).name} " +
                if (probe.alive(pid)) "state=survived" else "state=reaped"
        }

        // Wait the remaining budget out: a reaped process's socket closes asynchronously, and a
        // TERM'd foreign listener we correctly left alone may free the port by itself.
        while (leftMs() > 0 && !probe.bindable(port)) {
            Thread.sleep(minOf(pollMs, leftMs()).coerceAtLeast(1L))
        }
        val available = probe.bindable(port)

        val evidence = if (available) {
            if (reaped.isEmpty()) "" else "core-port: $port was held by a stale core process " +
                "(${reaped.joinToString("; ")}) — reclaimed before start"
        } else {
            val holderEvidence = holders.map { pid ->
                val binary = readBinaryPath(probe, pid)
                "pid=$pid binary=${binary.substringAfterLast('/').ifBlank { "unknown" }}"
            }
            "core-port: 127.0.0.1:$port is already in use" +
                (if (holderEvidence.isEmpty()) "" else " (held by ${holderEvidence.joinToString(", ")})") +
                " — no other app may be asked to free it; pick a different local port or stop the holder"
        }
        return ReclaimResult(port, available, reaped, evidence)
    }

    /**
     * The production probe. `/proc/<pid>/fd` of same-UID processes is readable by the app;
     * everything unreadable is skipped and unattributed holders are never signalled.
     */
    fun androidProbe(): Probe = object : Probe {
        private val selfPid = android.os.Process.myPid()

        override fun pids(): List<Int> = buildList {
            val proc = File("/proc")
            val names = proc.listFiles() ?: return@buildList
            for (entry in names) {
                entry.name.toIntOrNull()?.let(::add)
            }
        }

        override fun text(pid: Int, name: String): String? = runCatching {
            when (name) {
                // fd is a directory listing: one entry name per line.
                "fd" -> File("/proc/$pid/fd").list()?.joinToString("\n")
                else -> File("/proc/$pid/$name").readText()
            }
        }.getOrNull()

        override fun netText(name: String): String? = runCatching {
            File("/proc/net/$name").readText()
        }.getOrNull()

        override fun link(pid: Int, fd: String): String? = runCatching {
            android.system.Os.readlink("/proc/$pid/fd/$fd")
        }.getOrNull()

        override fun signal(pid: Int, signalNumber: Int): Boolean = runCatching {
            val sig = when (signalNumber) {
                SIGTERM -> android.system.OsConstants.SIGTERM
                SIGKILL -> android.system.OsConstants.SIGKILL
                else -> return@runCatching false
            }
            android.system.Os.kill(pid, sig)
            true
        }.getOrDefault(false)

        override fun alive(pid: Int): Boolean {
            if (pid == selfPid) return true
            return File("/proc/$pid").isDirectory
        }

        override fun bindable(port: Int): Boolean = runCatching {
            java.net.ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(java.net.InetSocketAddress("127.0.0.1", port))
            }
            true
        }.getOrDefault(false)

        override fun myUid(): Int = android.os.Process.myUid()
    }
}
