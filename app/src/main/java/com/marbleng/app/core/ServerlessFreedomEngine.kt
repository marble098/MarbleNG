package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Built-in Xray Freedom fragment profile. There is no remote proxy and no anonymity:
 * filtered HTTPS is reached by shredding the local TLS ClientHello / first writes so Iranian
 * DPI cannot reassemble SNI. Identity Guard still pins the (user) exit IP.
 *
 * Template follows the official XTLS `serverless_for_Iran.jsonc` chain
 * (tlshello length 6 interval 0 → full-fragment 1-1/1/4 maxSplit 517 + UDP noises)
 * while emitting `freedom`/`blackhole` for the locked Xray v26.7.28 core.
 */
object ServerlessFreedomEngine {
    const val PROFILE_ID = "marble-serverless-freedom"
    const val SOURCE_ID = "marble-serverless"
    const val DISPLAY_NAME = "Marble Freedom"

    fun matches(id: String, sourceId: String? = null): Boolean {
        if (id != PROFILE_ID) return false
        return sourceId.isNullOrBlank() || sourceId == SOURCE_ID
    }

    fun isServerless(profile: ProxyProfile): Boolean =
        matches(profile.id, profile.subscriptionId) || profile.scheme.equals("freedom", true)

    fun profile(settings: AppSettings = AppSettings()): ProxyProfile = ProxyProfile(
        id = PROFILE_ID,
        name = DISPLAY_NAME,
        scheme = "freedom",
        raw = "freedom://fragment",
        configJson = configJson(settings),
        host = "",
        port = 443,
        transport = "fragment",
        security = "tlshello",
        subscriptionId = SOURCE_ID,
        subscriptionName = "Marble",
        sourceManaged = true
    )

    fun configJson(settings: AppSettings = AppSettings()): String {
        val recipe = DpiEvasionPolicy.recipeFrom(settings).let { current ->
            if (current.innerEnabled) current else DpiEvasionPolicy.TLSHELLO_SNI
        }
        val outer = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject().put(
                    "fragment",
                    fragmentObject(
                        recipe.packets.ifBlank { "tlshello" },
                        recipe.length.ifBlank { "6" },
                        recipe.interval.ifBlank { "0" },
                        recipe.maxSplit
                    )
                )
            )
            .put(
                "streamSettings",
                JSONObject().put(
                    "sockopt",
                    JSONObject().put("dialerProxy", "full-fragment")
                )
            )

        val innerFragment = fragmentObject(
            recipe.innerPackets.ifBlank { "1-1" },
            recipe.innerLength.ifBlank { "1" },
            recipe.innerInterval.ifBlank { "4" },
            recipe.innerMaxSplit.ifBlank { "517" }
        )
        val inner = JSONObject()
            .put("tag", "full-fragment")
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject()
                    .put("fragment", innerFragment)
                    .put("noises", udpNoises())
            )

        return JSONObject()
            .put("outbounds", JSONArray().put(outer).put(inner))
            .toString()
    }

    fun pinSession(settings: AppSettings): AppSettings = settings.copy(
        continuousOptimizerEnabled = false,
        smartFallbackEnabled = false,
        identityGuardEnabled = true,
        identityGuardStrictNoFailover = true,
        fragmentEnabled = true,
        adaptiveFragmentEnabled = true,
        fragmentInnerEnabled = true
    )

    private fun fragmentObject(
        packets: String,
        length: String,
        interval: String,
        maxSplit: String
    ): JSONObject {
        val fragment = JSONObject()
            .put("packets", packets)
            .put("length", length)
            .put("interval", interval)
        val split = maxSplit.trim()
        if (split.isNotBlank()) {
            split.toIntOrNull()?.let { fragment.put("maxSplit", it) }
                ?: fragment.put("maxSplit", split)
        }
        return fragment
    }

    /** UDP noise skips port 53 inside Xray; used to pad QUIC-shaped datagrams on IPv4/IPv6. */
    private fun udpNoises(): JSONArray = JSONArray()
        .put(
            JSONObject()
                .put("type", "rand")
                .put("packet", "1250")
                .put("delay", "10")
                .put("applyTo", "ipv4")
        )
        .put(
            JSONObject()
                .put("type", "rand")
                .put("packet", "1230")
                .put("delay", "10")
                .put("applyTo", "ipv6")
        )
}
