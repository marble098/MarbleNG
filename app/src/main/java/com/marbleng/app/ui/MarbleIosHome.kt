package com.marbleng.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import com.marbleng.app.AppRepository
import com.marbleng.app.model.ConnectionPingState
import com.marbleng.app.model.HomeStyle
import com.marbleng.app.model.ProxyProfile
import kotlinx.coroutines.launch

private val IosCard = RoundedCornerShape(18.dp)
private val IosFill = Color(0xFFF2F2F7)
private val IosInk = Color(0xFF1C1C1E)
private val IosMuted = Color(0xFF8E8E93)
private val IosBlue = Color(0xFF007AFF)
private val IosGreen = Color(0xFF34C759)
private val IosRed = Color(0xFFFF3B30)

internal enum class IosHomeBox { STATUS, SERVERS, CONNECT }

@Composable
internal fun MarbleIosHome(
    style: HomeStyle,
    repo: AppRepository,
    evidence: HomeEvidence,
    actions: HomeActions,
    bottomClearance: Dp
) {
    val hidden = repo.settings.homeHiddenBoxes
        .split(',')
        .map { it.trim().uppercase() }
        .filter { it.isNotBlank() }
        .toSet()
    fun shown(box: IosHomeBox) = style != HomeStyle.CUSTOM || box.name !in hidden

    val groups = remember(repo.libraryProfiles, repo.subscriptions.size) {
        repo.libraryProfiles.groupBy { it.subscriptionName.ifBlank { it.subscriptionId.ifBlank { "Manual" } } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = bottomClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (shown(IosHomeBox.STATUS)) {
            IosStatusStrip(evidence, actions)
        }
        if (shown(IosHomeBox.SERVERS)) {
            IosServerBox(
                groups = groups,
                selectedId = evidence.profile?.id,
                onSelect = { actions.onConnectProfile(it) },
                extraSpace = style == HomeStyle.FLOAT,
                modifier = Modifier.weight(1f)
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (shown(IosHomeBox.CONNECT)) {
            when (style) {
                HomeStyle.SLIDE, HomeStyle.CUSTOM -> IosRtlSlideBar(evidence, actions)
                HomeStyle.FLOAT -> IosFloatingConnect(evidence, actions)
                HomeStyle.ORB -> IosOrbConnect(evidence, actions)
            }
        }
        if (style == HomeStyle.CUSTOM) {
            IosCustomizer(hidden) { next ->
                repo.updateSettings(repo.settings.copy(homeHiddenBoxes = next.joinToString(",")))
            }
        }
    }
}

@Composable
private fun IosStatusStrip(evidence: HomeEvidence, actions: HomeActions) {
    var ipOpen by remember { mutableStateOf(false) }
    LaunchedEffect(evidence.connected) { if (evidence.connected) ipOpen = true }
    val ping = homePingLabel(evidence)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(IosCard)
            .background(IosFill)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                homeStatusText(evidence),
                color = IosInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IosIconButton("i", onClick = { ipOpen = !ipOpen })
            Spacer(Modifier.width(8.dp))
            IosIconButton("+", onClick = actions.onPasteImport)
        }
        Text(
            evidence.nodeName.ifBlank { Tr.now.chooseRoute },
            color = IosMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(IosFill)
                    .kineticClickable(
                        enabled = homePingTappable(evidence),
                        role = Role.Button,
                        onClick = actions.onTestPing
                    ),
                contentAlignment = Alignment.Center
            ) {
                HomeGlyphIcon(HomeGlyph.CHECK, IosBlue, Modifier.size(16.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                ping,
                color = IosInk,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1
            )
        }
        AnimatedVisibility(
            visible = ipOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Text(
                evidence.ip.ifBlank { "—" },
                color = IosBlue,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .kineticClickable(role = Role.Button, onClick = actions.onCopyIp)
            )
        }
    }
}

@Composable
private fun IosServerBox(
    groups: Map<String, List<ProxyProfile>>,
    selectedId: String?,
    onSelect: (ProxyProfile) -> Unit,
    extraSpace: Boolean,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(IosCard)
            .background(IosFill)
            .padding(if (extraSpace) 16.dp else 10.dp)
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            groups.forEach { (name, servers) ->
                item(key = "h-$name") {
                    Text(
                        name,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        color = IosInk,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                items(servers, key = { it.id + it.subscriptionId }) { profile ->
                    val selected = profile.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) IosBlue.copy(alpha = .12f) else Color.White)
                            .kineticClickable(role = Role.Button) { onSelect(profile) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            profile.name,
                            color = IosInk,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IosRtlSlideBar(evidence: HomeEvidence, actions: HomeActions) {
    val travel = 220f
    val knob = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val armed = !evidence.disconnecting
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (evidence.connected) IosGreen else IosBlue)
                .padding(4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                homeActionLabel(evidence),
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .offset { IntOffset((-knob.value).toInt(), 0) }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(armed) {
                        if (!armed) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val done = knob.value >= travel * 0.72f
                                scope.launch {
                                    if (done) {
                                        knob.animateTo(travel)
                                        actions.onToggleConnection()
                                    }
                                    knob.animateTo(0f)
                                }
                            }
                        ) { change, amount ->
                            change.consume()
                            // RTL: drag toward leading (right) increases progress.
                            scope.launch { knob.snapTo((knob.value - amount).coerceIn(0f, travel)) }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                HomeGlyphIcon(connectButtonGlyph(evidence), IosBlue, Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun IosFloatingConnect(evidence: HomeEvidence, actions: HomeActions) {
    val expanded = evidence.connected || evidence.connecting
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedVisibility(visible = expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IosRoundAction(IosRed, "×") { actions.onToggleConnection() }
                IosRoundAction(IosBlue, "ms") { actions.onTestPing() }
            }
        }
        if (!expanded) {
            IosRoundAction(IosBlue, "⏻") { actions.onToggleConnection() }
        }
    }
}

@Composable
private fun IosOrbConnect(evidence: HomeEvidence, actions: HomeActions) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(if (evidence.connected) IosGreen else IosBlue)
                .kineticClickable(role = Role.Button, onClick = actions.onToggleConnection),
            contentAlignment = Alignment.Center
        ) {
            HomeGlyphIcon(connectButtonGlyph(evidence), Color.White, Modifier.size(40.dp))
        }
    }
}

@Composable
private fun IosRoundAction(color: Color, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(color)
            .kineticClickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IosIconButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(1.dp, IosMuted.copy(alpha = .4f), CircleShape)
            .kineticClickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = IosBlue, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun IosCustomizer(hidden: Set<String>, onChange: (Set<String>) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IosHomeBox.entries.forEach { box ->
            val on = box.name !in hidden
            Text(
                box.name.lowercase(),
                color = if (on) IosBlue else IosMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosFill)
                    .kineticClickable(role = Role.Button) {
                        val next = hidden.toMutableSet()
                        if (on) next += box.name else next -= box.name
                        onChange(next)
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
