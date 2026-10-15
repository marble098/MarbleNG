package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProbeMethod

enum class CoreEngine(val id: String) {
    XRAY("xray"),

    SINGBOX("singbox")
}

fun parseCoreEngine(raw: String): CoreEngine =
    CoreEngine.entries.firstOrNull { it.id.equals(raw.trim(), ignoreCase = true) }
        ?: when (raw.trim().lowercase()) {
            "sing-box", "singbox-extended", "sfa", "sfe" -> CoreEngine.SINGBOX
            "xray", "xray-core", "xtls" -> CoreEngine.XRAY
            else -> CoreEngine.XRAY
        }

fun AppSettings.coreEngine(): CoreEngine = parseCoreEngine(coreEngineId)

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
            "Xray-core with Reality, XHTTP and the full MarbleNG tuning stack."
        CoreEngine.SINGBOX ->
            "The extended sing-box fork: WARP, MASQUE, MTProxy, Mieru, TrustTunnel, mKCP and " +
                "a native URL test through its Clash API."
    }
}

fun ProbeMethod.availableOn(engine: CoreEngine): Boolean =
    this != ProbeMethod.URL_TEST || engine == CoreEngine.SINGBOX

fun ProbeMethod.forEngine(engine: CoreEngine): ProbeMethod =
    if (availableOn(engine)) this else ProbeMethod.REAL_DELAY

fun ProbeMethod.unavailableReason(engine: CoreEngine): String = when {
    availableOn(engine) -> ""
    else -> "Runs on sing-box extended's own delay controller. Switch Settings → Tunnel core to " +
        "sing-box extended to use it."
}

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

