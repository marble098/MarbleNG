package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object SingBoxCoreSelfTest {
    const val STARTUP_TIMEOUT_MS: Long = 8_000L

    const val SETTLE_MS: Long = 700L

    data class Verdict(
        val ok: Boolean,
        val reason: String,
        val fingerprint: String,
        val atMs: Long
    ) {
        val coreFault: Boolean get() = !ok && SingBoxAndroidRuntime.isUnusableCore(reason)

        val summary: String
            get() = when {
                ok -> "core starts and serves its local inbound"
                coreFault -> "core cannot start on this device"
                else -> "core self-test inconclusive"
            }

        fun asFailureReason(): String =
            if (coreFault) "core-crash: $reason" else "core-unavailable: $reason"
    }

    fun canaryConfig(socksPort: Int): String {
        require(socksPort in 1..65535) { "core-config: invalid self-test SOCKS port" }
        return JSONObject()
            .put("log", JSONObject().put("level", "error").put("timestamp", true))
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject()
                        .put("type", "mixed")
                        .put("tag", SingBoxConfigBuilder.INBOUND_TAG)
                        .put("listen", "127.0.0.1")
                        .put("listen_port", socksPort)
                )
            )
            .put(
                "outbounds",
                JSONArray().put(

                    JSONObject()
                        .put("type", "direct")
                        .put("tag", SingBoxConfigBuilder.DIRECT_TAG)
                )
            )
            .put("route", JSONObject().put("final", SingBoxConfigBuilder.DIRECT_TAG))
            .toString()
    }

    fun fingerprint(binary: File): String =
        "${binary.absolutePath}|${binary.length()}|${binary.lastModified()}"

    fun probe(binary: File, workspace: File, socksPort: Int, timeoutMs: Long = STARTUP_TIMEOUT_MS): Verdict {
        val stamp = fingerprint(binary)
        if (!binary.isFile || binary.length() < 1024) {
            return Verdict(false, "core-install: sing-box executable is missing", stamp, System.currentTimeMillis())
        }
        return try {
            runCatching { workspace.mkdirs() }
            SingBoxProcessSession.open(
                binary = binary,
                config = canaryConfig(socksPort),
                configFile = File(workspace, "selftest.json"),
                logFile = File(workspace, "selftest.log"),
                tempDir = workspace,
                socksPort = socksPort,

                apiPort = 0,
                secret = "",
                startupTimeoutMs = timeoutMs,
                validate = false,
                awaitApi = false,
                settleMs = SETTLE_MS
            ).use { Verdict(true, "", stamp, System.currentTimeMillis()) }
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            Verdict(false, "core-selftest: cancelled", stamp, System.currentTimeMillis())
        } catch (error: Exception) {
            Verdict(
                false,
                SingBoxAndroidRuntime.explain(error.message ?: error.javaClass.simpleName),
                stamp,
                System.currentTimeMillis()
            )
        }
    }

    fun read(cacheFile: File, binary: File): Verdict? = runCatching {
        val json = JSONObject(cacheFile.readText())
        val verdict = Verdict(
            ok = json.optBoolean("ok", false),
            reason = json.optString("reason", ""),
            fingerprint = json.optString("fingerprint", ""),
            atMs = json.optLong("at", 0L)
        )
        verdict.takeIf { it.atMs > 0L && it.fingerprint == fingerprint(binary) }
    }.getOrNull()

    fun write(cacheFile: File, verdict: Verdict) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            SingBoxProcessSession.atomicWrite(
                cacheFile,
                JSONObject()
                    .put("ok", verdict.ok)
                    .put("reason", verdict.reason)
                    .put("fingerprint", verdict.fingerprint)
                    .put("at", verdict.atMs)
                    .toString()
            )
        }
    }
}
