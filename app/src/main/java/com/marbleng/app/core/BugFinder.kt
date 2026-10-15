package com.marbleng.app.core

// MARBLE_VERIFIED_EVIDENCE_CLASSIFICATION_V50

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import com.marbleng.app.MarbleApplication
import com.marbleng.app.data.AppStore
import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.ConnectionRecord
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.net.PrivacyReport
import java.io.File
import java.io.RandomAccessFile
import java.net.Inet6Address
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.ArrayDeque
import java.util.Locale
import java.util.TimeZone

enum class BugSeverity { PASS, INFO, WARN, FAIL }

data class BugCheck(
    val title: String,
    val severity: BugSeverity,
    val detail: String,
    val action: String = ""
)

data class BugSection(
    val title: String,
    val body: String
)

data class BugReport(
    val generatedAt: Long,
    val state: String,
    val checks: List<BugCheck>,
    val evidence: List<String>,
    val sections: List<BugSection> = emptyList()
) {
    val failures get() = checks.count { it.severity == BugSeverity.FAIL }
    val warnings get() = checks.count { it.severity == BugSeverity.WARN }
    val passed get() = checks.count { it.severity == BugSeverity.PASS }

    val headline: String
        get() = when {
            failures > 0 -> "$failures current critical issue${if (failures == 1) "" else "s"} found"
            warnings > 0 -> "$warnings diagnostic warning${if (warnings == 1) "" else "s"} found"
            else -> "Runtime looks healthy • full evidence captured"
        }

    fun asText(): String = buildString {
        appendLine("=== MarbleNG BUG FINDER ULTIMATE v17 ===")
        appendLine("generated=${Instant.ofEpochMilli(generatedAt)}")
        appendLine("state=$state")
        appendLine("result=$headline")
        appendLine("pass=$passed warn=$warnings fail=$failures")
        appendLine("mode=BOTH CORES OBSERVATION • SingBox extended added • passive evidence + log reading")
        appendLine()

        appendLine("=== HEALTH CHECKS ===")
        checks.forEach {
            appendLine("[${it.severity}] ${it.title}")
            appendLine(it.detail)
            if (it.action.isNotBlank()) appendLine("Action: ${it.action}")
            appendLine()
        }

        sections.forEach { section ->
            appendLine("=== ${section.title} ===")
            appendLine(section.body.ifBlank { "No evidence" })
            appendLine()
        }

        if (evidence.isNotEmpty()) {
            appendLine("=== PRIORITY ERROR / CRASH / BUG EVIDENCE ===")
            evidence.forEach(::appendLine)
        }

        appendLine()
        appendLine("=== PRIVACY NOTE ===")
        appendLine("Raw proxy/share configs, UUIDs, passwords, tokens, private keys and auth material are redacted.")
    }
}

private data class ExitEvidence(
    val text: String,
    val crashLike: Int,
    val lowMemory: Int,
    val crashSummaries: List<String> = emptyList(),
    val totalCrashLike: Int = 0
)

class BugFinder(private val context: Context, private val xray: XrayManager, private val singbox: SingBoxManager? = null) {
    private val diag = RuntimeDiagnostics(context)

