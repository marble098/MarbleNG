package com.marbleng.app.core

import com.marbleng.app.model.AppSettings
import com.marbleng.app.model.ProxyProfile

object ProfileSecurityAuditor {
    enum class SecurityLevel {
        STRONG,
        ADEQUATE,
        WEAK,
        INSECURE,
        INFO_LEVEL,
        UNKNOWN
    }

    data class SecurityFinding(
        val category: String,
        val level: SecurityLevel,
        val detail: String,
        val recommendation: String = ""
    )

    data class SecurityAssessment(
        val profileId: String,
        val profileName: String,
        val level: SecurityLevel,
        val score: Double,
        val findings: List<SecurityFinding>,
        val forwardSecrecy: Boolean,
        val encrypted: Boolean,
        val dpiResistant: Boolean,
        val deprecated: Boolean
    )

    fun assess(profile: ProxyProfile): SecurityAssessment {
        val findings = mutableListOf<SecurityFinding>()
        val scheme = profile.scheme.lowercase()
        val security = profile.security.lowercase()
        val transport = profile.transport.lowercase()
        val raw = profile.raw.lowercase()

        var forwardSecrecy = false
        var encrypted = false
        var dpiResistant = false
        var deprecated = false

        when {
            scheme == "vless" -> {
                encrypted = security.contains("reality") || security.contains("tls")
                forwardSecrecy = encrypted
                dpiResistant = security.contains("reality")

                if (security.contains("reality")) {
                    findings += SecurityFinding("protocol", SecurityLevel.STRONG,
                        "VLESS+REALITY: Best-in-class anti-detection with forward secrecy")
                } else if (security.contains("tls")) {
                    findings += SecurityFinding("protocol", SecurityLevel.STRONG,
                        "VLESS+TLS: Strong encryption with forward secrecy")
                } else if (security == "none" || security.isBlank()) {
                    findings += SecurityFinding("protocol", SecurityLevel.WEAK,
                        "VLESS without encryption: Traffic is visible to ISP",
                        "Upgrade to VLESS+REALITY or VLESS+TLS")
                }
            }

            scheme == "vmess" -> {
                if (security.contains("tls") || security.contains("reality")) {
                    encrypted = true
                    forwardSecrecy = true
                    findings += SecurityFinding("protocol", SecurityLevel.ADEQUATE,
                        "VMess+TLS: Adequate encryption with forward secrecy")
                } else if (security == "none" || security.isBlank()) {
                    encrypted = false
                    forwardSecrecy = false
                    findings += SecurityFinding("protocol", SecurityLevel.WEAK,
                        "VMess without TLS: NO FORWARD SECRECY. Traffic can be decrypted later if key is compromised.",
                        "Strongly recommend migrating to VLESS+REALITY or at minimum VMess+TLS")
                }

                if (raw.contains("aead")) {
                    findings += SecurityFinding("cipher", SecurityLevel.ADEQUATE,
                        "VMess AEAD mode: Authenticated encryption")
                }

                if (!raw.contains("aead") && !raw.contains("alterid=0")) {
                    deprecated = true
                    findings += SecurityFinding("cipher", SecurityLevel.INSECURE,
                        "Legacy VMess with alterId > 0: Deprecated and potentially insecure",
                        "Set alterId to 0 or migrate to VLESS")
                }
            }

            scheme == "trojan" -> {
                encrypted = true
                forwardSecrecy = true
                dpiResistant = true
                findings += SecurityFinding("protocol", SecurityLevel.STRONG,
                    "Trojan: TLS-based with good DPI resistance")
            }

            scheme == "shadowsocks" || scheme == "ss" -> {
                encrypted = true

                val hasAead = raw.contains("2022-blake3") || raw.contains("aes-256-gcm") ||
                    raw.contains("aes-128-gcm") || raw.contains("chacha20")
                if (hasAead) {
                    findings += SecurityFinding("protocol", SecurityLevel.ADEQUATE,
                        "Shadowsocks with AEAD cipher")
                } else {
                    findings += SecurityFinding("protocol", SecurityLevel.WEAK,
                        "Shadowsocks with potentially weak cipher",
                        "Upgrade to SS2022 or AEAD cipher")
                }
            }

            scheme == "hysteria2" || scheme == "hy2" -> {
                encrypted = true
                forwardSecrecy = true
                findings += SecurityFinding("protocol", SecurityLevel.ADEQUATE,
                    "Hysteria2: QUIC-based with built-in encryption")

                findings += SecurityFinding("transport", SecurityLevel.INFO_LEVEL,
                    "Hysteria2 uses UDP which may be blocked during Iranian clampdowns")
            }

            scheme == "hysteria" -> {
                encrypted = true
                forwardSecrecy = true
                findings += SecurityFinding("protocol", SecurityLevel.ADEQUATE,
                    "Hysteria: TLS-encrypted QUIC-based protocol")
                findings += SecurityFinding("transport", SecurityLevel.INFO_LEVEL,
                    "Hysteria uses UDP which may be blocked during Iranian clampdowns")
            }

            scheme == "tuic" || scheme == "anytls" -> {
                encrypted = true
                forwardSecrecy = true
                findings += SecurityFinding("protocol", SecurityLevel.ADEQUATE,
                    "${scheme.uppercase()}: sing-box extended QUIC/DATAGRAM protocol")
                findings += SecurityFinding("transport", SecurityLevel.INFO_LEVEL,
                    "${scheme.uppercase()} uses UDP which may be blocked during Iranian clampdowns")
            }

            scheme == "wireguard" -> {
                encrypted = true
                forwardSecrecy = false
                findings += SecurityFinding("protocol", SecurityLevel.ADEQUATE,
                    "WireGuard: Strong encryption but limited forward secrecy",
                    "Consider VLESS+REALITY for better censorship resistance")
            }

            scheme == "ssh" -> {
                encrypted = true
                findings += SecurityFinding("protocol", SecurityLevel.WEAK,
                    "SSH tunnel: Basic encryption, not designed for anti-DPI",
                    "Use VLESS+REALITY for censorship environments")
            }

            scheme == "socks" || scheme == "http" -> {
                encrypted = false
                forwardSecrecy = false
                findings += SecurityFinding("protocol", SecurityLevel.INSECURE,
                    "Plain SOCKS/HTTP proxy: No encryption at all",
                    "Must be wrapped in TLS or replaced with VLESS+REALITY")
            }

            else -> {
                findings += SecurityFinding("protocol", SecurityLevel.UNKNOWN,
                    "Unknown protocol scheme: $scheme")
            }
        }

        when {
            transport.contains("xhttp") || transport.contains("splithttp") -> {
                dpiResistant = true
                findings += SecurityFinding("transport", SecurityLevel.STRONG,
                    "XHTTP/SplitHTTP: Modern HTTP-like transport, good for censorship")
            }
            transport.contains("ws") || transport.contains("websocket") -> {
                dpiResistant = encrypted
                findings += SecurityFinding("transport",
                    if (encrypted) SecurityLevel.ADEQUATE else SecurityLevel.WEAK,
                    "WebSocket: ${if (encrypted) "Encrypted" else "UNENCRYPTED"} WebSocket transport")
            }
            transport.contains("grpc") -> {
                findings += SecurityFinding("transport", SecurityLevel.ADEQUATE,
                    "gRPC: HTTP/2-based transport")
            }
            transport.contains("httpupgrade") -> {
                dpiResistant = encrypted
                findings += SecurityFinding("transport", SecurityLevel.ADEQUATE,
                    "HTTPUpgrade: Lightweight HTTP transport")
            }
            transport.contains("tcp") || transport.isBlank() -> {
                if (!encrypted) {
                    findings += SecurityFinding("transport", SecurityLevel.WEAK,
                        "Plain TCP: No transport camouflage")
                }
            }
        }

        when (profile.port) {
            443 -> findings += SecurityFinding("port", SecurityLevel.STRONG,
                "Port 443: Standard HTTPS port, blends with normal traffic")
            80 -> findings += SecurityFinding("port", SecurityLevel.ADEQUATE,
                "Port 80: HTTP port, may be inspected more closely")
            in setOf(8080, 2053, 2083, 2087, 2096, 8443) ->
                findings += SecurityFinding("port", SecurityLevel.ADEQUATE,
                    "Port ${profile.port}: Common CDN port, generally allowed")
            else -> {
                findings += SecurityFinding("port", SecurityLevel.WEAK,
                    "Port ${profile.port}: Non-standard port may be blocked during filtering",
                    "Consider using port 443 for better survival under censorship")
            }
        }

        if (raw.contains("flow=xtls-rprx-vision")) {
            forwardSecrecy = true
            findings += SecurityFinding("flow", SecurityLevel.STRONG,
                "XTLS Vision: Strong flow with forward secrecy")
        }

        val score = computeScore(findings)
        val level = computeLevel(findings, score)

        return SecurityAssessment(
            profileId = profile.id,
            profileName = profile.name,
            level = level,
            score = score,
            findings = findings,
            forwardSecrecy = forwardSecrecy,
            encrypted = encrypted,
            dpiResistant = dpiResistant,
            deprecated = deprecated
        )
    }

