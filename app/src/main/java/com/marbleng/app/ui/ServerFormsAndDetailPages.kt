package com.marbleng.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.core.ManualConfigBuilder
import com.marbleng.app.core.ManualConfigDraft
import com.marbleng.app.core.ManualProtocol
import com.marbleng.app.core.ProfileSecurityAuditor
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.ui.theme.success
import com.marbleng.app.ui.theme.warning

private enum class AddSheetTab {
    IMPORT,
    SUBSCRIPTION,
    NODE,
    CHAIN
}

private val SS_METHODS = listOf(
    "2022-blake3-aes-128-gcm",
    "2022-blake3-aes-256-gcm",
    "2022-blake3-chacha20-poly1305",
    "aes-128-gcm",
    "aes-256-gcm",
    "chacha20-ietf-poly1305",
    "none"
)

private val VLESS_FLOWS = listOf("", "xtls-rprx-vision")
private val STREAM_TRANSPORTS = listOf("raw", "websocket", "grpc", "xhttp", "httpupgrade")
private val TLS_FINGERPRINTS = listOf("chrome", "firefox", "safari", "ios", "android", "edge", "random", "unsafe")

@Composable
fun ServerAddSheet(
    repo: AppRepository,
    onImportFile: () -> Unit,
    onOpenQrImport: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val strings = Tr.now
    val clipboard = LocalClipboardManager.current
    var activeTab by rememberSaveable { mutableStateOf(AddSheetTab.IMPORT) }
    var targetGroup by rememberSaveable {
        mutableStateOf(repo.intakeTargetOrManual(repo.librarySourceFilter))
    }

    val groupChoices = buildList {
        add(MarbleMenuOption("manual", strings.homeManualGroup))
        repo.subscriptions.forEach { add(MarbleMenuOption(it.id, it.name)) }
    }

    MarbleBottomSheet(
        title = trx("Add Server or Source"),
        subtitle = trx("Import links, add an HTTPS subscription, build a node or compose a chain"),
        onDismiss = onDismiss
    ) {
        MarbleSegmentedControl(
            options = listOf(
                AddSheetTab.IMPORT to trx("Import"),
                AddSheetTab.SUBSCRIPTION to trx("Sub"),
                AddSheetTab.NODE to trx("Manual"),
                AddSheetTab.CHAIN to trx("Chain")
            ),
            selected = activeTab,
            onSelect = { activeTab = it }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (activeTab) {
                AddSheetTab.IMPORT -> {
                    var pastedText by remember { mutableStateOf("") }
                    MarbleCard {
                        MarbleListItem(
                            title = strings.addFromClipboard,
                            subtitle = trx("Automatically detects proxy links, JSON or HTTPS subscription URLs"),
                            glyph = MarbleGlyph.COPY,
                            iconTint = colors.primary,
                            onClick = {
                                val raw = clipboard.getText()?.text?.trim().orEmpty()
                                if (raw.isBlank()) {
                                    repo.setRuntimeMessage(strings.clipboardNothingFound)
                                } else {
                                    repo.importClipboard(raw, targetGroup)
                                    onDismiss()
                                }
                            }
                        )
                        MarbleDivider()
                        MarbleListItem(
                            title = trx("Scan or Pick QR Code"),
                            subtitle = trx("Read QR code from camera or gallery image"),
                            glyph = MarbleGlyph.QR,
                            iconTint = colors.secondary,
                            onClick = {
                                onDismiss()
                                onOpenQrImport()
                            }
                        )
                        MarbleDivider()
                        MarbleListItem(
                            title = trx("Import from File"),
                            subtitle = trx("Load a .txt or .json file from device storage"),
                            glyph = MarbleGlyph.FOLDER,
                            iconTint = colors.tertiary,
                            onClick = {
                                onDismiss()
                                onImportFile()
                            }
                        )
                    }

                    if (groupChoices.size > 1) {
                        MarbleDropdownField(
                            label = trx("Save Imported Nodes Into"),
                            value = targetGroup,
                            options = groupChoices,
                            onSelect = { targetGroup = it }
                        )
                    }

                    MarbleTextField(
                        label = trx("Paste Links or JSON"),
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        placeholder = "vless://... or { \"outbounds\": [...] }",
                        singleLine = false,
                        monospace = true,
                        minLines = 4,
                        maxLines = 8
                    )
                    MarbleButton(
                        label = trx("Import Text"),
                        glyph = MarbleGlyph.PLUS,
                        enabled = pastedText.trim().isNotBlank() && !repo.busy,
                        onClick = {
                            repo.importClipboard(pastedText, targetGroup)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AddSheetTab.SUBSCRIPTION -> {
                    var subName by remember { mutableStateOf("") }
                    var subUrl by remember { mutableStateOf("") }

                    MarbleCard(contentPadding = PaddingValues(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            MarbleTextField(
                                label = trx("Subscription Name"),
                                value = subName,
                                onValueChange = { subName = it },
                                placeholder = trx("My Provider")
                            )
                            MarbleTextField(
                                label = trx("HTTPS Subscription URL"),
                                value = subUrl,
                                onValueChange = { subUrl = it },
                                placeholder = "https://example.com/sub"
                            )
                            MarbleSectionFooter(
                                text = trx("Leave URL blank to create a local-only server group.")
                            )
                            MarbleButton(
                                label = trx("Save Subscription"),
                                glyph = MarbleGlyph.PLUS,
                                enabled = subName.trim().isNotBlank() && !repo.busy,
                                onClick = {
                                    repo.addSubscription(subName.trim(), subUrl.trim())
                                    onDismiss()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                AddSheetTab.NODE -> {
                    MarbleManualNodeForm(
                        repo = repo,
                        targetGroup = targetGroup,
                        groupChoices = groupChoices,
                        onTargetGroupChange = { targetGroup = it },
                        onSaved = onDismiss
                    )
                }

                AddSheetTab.CHAIN -> {
                    MarbleChainBuilderForm(
                        repo = repo,
                        targetGroup = targetGroup,
                        groupChoices = groupChoices,
                        onTargetGroupChange = { targetGroup = it },
                        onSaved = onDismiss
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MarbleManualNodeForm(
    repo: AppRepository,
    targetGroup: String,
    groupChoices: List<MarbleMenuOption>,
    onTargetGroupChange: (String) -> Unit,
    onSaved: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var draft by remember { mutableStateOf(ManualConfigDraft()) }
    val protocol = draft.protocol
    val streamCapable = protocol in setOf(ManualProtocol.VLESS, ManualProtocol.VMESS, ManualProtocol.TROJAN)
    val realityCapable = draft.transport in setOf("raw", "xhttp", "grpc")
    val missing = ManualConfigBuilder.missingRequirement(draft)

    MarbleCard(contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MarbleTextField(
                label = trx("Server Name"),
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                placeholder = trx("Frankfurt · 01")
            )

            MarbleDropdownField(
                label = trx("Protocol"),
                value = protocol.name,
                options = ManualProtocol.entries.map { MarbleMenuOption(it.name, it.label) },
                onSelect = { raw ->
                    val picked = ManualProtocol.entries.firstOrNull { it.name == raw } ?: return@MarbleDropdownField
                    draft = ManualConfigDraft(protocol = picked).copy(
                        name = draft.name,
                        host = draft.host,
                        port = when (picked) {
                            ManualProtocol.HTTP -> "80"
                            ManualProtocol.SOCKS5 -> "1080"
                            ManualProtocol.SSH -> "22"
                            ManualProtocol.WIREGUARD -> "51820"
                            else -> "443"
                        },
                        security = when (picked) {
                            ManualProtocol.SHADOWSOCKS,
                            ManualProtocol.HTTP,
                            ManualProtocol.SOCKS5,
                            ManualProtocol.SSH,
                            ManualProtocol.WIREGUARD -> "none"
                            else -> "tls"
                        }
                    )
                }
            )

            if (protocol != ManualProtocol.XRAY_JSON) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MarbleTextField(
                        label = trx("Server Host / IP"),
                        value = draft.host,
                        onValueChange = { draft = draft.copy(host = it) },
                        placeholder = "example.com",
                        modifier = Modifier.weight(1.7f)
                    )
                    MarbleTextField(
                        label = trx("Port"),
                        value = draft.port,
                        onValueChange = { draft = draft.copy(port = it.filter(Char::isDigit).take(5)) },
                        modifier = Modifier.weight(0.9f)
                    )
                }
            }

            when (protocol) {
                ManualProtocol.XRAY_JSON -> {
                    MarbleTextField(
                        label = trx("Xray Outbound / Config JSON"),
                        value = draft.customJson,
                        onValueChange = { draft = draft.copy(customJson = it) },
                        singleLine = false,
                        monospace = true,
                        minLines = 6,
                        maxLines = 14
                    )
                }

                ManualProtocol.VLESS -> {
                    MarbleSecretField(
                        label = "UUID",
                        value = draft.uuid,
                        onValueChange = { draft = draft.copy(uuid = it) }
                    )
                    MarbleDropdownField(
                        label = trx("Flow"),
                        value = draft.flow,
                        options = VLESS_FLOWS.map { MarbleMenuOption(it, it.ifBlank { "none" }) },
                        onSelect = { draft = draft.copy(flow = it) }
                    )
                }

                ManualProtocol.VMESS -> {
                    MarbleSecretField(
                        label = "UUID",
                        value = draft.uuid,
                        onValueChange = { draft = draft.copy(uuid = it) }
                    )
                    MarbleTextField(
                        label = trx("Cipher"),
                        value = draft.encryption,
                        onValueChange = { draft = draft.copy(encryption = it) },
                        placeholder = "auto"
                    )
                }

                ManualProtocol.TROJAN -> {
                    MarbleSecretField(
                        label = trx("Password"),
                        value = draft.password,
                        onValueChange = { draft = draft.copy(password = it) }
                    )
                }

                ManualProtocol.SHADOWSOCKS -> {
                    MarbleDropdownField(
                        label = trx("Cipher Method"),
                        value = draft.method,
                        options = SS_METHODS.map { MarbleMenuOption(it) },
                        onSelect = { draft = draft.copy(method = it) }
                    )
                    MarbleSecretField(
                        label = trx("Password"),
                        value = draft.password,
                        onValueChange = { draft = draft.copy(password = it) }
                    )
                }

                ManualProtocol.HYSTERIA2 -> {
                    MarbleSecretField(
                        label = trx("Auth Password"),
                        value = draft.password,
                        onValueChange = { draft = draft.copy(password = it) }
                    )
                    MarbleTextField(
                        label = "SNI",
                        value = draft.sni,
                        onValueChange = { draft = draft.copy(sni = it) }
                    )
                    MarbleTextField(
                        label = "ALPN",
                        value = draft.alpn,
                        onValueChange = { draft = draft.copy(alpn = it) },
                        placeholder = "h3"
                    )
                    MarbleTextField(
                        label = trx("Pinned Cert SHA-256 (optional)"),
                        value = draft.pinnedPeerCertSha256,
                        onValueChange = { draft = draft.copy(pinnedPeerCertSha256 = it) }
                    )
                }

                ManualProtocol.HTTP, ManualProtocol.HTTPS, ManualProtocol.SOCKS5 -> {
                    MarbleTextField(
                        label = trx("Username (optional)"),
                        value = draft.username,
                        onValueChange = { draft = draft.copy(username = it) }
                    )
                    MarbleSecretField(
                        label = trx("Password (optional)"),
                        value = draft.password,
                        onValueChange = { draft = draft.copy(password = it) }
                    )
                    if (protocol == ManualProtocol.HTTPS) {
                        MarbleTextField(
                            label = "SNI",
                            value = draft.sni,
                            onValueChange = { draft = draft.copy(sni = it) }
                        )
                    }
                }

                ManualProtocol.SSH -> {
                    MarbleTextField(
                        label = trx("SSH Username"),
                        value = draft.username,
                        onValueChange = { draft = draft.copy(username = it) }
                    )
                    MarbleSecretField(
                        label = trx("SSH Password"),
                        value = draft.password,
                        onValueChange = { draft = draft.copy(password = it) }
                    )
                    MarbleTextField(
                        label = trx("Host Key SHA-256 (optional)"),
                        value = draft.sshHostKeySha256,
                        onValueChange = { draft = draft.copy(sshHostKeySha256 = it) }
                    )
                }

                ManualProtocol.WIREGUARD -> {
                    MarbleSecretField(
                        label = trx("Private Key"),
                        value = draft.wireguardSecretKey,
                        onValueChange = { draft = draft.copy(wireguardSecretKey = it) }
                    )
                    MarbleTextField(
                        label = trx("Peer Public Key"),
                        value = draft.wireguardPeerPublicKey,
                        onValueChange = { draft = draft.copy(wireguardPeerPublicKey = it) }
                    )
                    MarbleTextField(
                        label = trx("Local CIDR Address"),
                        value = draft.wireguardAddress,
                        onValueChange = { draft = draft.copy(wireguardAddress = it) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarbleTextField(
                            label = "MTU",
                            value = draft.wireguardMtu,
                            onValueChange = { draft = draft.copy(wireguardMtu = it) },
                            modifier = Modifier.weight(1f)
                        )
                        MarbleTextField(
                            label = trx("Keepalive"),
                            value = draft.wireguardKeepAlive,
                            onValueChange = { draft = draft.copy(wireguardKeepAlive = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (streamCapable) {
                MarbleDropdownField(
                    label = trx("Transport"),
                    value = draft.transport,
                    options = STREAM_TRANSPORTS.map { MarbleMenuOption(it, it.uppercase()) },
                    onSelect = { transport ->
                        draft = draft.copy(
                            transport = transport,
                            security = if (draft.security == "reality" && transport !in setOf("raw", "xhttp", "grpc")) {
                                "tls"
                            } else {
                                draft.security
                            }
                        )
                    }
                )

                if (draft.transport in setOf("websocket", "xhttp", "httpupgrade")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarbleTextField(
                            label = trx("Path"),
                            value = draft.path,
                            onValueChange = { draft = draft.copy(path = it) },
                            modifier = Modifier.weight(1f)
                        )
                        MarbleTextField(
                            label = trx("Host Header"),
                            value = draft.hostHeader,
                            onValueChange = { draft = draft.copy(hostHeader = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (draft.transport == "grpc") {
                    MarbleTextField(
                        label = trx("gRPC Service Name"),
                        value = draft.serviceName,
                        onValueChange = { draft = draft.copy(serviceName = it) }
                    )
                }

                val securityChoices = when {
                    protocol == ManualProtocol.TROJAN && realityCapable -> listOf("tls", "reality")
                    protocol == ManualProtocol.TROJAN -> listOf("tls")
                    realityCapable -> listOf("none", "tls", "reality")
                    else -> listOf("none", "tls")
                }
                MarbleDropdownField(
                    label = trx("Security"),
                    value = draft.security,
                    options = securityChoices.map { MarbleMenuOption(it, it.uppercase()) },
                    onSelect = { draft = draft.copy(security = it) }
                )

                if (draft.security in setOf("tls", "reality")) {
                    MarbleTextField(
                        label = "SNI",
                        value = draft.sni,
                        onValueChange = { draft = draft.copy(sni = it) }
                    )
                    MarbleDropdownField(
                        label = trx("TLS Fingerprint"),
                        value = draft.fingerprint,
                        options = TLS_FINGERPRINTS.map { MarbleMenuOption(it) },
                        onSelect = { draft = draft.copy(fingerprint = it) }
                    )
                }

                if (draft.security == "tls") {
                    MarbleTextField(
                        label = "ALPN",
                        value = draft.alpn,
                        onValueChange = { draft = draft.copy(alpn = it) },
                        placeholder = "h2,http/1.1"
                    )
                    MarbleTextField(
                        label = trx("Pinned Cert SHA-256 (optional)"),
                        value = draft.pinnedPeerCertSha256,
                        onValueChange = { draft = draft.copy(pinnedPeerCertSha256 = it) }
                    )
                } else if (draft.security == "reality") {
                    MarbleTextField(
                        label = trx("REALITY Public Key"),
                        value = draft.realityPublicKey,
                        onValueChange = { draft = draft.copy(realityPublicKey = it) }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarbleTextField(
                            label = trx("Short ID"),
                            value = draft.realityShortId,
                            onValueChange = { draft = draft.copy(realityShortId = it) },
                            modifier = Modifier.weight(1f)
                        )
                        MarbleTextField(
                            label = "SpiderX",
                            value = draft.realitySpiderX,
                            onValueChange = { draft = draft.copy(realitySpiderX = it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            if (groupChoices.size > 1) {
                MarbleDropdownField(
                    label = trx("Save To Group"),
                    value = targetGroup,
                    options = groupChoices,
                    onSelect = onTargetGroupChange
                )
            }

            if (missing != null) {
                Text(
                    text = trx(missing),
                    color = colors.warning,
                    // MARBLE_WEIGHT_LADDER_V235 — a blocking warning steps up to TITLE weight;
                    // colour alone should not be the only thing saying "this blocks Save".
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            MarbleButton(
                label = trx("Save Server"),
                glyph = MarbleGlyph.CHECK,
                enabled = missing == null && !repo.busy,
                onClick = {
                    if (repo.addManualProfile(draft, targetGroup)) {
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MarbleChainBuilderForm(
    repo: AppRepository,
    targetGroup: String,
    groupChoices: List<MarbleMenuOption>,
    onTargetGroupChange: (String) -> Unit,
    onSaved: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val eligible = repo.libraryProfiles.filterNot { it.scheme.equals("ssh", ignoreCase = true) }

    if (eligible.size < 2) {
        MarbleCard(contentPadding = PaddingValues(16.dp)) {
            Text(
                text = trx("Add at least two Xray-compatible servers first to compose a multi-hop proxy chain."),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val options = eligible.map { p ->
        MarbleMenuOption(
            value = "${p.subscriptionId}|${p.id}",
            label = cleanServerName(p.name),
            detail = "${protocolShortLabel(p)} · ${p.subscriptionName}"
        )
    }

    var chainName by remember { mutableStateOf("") }
    var entryKey by remember { mutableStateOf(options.first().value) }
    var exitKey by remember { mutableStateOf(options.getOrElse(1) { options.first() }.value) }

    fun decodeRef(key: String): Pair<String, String>? {
        val parts = key.split('|', limit = 2)
        return if (parts.size == 2) parts[0] to parts[1] else null
    }

    MarbleCard(contentPadding = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MarbleTextField(
                label = trx("Chain Name"),
                value = chainName,
                onValueChange = { chainName = it },
                placeholder = trx("Relay → Exit")
            )
            MarbleDropdownField(
                label = trx("Entry Relay (First Hop)"),
                value = entryKey,
                options = options,
                onSelect = { entryKey = it }
            )
            MarbleDropdownField(
                label = trx("Exit Server (Final Hop)"),
                value = exitKey,
                options = options,
                onSelect = { exitKey = it }
            )
            if (groupChoices.size > 1) {
                MarbleDropdownField(
                    label = trx("Save To Group"),
                    value = targetGroup,
                    options = groupChoices,
                    onSelect = onTargetGroupChange
                )
            }
            MarbleButton(
                label = trx("Create Multi-Hop Chain"),
                glyph = MarbleGlyph.ROUTE,
                enabled = entryKey != exitKey && !repo.busy,
                onClick = {
                    val hop1 = decodeRef(entryKey) ?: return@MarbleButton
                    val hop2 = decodeRef(exitKey) ?: return@MarbleButton
                    if (repo.addManualChain(chainName, listOf(hop1, hop2), targetGroup)) {
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ServerDetailPage(
    repo: AppRepository,
    profile: ProxyProfile,
    onBack: () -> Unit,
    onConnect: (ProxyProfile) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    val liveProfile = repo.profile(profile.id, profile.subscriptionId) ?: profile
    val isActive = repo.isActiveProfile(liveProfile)
    val country = repo.serverLocation(liveProfile)
    val audit = remember(liveProfile.id, liveProfile.configJson) {
        ProfileSecurityAuditor.assess(liveProfile)
    }
    val familyScan = repo.ipFamilyScan(liveProfile)
    var jsonDraft by remember(liveProfile.id, liveProfile.configJson) {
        mutableStateOf(liveProfile.configJson)
    }
    var showQrSheet by remember { mutableStateOf(false) }

    val securityTone = when (audit.level) {
        ProfileSecurityAuditor.SecurityLevel.STRONG -> colors.success
        ProfileSecurityAuditor.SecurityLevel.ADEQUATE -> colors.primary
        ProfileSecurityAuditor.SecurityLevel.WEAK -> colors.warning
        ProfileSecurityAuditor.SecurityLevel.INSECURE -> colors.error
        else -> colors.onSurfaceVariant
    }

    MarbleExpressivePageScaffold(
        glyph = MarbleGlyph.SERVER,
        tint = securityTone,
        title = cleanServerName(liveProfile.name),
        subtitle = "${protocolShortLabel(liveProfile)} · ${liveProfile.host}:${liveProfile.port}",
        onBack = onBack
    ) {
        item(key = "detail-summary") {
            MarbleCard {
                MarbleListItem(
                    title = cleanServerName(liveProfile.name),
                    subtitle = "${protocolShortLabel(liveProfile)} · ${country.name}",
                    leading = {
                        MarbleServerAvatar(profile = liveProfile, countryCode = country.code)
                    },
                    showChevron = false,
                    trailing = {
                        MarbleBadge(text = audit.level.name, color = securityTone)
                    }
                )
                MarbleDivider()
                MarbleListItem(
                    title = "${liveProfile.host}:${liveProfile.port}",
                    subtitle = "${trx("Source")}: ${liveProfile.subscriptionName}",
                    glyph = MarbleGlyph.SERVER,
                    iconTint = colors.secondary,
                    showChevron = false
                )
                if (audit.findings.isNotEmpty()) {
                    audit.findings.take(3).forEach { finding ->
                        MarbleDivider()
                        MarbleListItem(
                            title = trx(finding.category),
                            subtitle = trx(finding.detail),
                            glyph = MarbleGlyph.SHIELD,
                            iconTint = securityTone,
                            showChevron = false
                        )
                    }
                }
            }
        }

        item(key = "detail-actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Actions"))
                MarbleCard {
                    MarbleListItem(
                        title = if (isActive) Tr.now.disconnect else Tr.now.connect,
                        subtitle = if (isActive) {
                            trx("Stop the active VPN session")
                        } else {
                            trx("Start protected tunnel through this server")
                        },
                        glyph = if (isActive) MarbleGlyph.STOP else MarbleGlyph.POWER,
                        iconTint = if (isActive) colors.error else colors.success,
                        onClick = {
                            if (isActive) repo.stopVpn() else onConnect(liveProfile)
                        }
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Run Full Route Test"),
                        subtitle = trx("Measure tunnel latency, jitter and stability"),
                        glyph = MarbleGlyph.GAUGE,
                        iconTint = colors.primary,
                        enabled = !repo.busy && !repo.probeActive,
                        onClick = { repo.fullTest(liveProfile) }
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Scan IPv4 & IPv6 Reachability"),
                        subtitle = familyScan?.headline ?: trx("Probe A / AAAA DNS records and dual-stack TCP handshakes"),
                        glyph = MarbleGlyph.RADAR,
                        iconTint = colors.tertiary,
                        enabled = !repo.ipFamilyScanning && !repo.busy,
                        onClick = { repo.scanIpFamily(liveProfile) }
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Share QR Code"),
                        subtitle = trx("Show scannable QR code for this server"),
                        glyph = MarbleGlyph.QR,
                        iconTint = colors.secondary,
                        onClick = { showQrSheet = true }
                    )
                    MarbleDivider()
                    MarbleListItem(
                        title = trx("Copy Share URI"),
                        subtitle = trx("Copy raw share link to clipboard"),
                        glyph = MarbleGlyph.COPY,
                        iconTint = colors.warning,
                        onClick = {
                            clipboard.setText(AnnotatedString(liveProfile.raw.ifBlank { liveProfile.configJson }))
                            repo.setRuntimeMessage("Config link copied")
                        }
                    )
                }
            }
        }

        item(key = "detail-json-editor") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MarbleSectionHeader(title = trx("Outbound JSON Configuration"))
                MarbleCard(contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MarbleTextField(
                            label = trx("Xray JSON"),
                            value = jsonDraft,
                            onValueChange = { jsonDraft = it },
                            singleLine = false,
                            monospace = true,
                            minLines = 8,
                            maxLines = 22
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            MarbleButton(
                                label = trx("Save JSON"),
                                glyph = MarbleGlyph.CHECK,
                                enabled = !repo.busy && !isActive && jsonDraft != liveProfile.configJson,
                                onClick = {
                                    repo.updateProfileJson(liveProfile.id, jsonDraft, liveProfile.subscriptionId)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            MarbleButton(
                                label = Tr.now.copyAction,
                                glyph = MarbleGlyph.COPY,
                                style = MarbleButtonStyle.SECONDARY,
                                onClick = {
                                    clipboard.setText(AnnotatedString(jsonDraft))
                                    repo.setRuntimeMessage("JSON copied")
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (isActive) {
                            Text(
                                text = trx("Disconnect first to edit the active server's JSON."),
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showQrSheet) {
        ServerQrShareSheet(
            profile = liveProfile,
            onCopy = { link ->
                clipboard.setText(AnnotatedString(link))
                repo.setRuntimeMessage("Config link copied")
            },
            onDismiss = { showQrSheet = false }
        )
    }
}