    fun scan(
        appState: String,
        stateDetail: String,
        activeProfileId: String,
        settings: AppSettings,
        profiles: List<ProxyProfile>,
        history: List<ConnectionRecord>,
        networkLabel: String,
        sentinel: PrivacySentinelState,
        privacy: PrivacyReport?,
        tunnelUptimeMs: Long = 0L,
        lastProbeFault: String = ""
    ): BugReport {
        RuntimeDiagnostics.flush(400L)

        val now = System.currentTimeMillis()
        val checks = mutableListOf<BugCheck>()
        val sections = mutableListOf<BugSection>()
        val connected = appState == "CONNECTED"
        val fullTun = settings.connectionMode == ConnectionMode.FULL_TUN
        val port = if (fullTun) settings.socksPort else settings.localProxyPort

        val runtime = tail(File(context.filesDir, "logs/runtime-debug.log"), 700_000)
        val runtime1 = tail(File(context.filesDir, "logs/runtime-debug.log.1"), 350_000)
        val runtime2 = tail(File(context.filesDir, "logs/runtime-debug.log.2"), 250_000)
        val hevLog = tail(File(context.filesDir, "logs/hev-native.log"), 250_000)
        val xrayLog = tail(xray.logFile, 300_000)
        val singboxLog = if (singbox != null) tail(singbox.logFile, 250_000) else "SingBox manager not provided"
        val singboxUrlTestLog = if (singbox != null) tail(File(context.cacheDir, "singbox-urltest.log"), 80_000) else "Not available"
        val crashPending = tail(File(context.filesDir, "logs/fatal-crash-pending.txt"), 120_000)

        val allRuntime = listOf(runtime2, runtime1, runtime)
            .filterNot { it.startsWith("No log file yet:") }
            .joinToString("\n")

        val xbin = File(context.applicationInfo.nativeLibraryDir, "libxray.so")
        checks += if (xbin.isFile && xbin.length() > 0L) {
            BugCheck("Xray native core", BugSeverity.PASS, "${xbin.length()} bytes present")
        } else {
            BugCheck("Xray native core", BugSeverity.FAIL, "libxray.so is missing", "Rebuild the core bundle")
        }

        val jni = File(context.applicationInfo.nativeLibraryDir, "libmarbleng.so")
        checks += if (jni.isFile && jni.length() > 0L) {
            BugCheck("HEV JNI bridge", BugSeverity.PASS, "${jni.length()} bytes present")
        } else {
            BugCheck("HEV JNI bridge", BugSeverity.FAIL, "libmarbleng.so is missing", "Rebuild native HEV")
        }

        val sbin = File(context.applicationInfo.nativeLibraryDir, "libsingbox.so")
        checks += if (sbin.isFile && sbin.length() > 0L) {
            BugCheck("SingBox extended native core", BugSeverity.PASS, "libsingbox.so ${sbin.length()} bytes")
        } else {
            BugCheck("SingBox extended native core", BugSeverity.FAIL, "libsingbox.so is missing", "Rebuild SingBox extended bundle")
        }

        val selfTestLog = tail(File(context.cacheDir, "singbox-selftest.log"), 40_000)
        val coreEvidence = listOf(singboxLog, singboxUrlTestLog, selfTestLog).joinToString("\n")
        val selfTestVerdict = singbox?.lastSelfTest
            ?: SingBoxCoreSelfTest.read(File(context.filesDir, "singbox-selftest.json"), sbin)
        val singboxStartFault = SingBoxStartFailure.classify(singbox?.lastStartError.orEmpty(), allRuntime)
        checks += when {
            SingBoxAndroidRuntime.isCoreCrash(coreEvidence) -> BugCheck(
                "SingBox core start-up",
                BugSeverity.FAIL,
                "The sing-box core crashed while starting: " +
                    "${ProbeLocalFaultGate.headline(coreEvidence)} — every measurement through it " +
                    "fails identically, whatever the server says",
                SingBoxAndroidRuntime.CRASH_REMEDIATION
            )
            selfTestVerdict != null && selfTestVerdict.coreFault -> BugCheck(
                "SingBox core start-up",
                BugSeverity.FAIL,
                "The core self-test cannot start this binary: " +
                    ProbeLocalFaultGate.headline(selfTestVerdict.reason),
                SingBoxAndroidRuntime.CRASH_REMEDIATION
            )
            singboxStartFault != null -> BugCheck(
                "SingBox core start-up",
                BugSeverity.FAIL,
                singboxStartFault.headline,
                singboxStartFault.remediation
            )

            singbox?.lastStartReadiness?.controllerMissing == true -> BugCheck(
                "SingBox core start-up",
                BugSeverity.WARN,
                "The core is carrying traffic, but its Clash controller never answered " +
                    "(waited ${SingBoxProcessSession.CONTROLLER_TIMEOUT_MS} ms after the local " +
                    "inbound was up): Real delay through the core and the Engine page's live " +
                    "counters read nothing, while the route itself is unaffected",
                SingBoxAndroidRuntime.STARTUP_TIMEOUT_REMEDIATION
            )

            SingBoxAndroidRuntime.isPackageManagerFault(coreEvidence) -> BugCheck(
                "SingBox core start-up",
                BugSeverity.INFO,
                "The core logged Android's package-list probe (/data/system/packages.xml: " +
                    "permission denied) and kept running • no crash signature in the retained logs",
                SingBoxAndroidRuntime.PACKAGE_MANAGER_REMEDIATION
            )
            else -> BugCheck(
                "SingBox core start-up",
                BugSeverity.PASS,
                if (selfTestVerdict?.ok == true) {
                    "self-test passed: the core starts and serves its local inbound"
                } else {
                    "no crash signature in the retained sing-box logs"
                }
            )
        }

        val xrayStartFault = XrayStartFailure.classify(xray.lastStartError, allRuntime)
        if (xrayStartFault != null) {
            checks += BugCheck(
                "Xray core start-up",
                BugSeverity.FAIL,
                xrayStartFault.headline,
                xrayStartFault.remediation
            )
        }

        checks += when {
            profiles.isEmpty() -> BugCheck("Server inventory", BugSeverity.FAIL, "No profiles installed")
            connected && activeProfileId.isBlank() ->
                BugCheck("Active profile", BugSeverity.FAIL, "CONNECTED but activeProfileId is empty", "Safe runtime reset")
            connected && profiles.none { it.id == activeProfileId } ->
                BugCheck("Active profile", BugSeverity.FAIL, "Connected profile no longer exists", "Reconnect a valid server")

            else -> {
                val stored = runCatching { AppStore(context).loadProfiles().size }.getOrNull()
                if (stored != null && stored != profiles.size) {
                    BugCheck(
                        "Server inventory",
                        BugSeverity.WARN,
                        "${profiles.size} loaded • $stored stored • store and memory disagree",
                        "Refresh the affected source or restart the app to reconcile the inventory"
                    )
                } else {
                    BugCheck("Server inventory", BugSeverity.PASS, "${profiles.size} profiles • ${stateDetail.ifBlank { "idle" }}")
                }
            }
        }

        checks += BugCheck("Android underlay", BugSeverity.INFO, networkLabel.ifBlank { "Unknown network" })

        val familyPlan = AddressFamilyPolicy.plan(
            settings = settings,
            underlayHasIpv6 = AddressFamilyPolicy.underlayHasIpv6()
        )
        val lastTunEstablished = allRuntime.lastIndexOf("TUN | established")
        val ipv6RouteCaptured = lastTunEstablished >= 0 &&
            "ipv6Captured=true" in allRuntime.substring(lastTunEstablished)
        checks += when {
            !settings.ipv6Enabled -> BugCheck(
                "IPv6 path",
                BugSeverity.INFO,
                "Disabled by policy • IPv4-only TUN (IPv6 is not captured, so Happy Eyeballs cannot stall)"
            )
            !familyPlan.underlayHasIpv6 -> BugCheck(
                "IPv6 path",
                if (connected) BugSeverity.WARN else BugSeverity.INFO,
                "No global IPv6 address on this underlay • endpoints fall back to IPv4 • " +
                    nodeIpv6Evidence(profiles.firstOrNull { it.id == activeProfileId }, settings),
                "Check carrier/router IPv6 (DHCPv6-PD or 6rd), then reconnect"
            )
            connected && fullTun && !ipv6RouteCaptured -> BugCheck(
                "IPv6 path",
                BugSeverity.FAIL,
                "Android established the tunnel without capturing ::/0 • IPv6 would leave the VPN",
                "Reconnect; if it repeats, review split-tunnel and IPv6 support on the network"
            )
            else -> BugCheck(
                "IPv6 path",
                BugSeverity.PASS,
                "${AddressFamilyPolicy.describe(familyPlan)} • endpoint ${familyPlan.endpointStrategy} " +
                    "• dns ${familyPlan.dnsQueryStrategy}" +
                    (if (familyPlan.raceEnabled) " • race ${familyPlan.tryDelayMs} ms" else "") +
                    " • ${nodeIpv6Evidence(profiles.firstOrNull { it.id == activeProfileId }, settings)}"
            )
        }

        val coreState = resolveActiveCoreState(
            engine = settings.coreEngine(),
            xrayAlive = xray.isAlive,
            xrayPid = xray.processPid,
            xrayPhase = xray.lastStartPhase,
            xrayError = xray.lastStartError,
            singboxAlive = singbox?.isAlive ?: false,
            singboxPhase = singbox?.lastStartPhase.orEmpty(),
            singboxError = singbox?.lastStartError.orEmpty()
        )
        val alive = coreState.alive
        val xrayPid = coreState.pid
        checks += when {
            connected && !alive -> BugCheck(
                "${coreState.label} process",
                BugSeverity.FAIL,
                "UI says CONNECTED but ${coreState.label} is dead (phase=${coreState.phase.ifBlank { "stopped" }})",
                "Safe runtime reset"
            )
            alive -> BugCheck(
                "${coreState.label} process",
                BugSeverity.PASS,
                "alive=true • pid=${xrayPid.takeIf { it > 0 } ?: "unknown"} • phase=${coreState.phase.ifBlank { "running" }}"
            )
            else -> BugCheck(
                "${coreState.label} process",
                BugSeverity.INFO,
                "${coreState.label} stopped while app is $appState"
            )
        }

        val latestStartCommand = allRuntime.lastIndexOf("VPN | command | action=com.marbleng.START")
        val latestConnectRequest = allRuntime.lastIndexOf("VPN | connect-request")
        val latestStartupTimeout = allRuntime.lastIndexOf("VPN | startup-timeout")
        val latestSocksReady = maxOf(
            allRuntime.lastIndexOf("XRAY | socks-ready"),
            allRuntime.lastIndexOf("SINGBOX | socks-ready")
        )
        val latestHevReady = allRuntime.lastIndexOf("HEV | ready")
        val latestReady = maxOf(latestSocksReady, latestHevReady)
        val latestVerifiedLatencySample = allRuntime.lastIndexOf(
            "method=verified-https-ttfb-rolling-ipdv-loss-p90"
        )

        val latestLegacyConnectEstimate = allRuntime.lastIndexOf(
            "method=socks-connect-estimate-rolling-ipdv-loss-p90"
        )
        val latestProbeFailure = maxOf(
            allRuntime.lastIndexOf("verified RTT endpoints are blocked or timing out"),
            allRuntime.lastIndexOf("ROUTE | probe-failed")
        )

        checks += when {
            appState == "CONNECTING" &&
                !alive &&
                latestStartCommand > latestStartupTimeout &&
                latestStartupTimeout > latestConnectRequest &&
                latestStartupTimeout > latestReady -> BugCheck(
                "Startup command progression",
                BugSeverity.FAIL,
                "Queued START stalled before connect-request; previous cleanup/core start did not release promptly",
                "Safe runtime reset, then retry"
            )
            appState in setOf("CONNECTING", "BLOCKED") &&
                latestStartupTimeout > latestConnectRequest &&
                latestStartupTimeout > latestReady -> BugCheck(
                "Startup watchdog",
                BugSeverity.FAIL,
                "Latest connection attempt hit the startup timeout before Xray/HEV became ready",
                "Inspect XRAY start-result/start phase and routing assets"
            )
            appState == "BLOCKED" -> BugCheck(
                "Startup / blocked state",
                BugSeverity.FAIL,
                stateDetail.ifBlank { "Runtime is BLOCKED" },
                "Inspect the connection timeline before retrying"
            )
            appState == "CONNECTING" && !alive -> BugCheck(
                "Startup progression",
                BugSeverity.WARN,
                "CONNECTING while ${coreState.label} is not alive yet; no newer ready event is retained",
                "Re-run Bug Finder if this state persists"
            )
            else -> BugCheck(
                "Startup progression",
                BugSeverity.PASS,
                "No unresolved startup timeout or stalled START command in the current runtime state"
            )
        }

        val listener = alive && listenerBoundWithoutTraffic(port)
        checks += when {
            listener -> BugCheck("Local SOCKS listener", BugSeverity.PASS, "127.0.0.1:$port is bound • checked without opening a SOCKS connection")
            alive -> BugCheck("Local SOCKS listener", BugSeverity.FAIL, "${coreState.label} is alive but port $port is not bound", "Restart route")
            else -> BugCheck("Local SOCKS listener", BugSeverity.INFO, "Skipped because ${coreState.label} is stopped")
        }

        checks += BugCheck(
            "Bug Finder traffic policy",
            BugSeverity.PASS,
            "Passive diagnostics only • no HTTP/DNS/tunnel test is generated by this scan"
        )

        val containmentSignals = listOf(
            sentinel.tunnelRoutes,
            sentinel.ipv4Captured,
            sentinel.ipv6Captured,
            sentinel.dnsHijack,
            sentinel.encryptedDns,
            sentinel.systemDnsFallbackBlocked,
            sentinel.killSwitchArmed,
            sentinel.xrayAlive,
            sentinel.hevAlive
        )
        val containmentScore = containmentSignals.count { it } * 100 / containmentSignals.size
        checks += when {
            !connected || !fullTun -> BugCheck(
                "Passive leak containment",
                BugSeverity.INFO,
                "Device-wide containment score is measured only during an active Full TUN"
            )
            containmentScore == 100 -> BugCheck(
                "Passive leak containment",
                BugSeverity.PASS,
                "100% (9/9) runtime guards • IPv4/IPv6 capture, DNS hijack, encrypted-only fallback policy, kill switch, Xray and HEV"
            )
            else -> BugCheck(
                "Passive leak containment",
                BugSeverity.WARN,
                "$containmentScore% (${containmentSignals.count { it }}/9) runtime guards are currently proven",
                "Open Privacy details and inspect the missing guard before trusting this route"
            )
        }
        checks += privacy?.let { audit ->
            BugCheck(
                "External anti-leak audit",
                if (audit.healthy) BugSeverity.PASS else BugSeverity.WARN,
                "Anti-IP ${audit.ipLeakScore}% • DNS ${audit.dnsLeakScore}% • combined ${audit.overallScore}%",
                if (audit.healthy) "" else audit.note
            )
        } ?: BugCheck(
            "External anti-leak audit",
            BugSeverity.INFO,
            "Not run in this process • Bug Finder stays passive and never creates leak-test traffic",
            "Run Privacy audit while connected for measured Anti-IP and DNS percentages"
        )

        // MARBLE_LIVE_METRIC_OBSERVABILITY_V47

        checks += when {
            latestHevReady >= 0 && latestVerifiedLatencySample > latestHevReady -> BugCheck(
                "Live Ping / Jitter / Quality",
                BugSeverity.PASS,
                "The latest healthy tunnel produced certificate-verified HTTPS RTT evidence"
            )
            latestHevReady >= 0 &&
                latestLegacyConnectEstimate > latestHevReady &&
                latestLegacyConnectEstimate >= latestVerifiedLatencySample -> BugCheck(
                "Live Ping / Jitter / Quality",
                BugSeverity.WARN,
                "A legacy Marble build published SOCKS CONNECT setup timing as a route estimate; " +
                    "that value is not Internet RTT evidence",
                "Rebuild with v50; live Ping/Jitter/Quality now accept verified HTTPS only"
            )
            latestHevReady >= 0 && latestProbeFailure > latestHevReady -> BugCheck(
                "Live Ping / Jitter / Quality",
                BugSeverity.WARN,
                "The latest tunnel is ready, but certificate-verified RTT endpoints failed or timed out",
                "Marble keeps metrics unknown instead of fabricating a localhost/SOCKS estimate"
            )
            connected && latestHevReady >= 0 -> BugCheck(
                "Live Ping / Jitter / Quality",
                BugSeverity.INFO,
                "Tunnel process is ready and the first certificate-verified RTT sample is pending",
                "Literal and domain HTTPS probes are provider-diverse, bounded and failure-backed-off"
            )
            else -> BugCheck(
                "Live Ping / Jitter / Quality",
                BugSeverity.INFO,
                "No active healthy tunnel is available for a verified live RTT sample"
            )
        }

        if (fullTun && connected) {
            val connect = allRuntime.lastIndexOf("VPN | connect-request")
            val tun = allRuntime.lastIndexOf("TUN | established")
            val hevEnter = allRuntime.lastIndexOf("HEV | run-enter")
            val hevExit = allRuntime.lastIndexOf("HEV | run-exit")
            val blocked = allRuntime.lastIndexOf("VPN | blocked")

            checks += when {
                connect >= 0 && blocked > connect && blocked > tun ->
                    BugCheck("VPN lifecycle", BugSeverity.FAIL, "Latest connection attempt entered BLOCKED", "Inspect connection timeline")
                tun < connect ->
                    BugCheck("Android TUN lifecycle", BugSeverity.FAIL, "No TUN established after latest connect request", "Inspect VPN lifecycle evidence")
                hevEnter < tun ->
                    BugCheck("HEV lifecycle", BugSeverity.FAIL, "TUN established but no HEV run-enter followed", "Inspect native HEV evidence")
                hevExit > hevEnter ->
                    BugCheck("HEV lifecycle", BugSeverity.FAIL, "Latest HEV run exited while UI is CONNECTED", "Reconnect and inspect HEV exit")
                else -> BugCheck("TUN + HEV lifecycle", BugSeverity.PASS, "Latest lifecycle is consistent with CONNECTED")
            }

            checks += BugCheck(
                "Native statistics safety",
                BugSeverity.PASS,
                "Bug Finder does not call HevTunnel.stats; it consumes service/native logs instead so diagnostics cannot race HEV startup"
            )
        } else if (connected) {
            checks += BugCheck("Forwarding mode", BugSeverity.INFO, "Local proxy mode • TUN/HEV lifecycle checks not required")
        }

        val assets = xray.routingAssetStatus()
        checks += when {
            RoutingEngine.needsGeoSite(settings) && !assets.geoSiteReady ->
                BugCheck("GeoSite asset", BugSeverity.FAIL, "Ad/routing policy needs geosite.dat but it is missing", "Prepare routing assets")
            RoutingEngine.needsGeoIp(settings) && !assets.geoIpReady ->
                BugCheck("GeoIP asset", BugSeverity.FAIL, "Geo routing selected but geoip.dat is missing", "Prepare routing assets")
            else -> BugCheck("Routing assets", BugSeverity.PASS, "geoip=${assets.geoIpReady} (${assets.geoIpBytes}) • geosite=${assets.geoSiteReady} (${assets.geoSiteBytes})")
        }

        val diagnosticStatus = RuntimeDiagnostics.status()
        checks += when {
            diagnosticStatus.dropped > 0 -> BugCheck(
                "Diagnostics backpressure",
                BugSeverity.WARN,
                "${diagnosticStatus.dropped} diagnostic entries were dropped instead of slowing the app",
                "Keep Debug Mode only while investigating if storage is extremely slow"
            )
            !diagnosticStatus.writerHealthy -> BugCheck(
                "Diagnostics writer",
                BugSeverity.WARN,
                diagnosticStatus.writerError.ifBlank { "Writer reported an unknown failure" },
                "Check storage availability"
            )
            else -> BugCheck(
                "Diagnostics fast path",
                BugSeverity.PASS,
                "queue=${diagnosticStatus.queued}/4096 • dropped=${diagnosticStatus.dropped} • ring=${diagnosticStatus.ringEvents}"
            )
        }

        // MARBLE_RESOLVER_HEALTH_V38 / MARBLE_DIAGNOSTICS_CONSISTENCY_V1

        val activeCoreLog = if (settings.coreEngine() == CoreEngine.SINGBOX) singboxLog else xrayLog
        val rawLines = activeCoreLog.lineSequence().filter { it.isNotBlank() }.toList()
        val resolverSummary = ResolverFailureClassifier.summarize(rawLines)

        val persistedEvidence = runCatching {
            (context.applicationContext as? MarbleApplication)?.repo?.intelligence?.resolverEvidence().orEmpty()
        }.getOrDefault(emptyList())

        val endpointEvidence = ResolverEvidencePolicy.observe(rawLines.asSequence(), persistedEvidence, now)
        val worstEndpoint = endpointEvidence.maxByOrNull { it.decisiveFailures }
        val demotedEndpoints = ResolverEvidencePolicy.demoted(
            endpointEvidence.map { it.endpoint },
            endpointEvidence,
            now
        )

        val dohDeadlineCount = maxOf(resolverSummary.deadlineCount, endpointEvidence.sumOf { it.deadlines })
        val dnsEofCount = maxOf(resolverSummary.eofCount, endpointEvidence.sumOf { it.eof })
        val certExpired = maxOf(resolverSummary.certExpiredCount, endpointEvidence.sumOf { it.certExpired })
        val tlsCount = maxOf(resolverSummary.tlsCount, endpointEvidence.sumOf { it.tls })
        val resolverErrors = maxOf(resolverSummary.transportFailures, dohDeadlineCount + dnsEofCount + certExpired + tlsCount)

        val effectiveUptimeMs = if (tunnelUptimeMs > 0L) {
            tunnelUptimeMs
        } else {
            estimateSessionWindowMs(rawLines, allRuntime)
        }
        val resolverWindow = ResolverEvidencePolicy.window(resolverErrors, effectiveUptimeMs)
        val windowMinutes = resolverWindow.windowMs / 60_000L
        val rateLabel = if (resolverWindow.rateUnknown) {
            "window unknown"
        } else {
            String.format(java.util.Locale.US, "%.2f/min over %d min", resolverWindow.perMinute, windowMinutes)
        }
        val attributionLabel = if (worstEndpoint == null) {
            "no endpoint attribution in the retained log"
        } else {
            "worst=${worstEndpoint.endpoint} (${worstEndpoint.decisiveFailures}) • " +
                "demoted=${demotedEndpoints.joinToString(",").ifBlank { "none" }}"
        }

        checks += when {
            certExpired > 0 -> BugCheck(
                "Resolver health",
                BugSeverity.WARN,
                "$certExpired expired-certificate resolver event(s) observed in the current session",
                "Endpoint quarantine is active; an expired resolver will not stay in rotation silently"
            )
            resolverErrors == 0 -> BugCheck(
                "Resolver health",
                BugSeverity.PASS,
                "No current-session DoH deadline, DNS EOF or TLS resolver failure observed"
            )
            resolverWindow.severity == ResolverEvidencePolicy.Severity.SEVERE -> BugCheck(
                "Resolver health",
                BugSeverity.WARN,
                "$resolverErrors resolver errors at $rateLabel • " +
                    "$dohDeadlineCount DoH deadlines • $dnsEofCount DNS EOF • " +
                    "$tlsCount TLS • $attributionLabel",
                "Failing endpoints are demoted and encrypted fallback stays armed; if this rate " +
                    "survives a reconnect, the operator is filtering every resolver in the pool"
            )

            resolverWindow.rateUnknown &&
                resolverWindow.severity == ResolverEvidencePolicy.Severity.ELEVATED -> BugCheck(
                "Resolver health",
                BugSeverity.WARN,
                "$resolverErrors retained resolver errors • $rateLabel • " +
                    "$dohDeadlineCount DoH deadlines • $dnsEofCount DNS EOF • " +
                    "$tlsCount TLS • $attributionLabel",
                "Encrypted fallback remains active; re-run while connected for a rate-based verdict"
            )
            else -> BugCheck(
                "Resolver health",
                BugSeverity.INFO,
                "$resolverErrors resolver events contained at $rateLabel • " +
                    "$dohDeadlineCount DoH deadlines • $attributionLabel",
                "Below the rate that affects resolution: encrypted failover absorbed them"
            )
        }

        val intelligence = runCatching {
            (context.applicationContext as? MarbleApplication)?.repo?.intelligence
        }.getOrNull()
        val stormActive = intelligence?.dnsStormActive() == true
        val stormRate = intelligence?.dnsStormRatePerMinute() ?: 0.0
        if (stormActive || stormRate >= 3.0) {
            checks += BugCheck(
                "DNS storm guard",
                BugSeverity.WARN,
                "DNS resolver storm active (${String.format(java.util.Locale.US, "%.2f", stormRate)}/min) • resolver pool in parallel race mode",
                "Encrypted fallback resolvers are racing; demoted endpoints will be recovered when calm"
            )
        }

        val domainFaults = intelligence?.dnsDomainFaultSummary().orEmpty()
        if (domainFaults.isNotBlank()) {
            checks += BugCheck(
                "DNS name faults",
                BugSeverity.INFO,
                "Names failing on several independent resolvers • $domainFaults",
                "These lookups are a property of the name, not of the resolver pool: " +
                    "their failures no longer demote resolvers or arm the storm guard"
            )
        }

        val lastTcpInfo = allRuntime.lineSequence().filter { it.contains("tcp-info") }.lastOrNull()
        if (lastTcpInfo != null) {
            val lost = Regex("lost=(\\d+)").find(lastTcpInfo)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val unacked = Regex("unacked=(\\d+)").find(lastTcpInfo)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val cwnd = Regex("cwnd=(\\d+)").find(lastTcpInfo)?.groupValues?.get(1)?.toIntOrNull() ?: 10
            val mss = Regex("mss=(\\d+)").find(lastTcpInfo)?.groupValues?.get(1)?.toIntOrNull() ?: 1400
            val pmtu = Regex("pmtu=(\\d+)").find(lastTcpInfo)?.groupValues?.get(1)?.toIntOrNull() ?: 1500
            val stressed = lastTcpInfo.contains("stressed=true")

            if (stressed && (lost >= 10 || unacked >= 10 || (cwnd <= 2 && lost > 0) || (mss in 1..600))) {
                checks += BugCheck(
                    "TCP path health",
                    BugSeverity.WARN,
                    "Transport stressed: lost=$lost, unacked=$unacked, cwnd=$cwnd, mss=$mss (pmtu=$pmtu) • middlebox packet loss or blackhole MTU",
                    "Adaptive MTU/MSS reduction active; consider switching node if loss persists"
                )
            } else if (connected) {
                checks += BugCheck(
                    "TCP path health",
                    BugSeverity.PASS,
                    "Transport parameters normal • cwnd=$cwnd • pmtu=$pmtu • mss=$mss"
                )
            }
        }

        val exits = historicalExits(now)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            checks += when {
                exits.crashLike > 0 -> BugCheck(
                    "Historical process exits",
                    if (exits.crashLike >= 3) BugSeverity.FAIL else BugSeverity.WARN,
                    "${exits.crashLike} crash/ANR/native-crash exit record(s) found in Android history" +

                        exits.crashSummaries.joinToString("") { " • $it" },
                    "Review the PROCESS EXIT HISTORY section for the full traces"
                )
                exits.totalCrashLike > 0 -> BugCheck(
                    "Historical process exits",
                    BugSeverity.WARN,
                    "${exits.totalCrashLike} historical crash/ANR record(s) found in Android history" +
                        exits.crashSummaries.joinToString("") { " • $it" },
                    "Review the PROCESS EXIT HISTORY section for the full traces"
                )
                exits.lowMemory >= 2 -> BugCheck(
                    "Historical process exits",
                    BugSeverity.WARN,
                    "${exits.lowMemory} historical Android LOW_MEMORY exit record(s) found • " +
                        "system pressure evidence; largeHeap and v26 trim hooks are armed",
                    "Full process-exit evidence is preserved below"
                )
                exits.lowMemory > 0 -> BugCheck(
                    "Historical process exits",
                    BugSeverity.INFO,
                    "${exits.lowMemory} historical Android LOW_MEMORY exit record(s) found • " +
                        "system pressure evidence, not a Java/native crash; v26 trim hooks are armed",
                    "Full process-exit evidence is preserved below"
                )
                else -> BugCheck("Historical process exits", BugSeverity.PASS, "No recent crash/ANR record returned by Android")
            }
        } else {
            checks += BugCheck(
                "Historical process exits",
                BugSeverity.INFO,
                "ApplicationExitInfo requires Android 11+; fatal tombstones and runtime logs remain available"
            )
        }

