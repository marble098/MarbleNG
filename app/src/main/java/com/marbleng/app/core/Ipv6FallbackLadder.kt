package com.marbleng.app.core

import com.marbleng.app.model.AddressFamilyMode
import com.marbleng.app.model.AppSettings

/**
 * MARBLE_IPV6_FALLBACK_LADDER_V196 — how an IPv6-forward client stays connected.
 *
 * ## The failure this replaces
 *
 * A real session log: Force IPv6 on, a library whose nodes are overwhelmingly IPv4-only, and a
 * Wi-Fi with no IPv6 route at all. Every connect attempt was refused before the tunnel even
 * started — `AddressFamilyPolicy.excludedIpv4Endpoint` said "this node has only an IPv4 address",
 * the service called `failBeforeTunnel`, the kill switch held the old TUN, and the user read
 * `BLOCKED • Kill switch active` on server after server (Germany 2/3/4/5, Turkey 1/6/8, mci,
 * Netherlands 3) until one node happened to have an AAAA record. Minutes of outage, produced
 * entirely by the app's own policy rather than by any network.
 *
 * The old behaviour was not wrong about the *facts* — a v4-only node genuinely cannot be dialled
 * over IPv6 — it was wrong about the *response*. "Fail closed" is the correct answer for a leak
 * (never send traffic outside the tunnel) and the wrong answer for a transport preference
 * (never refuse to build the tunnel at all). A preference that cannot be honoured must degrade
 * along a defined ladder, loudly and reversibly; it must not become an outage.
 *
 * ## The ladder
 *
 * Each rung is tried in order, and the first one the evidence supports wins:
 *
 *  1. [FamilyRung.IPV6_STRICT] — the node answers over IPv6 and the underlay carries IPv6.
 *     Force IPv6 is honoured exactly as asked: v6 transport, no v4 dial.
 *  2. [FamilyRung.IPV6_FIRST] — the node publishes AAAA but IPv6 is unproven or measured shaky
 *     here. IPv6 is still dialled first; IPv4 stays armed behind it, so a broken v6 path costs a
 *     race delay instead of the whole session.
 *  3. [FamilyRung.IPV4_FIRST] — the node is IPv4-only, or this network has no IPv6 route at all.
 *     The *node socket* goes over IPv4 while IPv6 stays fully enabled for everything the tunnel
 *     carries: an IPv4-reachable exit still serves IPv6 destinations, which is the whole reason
 *     "Force IPv6" was never a statement about the transport in the first place.
 *  4. [FamilyRung.REFUSED] — the only remaining rung is one the user explicitly demanded stay
 *     closed (strict enforcement), or the node is literally unreachable in the only family it has
 *     (an IPv6-literal node on a network without IPv6). A refusal at this point is honest, and it
 *     always carries the concrete alternative instead of a dead end.
 *
 * ## Why the mode, not a patch at the refusal site
 *
 * The decision is expressed as a *different [AddressFamilyMode]* for this one session rather than
 * as a special case inside the connect path, because seven subsystems read the family mode (the
 * Xray sockopt writer, the DNS writer, the sing-box dialer, the preflight validator, the config
 * builder's `require`, the JVM probers and Bug Finder). Patching one of them would have produced
 * exactly the class of bug this project keeps fixing: a policy that one reader honours and six
 * others do not. Rewriting the mode once, in [AppRepository.effectiveSettingsFor], means every
 * reader agrees — including the two `require` calls that used to throw.
 *
 * Nothing here is persisted: the ladder is re-evaluated for every connect, so plugging in a v6
 * network or scanning a v6-capable node restores strict Force IPv6 by itself, with nothing for
 * the user to re-enable.
 */
enum class FamilyRung {
    /** The requested mode needed no adjustment. */
    AS_REQUESTED,

    /** Force IPv6 honoured: IPv6 transport, no IPv4 dial. */
    IPV6_STRICT,

    /** IPv6 dialled first with IPv4 armed behind it. */
    IPV6_FIRST,

    /** IPv4 carries the node socket; IPv6 stays enabled for destinations. */
    IPV4_FIRST,

    /** Nothing can carry this node under the user's own explicit demand. */
    REFUSED
}

/**
 * Everything the ladder is allowed to know. All of it is measurable, and every field has an
 * explicit "not known yet" state so a missing measurement can never masquerade as a negative one.
 */
data class FamilyEvidence(
    /** A global IPv6 address exists on a real interface right now. */
    val underlayHasIpv6: Boolean,
    /** The node publishes an AAAA record (or is a v6 literal). `null` = never measured. */
    val nodeHasIpv6: Boolean? = null,
    /** An IPv6 connect to the node actually succeeded. `null` = never measured. */
    val nodeIpv6Proven: Boolean? = null,
    /** The node publishes an A record (or is a v4 literal). `null` = never measured. */
    val nodeHasIpv4: Boolean? = null,
    /** The configured host is an IPv4 literal: no AAAA can ever appear for it. */
    val nodeIsIpv4Literal: Boolean = false,
    /** The configured host is an IPv6 literal: it has no IPv4 path at all. */
    val nodeIsIpv6Literal: Boolean = false
) {
    /** True only when something positively proves the node cannot be dialled over IPv6. */
    val ipv6Impossible: Boolean get() = nodeIsIpv4Literal || nodeHasIpv6 == false

    /** True only when something positively proves the node cannot be dialled over IPv4. */
    val ipv4Impossible: Boolean get() = nodeIsIpv6Literal || nodeHasIpv4 == false
}

