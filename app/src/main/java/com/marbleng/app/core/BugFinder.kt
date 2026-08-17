package com.marbleng.app.core

// MARBLE_BUG_FINDER_V11
// MARBLE_CONNECT_RESCUE_V12
// MARBLE_BUG_FINDER_V13

import android.content.Context
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ConnectionRecord
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.nativebridge.HevTunnel
import java.io.File
import java.net.InetSocketAddress
import java.time.Instant

enum class BugSeverity { PASS, INFO, WARN, FAIL }

data class BugCheck(
    val title: String,
    val severity: BugSeverity,
    val detail: String,
    val action: String = ""
)

data class BugReport(
    val generatedAt: Long,
    val state: String,
    val checks: List<BugCheck>,
    val evidence: List<String>
) {
    val failures get() = checks.count { it.severity == BugSeverity.FAIL }
    val warnings get() = checks.count { it.severity == BugSeverity.WARN }
    val passed get() = checks.count { it.severity == BugSeverity.PASS }

    val headline: String
        get() = when {
            failures > 0 -> "$failures critical issue${if (failures == 1) "" else "s"} found"
            warnings > 0 -> "$warnings warning${if (warnings == 1) "" else "s"} found"
            else -> "No critical runtime bug detected"
        }

    fun asText(): String = buildString {
        appendLine("=== MarbleNG Bug Finder v13 ===")
        appendLine("generated=${Instant.ofEpochMilli(generatedAt)}")
        appendLine("state=$state")
        appendLine("result=$headline")
        appendLine("pass=$passed warn=$warnings fail=$failures")
        appendLine()
        checks.forEach {
            appendLine("[${it.severity}] ${it.title}")
            appendLine(it.detail)
            if (it.action.isNotBlank()) appendLine("Action: ${it.action}")
            appendLine()
        }
        if (evidence.isNotEmpty()) {
            appendLine("=== Recent evidence ===")
            evidence.forEach(::appendLine)
        }
    }
}