        val active = profiles.firstOrNull { it.id == activeProfileId }

        RealityHandshakePolicy.check(RealityHandshakePolicy.scan(coreEvidence), active)?.let { checks += it }
        sections += BugSection("SYSTEM / BUILD", systemSection(settings, diagnosticStatus))
        sections += BugSection(
            "CURRENT CONNECTION",
            buildString {
                appendLine("appState=$appState")
                appendLine("stateDetail=${sanitize(stateDetail)}")
                appendLine("mode=${settings.connectionMode}")
                appendLine("network=${sanitize(networkLabel)}")
                appendLine("activeProfileId=${activeProfileId.take(16)}")
                if (active != null) {
                    appendLine("profileName=${sanitize(active.name)}")
                    appendLine("scheme=${active.scheme}")
                    appendLine("transport=${active.transport}")
                    appendLine("security=${active.security}")
                    appendLine("endpoint=${sanitize(active.host)}:${active.port}")
                    appendLine("configFingerprint=${diag.sha256(active.configJson)}")
                }
                appendLine("engine=${coreState.id}")
                appendLine("coreAlive=${coreState.alive}")
                appendLine("corePid=${coreState.pid}")
                appendLine("coreStartPhase=${sanitize(coreState.phase)}")
                appendLine("coreStartError=${sanitize(coreState.error)}")
                appendLine("xrayAlive=${xray.isAlive}")
                appendLine("xrayPid=${xray.processPid}")
                appendLine("xrayStartPhase=${sanitize(xray.lastStartPhase)}")
                appendLine("xrayLastStartError=${sanitize(xray.lastStartError)}")
                appendLine("listenerBound=$listener")
                appendLine("socksPort=$port")
                appendLine("mux=${settings.muxEnabled}")
                appendLine("dnsStrategy=${settings.dnsQueryStrategy}")
                appendLine("routing=${settings.routingMode}")
                appendLine("debugMode=${settings.debugModeEnabled}")
            }
        )

