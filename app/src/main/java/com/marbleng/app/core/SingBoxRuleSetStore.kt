package com.marbleng.app.core

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** APK assets are the startup authority; an offline first install must never contact GitHub.
 * Extraction is checksum-verified and atomic, including after a package update or partial write. */
class SingBoxRuleSetStore(private val context: Context) {
    @Synchronized fun prepare(): Map<String, String> {
        val manifest = context.assets.open("singbox/manifest.json").bufferedReader().use { JSONObject(it.readText()) }
        val directory = File(context.filesDir, "singbox-rules").apply { mkdirs() }
        val entries = manifest.getJSONArray("rules")
        val paths = linkedMapOf<String, String>()
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val name = entry.getString("file")
            require(name.matches(Regex("[a-z0-9-]+\\.srs"))) { "Unsafe bundled rule set name" }
            val target = File(directory, name)
            val expectedSize = entry.getLong("size")
            val expectedHash = entry.getString("sha256")
            fun valid(file: File): Boolean = file.isFile && file.length() == expectedSize && sha256(file) == expectedHash
            if (!valid(target)) {
                val staging = File(directory, "$name.tmp")
                try {
                    context.assets.open("singbox/$name").use { input ->
                        staging.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= expectedSize && total <= 2 * 1024 * 1024) { "Oversized bundled rule set" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    require(valid(staging)) { "Bundled rule set checksum mismatch: $name" }
                    check(staging.renameTo(target)) { "Cannot install bundled rule set: $name" }
                } finally { staging.delete() }
            }
            paths[entry.getString("tag")] = target.absolutePath
        }
        return paths
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
