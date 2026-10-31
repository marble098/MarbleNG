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
import androidx.compose.material3.MaterialTheme
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

@Composable
fun SettingsConnectionModePage(
    repo: AppRepository,
    onBack: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val s = repo.settings

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.TUNNEL,
        tint = colors.primary,
        title = trx("How you connect"),
        onBack = onBack
    ) {
        item(key = "conn-mode") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Connection Mode"))
                MarbleCard {
                    MarbleChoiceRow(
                        title = trx("Full TUN (Device VPN)"),
                        subtitle = trx("Routes all device apps through the encrypted VPN interface"),
                        selected = s.connectionMode == ConnectionMode.FULL_TUN,
                        glyph = MarbleGlyph.SHIELD,
                        iconTint = colors.primary,
                        onClick = { repo.setConnectionMode(ConnectionMode.FULL_TUN) }
                    )
                    MarbleDivider()
                    MarbleChoiceRow(
                        title = trx("Local Proxy Only"),
                        subtitle = "${trx("Exposes a local SOCKS5 listener on")} 127.0.0.1:${s.localProxyPort}",
                        selected = s.connectionMode == ConnectionMode.LOCAL_PROXY,
                        glyph = MarbleGlyph.LINK,
                        iconTint = colors.secondary,
                        onClick = { repo.setConnectionMode(ConnectionMode.LOCAL_PROXY) }
                    )
                }
            }
        }

        item(key = "conn-ports") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Local Ports & Behavior"))
                MarbleCard {
                    MarbleNumberStepperRow(
                        title = trx("Local SOCKS5 Port"),
                        subtitle = trx("Port used in Local Proxy mode"),
                        value = s.localProxyPort,
                        range = 1024..65535,
                        step = 1,
                        glyph = MarbleGlyph.SERVER,
                        iconTint = colors.tertiary,
                        onValueChange = { repo.updateSettings(s.copy(localProxyPort = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Auto-Connect After Scan"),
                        subtitle = trx("Connect automatically to the top server after a Smart Rank scan"),
                        checked = s.autoConnectBestAfterScan,
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.warning,
                        onCheckedChange = { repo.updateSettings(s.copy(autoConnectBestAfterScan = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Adaptive MTU Healing"),
                        subtitle = trx("Adjust tunnel MTU automatically on lossy paths"),
                        checked = s.adaptiveMtuEnabled,
                        glyph = MarbleGlyph.TUNNEL,
                        iconTint = colors.success,
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
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val engine = s.coreEngine()
    val isXray = engine == CoreEngine.XRAY

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.SERVER,
        tint = colors.secondary,
        title = trx("Proxy engine"),
        onBack = onBack
    ) {
        item(key = "core-selector") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Select Core Engine"))
                MarbleCard {
                    CoreEngine.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = CoreEngineInfo.displayName(candidate),
                            subtitle = trx(CoreEngineInfo.summary(candidate)),
                            selected = engine == candidate,
                            badge = if (candidate == CoreEngine.XRAY) {
                                BuildConfig.XRAY_CORE_TAG
                            } else {
                                BuildConfig.SINGBOX_CORE_TAG
                            },
                            glyph = MarbleGlyph.SERVER,
                            iconTint = if (candidate == CoreEngine.XRAY) colors.success else colors.secondary,
                            onClick = { repo.setCoreEngine(candidate) }
                        )
                    }
                }
            }
        }

        item(key = "core-sniff-inbound") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Inbound & Sniffing"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Traffic Sniffing"),
                        subtitle = trx("Inspect HTTP / TLS / QUIC hostnames for domain routing"),
                        checked = if (isXray) s.xraySniffingEnabled else s.singBoxSniffEnabled,
                        glyph = MarbleGlyph.RADAR,
                        iconTint = colors.primary,
                        onCheckedChange = { enabled ->
                            repo.updateSettings(
                                if (isXray) s.copy(xraySniffingEnabled = enabled)
                                else s.copy(singBoxSniffEnabled = enabled)
                            )
                        }
                    )
                    if (isXray && s.xraySniffingEnabled) {
                        MarbleDivider()
                        MarbleSwitchRow(
                            title = trx("Route-Only Sniffing"),
                            subtitle = trx("Use sniffed hostname for routing without rewriting destination"),
                            checked = s.xraySniffingRouteOnly,
                            glyph = MarbleGlyph.ROUTE,
                            iconTint = colors.tertiary,
                            onCheckedChange = { repo.updateSettings(s.copy(xraySniffingRouteOnly = it)) }
                        )
                    }
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Allow LAN Inbound"),
                        subtitle = trx("Bind local inbound on 0.0.0.0 so LAN devices can share this tunnel"),
                        checked = if (isXray) s.xrayAllowLan else s.singBoxAllowLan,
                        glyph = MarbleGlyph.GLOBE,
                        iconTint = colors.warning,
                        onCheckedChange = { allow ->
                            repo.updateSettings(
                                if (isXray) s.copy(xrayAllowLan = allow)
                                else s.copy(singBoxAllowLan = allow)
                            )
                        }
                    )
                    MarbleDivider()
                    MarbleNumberStepperRow(
                        title = trx("HTTP Inbound Port"),
                        subtitle = trx("0 disables the optional local HTTP proxy"),
                        value = if (isXray) s.xrayHttpInboundPort else s.singBoxHttpInboundPort,
                        range = 0..65535,
                        step = 1,
                        glyph = MarbleGlyph.LINK,
                        iconTint = colors.secondary,
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
                MarbleSectionHeader(title = trx("Multiplexing & Transport"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = if (isXray) "Mux.Cool" else trx("Multiplexing"),
                        subtitle = trx("Multiplex multiple streams over a single proxy connection"),
                        checked = if (isXray) s.muxEnabled else s.singBoxMuxEnabled,
                        glyph = MarbleGlyph.LAYERS,
                        iconTint = colors.secondary,
                        onCheckedChange = { enabled ->
                            repo.updateSettings(
                                if (isXray) s.copy(muxEnabled = enabled)
                                else s.copy(singBoxMuxEnabled = enabled)
                            )
                        }
                    )
                    if (isXray && s.muxEnabled) {
                        MarbleDivider()
                        MarbleNumberStepperRow(
                            title = trx("Mux Concurrency"),
                            value = s.muxConcurrency,
                            range = 1..128,
                            glyph = MarbleGlyph.TUNERS,
                            iconTint = colors.primary,
                            onValueChange = { repo.updateSettings(s.copy(muxConcurrency = it)) }
                        )
                    } else if (!isXray && s.singBoxMuxEnabled) {
                        MarbleDivider()
                        MarbleSwitchRow(
                            title = trx("Mux Frame Padding"),
                            subtitle = trx("Pad multiplexed frames against packet-size fingerprinting"),
                            checked = s.singBoxMuxPadding,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.success,
                            onCheckedChange = { repo.updateSettings(s.copy(singBoxMuxPadding = it)) }
                        )
                    }
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("TCP Fast Open"),
                        subtitle = trx("Send initial payload inside the TCP SYN packet"),
                        checked = s.tcpFastOpenEnabled,
                        glyph = MarbleGlyph.SPARK,
                        iconTint = colors.warning,
                        onCheckedChange = { repo.updateSettings(s.copy(tcpFastOpenEnabled = it)) }
                    )
                    if (isXray) {
                        MarbleDivider()
                        MarbleSwitchRow(
                            title = trx("Verify Config Compatibility"),
                            subtitle = trx("Validate final config with Xray before starting tunnel"),
                            checked = s.configCompatibilityMode,
                            glyph = MarbleGlyph.CHECK,
                            iconTint = colors.success,
                            onCheckedChange = { repo.updateSettings(s.copy(configCompatibilityMode = it)) }
                        )
                        MarbleDivider()
                        MarbleSwitchRow(
                            title = trx("Allow Unencrypted Outbounds"),
                            subtitle = trx("Switch off to refuse plaintext public proxy configs"),
                            checked = s.allowUnencryptedPublicOutbound,
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = colors.error,
                            onCheckedChange = { repo.updateSettings(s.copy(allowUnencryptedPublicOutbound = it)) }
                        )
                    } else {
                        MarbleDivider()
                        MarbleSwitchRow(
                            title = trx("Unified Delay Measurement"),
                            subtitle = trx("Measure true end-to-end round-trip latency"),
                            checked = s.singBoxUnifiedDelay,
                            glyph = MarbleGlyph.GAUGE,
                            iconTint = colors.success,
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
                MarbleSectionHeader(title = trx("Log Level & Extra JSON"))
                MarbleChipStrip {
                    levels.forEach { lvl ->
                        MarblePillChip(
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
                    MarbleChipStrip {
                        XrayMuxUdp443Modes.ALL.forEach { mode ->
                            MarblePillChip(
                                label = "UDP 443: ${mode.uppercase()}",
                                selected = s.muxUdp443.equals(mode, ignoreCase = true),
                                accent = colors.secondary,
                                onClick = { repo.updateSettings(s.copy(muxUdp443 = mode)) }
                            )
                        }
                    }
                } else if (!isXray && s.singBoxMuxEnabled) {
                    MarbleChipStrip {
                        SingBoxMuxProtocols.ALL.forEach { proto ->
                            MarblePillChip(
                                label = proto.uppercase(),
                                selected = s.singBoxMuxProtocol.equals(proto, ignoreCase = true),
                                accent = colors.secondary,
                                onClick = { repo.updateSettings(s.copy(singBoxMuxProtocol = proto)) }
                            )
                        }
                    }
                }

                if (isXray) {
                    MarbleChipStrip {
                        XrayDomainStrategies.ALL.forEach { ds ->
                            MarblePillChip(
                                label = ds,
                                selected = s.xraySockoptDomainStrategy.equals(ds, ignoreCase = true),
                                accent = colors.tertiary,
                                onClick = { repo.updateSettings(s.copy(xraySockoptDomainStrategy = ds)) }
                            )
                        }
                    }
                }

                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    MarbleTextField(
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
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val current = s.tunnelCore()

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.LAYERS,
        tint = colors.tertiary,
        title = trx("Tunnel path"),
        onBack = onBack
    ) {
        item(key = "tunnel-choice") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Packet Datapath"))
                MarbleCard {
                    TunnelCore.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) MarbleDivider()
                        val available = TunnelCoreInfo.isAvailable(candidate)
                        MarbleChoiceRow(
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
                            iconTint = if (candidate == TunnelCore.HEV) colors.tertiary else colors.primary,
                            onClick = { repo.setTunnelCore(candidate) }
                        )
                    }
                }
            }
        }

        if (current == TunnelCore.TUN2SOCKS) {
            item(key = "tun2socks-options") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    MarbleSectionHeader(title = "tun2socks")
                    MarbleCard {
                        MarbleNumberStepperRow(
                            title = trx("MTU Cap"),
                            subtitle = trx("0 follows the active VPN session MTU"),
                            value = s.tun2SocksMtu,
                            range = 0..Tun2SocksPolicy.MAX_MTU,
                            step = 20,
                            glyph = MarbleGlyph.TUNERS,
                            iconTint = colors.primary,
                            onValueChange = { repo.updateSettings(s.copy(tun2SocksMtu = it)) }
                        )
                        MarbleDivider()
                        MarbleNumberStepperRow(
                            title = trx("UDP Session Timeout"),
                            value = s.tun2SocksUdpTimeoutSec,
                            range = Tun2SocksPolicy.MIN_UDP_TIMEOUT_SEC..Tun2SocksPolicy.MAX_UDP_TIMEOUT_SEC,
                            step = 5,
                            suffix = "s",
                            glyph = MarbleGlyph.GAUGE,
                            iconTint = colors.success,
                            onValueChange = {
                                repo.updateSettings(
                                    s.copy(tun2SocksUdpTimeoutSec = Tun2SocksPolicy.normalizeUdpTimeout(it))
                                )
                            }
                        )
                        MarbleDivider()
                        MarbleNumberStepperRow(
                            title = trx("TCP Send Buffer"),
                            value = s.tun2SocksTcpSendBufferKb,
                            range = 0..Tun2SocksPolicy.MAX_BUFFER_KB,
                            step = 32,
                            suffix = " KB",
                            glyph = MarbleGlyph.UPLOAD,
                            iconTint = colors.secondary,
                            onValueChange = {
                                repo.updateSettings(
                                    s.copy(tun2SocksTcpSendBufferKb = Tun2SocksPolicy.normalizeBufferKb(it))
                                )
                            }
                        )
                        MarbleDivider()
                        MarbleNumberStepperRow(
                            title = trx("TCP Receive Buffer"),
                            value = s.tun2SocksTcpReceiveBufferKb,
                            range = 0..Tun2SocksPolicy.MAX_BUFFER_KB,
                            step = 32,
                            suffix = " KB",
                            glyph = MarbleGlyph.DOWNLOAD,
                            iconTint = colors.secondary,
                            onValueChange = {
                                repo.updateSettings(
                                    s.copy(tun2SocksTcpReceiveBufferKb = Tun2SocksPolicy.normalizeBufferKb(it))
                                )
                            }
                        )
                        MarbleDivider()
                        MarbleSwitchRow(
                            title = trx("Moderate Receive Buffer"),
                            subtitle = trx("Grow TCP receive window dynamically as needed"),
                            checked = s.tun2SocksModerateReceiveBuffer,
                            glyph = MarbleGlyph.SPARK,
                            iconTint = colors.warning,
                            onCheckedChange = { repo.updateSettings(s.copy(tun2SocksModerateReceiveBuffer = it)) }
                        )
                    }

                    MarbleChipStrip {
                        Tun2SocksPolicy.LOG_LEVELS.forEach { lvl ->
                            MarblePillChip(
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
    val colors = MaterialTheme.colorScheme
    val s = repo.settings
    val profile = parsePowerProfile(s.powerProfile)

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.SPARK,
        tint = colors.warning,
        title = trx("Speed and battery"),
        onBack = onBack
    ) {
        item(key = "power-profiles") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Performance Profile"))
                MarbleCard {
                    val descriptions = mapOf(
                        PowerProfile.EFFICIENT to trx("Lower background wakeups and smaller socket pools to save battery"),
                        PowerProfile.BALANCED to trx("Standard core settings exactly as configured"),
                        PowerProfile.TURBO to trx("Faster parallel pings, TCP Fast Open and scaled buffers"),
                        PowerProfile.EXTREME to trx("Maximum concurrency and large buffers on capable devices")
                    )
                    PowerProfile.entries.forEachIndexed { idx, candidate ->
                        if (idx > 0) MarbleDivider()
                        MarbleChoiceRow(
                            title = trx(candidate.label),
                            subtitle = descriptions[candidate].orEmpty(),
                            selected = profile == candidate,
                            glyph = MarbleGlyph.SPARK,
                            iconTint = when (candidate) {
                                PowerProfile.EFFICIENT -> colors.success
                                PowerProfile.BALANCED -> colors.primary
                                PowerProfile.TURBO -> colors.warning
                                PowerProfile.EXTREME -> colors.error
                            },
                            onClick = { repo.updateSettings(s.copy(powerProfile = candidate.id)) }
                        )
                    }
                }
            }
        }

        item(key = "power-warmup") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Connection Acceleration"))
                MarbleCard {
                    MarbleSwitchRow(
                        title = trx("Pre-Warm Active Route on Launch"),
                        subtitle = trx("Pre-resolve DNS so the first ping and connection start faster"),
                        checked = s.warmupOnLaunch,
                        glyph = MarbleGlyph.GAUGE,
                        iconTint = colors.tertiary,
                        onCheckedChange = { repo.updateSettings(s.copy(appBehavior = s.appBehavior.copy(warmupOnLaunch = it))) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Race Parallel Connect"),
                        subtitle = trx("Dial candidate endpoints in parallel for faster failover"),
                        checked = s.raceConnectEnabled,
                        glyph = MarbleGlyph.ROUTE,
                        iconTint = colors.secondary,
                        onCheckedChange = { repo.updateSettings(s.copy(raceConnectEnabled = it)) }
                    )
                    MarbleDivider()
                    MarbleSwitchRow(
                        title = trx("Smart Automatic Failover"),
                        subtitle = trx("Switch to a healthy backup server if the tunnel stalls"),
                        checked = s.smartFallbackEnabled,
                        glyph = MarbleGlyph.SHIELD,
                        iconTint = colors.success,
                        onCheckedChange = { repo.updateSettings(s.copy(smartFallbackEnabled = it)) }
                    )
                }
            }
        }
    }
}