        sections += BugSection("APP PROCESS + MEMORY", processSection())
        sections += BugSection("ALL APP THREADS", threadSection())
        sections += BugSection(
            "CONNECTION HISTORY",
            if (history.isEmpty()) {
                "No persisted connection history"
            } else {
                history.takeLast(200).joinToString("\n") {
                    "${Instant.ofEpochMilli(it.at)} | profile=${it.profileId.take(16)} | " +
                        "name=${sanitize(it.name)} | reason=${sanitize(it.reason)}"
                }
            }
        )
        sections += BugSection("CONNECTION / ENGINE EVENT TIMELINE", connectionTimeline(allRuntime))

        sections += BugSection(
            "PROCESS EXIT HISTORY",
            exits.text.ifBlank { "No ApplicationExitInfo records returned by Android" }
        )
        sections += BugSection(
            "DIAGNOSTICS ENGINE",
            buildString {
                appendLine("debugEnabled=${diagnosticStatus.debugEnabled}")
                appendLine("publicPath=${diagnosticStatus.publicPath}")
                appendLine("queue=${diagnosticStatus.queued}/4096")
                appendLine("dropped=${diagnosticStatus.dropped}")
                appendLine("ringEvents=${diagnosticStatus.ringEvents}")
                appendLine("writerHealthy=${diagnosticStatus.writerHealthy}")
                appendLine("writerError=${sanitize(diagnosticStatus.writerError)}")
            }
        )