class BugFinder(private val context: Context, private val xray: XrayManager) {
    fun scan(
        appState: String,
        stateDetail: String,
        activeProfileId: String,
        settings: AppSettings,
        profiles: List<ProxyProfile>,
        history: List<ConnectionRecord>,
        networkLabel: String
    ): BugReport {
        val out = mutableListOf<BugCheck>()
        val evidence = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val connected = appState == "CONNECTED"
        val fullTun = settings.connectionMode == ConnectionMode.FULL_TUN
        val port = if (fullTun) settings.socksPort else settings.localProxyPort

        val xbin = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        out += if (xbin.isFile && xbin.length() > 0L)
            BugCheck("Xray native core", BugSeverity.PASS, "${xbin.length()} bytes present")
        else BugCheck("Xray native core", BugSeverity.FAIL, "libxray.so is missing", "Rebuild the core bundle")

        val jni = runCatching { System.loadLibrary("marbleng") }.isSuccess
        out += if (jni) BugCheck("HEV JNI bridge", BugSeverity.PASS, "libmarbleng loaded")
        else BugCheck("HEV JNI bridge", BugSeverity.FAIL, "HEV/JNI library failed to load", "Rebuild native HEV")

        out += when {
            profiles.isEmpty() -> BugCheck("Config library", BugSeverity.FAIL, "No profiles installed")
            connected && activeProfileId.isBlank() ->
                BugCheck("Active profile", BugSeverity.FAIL, "CONNECTED but activeProfileId is empty", "Safe runtime reset")
            connected && profiles.none { it.id == activeProfileId } ->
                BugCheck("Active profile", BugSeverity.FAIL, "Connected profile no longer exists", "Reconnect a valid node")
            else -> BugCheck("Config library", BugSeverity.PASS, "${profiles.size} profiles • ${stateDetail.ifBlank { "idle" }}")
        }

        out += BugCheck("Android underlay", BugSeverity.INFO, networkLabel.ifBlank { "Unknown network" })

        val alive = xray.isAlive
        out += when {
            connected && !alive -> BugCheck("Xray process", BugSeverity.FAIL, "UI says CONNECTED but Xray is dead", "Safe runtime reset")
            alive -> BugCheck("Xray process", BugSeverity.PASS, "Xray process is alive")
            else -> BugCheck("Xray process", BugSeverity.INFO, "Xray is stopped while app is $appState")
        }

        val listener = alive && listenerBoundWithoutTraffic(port)
        out += when {
            listener -> BugCheck("Local SOCKS listener", BugSeverity.PASS, "127.0.0.1:$port accepts TCP")
            alive -> BugCheck("Local SOCKS listener", BugSeverity.FAIL, "Xray is alive but SOCKS port $port is closed", "Restart route")
            else -> BugCheck("Local SOCKS listener", BugSeverity.INFO, "Skipped because Xray is stopped")
        }

        var literalIpOk = false
        var literalDetail = "Not tested"
        if (listener) {
            val literal = runCatching {
                SocksHttpClient.get(port, "1.1.1.1", "/cdn-cgi/trace", 3_500, 2_048)
            }.getOrNull()
            if (literal != null && literal.status in 200..399) {
                literalIpOk = true
                literalDetail = "1.1.1.1 • HTTP ${literal.status} • ${literal.elapsedMs.toInt()} ms"
            }
        }
        out += when {
            literalIpOk ->
                BugCheck("Proxy transport without DNS", BugSeverity.PASS, literalDetail)
            listener ->
                BugCheck(
                    "Proxy transport without DNS",
                    BugSeverity.FAIL,
                    "SOCKS opens but literal-IP HTTPS also fails; this is not only a DNS problem",
                    "Check the selected node/transport/server path"
                )
            else -> BugCheck("Proxy transport without DNS", BugSeverity.INFO, "Skipped")
        }

        var proxyOk = false
        var proxyDetail = "Not tested"
        if (listener && literalIpOk) {
            for ((host,path) in arrayOf(
                "cp.cloudflare.com" to "/generate_204",
                "www.gstatic.com" to "/generate_204"
            )) {
                val r = runCatching {
                    SocksHttpClient.get(port, host, path, 4_000, 2_048)
                }.getOrNull()
                if (r != null && r.status in 200..399) {
                    proxyOk = true
                    proxyDetail = "$host • HTTP ${r.status} • ${r.elapsedMs.toInt()} ms"
                    break
                }
            }
        }
        out += when {
            proxyOk ->
                BugCheck("DNS + HTTPS through Xray", BugSeverity.PASS, proxyDetail)
            listener && literalIpOk ->
                BugCheck(
                    "Xray DNS upstream",
                    BugSeverity.FAIL,
                    "Literal-IP HTTPS works but hostname HTTPS fails. Proxy transport is alive; Xray DNS is the failing layer.",
                    "Reconnect with Marble DNS self-healing or change the resolver"
                )
            listener ->
                BugCheck(
                    "DNS + HTTPS through Xray",
                    BugSeverity.INFO,
                    "Not classified because literal-IP transport already failed"
                )
            else -> BugCheck("DNS + HTTPS through Xray", BugSeverity.INFO, "Skipped")
        }

        val runtime = tail(File(context.filesDir, "logs/runtime-debug.log"), 80000)
        val hevlog = tail(File(context.filesDir, "logs/hev-native.log"), 40000)
        val xlog = tail(xray.logFile, 40000)

        if (fullTun && connected) {
            val enter = runtime.lastIndexOf("HEV | run-enter")
            val exit = runtime.lastIndexOf("HEV | run-exit")
            val tun = runtime.lastIndexOf("TUN | established")

            out += when {
                tun < 0 -> BugCheck("Android TUN lifecycle", BugSeverity.FAIL, "No TUN established event for connected state", "Safe runtime reset")
                enter < 0 -> BugCheck("HEV lifecycle", BugSeverity.FAIL, "No HEV run-enter event", "Safe runtime reset")
                exit > enter -> BugCheck("HEV lifecycle", BugSeverity.FAIL, "Latest HEV already exited while UI is CONNECTED", "Safe runtime reset")
                else -> BugCheck("HEV lifecycle", BugSeverity.PASS, "Latest HEV run is still the newest lifecycle event")
            }

            val at = history.lastOrNull { it.reason.startsWith("connected:") }?.at ?: now
            val age = (now-at).coerceAtLeast(0)

            // Avoid poking the native stats API during HEV initialization. Diagnostics are never
            // allowed to become a crash vector just because the user opens Bug Finder quickly.
            if (age < 5_000L) {
                out += BugCheck(
                    "HEV counters",
                    BugSeverity.INFO,
                    "HEV counters warming up • scan again in ${(5_000L-age+999L)/1000L}s"
                )
            } else {
                val st = if (jni) runCatching { HevTunnel.stats() }.getOrNull() else null
                if (st == null || st.size < 4) {
                    out += BugCheck(
                        "HEV counters",
                        BugSeverity.FAIL,
                        "HEV stats API unavailable in Full TUN",
                        "Restart native datapath"
                    )
                } else {
                    val txp=st[0].coerceAtLeast(0); val txb=st[1].coerceAtLeast(0)
                    val rxp=st[2].coerceAtLeast(0); val rxb=st[3].coerceAtLeast(0)
                    val sev = if (age > 8000 && txb == 0L && rxb == 0L) BugSeverity.WARN else BugSeverity.PASS
                    out += BugCheck(
                        "HEV counters",
                        sev,
                        "tx=$txp/$txb bytes • rx=$rxp/$rxb bytes" +
                            if (sev == BugSeverity.WARN) " • zero traffic after ${age/1000}s" else "",
                        if (sev == BugSeverity.WARN) "Load a page and scan again" else ""
                    )
                    if (txb >= 32768 && rxb == 0L) {
                        out += BugCheck(
                            "HEV bidirectional flow",
                            BugSeverity.FAIL,
                            "HEV transmitted $txb bytes but received zero bytes",
                            "Datapath is likely stalled; reconnect"
                        )
                    } else if (proxyOk && txb == 0L && age > 8000) {
                        out += BugCheck(
                            "False-green detector",
                            BugSeverity.WARN,
                            "Xray works but HEV has not observed device traffic; CONNECTED does not prove TUN forwarding",
                            "Generate device traffic and scan again"
                        )
                    }
                }
            }
        } else if (connected) {
            out += BugCheck("Forwarding mode", BugSeverity.INFO, "Local proxy mode; HEV/TUN checks skipped")
        }

        val assets = xray.routingAssetStatus()
        out += when {
            settings.routeBlockAds && !assets.geoSiteReady ->
                BugCheck("GeoSite asset", BugSeverity.FAIL, "Routing/ad policy needs geosite.dat but it is missing", "Prepare routing assets")
            settings.routingMode.name.contains("GEO") && !assets.geoIpReady ->
                BugCheck("GeoIP asset", BugSeverity.FAIL, "Geo routing selected but geoip.dat is missing", "Prepare routing assets")
            else -> BugCheck("Routing assets", BugSeverity.PASS, "geoip=${assets.geoIpReady} • geosite=${assets.geoSiteReady}")
        }

        val incidentRuntime = latestIncident(runtime)
        (
            problems(incidentRuntime).takeLast(6) +
                problems(hevlog).takeLast(4) +
                problems(xlog).takeLast(6)
            )
            .distinct()
            .takeLast(12)
            .forEach { line -> evidence += sanitize(line) }

        return BugReport(
            System.currentTimeMillis(),
            "$appState • ${stateDetail.ifBlank { "no active route" }}",
            out,
            evidence
        )
    }

