package com.marbleng.app.core

import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.AppSettings

enum class FamilyRung {
    AS_REQUESTED,

    IPV6_STRICT,

    IPV6_FIRST,

    IPV4_FIRST,

    REFUSED
}

data class FamilyEvidence(
    val underlayHasIpv6: Boolean,
    val nodeHasIpv6: Boolean? = null,
    val nodeIpv6Proven: Boolean? = null,
    val nodeHasIpv4: Boolean? = null,
    val nodeIsIpv4Literal: Boolean = false,
    val nodeIsIpv6Literal: Boolean = false
) {
    val ipv6Impossible: Boolean get() = nodeIsIpv4Literal || nodeHasIpv6 == false

    val ipv4Impossible: Boolean get() = nodeIsIpv6Literal || nodeHasIpv4 == false
}

data class FamilyResolution(
    val requested: AddressFamilyMode,
    val effective: AddressFamilyMode,
    val rung: FamilyRung,
    val code: String,
    val headline: String,
    val advice: String = "",
    val refusal: String? = null
) {
    val degraded: Boolean get() = effective != requested && refusal == null
    val blocked: Boolean get() = refusal != null

    val trace: String
        get() = if (effective == requested) {
            "${requested.name.lowercase()} • $code"
        } else {
            "${requested.name.lowercase()}→${effective.name.lowercase()} • $code"
        }
}

object Ipv6FallbackLadder {
    const val STRICT_NODE_REFUSAL: String =
        "Force IPv6 is set to strict and this server has no IPv6 address. " +
            "Turn off strict IPv6 to dial it over IPv4 while IPv6 stays on for destinations, " +
            "or pick a server the family scan marked IPv6-capable."

    const val STRICT_UNDERLAY_REFUSAL: String =
        "Force IPv6 is set to strict and this network has no IPv6 route. " +
            "Turn off strict IPv6 to keep browsing over IPv6 through an IPv4-dialled server, " +
            "or join a network that provides IPv6."

    const val IPV6_LITERAL_NO_UNDERLAY: String =
        "This server has only an IPv6 address and this network has no IPv6 route. " +
            "Join an IPv6-capable network or pick a server with an IPv4 address."

    fun resolve(
        requested: AddressFamilyMode,
        evidence: FamilyEvidence,
        strict: Boolean = false
    ): FamilyResolution = when (requested) {
        AddressFamilyMode.FORCE_IPV6 -> resolveForceIpv6(evidence, strict)
        AddressFamilyMode.FORCE_IPV4 -> resolveForceIpv4(evidence, strict)
        AddressFamilyMode.PREFER_IPV6 -> resolvePreferIpv6(evidence)
        else -> unchanged(requested, "as-requested", "Address family follows the selected mode")
    }

    private fun resolveForceIpv6(evidence: FamilyEvidence, strict: Boolean): FamilyResolution {
        if (evidence.nodeIsIpv6Literal && !evidence.underlayHasIpv6) {
            return FamilyResolution(
                requested = AddressFamilyMode.FORCE_IPV6,
                effective = AddressFamilyMode.FORCE_IPV6,
                rung = FamilyRung.REFUSED,
                code = "v6-literal-no-underlay",
                headline = "This server is IPv6-only and the network has no IPv6 route",
                advice = "Pick a server with an IPv4 address, or join an IPv6-capable network",
                refusal = IPV6_LITERAL_NO_UNDERLAY
            )
        }

        if (evidence.underlayHasIpv6 && !evidence.ipv6Impossible && evidence.nodeIpv6Proven != false) {
            return FamilyResolution(
                requested = AddressFamilyMode.FORCE_IPV6,
                effective = AddressFamilyMode.FORCE_IPV6,
                rung = FamilyRung.IPV6_STRICT,
                code = if (evidence.nodeIpv6Proven == true) "v6-proven" else "v6-available",
                headline = "IPv6 end to end",
                advice = "Node and network both carry IPv6"
            )
        }

        if (evidence.underlayHasIpv6 && !evidence.ipv6Impossible) {
            if (strict) {
                return strictRefusal(
                    code = "v6-unproven-strict",
                    headline = "The node's IPv6 address did not answer and strict IPv6 is on",
                    refusal = STRICT_NODE_REFUSAL
                )
            }
            return FamilyResolution(
                requested = AddressFamilyMode.FORCE_IPV6,
                effective = AddressFamilyMode.PREFER_IPV6,
                rung = FamilyRung.IPV6_FIRST,
                code = "v6-unproven",
                headline = "IPv6 first, IPv4 held in reserve",
                advice = "This node's IPv6 path has not answered yet, so IPv4 stays armed behind it"
            )
        }

        if (strict) {
            return if (evidence.underlayHasIpv6) {
                strictRefusal(
                    code = "node-v4-only-strict",
                    headline = "This server has no IPv6 address and strict IPv6 is on",
                    refusal = STRICT_NODE_REFUSAL
                )
            } else {
                strictRefusal(
                    code = "underlay-v4-only-strict",
                    headline = "This network has no IPv6 route and strict IPv6 is on",
                    refusal = STRICT_UNDERLAY_REFUSAL
                )
            }
        }
        val nodeIsTheLimit = evidence.ipv6Impossible
        return FamilyResolution(
            requested = AddressFamilyMode.FORCE_IPV6,
            effective = AddressFamilyMode.PREFER_IPV6,
            rung = FamilyRung.IPV4_FIRST,
            code = if (nodeIsTheLimit) "node-v4-only" else "underlay-v4-only",
            headline = if (nodeIsTheLimit) {
                "Server dialled over IPv4 • IPv6 stays on for destinations"
            } else {
                "Network has no IPv6 route • server dialled over IPv4"
            },
            advice = if (nodeIsTheLimit) {
                "Scan the group to find IPv6-capable servers; IPv6 sites still work through this one"
            } else {
                "IPv6 returns by itself on a v6-capable network; nothing to re-enable"
            }
        )
    }