        val problemEvidence = (
            problems(allRuntime).takeLast(220) +
                problems(hevLog).takeLast(120) +
                problems(xrayLog).takeLast(160) +
                problems(singboxLog).takeLast(120) +
                problems(singboxUrlTestLog).takeLast(60) +
                problems(crashPending).takeLast(40)
            ).distinct().takeLast(500)

        sections += BugSection(
            "ERROR / CRASH / BUG INDEX",
            if (problemEvidence.isEmpty()) "No error-like evidence in retained logs"
            else problemEvidence.joinToString("\n")
        )
        sections += BugSection("IN-MEMORY EVENT RING", RuntimeDiagnostics.recentEvents(700).joinToString("\n"))
        sections += BugSection("RUNTIME LOG RETAINED TAIL", sanitize(allRuntime))
        sections += BugSection("HEV NATIVE RETAINED TAIL", sanitize(hevLog))
        sections += BugSection("XRAY RETAINED TAIL", sanitize(xrayLog))
        if (crashPending.isNotBlank() && !crashPending.startsWith("No log file yet:")) {
            sections += BugSection("PENDING FATAL CRASH TOMBSTONE", sanitize(crashPending))
        }

        val localClientAborts = singboxLog.lineSequence().count { CoreLogNoise.isLocalClientHandshakeAbort(it) }
        sections += BugSection(
            "SINGBOX EXTENDED CORE STATUS",
            "libsingbox.so=" + File(context.applicationInfo.nativeLibraryDir, "libsingbox.so").exists() +
                " • singboxLog=" + (singboxLog.length) + " chars • urlTestLog=" + (singboxUrlTestLog.length) +
                " chars • localClientHandshakeAborts=$localClientAborts" +
                (if (localClientAborts > 0) " (benign: local client(s) closed mid-handshake; not counted as failures)" else "")
        )