    /**
     * Detect that Xray owns the loopback listener without sending an incomplete SOCKS request.
     * The old connect-and-close check made Bug Finder create the exact EOF warning it then reported.
     */
    private fun listenerBoundWithoutTraffic(port: Int): Boolean {
        return runCatching {
            java.net.ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("127.0.0.1", port))
            }
            false
        }.getOrElse {
            true
        }
    }

    private fun latestIncident(text: String): String {
        if (text.isBlank()) return text
        val markers = listOf(
            "VPN | connect-request",
            "VPN | service-created"
        )
        val start = markers.maxOf { marker -> text.lastIndexOf(marker) }
        return if (start >= 0) text.substring(start) else text
    }

    private fun tail(file: File, max: Int) = runCatching {
        if (!file.isFile) "" else file.readText().let { if (it.length <= max) it else it.takeLast(max) }
    }.getOrDefault("")

    private fun problems(text: String) = text.lineSequence().filter { line ->
        val cleanHevExit =
            line.contains("HEV | run-exit", true) &&
                line.contains("code=0", true) &&
                line.contains("runningFlag=false", true) &&
                line.contains("xrayAlive=false", true)

        val benignHistoricalNoise =
            line.contains("proxy/socks: failed to read request > EOF", true) ||
                (
                    line.contains("HTTPUpgrade transport", true) &&
                        line.contains("deprecated", true)
                    )

        !cleanHevExit &&
            !benignHistoricalNoise &&
            (
                line.contains("error", true) ||
                    line.contains("fail", true) ||
                    line.contains("blocked", true) ||
                    line.contains("run-exit", true) ||
                    line.contains("handshake", true) ||
                    line.contains("timeout", true) ||
                    line.contains("refused", true)
                )
    }.toList().takeLast(24)

    private fun sanitize(s: String) = s.take(900)
        .replace(Regex("(?i)(uuid|password|token|private[-_ ]?key)=[^ |]+"), "$1=<redacted>")
}