    private fun computeScore(findings: List<SecurityFinding>): Double {
        if (findings.isEmpty()) return 50.0

        var score = 70.0

        findings.forEach { finding ->
            score += when (finding.level) {
                SecurityLevel.STRONG -> 15.0
                SecurityLevel.ADEQUATE -> 5.0
                SecurityLevel.WEAK -> -15.0
                SecurityLevel.INSECURE -> -30.0
                SecurityLevel.INFO_LEVEL -> 0.0
                SecurityLevel.UNKNOWN -> -10.0
            }
        }

        return score.coerceIn(0.0, 100.0)
    }

    private fun computeLevel(findings: List<SecurityFinding>, score: Double): SecurityLevel {
        if (findings.any { it.level == SecurityLevel.INSECURE }) return SecurityLevel.INSECURE
        if (score >= 80) return SecurityLevel.STRONG
        if (score >= 60) return SecurityLevel.ADEQUATE
        if (score >= 35) return SecurityLevel.WEAK
        return SecurityLevel.INSECURE
    }

    fun rankPenalty(profile: ProxyProfile): Double {
        val assessment = assess(profile)
        return when (assessment.level) {
            SecurityLevel.STRONG -> 0.0
            SecurityLevel.ADEQUATE -> -2.0
            SecurityLevel.WEAK -> -12.0
            SecurityLevel.INSECURE -> -25.0
            SecurityLevel.UNKNOWN -> -8.0
            SecurityLevel.INFO_LEVEL -> 0.0
        }
    }