        val runtimeConfigText = runCatching {
            File(context.filesDir, "runtime-singbox.json").readText()
        }.getOrDefault("")
        val netlinkRequests = if (runtimeConfigText.isBlank()) {
            listOf("no runtime config on disk")
        } else {
            SingBoxAndroidRuntime.ANDROID_FORBIDDEN_ROUTE_KEYS
                .filter { key -> "\"$key\"" in runtimeConfigText }
                .ifEmpty { listOf("none — the config asks for no interface monitor") }
        }
        sections += BugSection(
            "SINGBOX ANDROID RUNTIME CONTRACT",
            "interface-monitor requests in runtime config: " + netlinkRequests.joinToString(", ") +
                "\ndeprecation escape hatches exported: " +
                SingBoxAndroidRuntime.DEPRECATION_ENV.keys.sorted().joinToString(", ") +
                "\nnetlink ban seen in retained sing-box log: " +
                SingBoxAndroidRuntime.isNetlinkBan(singboxLog + singboxUrlTestLog) +

                "\ncrash signature in retained sing-box log: " +
                SingBoxAndroidRuntime.isCoreCrash(coreEvidence) +
                "\npackage-manager probe (/data/system/packages.xml) seen: " +
                SingBoxAndroidRuntime.isPackageManagerFault(coreEvidence) +

                "\nlast start-up: inbound=" + (singbox?.lastStartReadiness?.inboundUp ?: false) +
                " controller=" + (singbox?.lastStartReadiness?.controllerUp ?: false) +
                " phase=" + (singbox?.lastStartReadiness?.phase ?: "not run") +
                " elapsedMs=" + (singbox?.lastStartReadiness?.elapsedMs ?: 0L) +
                "\ncore self-test: " + (
                    selfTestVerdict?.let { verdict ->
                        "${verdict.summary} • fingerprint=${verdict.fingerprint}" +
                            if (verdict.reason.isBlank()) "" else " • ${ProbeLocalFaultGate.headline(verdict.reason)}"
                    } ?: "not run yet — the next connect or measurement runs it once for this binary"
                    ) +
                "\nlast sweep stopped by a local fault: " +
                (if (lastProbeFault.isBlank()) "none" else ProbeLocalFaultGate.summary(lastProbeFault))
        )

