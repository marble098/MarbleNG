package com.marbleng.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import com.marbleng.app.AppRepository
import com.marbleng.app.BuildConfig
import com.marbleng.app.core.CoreEngine
import com.marbleng.app.core.CoreEngineInfo
import com.marbleng.app.core.Tun2SocksPolicy
import com.marbleng.app.core.TunnelCore
import com.marbleng.app.core.TunnelCoreInfo
import com.marbleng.app.core.coreEngine
import com.marbleng.app.core.tunnelCore
import com.marbleng.app.model.ConnectionMode
import com.marbleng.app.model.PowerProfile
import com.marbleng.app.model.SingBoxMuxProtocols
import com.marbleng.app.model.XrayDomainStrategies
import com.marbleng.app.model.XrayMuxUdp443Modes
import com.marbleng.app.model.parsePowerProfile

@Composable
fun SettingsConnectionModePage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
    val s = repo.settings

    IosSubPageScaffold(
        title = trx("Connection Mode & Ports"),
        onBack = onBack
    ) {
        item(key = "conn-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Connection Mode"))
                IosCard {
                    IosChoiceRow(
                        title = trx("Full TUN (Device VPN)"),
                        subtitle = trx("Routes all device apps through the encrypted VPN interface"),
                        selected = s.connectionMode == ConnectionMode.FULL_TUN,
                        glyph = MarbleGlyph.SHIELD,
                        squircleColor = colors.blue,
                        onClick = { repo.setConnectionMode(ConnectionMode.FULL_TUN) }
                    )
                    IosDivider()
                    IosChoiceRow(
                        title = trx("Local Proxy Only"),
                        subtitle = "${trx("Exposes a local SOCKS5 listener on")} 127.0.0.1:${s.localProxyPort}",
                        selected = s.connectionMode == ConnectionMode.LOCAL_PROXY,
                        glyph = MarbleGlyph.LINK,
                        squircleColor = colors.indigo,
                        onClick = { repo.setConnectionMode(ConnectionMode.LOCAL_PROXY) }
                    )
                }
            }
        }

        item(key = "conn-ports") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Local Ports & Behavior"))
                IosCard {
                    IosNumberStepperRow(
                        title = trx("Local SOCKS5 Port"),
                        subtitle = trx("Port used in Local Proxy mode"),
                        value = s.localProxyPort,
                        range = 1024..65535,
                        step = 1,
                        glyph = MarbleGlyph.SERVER,
                        squircleColor = colors.teal,
                        onValueChange = { repo.updateSettings(s.copy(localProxyPort = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Auto-Connect After Scan"),
                        subtitle = trx("Connect automatically to the top server after a Smart Rank scan"),
                        checked = s.autoConnectBestAfterScan,
                        glyph = MarbleGlyph.SPARK,
                        squircleColor = colors.orange,
                        onCheckedChange = { repo.updateSettings(s.copy(autoConnectBestAfterScan = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Adaptive MTU Healing"),
                        subtitle = trx("Adjust tunnel MTU automatically on lossy paths"),
                        checked = s.adaptiveMtuEnabled,
                        glyph = MarbleGlyph.TUNNEL,
                        squircleColor = colors.green,
                        onCheckedChange = { repo.updateSettings(s.copy(adaptiveMtuEnabled = it)) }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsCoreEnginePage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
    val s = repo.settings
    val engine = s.coreEngine()
    val isXray = engine == CoreEngine.XRAY

    IosSubPageScaffold(
        title = trx("Proxy Core Engine"),
        onBack = onBack
    ) {
        item(key = "core-selector") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Select Core Engine"))
                IosCard {
                    CoreEngine.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) IosDivider()
                        IosChoiceRow(
                            title = CoreEngineInfo.displayName(candidate),
                            subtitle = trx(CoreEngineInfo.summary(candidate)),
                            selected = engine == candidate,
                            badge = if (candidate == CoreEngine.XRAY) {
                                BuildConfig.XRAY_CORE_TAG
                            } else {
                                BuildConfig.SINGBOX_CORE_TAG
                            },
                            glyph = MarbleGlyph.SERVER,
                            squircleColor = if (candidate == CoreEngine.XRAY) colors.green else colors.indigo,
                            onClick = { repo.setCoreEngine(candidate) }
                        )
                    }
                }
            }
        }

        item(key = "core-sniff-inbound") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Inbound & Sniffing"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Traffic Sniffing"),
                        subtitle = trx("Inspect HTTP / TLS / QUIC hostnames for domain routing"),
                        checked = if (isXray) s.xraySniffingEnabled else s.singBoxSniffEnabled,
                        glyph = MarbleGlyph.RADAR,
                        squircleColor = colors.blue,
                        onCheckedChange = { enabled ->
                            repo.updateSettings(
                                if (isXray) s.copy(xraySniffingEnabled = enabled)
                                else s.copy(singBoxSniffEnabled = enabled)
                            )
                        }
                    )
                    if (isXray && s.xraySniffingEnabled) {
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Route-Only Sniffing"),
                            subtitle = trx("Use sniffed hostname for routing without rewriting destination"),
                            checked = s.xraySniffingRouteOnly,
                            glyph = MarbleGlyph.ROUTE,
                            squircleColor = colors.teal,
                            onCheckedChange = { repo.updateSettings(s.copy(xraySniffingRouteOnly = it)) }
                        )
                    }
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Allow LAN Inbound"),
                        subtitle = trx("Bind local inbound on 0.0.0.0 so LAN devices can share this tunnel"),
                        checked = if (isXray) s.xrayAllowLan else s.singBoxAllowLan,
                        glyph = MarbleGlyph.GLOBE,
                        squircleColor = colors.orange,
                        onCheckedChange = { allow ->
                            repo.updateSettings(
                                if (isXray) s.copy(xrayAllowLan = allow)
                                else s.copy(singBoxAllowLan = allow)
                            )
                        }
                    )
                    IosDivider()
                    IosNumberStepperRow(
                        title = trx("HTTP Inbound Port"),
                        subtitle = trx("0 disables the optional local HTTP proxy"),
                        value = if (isXray) s.xrayHttpInboundPort else s.singBoxHttpInboundPort,
                        range = 0..65535,
                        step = 1,
                        glyph = MarbleGlyph.LINK,
                        squircleColor = colors.purple,
                        onValueChange = { port ->
                            repo.updateSettings(
                                if (isXray) s.copy(xrayHttpInboundPort = port)
                                else s.copy(singBoxHttpInboundPort = port)
                            )
                        }
                    )
                }
            }
        }

        item(key = "core-mux-tcp") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Multiplexing & Transport"))
                IosCard {
                    IosSwitchRow(
                        title = if (isXray) "Mux.Cool" else trx("Multiplexing"),
                        subtitle = trx("Multiplex multiple streams over a single proxy connection"),
                        checked = if (isXray) s.muxEnabled else s.singBoxMuxEnabled,
                        glyph = MarbleGlyph.LAYERS,
                        squircleColor = colors.indigo,
                        onCheckedChange = { enabled ->
                            repo.updateSettings(
                                if (isXray) s.copy(muxEnabled = enabled)
                                else s.copy(singBoxMuxEnabled = enabled)
                            )
                        }
                    )
                    if (isXray && s.muxEnabled) {
                        IosDivider()
                        IosNumberStepperRow(
                            title = trx("Mux Concurrency"),
                            value = s.muxConcurrency,
                            range = 1..128,
                            glyph = MarbleGlyph.TUNERS,
                            squircleColor = colors.blue,
                            onValueChange = { repo.updateSettings(s.copy(muxConcurrency = it)) }
                        )
                    } else if (!isXray && s.singBoxMuxEnabled) {
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Mux Frame Padding"),
                            subtitle = trx("Pad multiplexed frames against packet-size fingerprinting"),
                            checked = s.singBoxMuxPadding,
                            glyph = MarbleGlyph.SHIELD,
                            squircleColor = colors.green,
                            onCheckedChange = { repo.updateSettings(s.copy(singBoxMuxPadding = it)) }
                        )
                    }
                    IosDivider()
                    IosSwitchRow(
                        title = trx("TCP Fast Open"),
                        subtitle = trx("Send initial payload inside the TCP SYN packet"),
                        checked = s.tcpFastOpenEnabled,
                        glyph = MarbleGlyph.SPARK,
                        squircleColor = colors.orange,
                        onCheckedChange = { repo.updateSettings(s.copy(tcpFastOpenEnabled = it)) }
                    )
                    if (isXray) {
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Verify Config Compatibility"),
                            subtitle = trx("Validate final config with Xray before starting tunnel"),
                            checked = s.configCompatibilityMode,
                            glyph = MarbleGlyph.CHECK,
                            squircleColor = colors.green,
                            onCheckedChange = { repo.updateSettings(s.copy(configCompatibilityMode = it)) }
                        )
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Allow Unencrypted Outbounds"),
                            subtitle = trx("Switch off to refuse plaintext public proxy configs"),
                            checked = s.allowUnencryptedPublicOutbound,
                            glyph = MarbleGlyph.SHIELD,
                            squircleColor = colors.red,
                            onCheckedChange = { repo.updateSettings(s.copy(allowUnencryptedPublicOutbound = it)) }
                        )
                    } else {
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Unified Delay Measurement"),
                            subtitle = trx("Measure true end-to-end round-trip latency"),
                            checked = s.singBoxUnifiedDelay,
                            glyph = MarbleGlyph.GAUGE,
                            squircleColor = colors.green,
                            onCheckedChange = { repo.updateSettings(s.copy(singBoxUnifiedDelay = it)) }
                        )
                    }
                }
            }
        }

        item(key = "core-log-and-json") {
            val levels = if (isXray) {
                listOf("error", "warning", "info", "debug", "none")
            } else {
                listOf("trace", "debug", "info", "warn", "error", "fatal")
            }
            val currentLevel = if (isXray) s.xrayLogLevel else s.singBoxLogLevel

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IosSectionHeader(title = trx("Log Level & Extra JSON"))
                IosChipStrip {
                    levels.forEach { lvl ->
                        IosPillChip(
                            label = lvl.uppercase(),
                            selected = currentLevel.equals(lvl, ignoreCase = true),
                            onClick = {
                                repo.updateSettings(
                                    if (isXray) s.copy(xrayLogLevel = lvl)
                                    else s.copy(singBoxLogLevel = lvl)
                                )
                            }
                        )
                    }
                }

                if (isXray && s.muxEnabled) {
                    IosChipStrip {
                        XrayMuxUdp443Modes.ALL.forEach { mode ->
                            IosPillChip(
                                label = "UDP 443: ${mode.uppercase()}",
                                selected = s.muxUdp443.equals(mode, ignoreCase = true),
                                accent = colors.indigo,
                                onClick = { repo.updateSettings(s.copy(muxUdp443 = mode)) }
                            )
                        }
                    }
                } else if (!isXray && s.singBoxMuxEnabled) {
                    IosChipStrip {
                        SingBoxMuxProtocols.ALL.forEach { proto ->
                            IosPillChip(
                                label = proto.uppercase(),
                                selected = s.singBoxMuxProtocol.equals(proto, ignoreCase = true),
                                accent = colors.indigo,
                                onClick = { repo.updateSettings(s.copy(singBoxMuxProtocol = proto)) }
                            )
                        }
                    }
                }

                if (isXray) {
                    IosChipStrip {
                        XrayDomainStrategies.ALL.forEach { ds ->
                            IosPillChip(
                                label = ds,
                                selected = s.xraySockoptDomainStrategy.equals(ds, ignoreCase = true),
                                accent = colors.teal,
                                onClick = { repo.updateSettings(s.copy(xraySockoptDomainStrategy = ds)) }
                            )
                        }
                    }
                }

                IosCard(contentPadding = PaddingValues(16.dp)) {
                    IosTextField(
                        label = trx("Extra Core JSON (Advanced)"),
                        value = if (isXray) s.xrayExtraJson else s.singBoxExtraJson,
                        onValueChange = { json ->
                            repo.updateSettings(
                                if (isXray) s.copy(xrayExtraJson = json)
                                else s.copy(singBoxExtraJson = json)
                            )
                        },
                        placeholder = "{ }",
                        singleLine = false,
                        monospace = true,
                        minLines = 5,
                        maxLines = 14
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsTunnelDatapathPage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
    val s = repo.settings
    val current = s.tunnelCore()

    IosSubPageScaffold(
        title = trx("Tunnel Datapath"),
        onBack = onBack
    ) {
        item(key = "tunnel-choice") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Packet Datapath"))
                IosCard {
                    TunnelCore.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) IosDivider()
                        val available = TunnelCoreInfo.isAvailable(candidate)
                        IosChoiceRow(
                            title = TunnelCoreInfo.displayName(candidate),
                            subtitle = if (available) {
                                trx(TunnelCoreInfo.summary(candidate))
                            } else {
                                trx(TunnelCoreInfo.unavailableReason(candidate))
                            },
                            selected = current == candidate,
                            enabled = available,
                            badge = if (candidate == TunnelCore.HEV) {
                                BuildConfig.HEV_CORE_TAG
                            } else {
                                BuildConfig.TUN2SOCKS_CORE_TAG
                            },
                            glyph = MarbleGlyph.LAYERS,
                            squircleColor = if (candidate == TunnelCore.HEV) colors.teal else colors.blue,
                            onClick = { repo.setTunnelCore(candidate) }
                        )
                    }
                }
            }
        }

        if (current == TunnelCore.TUN2SOCKS) {
            item(key = "tun2socks-options") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    IosSectionHeader(title = "tun2socks")
                    IosCard {
                        IosNumberStepperRow(
                            title = trx("MTU Cap"),
                            subtitle = trx("0 follows the active VPN session MTU"),
                            value = s.tun2SocksMtu,
                            range = 0..Tun2SocksPolicy.MAX_MTU,
                            step = 20,
                            glyph = MarbleGlyph.TUNERS,
                            squircleColor = colors.blue,
                            onValueChange = { repo.updateSettings(s.copy(tun2SocksMtu = it)) }
                        )
                        IosDivider()
                        IosNumberStepperRow(
                            title = trx("UDP Session Timeout"),
                            value = s.tun2SocksUdpTimeoutSec,
                            range = Tun2SocksPolicy.MIN_UDP_TIMEOUT_SEC..Tun2SocksPolicy.MAX_UDP_TIMEOUT_SEC,
                            step = 5,
                            suffix = "s",
                            glyph = MarbleGlyph.GAUGE,
                            squircleColor = colors.green,
                            onValueChange = {
                                repo.updateSettings(
                                    s.copy(tun2SocksUdpTimeoutSec = Tun2SocksPolicy.normalizeUdpTimeout(it))
                                )
                            }
                        )
                        IosDivider()
                        IosNumberStepperRow(
                            title = trx("TCP Send Buffer"),
                            value = s.tun2SocksTcpSendBufferKb,
                            range = 0..Tun2SocksPolicy.MAX_BUFFER_KB,
                            step = 32,
                            suffix = " KB",
                            glyph = MarbleGlyph.UPLOAD,
                            squircleColor = colors.indigo,
                            onValueChange = {
                                repo.updateSettings(
                                    s.copy(tun2SocksTcpSendBufferKb = Tun2SocksPolicy.normalizeBufferKb(it))
                                )
                            }
                        )
                        IosDivider()
                        IosNumberStepperRow(
                            title = trx("TCP Receive Buffer"),
                            value = s.tun2SocksTcpReceiveBufferKb,
                            range = 0..Tun2SocksPolicy.MAX_BUFFER_KB,
                            step = 32,
                            suffix = " KB",
                            glyph = MarbleGlyph.DOWNLOAD,
                            squircleColor = colors.purple,
                            onValueChange = {
                                repo.updateSettings(
                                    s.copy(tun2SocksTcpReceiveBufferKb = Tun2SocksPolicy.normalizeBufferKb(it))
                                )
                            }
                        )
                        IosDivider()
                        IosSwitchRow(
                            title = trx("Moderate Receive Buffer"),
                            subtitle = trx("Grow TCP receive window dynamically as needed"),
                            checked = s.tun2SocksModerateReceiveBuffer,
                            glyph = MarbleGlyph.SPARK,
                            squircleColor = colors.orange,
                            onCheckedChange = { repo.updateSettings(s.copy(tun2SocksModerateReceiveBuffer = it)) }
                        )
                    }

                    IosChipStrip {
                        Tun2SocksPolicy.LOG_LEVELS.forEach { lvl ->
                            IosPillChip(
                                label = lvl.uppercase(),
                                selected = s.tun2SocksLogLevel.equals(lvl, ignoreCase = true),
                                onClick = { repo.updateSettings(s.copy(tun2SocksLogLevel = lvl)) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsPowerProfilePage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = IosTheme.colors
    val s = repo.settings
    val profile = parsePowerProfile(s.powerProfile)

    IosSubPageScaffold(
        title = trx("Power & Speed Profile"),
        onBack = onBack
    ) {
        item(key = "power-profiles") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Performance Profile"))
                IosCard {
                    val descriptions = mapOf(
                        PowerProfile.EFFICIENT to trx("Lower background wakeups and smaller socket pools to save battery"),
                        PowerProfile.BALANCED to trx("Standard core settings exactly as configured"),
                        PowerProfile.TURBO to trx("Faster parallel pings, TCP Fast Open and scaled buffers"),
                        PowerProfile.EXTREME to trx("Maximum concurrency and large buffers on capable devices")
                    )
                    PowerProfile.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) IosDivider()
                        IosChoiceRow(
                            title = trx(candidate.label),
                            subtitle = descriptions[candidate].orEmpty(),
                            selected = profile == candidate,
                            glyph = MarbleGlyph.SPARK,
                            squircleColor = when (candidate) {
                                PowerProfile.EFFICIENT -> colors.green
                                PowerProfile.BALANCED -> colors.blue
                                PowerProfile.TURBO -> colors.orange
                                PowerProfile.EXTREME -> colors.red
                            },
                            onClick = { repo.updateSettings(s.copy(powerProfile = candidate.id)) }
                        )
                    }
                }
            }
        }

        item(key = "power-warmup") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IosSectionHeader(title = trx("Connection Acceleration"))
                IosCard {
                    IosSwitchRow(
                        title = trx("Pre-Warm Active Route on Launch"),
                        subtitle = trx("Pre-resolve DNS so the first ping and connection start faster"),
                        checked = s.warmupOnLaunch,
                        glyph = MarbleGlyph.GAUGE,
                        squircleColor = colors.teal,
                        onCheckedChange = { repo.updateSettings(s.copy(appBehavior = s.appBehavior.copy(warmupOnLaunch = it))) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Race Parallel Connect"),
                        subtitle = trx("Dial candidate endpoints in parallel for faster failover"),
                        checked = s.raceConnectEnabled,
                        glyph = MarbleGlyph.ROUTE,
                        squircleColor = colors.indigo,
                        onCheckedChange = { repo.updateSettings(s.copy(raceConnectEnabled = it)) }
                    )
                    IosDivider()
                    IosSwitchRow(
                        title = trx("Smart Automatic Failover"),
                        subtitle = trx("Switch to a healthy backup server if the tunnel stalls"),
                        checked = s.smartFallbackEnabled,
                        glyph = MarbleGlyph.SHIELD,
                        squircleColor = colors.green,
                        onCheckedChange = { repo.updateSettings(s.copy(smartFallbackEnabled = it)) }
                    )
                }
            }
        }
    }
}