/** The ladder's answer for one connect attempt. */
data class FamilyResolution(
    val requested: AddressFamilyMode,
    val effective: AddressFamilyMode,
    val rung: FamilyRung,
    /** Stable machine code for diagnostics: `v6-proven`, `node-v4-only`, `underlay-v4-only`, … */
    val code: String,
    /** One sentence for the user, naming what happened. */
    val headline: String,
    /** What the app is doing about it, or what the user can do. */
    val advice: String = "",
    /** Non-null only on [FamilyRung.REFUSED]; this is the sentence the connect path reports. */
    val refusal: String? = null
) {
    val degraded: Boolean get() = effective != requested && refusal == null
    val blocked: Boolean get() = refusal != null

    /** Compact diagnostics line: `force_ipv6→prefer_ipv6 • node-v4-only`. */
    val trace: String
        get() = if (effective == requested) {
            "${requested.name.lowercase()} • $code"
        } else {
            "${requested.name.lowercase()}→${effective.name.lowercase()} • $code"
        }
}

object Ipv6FallbackLadder {

    /** What a v4-only node does to a strict Force IPv6 demand, when the user insists on strict. */
    const val STRICT_NODE_REFUSAL: String =
        "Force IPv6 is set to strict and this server has no IPv6 address. " +
            "Turn off strict IPv6 to dial it over IPv4 while IPv6 stays on for destinations, " +
            "or pick a server the family scan marked IPv6-capable."

    /** What a network without IPv6 does to a strict Force IPv6 demand. */
    const val STRICT_UNDERLAY_REFUSAL: String =
        "Force IPv6 is set to strict and this network has no IPv6 route. " +
            "Turn off strict IPv6 to keep browsing over IPv6 through an IPv4-dialled server, " +
            "or join a network that provides IPv6."

    /** An IPv6-literal node on a network without IPv6: no rung can carry it. */
    const val IPV6_LITERAL_NO_UNDERLAY: String =
        "This server has only an IPv6 address and this network has no IPv6 route. " +
            "Join an IPv6-capable network or pick a server with an IPv4 address."

    /**
     * Resolve the family mode this connect attempt should actually run with.
     *
     * @param strict the user's explicit "never silently change my family" switch
     *   ([AppSettings.strictAddressFamily]). Off by default: a preference that turns into an
     *   outage is a bug, not a security property.
     */
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
        // Rung 0 — an IPv6-literal node is the pure case, and the only one that can still be a
        // genuine dead end: it has no IPv4 path to degrade onto.
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

        // Rung 1 — strict Force IPv6, honoured. Either the scan proved a v6 connect, or nothing
        // contradicts it and the underlay can carry the family: an unmeasured node is given the
        // benefit of the doubt exactly once, because the dial itself is the cheapest measurement.
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

        // Rung 2 — the node advertises IPv6 but this link could not prove it. IPv6 keeps the first
        // attempt; IPv4 is the safety net behind it instead of a refusal in front of it.
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

        // Rung 3 — the transport cannot be IPv6 (v4-only node, or a network without IPv6), but
        // everything the tunnel carries still can be. This is the rung that used to be an outage.
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
        // The mirror image, for completeness: a v6-only node under Force IPv4 is just as stranded
        // as a v4-only node under Force IPv6, and gets the same treatment rather than a refusal.
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
        // A preference never refuses, so there is nothing to rescue — but naming the rung keeps
        // the diagnostics line honest about which family is actually going to open the socket.
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

    /**
     * Project a resolution back into the settings object every subsystem reads.
     *
     * The three legacy projections (`ipv6Enabled`, `preferIpv6`, `dnsQueryStrategy`) are rewritten
     * from the effective mode with the same table the Settings page uses, so a degraded session
     * cannot leave one reader on the old mode — the precise failure this ladder exists to remove.
     */
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

    /**
     * Build the evidence for one node from the host string and whatever the family scan knows.
     *
     * A literal host is decided locally and needs no measurement at all: `185.x.x.x` can never
     * gain an AAAA record, and `[2a01::1]` can never gain an A record. A stale or foreign-network
     * scan is ignored by the caller before it reaches this function.
     *
     * V198: equipped with confidence awareness and NAT64 handling:
     *  - low-confidence (<20%) scans are treated as unmeasured to avoid poisoning the ladder
     *  - NAT64 synthetic AAAA is treated as "has IPv6 record" but not "proven native IPv6",
     *    so the ladder keeps IPv4 as primary and IPv6 as opportunistic
     */
    fun evidenceFor(
        host: String,
        scan: IpFamilyScan?,
        underlayHasIpv6: Boolean
    ): FamilyEvidence {
        val clean = host.trim().removeSurrounding("[", "]")
        val literal = AddressFamilyPolicy.isLiteralIp(clean)
        val v6Literal = literal && clean.contains(':')
        val v4Literal = literal && !clean.contains(':')
        // V198: low confidence scans are not evidence
        val trustedScan = scan?.takeIf { it.confidence >= 20 || it.confidence == 0 && it.scannedAtMs > 0L } // 0 conf from old scans still trusted for backward compat
        // Actually: old scans have conf=0, we treat them as trusted if they have verdict; new low-conf (<20) is distrusted
        val effectiveScan = when {
            scan == null -> null
            scan.confidence == 0 && scan.scannedAtMs > 0L -> scan // backward compat: V197 scans have 0 conf but are valid
            scan.confidence < 20 -> null // V198 low-confidence is not evidence
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
            effectiveScan.nat64Detected -> false // NAT64 synthetic is not native proven
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