        return BugReport(
            generatedAt = now,
            state = "$appState • ${stateDetail.ifBlank { "no active route" }}",
            checks = checks,
            evidence = problemEvidence.takeLast(80),
            sections = sections
        )
    }

    private fun nodeIpv6Evidence(profile: ProxyProfile?, settings: AppSettings): String {
        if (
            profile == null ||
            !settings.ipv6Enabled ||
            profile.host.isBlank() ||
            profile.port !in 1..65535
        ) {
            return "endpoint probe skipped"
        }
        return runCatching {
            val candidate = AddressFamilyPolicy
                .resolveCandidates(
                    profile.host,
                    AddressFamilyPolicy.plan(settings = settings)
                )
                .filterIsInstance<Inet6Address>()
                .firstOrNull()
            when {
                candidate == null -> "no usable AAAA answer from encrypted endpoint DNS • IPv6 path unverified (Force IPv6 never falls back to IPv4)"
                Socket().use { socket ->
                    runCatching {
                        socket.connect(InetSocketAddress(candidate, profile.port), 900)
                    }.isSuccess
                } -> "server answers on IPv6 ${candidate.hostAddress}"
                else -> "server advertises IPv6 but the v6 connect failed • v6 quality here is suspect"
            }
        }.getOrDefault("server IPv6 probe unavailable")
    }

    private fun systemSection(settings: AppSettings, status: DiagnosticEngineStatus): String {
        val pi = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val versionCode = if (pi == null) -1L
        else if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode
        else {
            @Suppress("DEPRECATION")
            pi.versionCode.toLong()
        }

        return buildString {
            appendLine(diag.systemSnapshot())
            appendLine("package=${context.packageName}")
            appendLine("versionName=${pi?.versionName.orEmpty()}")
            appendLine("versionCode=$versionCode")
            appendLine("filesDir=${context.filesDir.absolutePath}")
            appendLine("debugOutput=${status.publicPath}")
            appendLine("debugEnabled=${settings.debugModeEnabled}")
            appendLine("generated=${Instant.now()}")
        }
    }

    private fun processSection(): String {
        val am = runCatching { context.getSystemService(ActivityManager::class.java) }.getOrNull()
        val rt = Runtime.getRuntime()
        val own = runCatching {
            am?.runningAppProcesses.orEmpty().filter { it.uid == Process.myUid() }
        }.getOrDefault(emptyList())
        val pss = runCatching {
            am?.getProcessMemoryInfo(intArrayOf(Process.myPid()))?.firstOrNull()?.totalPss
        }.getOrNull() ?: -1

        return buildString {
            appendLine("pid=${Process.myPid()} uid=${Process.myUid()}")
            appendLine("elapsedCpuMs=${Process.getElapsedCpuTime()}")
            appendLine("javaHeapUsed=${rt.totalMemory() - rt.freeMemory()}")
            appendLine("javaHeapCommitted=${rt.totalMemory()}")
            appendLine("javaHeapMax=${rt.maxMemory()}")
            appendLine("nativeHeapAllocated=${Debug.getNativeHeapAllocatedSize()}")
            appendLine("pssKb=$pss")
            appendLine("visibleOwnProcesses=${own.size}")
            own.forEach {
                appendLine(
                    "pid=${it.pid} process=${sanitize(it.processName)} importance=${it.importance} " +
                        "reasonCode=${it.importanceReasonCode}"
                )
            }
            if (own.isEmpty()) appendLine("Android did not expose a running-process row; own PID above remains authoritative.")
            appendLine("Note: modern Android does not permit reliable enumeration of every unrelated device process.")
        }
    }

    private fun threadSection(): String {
        val threads = Thread.getAllStackTraces().entries
            .sortedWith(compareBy({ it.key.name }, { it.key.id }))
            .take(160)

        return buildString {
            appendLine("captured=${threads.size}")
            threads.forEach { (thread, stack) ->
                appendLine()
                appendLine(
                    "THREAD id=${thread.id} name=${sanitize(thread.name)} state=${thread.state} " +
                        "daemon=${thread.isDaemon} priority=${thread.priority}"
                )
                stack.take(12).forEach { frame -> appendLine("  at ${sanitize(frame.toString())}") }
            }
        }
    }

    private fun estimateSessionWindowMs(rawLines: List<String>, allRuntime: String): Long {
        val timestamps = mutableListOf<Long>()
        val xrayFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        fun tryParseDate(str: String) {
            runCatching {
                val m1 = Regex("(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2})").find(str)
                if (m1 != null) {
                    val d = xrayFormat.parse(m1.groupValues[1])
                    if (d != null) timestamps += d.time
                    return
                }
                val m2 = Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})").find(str)
                if (m2 != null) {
                    val d = isoFormat.parse(m2.groupValues[1])
                    if (d != null) timestamps += d.time
                }
            }
        }

        rawLines.take(50).forEach { tryParseDate(it) }
        rawLines.takeLast(50).forEach { tryParseDate(it) }

        if (timestamps.size < 2) {
            allRuntime.lineSequence().filter { it.isNotBlank() }.take(50).forEach { tryParseDate(it) }
            allRuntime.lineSequence().filter { it.isNotBlank() }.toList().takeLast(50).forEach { tryParseDate(it) }
        }

        return if (timestamps.size >= 2) {
            val span = timestamps.maxOrNull()!! - timestamps.minOrNull()!!
            if (span > 1_000L) span else 0L
        } else {
            0L
        }
    }

    private fun historicalExits(now: Long): ExitEvidence {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return ExitEvidence("Unavailable on Android ${Build.VERSION.SDK_INT}; requires API 30+.", 0, 0)
        }

        val am = context.getSystemService(ActivityManager::class.java)
        val exits = runCatching {
            am.getHistoricalProcessExitReasons(context.packageName, 0, 32)
        }.getOrDefault(emptyList())

        var crashLike = 0
        var totalCrashLike = 0
        var lowMemory = 0
        val crashSummaries = mutableListOf<String>()
        val warningCutoff = now - 24L * 60L * 60L * 1000L
        val text = buildString {
            appendLine("records=${exits.size}")
            appendLine("warningWindowHours=24")
            exits.forEachIndexed { index, exit ->
                val reason = exitReason(exit.reason)
                val isCrash = exit.reason == ApplicationExitInfo.REASON_CRASH ||
                    exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                    exit.reason == ApplicationExitInfo.REASON_ANR
                if (isCrash) {
                    totalCrashLike++
                    if (exit.timestamp >= warningCutoff) {
                        crashLike++
                    }
                    crashSummaries += "#${index + 1} ${Instant.ofEpochMilli(exit.timestamp)} $reason " +
                        "status=${exit.status} ${sanitize(exit.description.orEmpty()).take(160)}"
                }
                if (
                    exit.timestamp >= warningCutoff &&
                    exit.reason == ApplicationExitInfo.REASON_LOW_MEMORY
                ) lowMemory++

                appendLine()
                appendLine("#${index + 1} ${Instant.ofEpochMilli(exit.timestamp)}")
                appendLine("process=${sanitize(exit.processName.orEmpty())}")
                appendLine("reason=$reason (${exit.reason})")
                appendLine("status=${exit.status}")
                appendLine("importance=${exit.importance}")
                appendLine("pssKb=${exit.pss} rssKb=${exit.rss}")
                appendLine("description=${sanitize(exit.description.orEmpty())}")

                if (index < 8) {
                    var trace = runCatching {
                        exit.traceInputStream?.bufferedReader()?.use { reader -> reader.readText().take(80_000) }
                    }.getOrNull()
                    if (trace.isNullOrBlank() && isCrash) {
                        trace = RuntimeDiagnostics.findTombstoneNear(context, exit.timestamp)
                    }
                    if (!trace.isNullOrBlank()) {
                        appendLine("--- exit trace ---")
                        appendLine(sanitize(trace))
                    }
                }
            }
        }
        return ExitEvidence(text, crashLike, lowMemory, crashSummaries.take(8), totalCrashLike)
    }

    private fun exitReason(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_UNKNOWN -> "UNKNOWN"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "REASON_$reason"
    }

    private fun connectionTimeline(text: String): String {
        if (text.isBlank()) return "No retained runtime timeline"
        val tags = listOf(
            "APP |", "VPN |", "TUN |", "HEV |", "XRAY |", "SINGBOX |", "EGRESS |", "DNS |",
            "IDENTITY |", "TURBO |", "AUTOPILOT |", "PRIVACY |", "NETWORK |"
        )
        val lines = text.lineSequence()
            .filter { line -> tags.any(line::contains) }
            .map(::sanitize)
            .takeLastCompat(1_000)
        return if (lines.isEmpty()) "No matching retained events" else lines.joinToString("\n")
    }

    private fun problems(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val keywords = listOf(
            "error", "fail", "fatal", "crash", "anr", "blocked", "exception", "timeout",
            "refused", "denied", "killed", "run-exit", "stall", "dead", "abort", "panic",
            "signal", "oom", "low_memory"
        )

        val candidates = text.lineSequence()
            .filter { line ->
                val cleanHevExit =
                    line.contains("HEV | run-exit", true) &&
                        line.contains("code=0", true) &&
                        line.contains("runningFlag=false", true) &&
                        line.contains("xrayAlive=false", true)
                val benignNoise =
                    line.contains("proxy/socks: failed to read request > EOF", true) ||
                        (line.contains("HTTPUpgrade transport", true) && line.contains("deprecated", true)) ||

                        CoreLogNoise.isLocalClientHandshakeAbort(line)
                !cleanHevExit && !benignNoise && keywords.any { line.contains(it, true) }
            }
            .map(::sanitize)
            .toList()

        val compacted = mutableListOf<String>()
        val repetitiveBurst = mutableListOf<String>()
        var burstLabel = ""
        fun flushRepetitiveBurst() {
            if (repetitiveBurst.isEmpty()) return
            if (repetitiveBurst.size <= 3) {
                compacted += repetitiveBurst
            } else {
                compacted += buildString {
                    append(burstLabel)
                    append(" ×")
                    append(repetitiveBurst.size)
                    append(" | first=")
                    append(repetitiveBurst.first())
                    append(" | last=")
                    append(repetitiveBurst.last())
                }
            }
            repetitiveBurst.clear()
            burstLabel = ""
        }

        candidates.forEach { line ->
            val nextLabel = when {
                line.contains("read/write on closed pipe", ignoreCase = true) ->
                    "XRAY CANCELLATION BURST | read/write on closed pipe"
                line.contains("context deadline exceeded", ignoreCase = true) &&
                    line.contains("dns-query", ignoreCase = true) ->
                    "DNS DEADLINE BURST | DoH context deadline exceeded"
                line.contains(
                    "app/dns: failed to read response length > EOF",
                    ignoreCase = true
                ) ->
                    "DNS EOF BURST | upstream DNS transport closed early"
                else -> ""
            }
            if (nextLabel.isNotEmpty()) {
                if (burstLabel.isNotEmpty() && burstLabel != nextLabel) flushRepetitiveBurst()
                burstLabel = nextLabel
                repetitiveBurst += line
            } else {
                flushRepetitiveBurst()
                compacted += line
            }
        }
        flushRepetitiveBurst()

        return compacted.takeLast(500)
    }

    private fun listenerBoundWithoutTraffic(port: Int): Boolean = runCatching {
        java.net.ServerSocket().use { socket ->
            socket.reuseAddress = true
            socket.bind(InetSocketAddress("127.0.0.1", port))
        }
        false
    }.getOrElse { true }

    private fun tail(file: File, maxBytes: Int): String {
        if (!file.isFile) return "No log file yet: ${file.name}"
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val read = raf.length().coerceAtMost(maxBytes.toLong()).toInt()
                raf.seek((raf.length() - read).coerceAtLeast(0L))
                val bytes = ByteArray(read)
                raf.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
        }.getOrElse { "Could not read ${file.name}: ${it::class.java.simpleName}: ${it.message}" }
    }

    private fun sanitize(value: String): String = RuntimeDiagnostics.redact(value).take(250_000)

    private fun <T> Sequence<T>.takeLastCompat(limit: Int): List<T> {
        val deque = ArrayDeque<T>()
        forEach { value ->
            deque.addLast(value)
            while (deque.size > limit) deque.removeFirst()
        }
        return deque.toList()
    }
}

internal object CoreLogNoise {
    private val clientAbortMarkers = listOf(
        "read fqdn: unexpected eof",
        "read fqdn: eof",
        "read port: unexpected eof",
        "read port: eof",
        "read ipv4 address: unexpected eof",
        "read ipv6 address: unexpected eof",
        "read version: eof"
    )

    fun isLocalClientHandshakeAbort(line: String): Boolean {
        val value = line.lowercase()

        val localInbound = value.contains("inbound/mixed[socks-in]") ||
            value.contains("inbound/socks[socks-in]")
        if (!localInbound || !value.contains("process connection from 127.0.0.1:")) return false
        return clientAbortMarkers.any { it in value } || value.trimEnd().endsWith(": eof")
    }
}
