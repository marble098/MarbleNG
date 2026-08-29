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
        val recipe = DpiEvasionPolicy.freedomRecipe(settings)
        val outbounds = JSONArray()

        val hasMiddle = recipe.middleEnabled || (settings.freedomMiddleEnabled && settings.freedomLayerCount >= 3)
        val middleTag = "middle-fragment"
        val innerTag = "full-fragment"

        val outerDialer = if (hasMiddle) middleTag else innerTag

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
                    JSONObject().put("dialerProxy", outerDialer)
                )
            )
        outbounds.put(outer)

        if (hasMiddle) {
            val middleFragment = fragmentObject(
                recipe.middlePackets.ifBlank { settings.freedomMiddlePackets.ifBlank { "1-3" } },
                recipe.middleLength.ifBlank { settings.freedomMiddleLength.ifBlank { "10-30" } },
                recipe.middleInterval.ifBlank { settings.freedomMiddleInterval.ifBlank { "5-10" } },
                recipe.middleMaxSplit.ifBlank { settings.freedomMiddleMaxSplit.ifBlank { "768" } }
            )
            val middle = JSONObject()
                .put("tag", middleTag)
                .put("protocol", "freedom")
                .put("settings", JSONObject().put("fragment", middleFragment))
                .put(
                    "streamSettings",
                    JSONObject().put(
                        "sockopt",
                        JSONObject().put("dialerProxy", innerTag)
                    )
                )
            outbounds.put(middle)
        }

        val innerFragment = fragmentObject(
            recipe.innerPackets.ifBlank { settings.freedomInnerPackets.ifBlank { "1-1" } },
            recipe.innerLength.ifBlank { settings.freedomInnerLength.ifBlank { "1" } },
            recipe.innerInterval.ifBlank { settings.freedomInnerInterval.ifBlank { "4" } },
            recipe.innerMaxSplit.ifBlank { settings.freedomInnerMaxSplit.ifBlank { "517" } }
        )
        val inner = JSONObject()
            .put("tag", innerTag)
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject()
                    .put("fragment", innerFragment)
                    .put("noises", udpNoises(settings))
            )
        outbounds.put(inner)

        return JSONObject()
            .put("outbounds", outbounds)
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
    private fun udpNoises(settings: AppSettings = AppSettings()): JSONArray {
        if (!settings.freedomUdpNoiseEnabled) return JSONArray()
        val array = JSONArray()
        val pkt4 = settings.freedomUdpNoisePacket4.ifBlank { "1250" }
        val del4 = settings.freedomUdpNoiseDelay4.ifBlank { "10" }
        val pkt6 = settings.freedomUdpNoisePacket6.ifBlank { "1230" }
        val del6 = settings.freedomUdpNoiseDelay6.ifBlank { "10" }

        val count = settings.freedomUdpNoiseCount.coerceIn(1, 10)
        for (i in 0 until count) {
            if (i % 2 == 0) {
                array.put(
                    JSONObject()
                        .put("type", "rand")
                        .put("packet", pkt4)
                        .put("delay", del4)
                        .put("applyTo", "ipv4")
                )
            } else {
                array.put(
                    JSONObject()
                        .put("type", "rand")
                        .put("packet", pkt6)
                        .put("delay", del6)
                        .put("applyTo", "ipv6")
                )
            }
        }
        if (array.length() == 1) {
            array.put(
                JSONObject()
                    .put("type", "rand")
                    .put("packet", pkt6)
                    .put("delay", del6)
                    .put("applyTo", "ipv6")
            )
        }
        return array
    }
}
