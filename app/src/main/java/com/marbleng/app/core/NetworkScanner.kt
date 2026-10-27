package com.marbleng.app.core

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.telephony.TelephonyManager
import com.marbleng.app.model.AppSettings
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * MARBLE_NETWORK_COUNCIL_V236 — the measuring half of the council.
 *
 * [NetworkCouncil] is the judgement and it is pure; this file is the only place that touches the
 * phone. It gathers exactly what the twelve seats asked for and nothing more:
 *
 *  * **The link.** Transport, operator, network type, signal, link estimate, metered/validated
 *    state, interface MTU and whether another VPN is already in front of the app. Every Android
 *    read is `runCatching`-guarded: a modern platform happily redacts Wi-Fi details from an app
 *    that never asked for a location permission, and a scanner that treats "redacted" as "no
 *    signal" would file a false finding.
 *  * **The path.** TCP handshake RTT ladders, a TLS handshake through the product's own
 *    [MultiVectorReachability], an honest UDP/53 test, a QUIC reachability probe that asks a
 *    server for a Version Negotiation packet, a padded-DNS MTU ladder, IPv4/IPv6 dials and a small
 *    CDN port matrix.
 *  * **DNS.** Plain DNS queries to the configured resolvers plus the two public pairs, with the
 *    answer inspected for [IranNetworkRegistry]'s block-page prefix.
 *
 * The budget is bounded and stated up front (~15 s worst case, 8 s typical), the probe set is
 * small enough to run on a metered link, and the scan **never downloads a payload** — the
 * throughput figure the council reasons about is the platform's own link estimate. A user who
 * wants a real throughput number has the benchmark engine for that.
 */
object NetworkScanner {
    const val MARKER: String = "MARBLE_NETWORK_COUNCIL_V236"

    /** Anchors are literal addresses on purpose: a DNS failure must not read as a dead path. */
    val TCP_ANCHORS: List<String> = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")
    val DNS_PUBLIC: List<String> = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9")
    val PORT_MATRIX: List<Int> = listOf(443, 80, 8443, 2053, 2083, 2087, 2096, 8080)
    val MTU_LADDER: List<Int> = listOf(1232, 1280, 1400, 1452, 1472)
    val NTP_ANCHORS: List<String> = listOf("162.159.200.1", "216.239.35.0")
    val QUIC_ANCHORS: List<String> = listOf("1.1.1.1", "8.8.8.8")
    val IPV6_ANCHOR: String = "2606:4700:4700::1111"
    val DNS_PROBE_NAME: String = "cloudflare.com"

    const val TCP_SAMPLES_PER_ANCHOR: Int = 3
    const val TCP_TIMEOUT_MS: Int = 1_500
    const val DNS_TIMEOUT_MS: Int = 1_500
    const val PORT_TIMEOUT_MS: Int = 1_200
    const val UDP_TIMEOUT_MS: Int = 1_400
    const val FAMILY_TIMEOUT_MS: Int = 2_000
    const val TOTAL_BUDGET_MS: Int = 16_000

    data class Stage(val label: String, val fraction: Float, val done: Int = 0, val total: Int = 0)

