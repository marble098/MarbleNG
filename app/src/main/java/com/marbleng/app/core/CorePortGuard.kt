package com.marbleng.app.core

import java.io.File

object CorePortGuard {
    data class CoreProcess(val pid: Int, val uid: Int, val binaryPath: String, val cmdline: String)

    data class ReclaimResult(
        val port: Int,
        val available: Boolean,
        val reaped: List<String>,
        val evidence: String
    )

    interface Probe {
        fun pids(): List<Int>

        fun text(pid: Int, name: String): String?

        fun link(pid: Int, fd: String): String?

        fun netText(name: String): String?

        fun signal(pid: Int, signalNumber: Int): Boolean

        fun alive(pid: Int): Boolean

        fun bindable(port: Int): Boolean

        fun myUid(): Int
    }

    private const val TCP_LISTEN_STATE = "0A"

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

    fun readBinaryPath(probe: Probe, pid: Int): String {
        val cmdline = probe.text(pid, "cmdline") ?: return ""
        val first = cmdline.substringBefore('\u0000').trim()
        return first
    }

    fun readUid(probe: Probe, pid: Int): Int {
        val status = probe.text(pid, "status") ?: return -1
        val line = status.lineSequence().firstOrNull { it.startsWith("Uid:") } ?: return -1
        return line.substringAfter("Uid:").trim().split(Regex("\\s+")).firstOrNull()
            ?.toIntOrNull() ?: -1
    }

    fun isCoreBinary(binaryPath: String, nativeLibDir: String, coreBinaries: Collection<String>): Boolean {
        if (binaryPath.isEmpty()) return false
        return coreBinaries.any { name ->
            File(nativeLibDir, name).absolutePath == binaryPath
        }
    }

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

    private const val SIGTERM = 15
    private const val SIGKILL = 9

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
