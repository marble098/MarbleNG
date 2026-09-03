package com.marbleng.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [ManualConfigBuilder.missingRequirement] (MARBLE_SERVERS_QUERY_V120).
 *
 * The Add-node form keeps its Save button disabled until this returns `null` and shows the returned
 * sentence as the reason, so the form and the builder can never disagree about what a complete
 * config is. These tests pin that shared contract down.
 */
class ManualConfigRequirementTest {

    @Test
    fun addressIsTheFirstRequirement() {
        assertEquals(
            "Server address is required",
            ManualConfigBuilder.missingRequirement(ManualConfigDraft())
        )
    }

    @Test
    fun portOutsideTheValidRangeIsRejected() {
        assertEquals(
            "Port must be between 1 and 65535",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(host = "1.2.3.4", port = "99999", uuid = "id")
            )
        )
        assertNull(
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(host = "1.2.3.4", port = "65535", uuid = "id")
            )
        )
    }

    @Test
    fun vlessNeedsAnIdentityAndATransportOrEncryption() {
        assertEquals(
            "UUID is required",
            ManualConfigBuilder.missingRequirement(ManualConfigDraft(host = "1.2.3.4"))
        )
        assertNull(
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(host = "1.2.3.4", uuid = "550e8400-e29b-41d4-a716-446655440000")
            )
        )
        // Plain-text VLESS with no encryption and no TLS cannot be built.
        assertEquals(
            "VLESS needs TLS/REALITY or a non-none encryption",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(
                    host = "1.2.3.4",
                    uuid = "550e8400-e29b-41d4-a716-446655440000",
                    security = "none",
                    encryption = "none"
                )
            )
        )
    }

    @Test
    fun xrayJsonDraftOnlyNeedsItsJson() {
        assertEquals(
            "Paste the Xray JSON first",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(protocol = ManualProtocol.XRAY_JSON)
            )
        )
        assertNull(
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(
                    protocol = ManualProtocol.XRAY_JSON,
                    customJson = "{\"outbounds\":[]}"
                )
            )
        )
    }

    @Test
    fun passwordProtocolsRequireTheirSecret() {
        assertEquals(
            "Password is required",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(protocol = ManualProtocol.TROJAN, host = "1.2.3.4")
            )
        )
        assertNull(
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(
                    protocol = ManualProtocol.TROJAN,
                    host = "1.2.3.4",
                    password = "hunter2"
                )
            )
        )
        assertEquals(
            "Auth password is required",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(protocol = ManualProtocol.HYSTERIA2, host = "1.2.3.4")
            )
        )
    }

    @Test
    fun shadowsocksNeedsMethodAndPassword() {
        assertEquals(
            "Password is required",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(protocol = ManualProtocol.SHADOWSOCKS, host = "1.2.3.4")
            )
        )
        assertEquals(
            "Method is required",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(
                    protocol = ManualProtocol.SHADOWSOCKS,
                    host = "1.2.3.4",
                    method = " ",
                    password = "hunter2"
                )
            )
        )
        assertNull(
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(
                    protocol = ManualProtocol.SHADOWSOCKS,
                    host = "1.2.3.4",
                    password = "hunter2"
                )
            )
        )
    }

    @Test
    fun sshNeedsBothCredentials() {
        assertEquals(
            "SSH username is required",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(protocol = ManualProtocol.SSH, host = "1.2.3.4", port = "22")
            )
        )
        assertEquals(
            "SSH password is required",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(
                    protocol = ManualProtocol.SSH,
                    host = "1.2.3.4",
                    port = "22",
                    username = "root"
                )
            )
        )
        assertNull(
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(
                    protocol = ManualProtocol.SSH,
                    host = "1.2.3.4",
                    port = "22",
                    username = "root",
                    password = "hunter2"
                )
            )
        )
    }

    @Test
    fun wireguardNeedsItsKeyPair() {
        assertEquals(
            "Private key is required",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(protocol = ManualProtocol.WIREGUARD, host = "1.2.3.4")
            )
        )
        assertEquals(
            "Peer public key is required",
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(
                    protocol = ManualProtocol.WIREGUARD,
                    host = "1.2.3.4",
                    wireguardSecretKey = "secret"
                )
            )
        )
        assertNull(
            ManualConfigBuilder.missingRequirement(
                ManualConfigDraft(
                    protocol = ManualProtocol.WIREGUARD,
                    host = "1.2.3.4",
                    wireguardSecretKey = "secret",
                    wireguardPeerPublicKey = "peer"
                )
            )
        )
    }

    @Test
    fun plainProxiesNeedNothingButAnAddress() {
        listOf(ManualProtocol.SOCKS5, ManualProtocol.HTTP, ManualProtocol.HTTPS).forEach { protocol ->
            assertNull(
                "expected $protocol to be savable with only an address",
                ManualConfigBuilder.missingRequirement(
                    ManualConfigDraft(protocol = protocol, host = "1.2.3.4", port = "1080")
                )
            )
        }
    }
}
