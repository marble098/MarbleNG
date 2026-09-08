package com.marbleng.app.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * MARBLE_SINGBOX_ANDROID_CLI_CRASH_V157 — one question, asked of the binary instead of of a
 * server: **can this core start at all on this device?**
 *
 * ## Why this exists
 *
 * Three releases shipped an Android sing-box artifact that died with a Go panic in
 * `protocol/direct/outbound.go` for every profile, because an app-UID child gets no netlink
 * interface monitor and every MarbleNG config carries a `direct` outbound. Nothing in the product
 * could tell that apart from a dead network, so the honest-looking answers piled up:
 *
 * ```
 * core-start: exited 2: … WARN network: initialize package manager: read packages list: open
 *     /data/system/packages.xml: permission denied
 *     panic: runtime error: invalid memory address or nil pointer dereference
 *     [signal SIGSEGV: segmentation violation code=0x1 addr=0x30 pc=0x…]
 * state=BLOCKED • Core/configuration error
 * URL test  → reachable=0 of 17
 * Real delay → reachable=0 of 17
 * Bug Finder (DISCONNECTED) → failures=0
 * ```
 *
 * Seventeen servers did not go down. One binary could not run, and every surface that measured
 * through it reported the local fault as a remote verdict.
 *
 * ## What the canary is
 *
 * The smallest config that still walks the whole start-up path the product depends on: a local
 * `mixed` inbound (what hev-socks5-tunnel dials), a `direct` outbound (the component that
 * crashed, and the one bypass routing needs), and `route.final` pointing at it. It resolves no
 * domain, opens no outbound socket, downloads no rule set and starts no controller, so it cannot
 * produce traffic, cannot be blocked by a network, and cannot be answered by a server. Whatever it
 * reports is a property of the binary and the device.
 *
 * It is deliberately *not* a production config: adding DNS transports, rule sets or the Clash
 * controller would add ways for the canary to fail that have nothing to do with the question it
 * asks, and a false "the core is broken" is worse than no canary at all.
 *
 * Having no `dns` section at all is safe on the pinned core, and that is worth writing down
 * because the same 1.14 release makes a *missing* `route.default_domain_resolver` fatal: the
 * deprecation fires when a dialer has to resolve a domain **and** more than one DNS transport
 * exists (MARBLE_SINGBOX_ANDROID_RUNTIME_V155 §2). The canary resolves nothing and configures no
 * transport, so neither half of that condition holds — and [SingBoxAndroidRuntime.prepare] exports
 * every `ENABLE_DEPRECATED_*` flag anyway, so a deprecation MarbleNG has not met yet degrades to a
 * log line instead of ending the verdict. A canary the core refused for a schema reason would
 * report `core-unavailable:` forever and never once see the crash it exists to detect.
 *
 * ## What the verdict is used for
 *
 *  - [SingBoxManager] refuses to spawn a known-dead core for a connect or a measurement, so one
 *    broken binary costs one process, not three readers × seventeen nodes × two retries;
 *  - the measurement plane reports `core-crash:` — a local fault — instead of a latency of zero,
 *    and [ProbeLocalFaultGate] stops a sweep after the first one;
 *  - Bug Finder prints the verdict whether or not a session is live, so a scan in DISCONNECTED can
 *    no longer return `failures=0` on a device whose core cannot start.
 *
 * A verdict is only ever *acted on* when it is a core fault ([Verdict.coreFault]). A canary that
 * fails because the loopback port was taken, or because the device was out of memory, is recorded
 * and reported but never blocks the engine: that would be the canary inventing an outage of its
 * own.
 */
object SingBoxCoreSelfTest {

    /** How long the canary waits for the core to come up, and then to stay up. */
    const val STARTUP_TIMEOUT_MS: Long = 8_000L

    /**
     * The grace window after the inbound answers. `box.Start()` opens inbounds at
     * `StartStateStart` and walks outbounds through `StartStatePostStart` afterwards, so the
     * crashing component runs *after* the port is already listening; without this window the
     * canary could report PASS microseconds before the panic. See
     * [SingBoxProcessSession.open]'s `settleMs`.
     */
    const val SETTLE_MS: Long = 700L

    /**
     * One answer about one binary.
     *
     * [fingerprint] is what makes the verdict cacheable: the same bytes in the same place answer
     * the same way, so an APK update (which changes the file's size and mtime) invalidates it and
     * a restart does not.
     */
    data class Verdict(
        val ok: Boolean,
        val reason: String,
        val fingerprint: String,
        val atMs: Long
    ) {
        /**
         * True when the verdict is a fault no node, no configuration and no network could have
         * changed — a crash, the app-UID package-manager probe, the netlink ban. This is the only
         * verdict the product is allowed to short-circuit on.
         */
        val coreFault: Boolean get() = !ok && SingBoxAndroidRuntime.isUnusableCore(reason)

        /** One line for the Engine page and Bug Finder. */
        val summary: String
            get() = when {
                ok -> "core starts and serves its local inbound"
                coreFault -> "core cannot start on this device"
                else -> "core self-test inconclusive"
            }

        /** The reason prefixed with the token the rest of the product classifies on. */
        fun asFailureReason(): String =
            if (coreFault) "core-crash: $reason" else "core-unavailable: $reason"
    }

    /**
     * The canary document. Same inbound type, same `direct` outbound and same `route.final`
     * contract the production writer emits — nothing the pinned core has not already been asked
     * to parse — and nothing that can touch a network.
     */
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
                    // The component that crashed on Android, and the one every bypass rule needs.
                    JSONObject()
                        .put("type", "direct")
                        .put("tag", SingBoxConfigBuilder.DIRECT_TAG)
                )
            )
            .put("route", JSONObject().put("final", SingBoxConfigBuilder.DIRECT_TAG))
            .toString()
    }

    /** Identity of the binary the verdict is about. Cheap, and changes on every APK update. */
    fun fingerprint(binary: File): String =
        "${binary.absolutePath}|${binary.length()}|${binary.lastModified()}"

    /**
     * Runs the canary once and reports what happened. Never throws: an unreadable binary, a
     * cancelled probe and a panic are all answers, and the caller decides what each one means.
     */
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
                // No controller and no separate `check` spawn: the canary asks about start-up,
                // and both would only add a second way to fail.
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

    /** The cached verdict for [binary], or `null` when there is none or it belongs to another build. */
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

    /** Persists a verdict. Best-effort: a read-only cache dir costs one extra canary, nothing more. */
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