    private fun resolveForceIpv4(evidence: FamilyEvidence, strict: Boolean): FamilyResolution {
        if (!evidence.ipv4Impossible) {
            return unchanged(
                AddressFamilyMode.FORCE_IPV4,
                "v4-available",
                "IPv4 only, as requested"
            )
        }
        if (strict || !evidence.underlayHasIpv6) {
            return FamilyResolution(
                requested = AddressFamilyMode.FORCE_IPV4,
                effective = AddressFamilyMode.FORCE_IPV4,
                rung = FamilyRung.REFUSED,
                code = if (strict) "node-v6-only-strict" else "node-v6-only-no-underlay",
                headline = "This server has no IPv4 address",
                advice = "Pick a server with an IPv4 address, or allow IPv6 in Settings",
                refusal = AddressFamilyPolicy.IPV6_LITERAL_DISABLED
            )
        }
        return FamilyResolution(
            requested = AddressFamilyMode.FORCE_IPV4,
            effective = AddressFamilyMode.PREFER_IPV4,
            rung = FamilyRung.IPV4_FIRST,
            code = "node-v6-only",
            headline = "This server is IPv6-only • IPv4 stays preferred everywhere else",
            advice = "Force IPv4 is kept as a preference for this session so the node can be dialled"
        )
    }

    private fun resolvePreferIpv6(evidence: FamilyEvidence): FamilyResolution {
        val rung = when {
            evidence.underlayHasIpv6 && !evidence.ipv6Impossible -> FamilyRung.IPV6_FIRST
            else -> FamilyRung.IPV4_FIRST
        }
        return FamilyResolution(
            requested = AddressFamilyMode.PREFER_IPV6,
            effective = AddressFamilyMode.PREFER_IPV6,
            rung = rung,
            code = when {
                rung == FamilyRung.IPV6_FIRST -> "prefer-v6"
                evidence.ipv6Impossible -> "prefer-v6-node-v4-only"
                else -> "prefer-v6-underlay-v4-only"
            },
            headline = if (rung == FamilyRung.IPV6_FIRST) {
                "IPv6 preferred for this node"
            } else {
                "IPv4 opens this node • IPv6 stays preferred for destinations"
            }
        )
    }

    private fun strictRefusal(code: String, headline: String, refusal: String) = FamilyResolution(
        requested = AddressFamilyMode.FORCE_IPV6,
        effective = AddressFamilyMode.FORCE_IPV6,
        rung = FamilyRung.REFUSED,
        code = code,
        headline = headline,
        advice = "Turn off strict IPv6 in Settings → DNS & IP version to let Marble fall back",
        refusal = refusal
    )

    private fun unchanged(mode: AddressFamilyMode, code: String, headline: String) =
        FamilyResolution(
            requested = mode,
            effective = mode,
            rung = FamilyRung.AS_REQUESTED,
            code = code,
            headline = headline
        )

    fun apply(settings: AppSettings, resolution: FamilyResolution): AppSettings {
        if (resolution.effective == settings.addressFamilyMode) return settings
        val mode = resolution.effective
        return settings.copy(
            addressFamilyMode = mode,
            ipv6Enabled = mode != AddressFamilyMode.FORCE_IPV4,
            preferIpv6 = mode == AddressFamilyMode.SMART ||
                mode == AddressFamilyMode.PREFER_IPV6 ||
                mode == AddressFamilyMode.FORCE_IPV6,
            dnsQueryStrategy = when (mode) {
                AddressFamilyMode.FORCE_IPV4 -> "UseIPv4"
                AddressFamilyMode.FORCE_IPV6 -> "UseIPv6"
                else -> "UseIP"
            }
        )
    }

    fun evidenceFor(
        host: String,
        scan: IpFamilyScan?,
        underlayHasIpv6: Boolean
    ): FamilyEvidence {
        val clean = host.trim().removeSurrounding("[", "]")
        val literal = AddressFamilyPolicy.isLiteralIp(clean)
        val v6Literal = literal && clean.contains(':')
        val v4Literal = literal && !clean.contains(':')

        val trustedScan = scan?.takeIf { it.confidence >= 20 || it.confidence == 0 && it.scannedAtMs > 0L }

        val effectiveScan = when {
            scan == null -> null
            scan.confidence == 0 && scan.scannedAtMs > 0L -> scan
            scan.confidence < 20 -> null
            else -> scan
        }
        val hasIpv6 = when {
            v6Literal -> true
            v4Literal -> false
            effectiveScan != null -> effectiveScan.hasIpv6
            else -> null
        }
        val ipv6Proven = when {
            effectiveScan == null -> null
            effectiveScan.nat64Detected -> false
            effectiveScan.ipv6Ok -> true
            effectiveScan.hasIpv6 -> false
            else -> null
        }
        return FamilyEvidence(
            underlayHasIpv6 = underlayHasIpv6,
            nodeHasIpv6 = hasIpv6,
            nodeIpv6Proven = ipv6Proven,
            nodeHasIpv4 = when {
                v4Literal -> true
                v6Literal -> false
                effectiveScan != null -> effectiveScan.hasIpv4
                else -> null
            },
            nodeIsIpv4Literal = v4Literal,
            nodeIsIpv6Literal = v6Literal
        )
    }
}