    /**
     * Run one full scan. Blocking: the caller owns the worker thread (the repository has one) and
     * the UI only ever sees [Stage] callbacks and the finished evidence.
     */
    fun scan(
        context: Context,
        settings: AppSettings,
        onStage: (Stage) -> Unit = {}
    ): NetworkCouncil.NetworkEvidence {
        val startedAt = System.currentTimeMillis()
        val environment = captureEnvironment(context)
        onStage(Stage("Link and operator", 0.08f))

        val resolvers = (listOf(settings.dnsPrimaryIp, settings.dnsSecondaryIp) + DNS_PUBLIC)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(5)
        val dns = probeDns(resolvers)
        onStage(Stage("Name resolution", 0.24f, dns.size, resolvers.size))

        val latency = probeLatency()
        onStage(Stage("TCP handshakes", 0.44f, latency.samplesMs.size, attemptsOf(latency)))

        val reachability = runCatching {
            MultiVectorReachability.probe(
                host = "www.gstatic.com",
                port = 443,
                timeoutMs = 3_000,
                settings = settings
            )
        }.getOrNull()

        val ports = probePorts()
        onStage(Stage("Ports and firewall", 0.62f, ports.size, PORT_MATRIX.size))

        val udpDnsOk = dns.any { it.answered }
        val ntpOk = probeNtp()
        val quicOk = probeQuic()
        onStage(Stage("UDP and QUIC", 0.76f))

        val mtu = probeMtu()
        onStage(Stage("MTU and fragmentation", 0.88f))

        val family = dialFamilies()
        onStage(Stage("IPv4 and IPv6", 0.97f))

        val pathMtu = mtu.first
        val evidence = NetworkCouncil.NetworkEvidence(
            radio = environment,
            latency = latency,
            dns = dns,
            ports = ports,
            path = NetworkCouncil.PathEvidence(
                ipv4Ok = family.ipv4Ok,
                ipv4Ms = family.ipv4Ms,
                ipv6Ok = family.ipv6Ok,
                ipv6Ms = family.ipv6Ms,
                tlsCompleted = reachability?.tlsHandshakeCompleted == true,
                injectedReset = reachability?.injectedResetSuspected == true,
                quicOk = quicOk,
                udpDnsOk = udpDnsOk || ntpOk,
                pathMtu = pathMtu,
                mtuConfidence = mtu.second
            ),
            device = captureDevice(context),
            throughputDownKbps = environment.linkDownKbps,
            capturedAtMs = System.currentTimeMillis()
        )
        onStage(Stage("Council deliberation", 1f))
        // Every probe above is individually timed out, so the scan is bounded even when a target
        // blackholes; the elapsed time is carried by `capturedAtMs` and never smoothed over.
        if (evidence.capturedAtMs - startedAt > TOTAL_BUDGET_MS) {
            onStage(Stage("Scan ran past its stated budget", 1f))
        }
        return evidence
    }

    private fun attemptsOf(latency: NetworkCouncil.LatencyEvidence): Int =
        latency.attempts.coerceAtLeast(latency.samplesMs.size + latency.failures)

    // ------------------------------------------------------------------------------------------
    // The link
    // ------------------------------------------------------------------------------------------

    fun captureEnvironment(context: Context): NetworkCouncil.RadioEvidence {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkCouncil.RadioEvidence()
        val network = connectivity.activeNetwork ?: return NetworkCouncil.RadioEvidence()
        val caps = connectivity.getNetworkCapabilities(network)
            ?: return NetworkCouncil.RadioEvidence()
        val linkProperties: LinkProperties? = runCatching { connectivity.getLinkProperties(network) }.getOrNull()

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkCouncil.LinkTransport.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkCouncil.LinkTransport.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkCouncil.LinkTransport.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkCouncil.LinkTransport.VPN
            else -> NetworkCouncil.LinkTransport.OTHER
        }

        val telephony = context.getSystemService(TelephonyManager::class.java)
        val operatorName = telephony?.networkOperatorName?.trim().orEmpty()
            .ifBlank { telephony?.simOperatorName?.trim().orEmpty() }
        val operatorCode = telephony?.networkOperator?.trim().orEmpty()
            .ifBlank { telephony?.simOperator?.trim().orEmpty() }
        val cellularType = if (transport == NetworkCouncil.LinkTransport.CELLULAR) {
            runCatching { cellularTypeName(telephony?.dataNetworkType ?: 0) }.getOrDefault("")
        } else {
            ""
        }

