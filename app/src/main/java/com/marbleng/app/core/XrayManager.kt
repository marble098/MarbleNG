package com.marbleng.app.core

import android.content.Context
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.RoutingSpec
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

class XrayManager(
    private val context: Context
) {

    @Volatile
    private var process: Process? = null

    val isAlive: Boolean
        get() = process?.isAlive == true

    val logFile: File
        get() = File(context.filesDir, "logs/xray.log")

    private val bin: File
        get() = File(
            context.applicationInfo.nativeLibraryDir,
            "libxray.so"
        )

    private val assetsDir: File =
        File(context.filesDir, "xray-assets")

    private val runtimeConfig: File
        get() = File(context.filesDir, "runtime.json")

    /** Location of a geo asset (geoip.dat / geosite.dat) in Xray's asset dir. */
    fun assetFile(name: String): File {
        assetsDir.mkdirs()
        return File(assetsDir, name)
    }

    /** True when both routing databases are present and non-empty. */
    fun geoAssetsReady(): Boolean =
        assetFile("geoip.dat").length() > 0L && assetFile("geosite.dat").length() > 0L

    /** Human-readable summary of the installed geo assets. */
    fun assetInfo(): String {
        fun kb(f: File) = if (f.length() > 0) "${f.length() / 1024} KB" else "missing"
        return "geoip.dat ${kb(assetFile("geoip.dat"))} • geosite.dat ${kb(assetFile("geosite.dat"))}"
    }

    /**
     * Copies Xray geo assets from APK assets into app private storage.
     *
     * Xray uses XRAY_LOCATION_ASSET to locate these files.
     */
    fun prepareAssets() {
        assetsDir.mkdirs()

        listOf(
            "geoip.dat",
            "geosite.dat"
        ).forEach { name ->

            val destination = File(assetsDir, name)

            if (!destination.exists() || destination.length() <= 0L) {
                runCatching {
                    context.assets
                        .open("xray/$name")
                        .use { source ->

                            destination.outputStream().use { output ->
                                source.copyTo(output)
                            }
                        }
                }
            }
        }
    }

    /**
     * Creates a ProcessBuilder configured for Xray.
     *
     * Important:
     * XRAY_LOCATION_ASSET is configured for every Xray invocation,
     * including configuration validation and benchmark processes.
     */
    private fun createProcessBuilder(
        vararg args: String
    ): ProcessBuilder {

        val command = ArrayList<String>()

        command += bin.absolutePath
        command += args

        return ProcessBuilder(command).apply {

            redirectErrorStream(true)

            environment()["XRAY_LOCATION_ASSET"] =
                assetsDir.absolutePath
        }
    }

    /**
     * Start Xray using the supplied profile.
     *
     * Flow:
     * 1. Stop previous process.
     * 2. Prepare geo assets.
     * 3. Generate hardened runtime config.
     * 4. Run Xray config validation.
     * 5. Start actual Xray process.
     * 6. Wait until local SOCKS port is available.
     */
    @Synchronized
    fun start(
        profile: ProxyProfile,
        port: Int,
        routing: RoutingSpec? = null
    ): Boolean {

        stop()

        prepareAssets()

        if (!bin.exists()) {
            return false
        }

        logFile.parentFile?.mkdirs()

        val config = runtimeConfig

        return runCatching {

            config.writeText(
                XrayConfigHardener.harden(
                    profile.configJson,
                    port,
                    routing
                )
            )

            /*
             * First validate generated Xray configuration.
             *
             * Do not use ProcessBuilder.Redirect.DISCARD here.
             * Android does not expose it consistently.
             */
            val testProcess =
                createProcessBuilder(
                    "run",
                    "-test",
                    "-c",
                    config.absolutePath
                )
                    .redirectOutput(
                        ProcessBuilder.Redirect.appendTo(logFile)
                    )
                    .start()

            val testExitCode = testProcess.waitFor()

            if (testExitCode != 0) {
                return@runCatching false
            }

            /*
             * Start actual Xray process.
             *
             * stdout + stderr are appended to the Xray log.
             */
            val startedProcess =
                createProcessBuilder(
                    "run",
                    "-c",
                    config.absolutePath
                )
                    .redirectOutput(
                        ProcessBuilder.Redirect.appendTo(logFile)
                    )
                    .start()

            process = startedProcess

            /*
             * Xray process may technically start but fail before SOCKS
             * becomes ready, so verify the localhost listener.
             */
            if (!waitPort(port, 5_000L)) {

                stopProcess(startedProcess)

                if (process === startedProcess) {
                    process = null
                }

                false
            } else {
                true
            }

        }.getOrElse {

            process?.let {
                stopProcess(it)
            }

            process = null

            false
        }
    }

    /**
     * Stop currently active Xray process.
     *
     * We intentionally avoid an unlimited waitFor() because a broken native
     * process could otherwise freeze the caller indefinitely.
     */
    @Synchronized
    fun stop() {

        val current = process ?: return

        process = null

        stopProcess(current)
    }

    /**
     * Gracefully stop a process, then force-stop it when necessary.
     */
    private fun stopProcess(
        target: Process
    ) {

        runCatching {
            target.destroy()
        }

        /*
         * Give Xray a short opportunity to terminate normally.
         */
        val deadline =
            System.currentTimeMillis() + 1_500L

        while (
            target.isAlive &&
            System.currentTimeMillis() < deadline
        ) {
            try {
                Thread.sleep(50L)
            } catch (_: InterruptedException) {

                Thread.currentThread().interrupt()

                break
            }
        }

        /*
         * Native processes occasionally ignore normal destroy().
         */
        if (target.isAlive) {
            runCatching {
                target.destroyForcibly()
            }
        }

        /*
         * Final short wait, without risking an infinite block.
         */
        val forceDeadline =
            System.currentTimeMillis() + 750L

        while (
            target.isAlive &&
            System.currentTimeMillis() < forceDeadline
        ) {
            try {
                Thread.sleep(25L)
            } catch (_: InterruptedException) {

                Thread.currentThread().interrupt()

                break
            }
        }
    }

    /**
     * Starts a temporary Xray instance for benchmark/testing operations.
     *
     * This does not replace the primary Xray process.
     *
     * The temporary process gets its own configuration and log file.
     */
    fun temporary(
        profile: ProxyProfile,
        port: Int,
        block: (Int) -> Unit
    ): Boolean {

        prepareAssets()

        if (!bin.exists()) {
            return false
        }

        val config =
            File(
                context.cacheDir,
                "bench-$port.json"
            )

        val benchmarkLog =
            File(
                context.cacheDir,
                "xray-bench-$port.log"
            )

        return runCatching {

            config.writeText(
                XrayConfigHardener.harden(
                    profile.configJson,
                    port
                )
            )

            /*
             * Validate temporary config before starting benchmark process.
             */
            val testProcess =
                createProcessBuilder(
                    "run",
                    "-test",
                    "-c",
                    config.absolutePath
                )
                    .redirectOutput(
                        ProcessBuilder.Redirect.appendTo(
                            benchmarkLog
                        )
                    )
                    .start()

            if (testProcess.waitFor() != 0) {
                return@runCatching false
            }

            /*
             * Android-compatible replacement for Redirect.DISCARD.
             *
             * Writing to a private benchmark log means the child process
             * cannot block because stdout/stderr pipe buffers became full.
             */
            val temporaryProcess =
                createProcessBuilder(
                    "run",
                    "-c",
                    config.absolutePath
                )
                    .redirectOutput(
                        ProcessBuilder.Redirect.appendTo(
                            benchmarkLog
                        )
                    )
                    .start()

            try {

                if (!waitPort(port, 5_000L)) {

                    false

                } else {

                    block(port)

                    true
                }

            } finally {

                stopProcess(temporaryProcess)

                runCatching {
                    config.delete()
                }
            }

        }.getOrElse {

            runCatching {
                config.delete()
            }

            false
        }
    }

    /**
     * Wait for localhost TCP port to become available.
     */
    private fun waitPort(
        port: Int,
        timeoutMs: Long
    ): Boolean {

        val deadline =
            System.currentTimeMillis() + timeoutMs

        while (
            System.currentTimeMillis() < deadline
        ) {

            val connected =
                runCatching {

                    Socket().use { socket ->

                        socket.connect(
                            InetSocketAddress(
                                "127.0.0.1",
                                port
                            ),
                            200
                        )
                    }

                }.isSuccess

            if (connected) {
                return true
            }

            /*
             * If the main Xray process already died, don't waste the entire
             * timeout waiting for a port that can never open.
             */
            val current = process

            if (
                current != null &&
                !current.isAlive
            ) {
                return false
            }

            try {
                Thread.sleep(100L)
            } catch (_: InterruptedException) {

                Thread.currentThread().interrupt()

                return false
            }
        }

        return false
    }
}