    fun shouldExcludeFromRank(profile: ProxyProfile): Boolean {
        val assessment = assess(profile)
        return assessment.level == SecurityLevel.INSECURE && assessment.deprecated
    }

    enum class RankEligibility { ACTIVE, DEPRECATED }

    data class Eligibility(
        val eligibility: RankEligibility,
        val reason: String
    ) {
        val active: Boolean get() = eligibility == RankEligibility.ACTIVE
    }

    fun rankEligibility(
        profile: ProxyProfile,
        settings: AppSettings = AppSettings()
    ): Eligibility {
        val scheme = profile.scheme.lowercase()
        val security = effectiveSecurity(profile)
        val assessment = assess(profile)

        val engine = settings.coreEngine()
        CoreConfigSuperset.coreGapIssue(profile, engine)?.let {
            return Eligibility(RankEligibility.DEPRECATED, "core-gap")
        }
        val verdict = CoreConfigSuperset.verdict(profile, engine, settings)
        if (!verdict.runnable) return Eligibility(RankEligibility.DEPRECATED, verdict.reason)
        if (verdict.note.isNotEmpty()) return Eligibility(RankEligibility.ACTIVE, "unencrypted-outbound")
        return when {
            scheme == "vless" && security !in setOf("tls", "reality") ->
                Eligibility(RankEligibility.DEPRECATED, "vless-without-tls-reality")
            scheme == "vmess" && security !in setOf("tls", "reality") ->
                Eligibility(RankEligibility.DEPRECATED, "vmess-without-forward-secrecy")
            assessment.deprecated ->
                Eligibility(RankEligibility.DEPRECATED, "deprecated-cipher")
            else ->
                Eligibility(RankEligibility.ACTIVE, "active")
        }
    }

    fun partitionForRank(
        profiles: List<ProxyProfile>,
        settings: AppSettings = AppSettings()
    ): Pair<List<ProxyProfile>, List<Pair<ProxyProfile, String>>> {
        val active = mutableListOf<ProxyProfile>()
        val deprecated = mutableListOf<Pair<ProxyProfile, String>>()
        profiles.forEach { profile ->
            val eligibility = rankEligibility(profile, settings)
            if (eligibility.active) active += profile
            else deprecated += profile to eligibility.reason
        }
        return active to deprecated
    }

    private fun effectiveSecurity(profile: ProxyProfile): String {
        val explicit = profile.security.trim().lowercase()
        if (explicit.contains("reality")) return "reality"
        if (explicit.contains("tls")) return "tls"
        val json = profile.configJson.lowercase()
        if (json.contains("\"security\":\"reality\"") || json.contains("\"security\": \"reality\"")) return "reality"
        if (json.contains("\"security\":\"tls\"") || json.contains("\"security\": \"tls\"")) return "tls"
        return explicit.ifBlank { "none" }
    }

    fun batchAssess(profiles: List<ProxyProfile>): Map<String, SecurityAssessment> {
        return profiles.associate { it.id to assess(it) }
    }

    fun levelCounts(profiles: List<ProxyProfile>): Map<SecurityLevel, Int> {
        return profiles.groupBy { assess(it).level }.mapValues { it.value.size }
    }
}
