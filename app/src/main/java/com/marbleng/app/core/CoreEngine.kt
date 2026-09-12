package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProbeMethod

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

// ───────────────────────────────────────────────────────────────────────────────────────────────
// MARBLE_URLTEST_SINGBOX_ONLY_V156 — which measurement belongs to which core
// ───────────────────────────────────────────────────────────────────────────────────────────────

/**
 * The URL test is the sing-box extended core's *own* measurement: `GET /proxies/{tag}/delay`
 * against the core's Clash controller, which times the round trip with unified-delay accounting
 * through the outbound the core itself dialed.
 *
 * MarbleNG used to answer the same button on the Xray engine with a look-alike: an HTTPS HEAD
 * pushed through Xray's SOCKS inbound by a Kotlin socket. That is a different measurement wearing
 * the same label — no controller, no unified delay, and a second, independently-timed HTTP stack
 * — so the same server reported two different numbers depending on which core happened to be
 * selected. One method, one engine, one number: the URL test exists exactly when the engine that
 * owns it is selected, and is not offered otherwise.
 */
fun ProbeMethod.availableOn(engine: CoreEngine): Boolean =
    this != ProbeMethod.URL_TEST || engine == CoreEngine.SINGBOX

/**
 * The method to run when the user's stored choice is not offered by [engine] — switching the
 * engine to Xray while "URL test" was selected must degrade to the closest honest measurement
 * instead of silently measuring something else or reporting a permanent failure.
 */
fun ProbeMethod.forEngine(engine: CoreEngine): ProbeMethod =
    if (availableOn(engine)) this else ProbeMethod.REAL_DELAY

/** Why [availableOn] is false, in the one sentence the Settings page shows under the row. */
fun ProbeMethod.unavailableReason(engine: CoreEngine): String = when {
    availableOn(engine) -> ""
    else -> "Runs on sing-box extended's own delay controller. Switch Settings → Tunnel core to " +
        "sing-box extended to use it."
}

// ───────────────────────────────────────────────────────────────────────────────────────────────
// MARBLE_ENGINE_AWARE_BUGFINDER_V164 — which core's liveness describes the live session
// ───────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Engine-aware snapshot of the core that carries the current session.
 *
 * The Bug Finder used to ask only `xray.isAlive`, which is false by construction on a sing-box
 * session: [MarbleVpnService] stops *both* cores when an engine takes over, so a healthy sing-box
 * tunnel reported `xrayStartPhase=stopped`, `xrayPid=-1` and `xrayAlive=false` — and the scan
 * printed "UI says CONNECTED but Xray is dead" for a route that was carrying traffic. The honest
 * question is "is the core the settings selected alive?", and it is answered once here so every
 * reader (Bug Finder, the current-connection block, the SOCKS-listener check) agrees.
 */
data class ActiveCoreState(
    val engine: CoreEngine,
    val alive: Boolean,
    val pid: Long,
    val phase: String,
    val error: String
) {
    val id: String get() = engine.id
    val label: String get() = CoreEngineInfo.displayName(engine)
}

/**
 * Selects the liveness evidence that matches [engine]. `pid` is only meaningful for Xray (the
 * sing-box session does not expose a child PID to the diagnostic plane), so it is `-1` there.
 */
fun resolveActiveCoreState(
    engine: CoreEngine,
    xrayAlive: Boolean,
    xrayPid: Long,
    xrayPhase: String,
    xrayError: String,
    singboxAlive: Boolean,
    singboxPhase: String,
    singboxError: String
): ActiveCoreState = when (engine) {
    CoreEngine.XRAY -> ActiveCoreState(engine, xrayAlive, xrayPid, xrayPhase, xrayError)
    CoreEngine.SINGBOX -> ActiveCoreState(engine, singboxAlive, -1L, singboxPhase, singboxError)
}

