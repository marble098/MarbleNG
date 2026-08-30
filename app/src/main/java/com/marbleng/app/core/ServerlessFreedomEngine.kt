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
 * (packet-split outer → full-fragment 1-1/1/4 maxSplit 517 + dedicated UDP-noises outbound)
 * while emitting `freedom`/`blackhole` for the locked Xray v26.7.28 core.
 *
 * Why YouTube / X / Reddit previously failed or loaded half-broken on Freedom:
 *  - an untested middle hop slowed the first flight past CDN idle cutoffs
 *  - UDP/QUIC (YouTube media, X live) stalled while apps waited on blocked UDP/443;
 *    Marble now defaults to TCP fallback and keeps dedicated noises as an expert option
 *  - poisoned injector ranges were not blocked, so half-resolved multi-CDN hosts stuck
 */
object ServerlessFreedomEngine {
    const val PROFILE_ID = "marble-serverless-freedom"
    const val SOURCE_ID = "marble-serverless"
    const val DISPLAY_NAME = "Marble Freedom"
    const val UDP_NOISES_TAG = "udp-noises"
    const val INNER_TAG = "full-fragment"
    const val MIDDLE_TAG = "middle-fragment"

    fun matches(id: String, sourceId: String? = null): Boolean {
        if (id != PROFILE_ID) return false
        return sourceId.isNullOrBlank() || sourceId == SOURCE_ID
    }

    fun isServerless(profile: ProxyProfile): Boolean =
        matches(profile.id, profile.subscriptionId) || profile.scheme.equals("freedom", true)

    fun profile(
        settings: AppSettings = AppSettings(),
        iranMode: IranModeState = IranModeState()
    ): ProxyProfile = ProxyProfile(
        id = PROFILE_ID,
        name = DISPLAY_NAME,
        scheme = "freedom",
        raw = "freedom://fragment",
        configJson = configJson(settings, iranMode),
        host = "",
        port = 443,
        transport = "fragment",
        security = "tlshello",
        subscriptionId = SOURCE_ID,
        subscriptionName = "Marble",
        sourceManaged = true
    )

    fun configJson(
        settings: AppSettings = AppSettings(),
        iranMode: IranModeState = IranModeState()
    ): String {
        val recipe = DpiEvasionPolicy.freedomRecipe(settings, iranMode)
        val outbounds = JSONArray()

        // Official XTLS is a 2-hop chain (outer → full-fragment). A middle hop is only emitted
        // when the active recipe (or an explicit Custom preset) asks for it — never by stale
        // freedomMiddleEnabled leftovers from an older install.
        val effectiveMiddle = recipe.middleEnabled

        val outerDialer = if (effectiveMiddle) MIDDLE_TAG else INNER_TAG

        val outer = JSONObject()
            .put("tag", "proxy")
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject().put(
                    "fragment",
                    fragmentObject(
                        recipe.packets.ifBlank { "1-1" },
                        recipe.length.ifBlank { "1-3" },
                        recipe.interval.ifBlank { "5-10" },
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

        if (effectiveMiddle) {
            val middleFragment = fragmentObject(
                recipe.middlePackets.ifBlank { settings.freedomMiddlePackets.ifBlank { "1-3" } },
                recipe.middleLength.ifBlank { settings.freedomMiddleLength.ifBlank { "10-30" } },
                recipe.middleInterval.ifBlank { settings.freedomMiddleInterval.ifBlank { "5-10" } },
                recipe.middleMaxSplit.ifBlank { settings.freedomMiddleMaxSplit.ifBlank { "768" } }
            )
            val middle = JSONObject()
                .put("tag", MIDDLE_TAG)
                .put("protocol", "freedom")
                .put("settings", JSONObject().put("fragment", middleFragment))
                .put(
                    "streamSettings",
                    JSONObject().put(
                        "sockopt",
                        JSONObject().put("dialerProxy", INNER_TAG)
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
        // TCP hop: fragment only. UDP noises live on a dedicated outbound (official XTLS shape)
        // so ordinary TCP is not padded. Routing either rejects QUIC for TCP fallback (default)
        // or sends UDP/443 to the noises outbound when the user explicitly allows it.
        val inner = JSONObject()
            .put("tag", INNER_TAG)
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject().put("fragment", innerFragment)
            )
        outbounds.put(inner)

        if (settings.freedomUdpNoiseEnabled) {
            outbounds.put(udpNoisesOutbound(settings))
        }

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
        fragmentInnerEnabled = true,
        // Freedom must always hijack classic DNS; without it the poisoned ISP resolver wins.
        dnsHijackEnabled = true,
        freedomDnsHijack = true
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

    /**
     * Dedicated UDP-noises outbound matching XTLS Serverless-for-Iran.
     * Routed only for QUIC and UDP/443 by [XrayConfigHardener] when TCP fallback is off; never
     * attached to the TCP hop.
     * NoisePacketWriter skips port 53 internally.
     */
    private fun udpNoisesOutbound(settings: AppSettings): JSONObject {
        val pkt4 = settings.freedomUdpNoisePacket4.ifBlank { "1250" }
        val del4 = settings.freedomUdpNoiseDelay4.ifBlank { "10" }
        val pkt6 = settings.freedomUdpNoisePacket6.ifBlank { "1230" }
        val del6 = settings.freedomUdpNoiseDelay6.ifBlank { "10" }
        // Official ships ~13 IPv4 + ~13 IPv6 entries. A smaller burst still defeats
        // stateful UDP trackers; cap at 16 total for mobile battery.
        val pairs = settings.freedomUdpNoiseCount.coerceIn(2, 16)
        val array = JSONArray()
        repeat(pairs) {
            array.put(
                JSONObject()
                    .put("type", "rand")
                    .put("packet", pkt4)
                    .put("delay", del4)
                    .put("applyTo", "ipv4")
            )
        }
        repeat(pairs) {
            array.put(
                JSONObject()
                    .put("type", "rand")
                    .put("packet", pkt6)
                    .put("delay", del6)
                    .put("applyTo", "ipv6")
            )
        }
        return JSONObject()
            .put("tag", UDP_NOISES_TAG)
            .put("protocol", "freedom")
            .put(
                "settings",
                JSONObject()
                    .put("domainStrategy", "UseIP")
                    .put("noises", array)
            )
    }
}