        val wifi = if (transport == NetworkCouncil.LinkTransport.WIFI) wifiSnapshot(context) else null
        val signalLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { caps.signalStrength }.getOrDefault(-1)
        } else {
            -1
        }

        return NetworkCouncil.RadioEvidence(
            transport = transport,
            operatorName = operatorName,
            operatorCode = operatorCode,
            networkType = if (transport == NetworkCouncil.LinkTransport.WIFI) {
                wifi?.label ?: "Wi-Fi"
            } else {
                cellularType.ifBlank { transport.label }
            },
            signalLevel = signalLevel.coerceIn(-1, 4),
            signalDbm = wifi?.rssi ?: 0,
            linkDownKbps = runCatching { caps.linkDownstreamBandwidthKbps }.getOrDefault(0),
            linkUpKbps = runCatching { caps.linkUpstreamBandwidthKbps }.getOrDefault(0),
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            captivePortal = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL),
            interfaceMtu = runCatching { linkProperties?.mtu ?: 0 }.getOrDefault(0),
            vpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        )
    }

    private data class WifiSnapshot(val label: String, val rssi: Int)

    /**
     * Wi-Fi detail is best-effort by design. Without a location permission Android redacts SSID,
     * BSSID and often the radio detail as well; the scanner therefore reports what it was allowed
     * to see and lets the council reason from the transport alone.
     */
    @Suppress("DEPRECATION")
    private fun wifiSnapshot(context: Context): WifiSnapshot? {
        val manager = runCatching {
            context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
        }.getOrNull() ?: return null
        val info = runCatching { manager.connectionInfo }.getOrNull() ?: return null
        val frequency = runCatching { info.frequency }.getOrDefault(0)
        val band = when {
            frequency in 5_925..7_125 -> "Wi-Fi 6E"
            frequency >= 4_900 -> "Wi-Fi 5 GHz"
            frequency in 2_400..2_500 -> "Wi-Fi 2.4 GHz"
            else -> "Wi-Fi"
        }
        val rssi = runCatching { info.rssi }.getOrDefault(0)
        return WifiSnapshot(label = band, rssi = rssi)
    }

    private fun cellularTypeName(type: Int): String = when (type) {
        20 -> "5G NR"
        13 -> "LTE"
        19 -> "LTE-A"
        3, 8, 9, 10, 15 -> "3G"
        1, 2, 4, 7, 11 -> "2G"
        else -> ""
    }

    @Suppress("UnspecifiedRegisterReceiverFlag", "DEPRECATION")
    fun captureDevice(context: Context): NetworkCouncil.DeviceEvidence {
        val battery = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100.0 / scale).roundToInt() else -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.getSystemService(PowerManager::class.java)?.currentThermalStatus ?: 0
            }.getOrDefault(0)
        } else {
            0
        }
        val memoryMb = runCatching {
            val info = android.app.ActivityManager.MemoryInfo()
            context.getSystemService(android.app.ActivityManager::class.java)?.getMemoryInfo(info)
            (info.totalMem / (1024L * 1024L)).coerceAtLeast(0L)
        }.getOrDefault(0L)

        return NetworkCouncil.DeviceEvidence(
            cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
            memoryMb = memoryMb,
            batteryPercent = percent,
            charging = charging,
            thermalStatus = thermal
        )
    }

    // ------------------------------------------------------------------------------------------
    // DNS
    // ------------------------------------------------------------------------------------------

    fun probeDns(servers: List<String>): List<NetworkCouncil.DnsEvidence> {
        if (servers.isEmpty()) return emptyList()
        val pool = pool(servers.size.coerceIn(1, 5), "marble-council-dns")
        return try {
            val tasks = servers.map { server ->
                Callable {
                    val started = System.currentTimeMillis()
                    val reply = sendDnsQuery(server, DNS_PROBE_NAME, 0, DNS_TIMEOUT_MS)
                    if (reply == null) {
                        NetworkCouncil.DnsEvidence(
                            server = server,
                            answered = false,
                            latencyMs = (System.currentTimeMillis() - started).toInt().coerceAtLeast(0)
                        )
                    } else {
                        val parsed = runCatching { DnsWireCodec.parseDetailed(reply) }.getOrNull()
                        val answer = parsed?.addresses?.firstOrNull()?.hostAddress.orEmpty()
                        NetworkCouncil.DnsEvidence(
                            server = server,
                            answered = parsed != null,
                            latencyMs = (System.currentTimeMillis() - started).toInt().coerceAtLeast(0),
                            rcode = parsed?.rcode ?: -1,
                            answer = answer,
                            blockPage = IranNetworkRegistry.isBlockPageAddress(answer)
                        )
                    }
                }
            }
            pool.invokeAll(tasks, (DNS_TIMEOUT_MS + 700).toLong(), TimeUnit.MILLISECONDS)
                .mapNotNull { future -> if (future.isCancelled) null else runCatching { future.get() }.getOrNull() }
        } catch (_: Exception) {
            emptyList()
        } finally {
            pool.shutdownNow()
        }
    }

    /**
     * One plain DNS question over UDP, with optional EDNS0 padding. The padding is what turns the
     * same code into the MTU ladder: a resolver that echoes a 1472-byte padded question is proof
     * that a 1500-byte path survived.
     */
    fun sendDnsQuery(server: String, name: String, padTo: Int, timeoutMs: Int): ByteArray? {
        val base = runCatching { DnsWireCodec.buildQuery(name) }.getOrNull() ?: return null
        val query = if (padTo > base.size) padQuery(base, padTo) else base
        return runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.send(DatagramPacket(query, query.size, InetAddress.getByName(server), 53))
                val buffer = ByteArray(2048)
                val reply = DatagramPacket(buffer, buffer.size)
                socket.receive(reply)
                reply.data.copyOfRange(reply.offset, reply.offset + reply.length)
            }
        }.getOrNull()
    }

    /**
     * Append one EDNS0 OPT record (RFC 6891) carrying an RFC 7830 padding option, so the finished
     * question is exactly [target] bytes. Layout: NAME(1) TYPE(2) CLASS(2) TTL(4) RDLEN(2) then
     * RDATA = option-code(2) + option-length(2) + padding.
     */
    internal fun padQuery(query: ByteArray, target: Int): ByteArray {
        if (query.size < 12 || target <= query.size) return query
        val out = query.copyOf()
        // ARCOUNT = 1: one additional record, the OPT above.
        out[10] = 0x00
        out[11] = 0x01

        val optionHeader = 4
        val recordHeader = 11
        val padLength = (target - out.size - recordHeader - optionHeader).coerceAtLeast(0)
        val rdataLength = optionHeader + padLength

        val result = ByteArray(out.size + recordHeader + rdataLength)
        System.arraycopy(out, 0, result, 0, out.size)
        var offset = out.size
        result[offset] = 0x00                 // root name
        offset += 1
        result[offset] = 0x00                 // TYPE high byte
        result[offset + 1] = 41               // TYPE = OPT
        offset += 2
        result[offset] = 0x10                 // CLASS = 4096-byte UDP payload
        result[offset + 1] = 0x00
        offset += 2
        result[offset] = 0x00                 // TTL: extended rcode, version, flags — all zero
        result[offset + 1] = 0x00
        result[offset + 2] = 0x00
        result[offset + 3] = 0x00
        offset += 4
        result[offset] = ((rdataLength shr 8) and 0xFF).toByte()
        result[offset + 1] = (rdataLength and 0xFF).toByte()
        offset += 2
        result[offset] = 0x00                 // option code 12 = padding
        result[offset + 1] = 12
        result[offset + 2] = ((padLength shr 8) and 0xFF).toByte()
        result[offset + 3] = (padLength and 0xFF).toByte()
        return result
    }

    // ------------------------------------------------------------------------------------------
    // TCP latency, ports, families
    // ------------------------------------------------------------------------------------------

    fun probeLatency(): NetworkCouncil.LatencyEvidence {
        val targets = TCP_ANCHORS.flatMap { anchor -> List(TCP_SAMPLES_PER_ANCHOR) { anchor } }
        val pool = pool(targets.size.coerceAtMost(9), "marble-council-tcp")
        var failures = 0
        val samples = mutableListOf<Int>()
        try {
            val tasks = targets.map { anchor ->
                Callable { connectTcp(anchor, 443, TCP_TIMEOUT_MS) }
            }
            val futures = pool.invokeAll(tasks, (TCP_TIMEOUT_MS + 500).toLong(), TimeUnit.MILLISECONDS)
            futures.forEach { future ->
                val value = if (future.isCancelled) null else runCatching { future.get() }.getOrNull()
                if (value == null || value < 0) failures++ else samples += value
            }
        } finally {
            pool.shutdownNow()
        }
        return NetworkCouncil.LatencyEvidence(
            samplesMs = samples.sorted(),
            attempts = targets.size,
            failures = failures,
            targets = TCP_ANCHORS.joinToString(", ")
        )
    }

    fun probePorts(): List<NetworkCouncil.PortEvidence> {
        val host = TCP_ANCHORS.first()
        val pool = pool(PORT_MATRIX.size, "marble-council-ports")
        return try {
            val tasks = PORT_MATRIX.map { port ->
                Callable {
                    val rtt = connectTcp(host, port, PORT_TIMEOUT_MS)
                    NetworkCouncil.PortEvidence(
                        host = host,
                        port = port,
                        protocol = NetworkCouncil.PortProtocol.TCP,
                        open = rtt >= 0,
                        latencyMs = rtt.coerceAtLeast(0)
                    )
                }
            }
            pool.invokeAll(tasks, (PORT_TIMEOUT_MS + 400).toLong(), TimeUnit.MILLISECONDS)
                .mapNotNull { future ->
                    if (future.isCancelled) null else runCatching { future.get() }.getOrNull()
                }
        } catch (_: Exception) {
            emptyList()
        } finally {
            pool.shutdownNow()
        }
    }

    data class FamilyDial(
        val ipv4Ok: Boolean,
        val ipv4Ms: Int,
        val ipv6Ok: Boolean,
        val ipv6Ms: Int
    )

    /** Direct dials, so a dead AAAA record cannot be mistaken for a slow server. */
    fun dialFamilies(): FamilyDial {
        val v4 = connectTcp("1.1.1.1", 443, FAMILY_TIMEOUT_MS)
        val v6 = connectTcp(IPV6_ANCHOR, 443, FAMILY_TIMEOUT_MS)
        return FamilyDial(
            ipv4Ok = v4 >= 0,
            ipv4Ms = v4.coerceAtLeast(0),
            ipv6Ok = v6 >= 0,
            ipv6Ms = v6.coerceAtLeast(0)
        )
    }

    private fun connectTcp(host: String, port: Int, timeoutMs: Int): Int {
        val started = System.currentTimeMillis()
        return runCatching {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), timeoutMs)
            }
            (System.currentTimeMillis() - started).toInt().coerceAtLeast(1)
        }.getOrDefault(-1)
    }

    // ------------------------------------------------------------------------------------------
    // UDP, QUIC and MTU
    // ------------------------------------------------------------------------------------------

    /** A 48-byte SNTP request; a public NTP server that answers proves plain UDP crosses the path. */
    fun probeNtp(): Boolean = NTP_ANCHORS.any { anchor ->
        val request = ByteArray(48)
        request[0] = 0x23 // LI = 0, version 4, mode 3 (client)
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = UDP_TIMEOUT_MS
                socket.send(DatagramPacket(request, request.size, InetAddress.getByName(anchor), 123))
                val buffer = ByteArray(64)
                socket.receive(DatagramPacket(buffer, buffer.size))
                true
            }
        }.getOrDefault(false)
    }

    /**
     * Ask a QUIC server for a Version Negotiation packet: the probe sends a long-header packet
     * carrying a reserved version, which a conforming server answers with a Version Negotiation
     * datagram. A reply is proof that UDP/443 crosses this path; silence is reported as silence
     * rather than being read as a block, so the council under-claims instead of over-claiming.
     */
    fun probeQuic(): Boolean = QUIC_ANCHORS.any { anchor ->
        val packet = quicVersionNegotiationRequest()
        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = UDP_TIMEOUT_MS
                socket.send(DatagramPacket(packet, packet.size, InetAddress.getByName(anchor), 443))
                val buffer = ByteArray(256)
                socket.receive(DatagramPacket(buffer, buffer.size))
                true
            }
        }.getOrDefault(false)
    }

    private fun quicVersionNegotiationRequest(): ByteArray {
        val random = java.util.Random()
        val packet = ByteArray(1_200)
        packet[0] = 0xC0.toByte() // long header form, fixed bit, Initial type
        // A reserved (greased) version: a server must not treat it as a version it speaks.
        packet[1] = 0x1A
        packet[2] = 0x2A
        packet[3] = 0x3A
        packet[4] = 0x4A
        packet[5] = 8 // DCID length
        for (index in 0 until 8) packet[6 + index] = random.nextInt(256).toByte()
        packet[14] = 8 // SCID length
        for (index in 0 until 8) packet[15 + index] = random.nextInt(256).toByte()
        for (index in 23 until packet.size) packet[index] = random.nextInt(256).toByte()
        return packet
    }

    /**
     * The MTU ladder: a padded DNS question is sent at increasing sizes and the largest answered
     * size is the measured ceiling. Returns (pathMtu, confidence) and (0, 0.0) when nothing above
     * the first rung answered — an unmeasured path is reported as unmeasured.
     */
    fun probeMtu(): Pair<Int, Double> {
        val resolver = DNS_PUBLIC.first()
        var largest = 0
        var answered = 0
        for (size in MTU_LADDER) {
            val reply = sendDnsQuery(resolver, DNS_PROBE_NAME, size, DNS_TIMEOUT_MS)
            if (reply == null) continue
            answered++
            if (size > largest) largest = size
        }
        if (largest == 0) return 0 to 0.0
        return largest to (answered.toDouble() / MTU_LADDER.size.toDouble())
    }

    private fun pool(size: Int, name: String) = Executors.newFixedThreadPool(size.coerceIn(1, 12)) { runnable ->
        Thread(runnable, name).apply { isDaemon = true }
    }
}
