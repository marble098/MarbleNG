package com.marbleng.app.core

import com.marbleng.app.model.AppSettings

/**
 * MARBLE_SINGBOX_CORE_V151 — the proxy core that carries the tunnel.
 *
 * MarbleNG shipped with exactly one engine for its whole life: Xray-core in front of
 * hev-socks5-tunnel. sing-box extended is now a real second engine, not a preview: it is pinned in
 * `core-lock.json`, downloaded by `scripts/prepare-native.sh`, packaged as `libsingbox.so` in every
 * ABI, and switched from Settings → Engine with one tap.
 *
 * Both engines keep the same data path so nothing else in the product has to change:
 *
 * ```
 *   Android VpnService (TUN)
 *        │
 *        ▼
 *   hev-socks5-tunnel  ──SOCKS5/127.0.0.1──▶  Xray-core  |  sing-box extended
 * ```
 *
 * That is the whole point of the local SOCKS hop: the tunnel transport (HEV), the VPN session, the
 * routing assets, the measurement plane and the Home screen are engine-agnostic. Only the process
 * that speaks to the server differs.
 */
enum class CoreEngine(val id: String) {
    /** Xray-core (XTLS) — the default engine and the one every legacy feature was built on. */
    XRAY("xray"),

    /** sing-box extended (shtorm-7) — the extended fork: WARP, MASQUE, MTProxy, XHTTP, mKCP… */
    SINGBOX("singbox")
}

/** Reads a persisted engine id and never fails: an unknown value is the shipped default. */
fun parseCoreEngine(raw: String): CoreEngine =
    CoreEngine.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().lowercase()) {
            "sing-box", "singbox-extended", "sfa", "sfe" -> CoreEngine.SINGBOX
            "xray", "xray-core", "xtls" -> CoreEngine.XRAY
            else -> CoreEngine.XRAY
        }

/** The engine the current settings ask for. One accessor so no caller can disagree. */
fun AppSettings.coreEngine(): CoreEngine = parseCoreEngine(coreEngineId)

/**
 * Everything the UI and the diagnostics need to know about an engine, in one place.
 *
 * The names are deliberately the ones the upstream projects use for themselves, and the binary
 * names are the real files inside `nativeLibraryDir` (Android only extracts `lib*.so` from
 * jniLibs, so both cores are shipped with that prefix and executed with ProcessBuilder).
 */
object CoreEngineInfo {
    const val XRAY_BINARY = "libxray.so"
    const val SINGBOX_BINARY = "libsingbox.so"

    fun displayName(engine: CoreEngine): String = when (engine) {
        CoreEngine.XRAY -> "Xray-core"
        CoreEngine.SINGBOX -> "sing-box extended"
    }

    fun binaryName(engine: CoreEngine): String = when (engine) {
        CoreEngine.XRAY -> XRAY_BINARY
        CoreEngine.SINGBOX -> SINGBOX_BINARY
    }

    fun upstream(engine: CoreEngine): String = when (engine) {
        CoreEngine.XRAY -> "XTLS/Xray-core"
        CoreEngine.SINGBOX -> "shtorm-7/sing-box-extended"
    }

    fun summary(engine: CoreEngine): String = when (engine) {
        CoreEngine.XRAY ->
            "Xray-core with Reality, XHTTP, fragment and the full MarbleNG tuning stack."
        CoreEngine.SINGBOX ->
            "The extended sing-box fork: WARP, MASQUE, MTProxy, Mieru, TrustTunnel, mKCP and " +
                "a native URL test through its Clash API."
    }
}
