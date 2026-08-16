package com.marbleng.app.core

// MARBLE_BUG_FINDER_V11

import android.content.Context
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ConnectionRecord
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.nativebridge.HevTunnel
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
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
        appendLine("=== MarbleNG Bug Finder v11 ===")
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

        val listener = alive && tcpOpen(port)
        out += when {
            listener -> BugCheck("Local SOCKS listener", BugSeverity.PASS, "127.0.0.1:$port accepts TCP")
            alive -> BugCheck("Local SOCKS listener", BugSeverity.FAIL, "Xray is alive but SOCKS port $port is closed", "Restart route")
            else -> BugCheck("Local SOCKS listener", BugSeverity.INFO, "Skipped because Xray is stopped")
        }

        var proxyOk = false
        var proxyDetail = "Not tested"
        if (listener) {
            for ((host,path) in arrayOf("cp.cloudflare.com" to "/generate_204", "www.gstatic.com" to "/generate_204")) {
                val r = runCatching { SocksHttpClient.get(port, host, path, 5_000, 2048) }.getOrNull()
                if (r != null && r.status in 200..399) {
                    proxyOk = true
                    proxyDetail = "$host • HTTP ${r.status} • ${r.elapsedMs.toInt()} ms"
                    break
                }
            }
        }
        out += when {
            proxyOk -> BugCheck("Real HTTPS through Xray", BugSeverity.PASS, proxyDetail)
            listener -> BugCheck("Real HTTPS through Xray", BugSeverity.FAIL, "SOCKS opens but two proxied HTTPS probes failed", "Check node/server/DNS/routing")
            else -> BugCheck("Real HTTPS through Xray", BugSeverity.INFO, "Skipped")
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

            val st = if (jni) runCatching { HevTunnel.stats() }.getOrNull() else null
            if (st == null || st.size < 4) {
                out += BugCheck("HEV counters", BugSeverity.FAIL, "HEV stats API unavailable in Full TUN", "Restart native datapath")
            } else {
                val txp=st[0].coerceAtLeast(0); val txb=st[1].coerceAtLeast(0)
                val rxp=st[2].coerceAtLeast(0); val rxb=st[3].coerceAtLeast(0)
                val at = history.lastOrNull { it.reason.startsWith("connected:") }?.at ?: now
                val age = (now-at).coerceAtLeast(0)
                val sev = if (age > 8000 && txb == 0L && rxb == 0L) BugSeverity.WARN else BugSeverity.PASS
                out += BugCheck("HEV counters", sev, "tx=$txp/$txb bytes • rx=$rxp/$rxb bytes" +
                    if (sev == BugSeverity.WARN) " • zero traffic after ${age/1000}s" else "",
                    if (sev == BugSeverity.WARN) "Load a page and scan again" else "")
                if (txb >= 32768 && rxb == 0L) {
                    out += BugCheck("HEV bidirectional flow", BugSeverity.FAIL,
                        "HEV transmitted $txb bytes but received zero bytes",
                        "Datapath is likely stalled; reconnect")
                } else if (proxyOk && txb == 0L && age > 8000) {
                    out += BugCheck("False-green detector", BugSeverity.WARN,
                        "Xray works but HEV has not observed device traffic; CONNECTED does not prove TUN forwarding",
                        "Generate device traffic and scan again")
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

        (problems(runtime).takeLast(6) + problems(hevlog).takeLast(4) + problems(xlog).takeLast(6))
            .distinct().takeLast(12).forEach { line -> evidence += sanitize(line) }

        return BugReport(now, "$appState • ${stateDetail.ifBlank { "no active route" }}", out, evidence)
    }

    private fun tcpOpen(port: Int) = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1200); it.isConnected }
    }.getOrDefault(false)

    private fun tail(file: File, max: Int) = runCatching {
        if (!file.isFile) "" else file.readText().let { if (it.length <= max) it else it.takeLast(max) }
    }.getOrDefault("")

    private fun problems(text: String) = text.lineSequence().filter {
        it.contains("error",true) || it.contains("fail",true) || it.contains("blocked",true) ||
            it.contains("run-exit",true) || it.contains("handshake",true) ||
            it.contains("timeout",true) || it.contains("refused",true)
    }.toList().takeLast(24)

    private fun sanitize(s: String) = s.take(900)
        .replace(Regex("(?i)(uuid|password|token|private[-_ ]?key)=[^ |]+"), "$1=<redacted>")
}
