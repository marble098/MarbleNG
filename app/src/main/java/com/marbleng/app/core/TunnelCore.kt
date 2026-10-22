package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.nativebridge.Tun2SocksTunnel

/**
 * MARBLE_TUN2SOCKS_CORE_V222 — the engine that moves packets between the VpnService TUN device
 * and the proxy core's loopback SOCKS5 inbound.
 *
 * This is deliberately a separate choice from [CoreEngine]. Xray and sing-box answer "who speaks
 * the proxy protocol"; this answers "who carries the device's packets to that proxy". Marble has
 * shipped one answer since the first tunnel (`hev-socks5-tunnel`, compiled into `libmarbleng.so`),
 * and the user can now pick the pinned `xjasonlyu/tun2socks` release instead, with its own
 * settings. The two choices compose: either proxy core runs behind either datapath.
 */
enum class TunnelCore(val id: String) {
    HEV("hev"),

    TUN2SOCKS("tun2socks")
}

fun parseTunnelCore(raw: String): TunnelCore =
    TunnelCore.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().lowercase()) {
            "hev-socks5-tunnel", "hev_socks5_tunnel", "hev-socks5" -> TunnelCore.HEV
            "tun2socks", "t2s", "tun-2-socks", "tun2socks-v2" -> TunnelCore.TUN2SOCKS
            else -> TunnelCore.HEV
        }

fun AppSettings.tunnelCore(): TunnelCore = parseTunnelCore(tunnelCoreId)

object TunnelCoreInfo {
    const val HEV_DRIVER = "hev-socks5-tunnel"
    const val TUN2SOCKS_DRIVER = "tun2socks"
    const val TUN2SOCKS_LIBRARY = "libtun2socks.so"

    fun displayName(core: TunnelCore): String = when (core) {
        TunnelCore.HEV -> "hev-socks5-tunnel"
        TunnelCore.TUN2SOCKS -> "tun2socks"
    }

    fun upstream(core: TunnelCore): String = when (core) {
        TunnelCore.HEV -> "heiher/hev-socks5-tunnel"
        TunnelCore.TUN2SOCKS -> "xjasonlyu/tun2socks"
    }

    fun summary(core: TunnelCore): String = when (core) {
        TunnelCore.HEV ->
            "The datapath Marble has always shipped: a small C tunnel compiled into the app, " +
                "started directly on the VpnService descriptor."
        TunnelCore.TUN2SOCKS ->
            "The alternative datapath: the pinned tun2socks release, built into libtun2socks.so " +
                "and loaded in-process, with its own MTU, UDP-timeout, buffer and log settings."
    }

    fun isAvailable(core: TunnelCore): Boolean = when (core) {
        TunnelCore.HEV -> true
        TunnelCore.TUN2SOCKS -> Tun2SocksTunnel.available()
    }

    fun unavailableReason(core: TunnelCore): String = when {
        isAvailable(core) -> ""
        else -> "This APK does not carry ${TUN2SOCKS_LIBRARY} for this device's ABI " +
            "(${Tun2SocksTunnel.unavailableReason()}). Marble stays on $HEV_DRIVER."
    }
}

/** What the chosen datapath is doing right now, for the Settings page and the diagnostics bundle. */
data class TunnelCoreState(
    val core: TunnelCore,
    val available: Boolean,
    val reason: String = ""
) {
    val label: String get() = TunnelCoreInfo.displayName(core)
}

fun resolveTunnelCoreState(core: TunnelCore): TunnelCoreState =
    TunnelCoreState(
        core = core,
        available = TunnelCoreInfo.isAvailable(core),
        reason = TunnelCoreInfo.unavailableReason(core)
    )
