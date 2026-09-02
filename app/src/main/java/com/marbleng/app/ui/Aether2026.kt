
package com.marbleng.app.ui

// MARBLE_FLOATING_TAB_BAR_V80
// MARBLE_CLEAN_SETTINGS_V81
// MARBLE_AMOLED_DARK_V82

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.marbleng.app.AppRepository
import com.marbleng.app.core.*
import com.marbleng.app.model.*
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* ==========================================================================
   FLOATING GLASS TAB BAR  --  replaces the static bottom bar
   ========================================================================== */

private enum class NavTarget { HOME, LIBRARY, SETTINGS }

@Composable
internal fun MarbleFloatingNavBar(
    selected: NavTarget,
    onSelect: (NavTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = MarbleMotion.current
    val breathe = motion.breathe(3_200)
    val glowAlpha = 0.06f + 0.04f * breathe

    val items = listOf(
        NavTarget.HOME to Pair("Home", Icons.Default.Home),
        NavTarget.LIBRARY to Pair("Library", Icons.Default.List),
        NavTarget.SETTINGS to Pair("Settings", Icons.Default.Settings)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val pillShape = RoundedCornerShape(32.dp)
        Box(
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth(0.82f)
                .shadow(
                    elevation = 12.dp,
                    shape = pillShape,
                    clip = false,
                    ambientColor = Aether.Cyan.copy(alpha = 0.18f),
                    spotColor = Aether.Cyan.copy(alpha = 0.28f)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Aether.Cyan.copy(alpha = 0.35f),
                            Aether.Amethyst.copy(alpha = 0.18f),
                            Aether.Cyan.copy(alpha = 0.12f)
                        )
                    ),
                    shape = pillShape
                )
                .clip(pillShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Aether.Glass.copy(alpha = 0.72f),
                            Aether.GlassStrong.copy(alpha = 0.55f)
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val activeIndex = items.indexOfFirst { it.first == selected }
                val slotWidth = size.width / items.size
                val cx = slotWidth * activeIndex + slotWidth / 2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Aether.Cyan.copy(alpha = glowAlpha),
                            Color.Transparent
                        ),
                        center = Offset(cx, size.height / 2f),
                        radius = size.height * 0.9f
                    ),
                    radius = size.height * 0.9f,
                    center = Offset(cx, size.height / 2f)
                )
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (target, pair) ->
                    val (label, icon) = pair
                    val isSelected = selected == target
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1f,
                        animationSpec = MarbleMotionSpecs.InteractionFloat,
                        label = "nav-scale"
                    )
                    val alpha by animateFloatAsState(
                        targetValue = if (isSelected) 1f else 0.55f,
                        animationSpec = MarbleMotionSpecs.Color,
                        label = "nav-alpha"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .kineticClickable(
                                role = Role.Tab,
                                pressScale = 0.92f,
                                onClick = { onSelect(target) }
                            )
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) Aether.Cyan else Aether.InkMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isSelected) Aether.Cyan else Aether.InkMuted.copy(alpha = alpha),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/* ==========================================================================
   CLEAN SETTINGS SCREEN  --  categorized, tabbed, minimal text
   ========================================================================== */

private enum class SettingsTab {
    GENERAL, CONNECTION, NETWORK, INTELLIGENCE, ADVANCED
}

@Composable
internal fun CleanSettingsScreen(
    repo: AppRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(SettingsTab.GENERAL) }
    val scrollState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val tabs = SettingsTab.entries

    Scaffold(
        topBar = {
            Surface(color = Aether.Void, tonalElevation = 0.dp) {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                "Settings",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        navigationIcon = {
                            PrismIconButton(
                                onClick = onBack,
                                size = 40.dp,
                                descriptiveLabel = "Back"
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                    ScrollableTabRow(
                        selectedTabIndex = tabs.indexOf(activeTab),
                        containerColor = Color.Transparent,
                        contentColor = Aether.Cyan,
                        edgePadding = 16.dp,
                        indicator = { tabPositions ->
                            if (tabs.indexOf(activeTab) < tabPositions.size) {
                                Box(
                                    modifier = Modifier
                                        .tabIndicatorOffset(tabPositions[tabs.indexOf(activeTab)])
                                        .height(3.dp)
                                        .padding(horizontal = 16.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(Aether.Cyan)
                                )
                            }
                        },
                        divider = {}
                    ) {
                        tabs.forEach { tab ->
                            val selected = activeTab == tab
                            Tab(
                                selected = selected,
                                onClick = { activeTab = tab },
                                text = {
                                    Text(
                                        when (tab) {
                                            SettingsTab.GENERAL -> "General"
                                            SettingsTab.CONNECTION -> "Connection"
                                            SettingsTab.NETWORK -> "Network"
                                            SettingsTab.INTELLIGENCE -> "Intelligence"
                                            SettingsTab.ADVANCED -> "Advanced"
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                selectedContentColor = Aether.Cyan,
                                unselectedContentColor = Aether.InkMuted
                            )
                        }
                    }
                }
            }
        },
        containerColor = Aether.Void
    ) { padding ->
        LazyColumn(
            state = scrollState,
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(vertical = 20.dp)
        ) {
            when (activeTab) {
                SettingsTab.GENERAL -> generalSettings(repo)
                SettingsTab.CONNECTION -> connectionSettings(repo)
                SettingsTab.NETWORK -> networkSettings(repo)
                SettingsTab.INTELLIGENCE -> intelligenceSettings(repo)
                SettingsTab.ADVANCED -> advancedSettings(repo)
            }
        }
    }
}

private fun LazyListScope.sectionHeader(title: String) {
    item {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Aether.Cyan,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun LargeToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    PrismPanel(
        modifier = modifier.fillMaxWidth(),
        accent = Aether.Cyan,
        radius = PrismSurface.TileRadius,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Aether.Ink
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Aether.Cyan,
                    checkedTrackColor = Aether.Cyan.copy(alpha = 0.3f),
                    uncheckedThumbColor = Aether.InkMuted,
                    uncheckedTrackColor = Aether.GlassStrong
                )
            )
        }
    }
}

@Composable
private fun SmallToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    PrismWell(
        modifier = modifier.fillMaxWidth(),
        tone = Aether.Cyan,
        radius = PrismSurface.InsetRadius,
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Aether.Ink
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.scale(0.82f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Aether.Cyan,
                    checkedTrackColor = Aether.Cyan.copy(alpha = 0.3f),
                    uncheckedThumbColor = Aether.InkMuted,
                    uncheckedTrackColor = Aether.GlassStrong
                )
            )
        }
    }
}

@Composable
private fun SelectionRow(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    PrismPanel(
        modifier = modifier.fillMaxWidth(),
        accent = Aether.Cyan,
        radius = PrismSurface.TileRadius,
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Aether.Ink
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { opt ->
                    PrismSelectionTile(
                        label = opt,
                        selected = selected == opt,
                        tone = Aether.Cyan,
                        onClick = { onSelect(opt) },
                        compact = true
                    )
                }
            }
        }
    }
}

/* -- General Settings Tab -- */
private fun LazyListScope.generalSettings(repo: AppRepository) {
    sectionHeader("Appearance")
    item {
        var theme by remember { mutableStateOf(repo.themeId.value) }
        SelectionRow(
            title = "Theme",
            options = listOf("System", "Light", "Dark"),
            selected = theme.replaceFirstChar { it.uppercase() },
            onSelect = {
                theme = it.lowercase()
                repo.setTheme(theme)
            }
        )
    }
    item {
        var lang by remember { mutableStateOf(repo.language.value) }
        SelectionRow(
            title = "Language",
            options = listOf("English", "Persian", "Auto"),
            selected = lang.replaceFirstChar { it.uppercase() },
            onSelect = {
                lang = it.lowercase()
                repo.setLanguage(lang)
            }
        )
    }

    sectionHeader("Notifications")
    item {
        var notify by remember { mutableStateOf(repo.notificationsEnabled.value) }
        LargeToggleRow(
            title = "Connection alerts",
            checked = notify,
            onCheckedChange = {
                notify = it
                repo.setNotificationsEnabled(it)
            }
        )
    }
    item {
        var subNotify by remember { mutableStateOf(repo.subscriptionAlerts.value) }
        SmallToggleRow(
            title = "Subscription updates",
            checked = subNotify,
            onCheckedChange = {
                subNotify = it
                repo.setSubscriptionAlerts(it)
            }
        )
    }

    sectionHeader("Updates")
    item {
        var autoCheck by remember { mutableStateOf(repo.autoUpdateCheck.value) }
        LargeToggleRow(
            title = "Auto-check for updates",
            checked = autoCheck,
            onCheckedChange = {
                autoCheck = it
                repo.setAutoUpdateCheck(it)
            }
        )
    }
}

/* -- Connection Settings Tab -- */
private fun LazyListScope.connectionSettings(repo: AppRepository) {
    sectionHeader("Mode")
    item {
        var mode by remember { mutableStateOf(repo.proxyMode.value) }
        SelectionRow(
            title = "Proxy mode",
            options = listOf("TUN (VPN)", "Local SOCKS"),
            selected = if (mode == ProxyMode.TUN) "TUN (VPN)" else "Local SOCKS",
            onSelect = {
                mode = if (it == "TUN (VPN)") ProxyMode.TUN else ProxyMode.SOCKS
                repo.setProxyMode(mode)
            }
        )
    }

    sectionHeader("Kill Switch")
    item {
        var kill by remember { mutableStateOf(repo.killSwitchEnabled.value) }
        LargeToggleRow(
            title = "Kill switch",
            checked = kill,
            onCheckedChange = {
                kill = it
                repo.setKillSwitchEnabled(it)
            }
        )
    }

    sectionHeader("Startup")
    item {
        var auto by remember { mutableStateOf(repo.autoConnectOnLaunch.value) }
        SmallToggleRow(
            title = "Auto-connect on launch",
            checked = auto,
            onCheckedChange = {
                auto = it
                repo.setAutoConnectOnLaunch(it)
            }
        )
    }
    item {
        var last by remember { mutableStateOf(repo.reconnectLastRoute.value) }
        SmallToggleRow(
            title = "Reconnect last route",
            checked = last,
            onCheckedChange = {
                last = it
                repo.setReconnectLastRoute(it)
            }
        )
    }
}

/* -- Network Settings Tab -- */
private fun LazyListScope.networkSettings(repo: AppRepository) {
    sectionHeader("DNS")
    item {
        var doh by remember { mutableStateOf(repo.dnsOverHttpsEnabled.value) }
        LargeToggleRow(
            title = "DNS over HTTPS",
            checked = doh,
            onCheckedChange = {
                doh = it
                repo.setDnsOverHttpsEnabled(it)
            }
        )
    }
    item {
        var ipv6 by remember { mutableStateOf(repo.ipv6Enabled.value) }
        SmallToggleRow(
            title = "IPv6 support",
            checked = ipv6,
            onCheckedChange = {
                ipv6 = it
                repo.setIpv6Enabled(it)
            }
        )
    }

    sectionHeader("Routing")
    item {
        var route by remember { mutableStateOf(repo.routingMode.value) }
        SelectionRow(
            title = "Routing mode",
            options = listOf("Proxy all", "Private direct", "Geo direct", "Custom"),
            selected = route,
            onSelect = {
                route = it
                repo.setRoutingMode(it)
            }
        )
    }

    sectionHeader("Split Tunneling")
    item {
        var split by remember { mutableStateOf(repo.splitTunnelEnabled.value) }
        LargeToggleRow(
            title = "Split tunneling",
            checked = split,
            onCheckedChange = {
                split = it
                repo.setSplitTunnelEnabled(it)
            }
        )
    }
}

/* -- Intelligence Settings Tab -- */
private fun LazyListScope.intelligenceSettings(repo: AppRepository) {
    sectionHeader("Marble Intelligence")
    item {
        var intel by remember { mutableStateOf(repo.marbleIntelligenceEnabled.value) }
        LargeToggleRow(
            title = "Enable Marble Intelligence",
            checked = intel,
            onCheckedChange = {
                intel = it
                repo.setMarbleIntelligenceEnabled(it)
            }
        )
    }
    item {
        var turbo by remember { mutableStateOf(repo.marbleTurboEnabled.value) }
        SmallToggleRow(
            title = "Marble Turbo",
            checked = turbo,
            onCheckedChange = {
                turbo = it
                repo.setMarbleTurboEnabled(it)
            }
        )
    }
    item {
        var adaptive by remember { mutableStateOf(repo.adaptiveMtuEnabled.value) }
        SmallToggleRow(
            title = "Adaptive MTU",
            checked = adaptive,
            onCheckedChange = {
                adaptive = it
                repo.setAdaptiveMtuEnabled(it)
            }
        )
    }

    sectionHeader("Iran Mode")
    item {
        var iran by remember { mutableStateOf(repo.iranMode.value) }
        SelectionRow(
            title = "Iran Mode",
            options = listOf("Auto", "Always on", "Off"),
            selected = iran,
            onSelect = {
                iran = it
                repo.setIranMode(it)
            }
        )
    }
}

/* -- Advanced Settings Tab -- */
private fun LazyListScope.advancedSettings(repo: AppRepository) {
    sectionHeader("Testing")
    item {
        var real by remember { mutableStateOf(repo.realXrayVerification.value) }
        LargeToggleRow(
            title = "Real Xray verification",
            checked = real,
            onCheckedChange = {
                real = it
                repo.setRealXrayVerification(it)
            }
        )
    }
    item {
        var tcp by remember { mutableStateOf(repo.tcpPingEnabled.value) }
        SmallToggleRow(
            title = "TCP ping",
            checked = tcp,
            onCheckedChange = {
                tcp = it
                repo.setTcpPingEnabled(it)
            }
        )
    }

    sectionHeader("Fragment & Mux")
    item {
        var frag by remember { mutableStateOf(repo.fragmentationEnabled.value) }
        LargeToggleRow(
            title = "TLS fragmentation",
            checked = frag,
            onCheckedChange = {
                frag = it
                repo.setFragmentationEnabled(it)
            }
        )
    }
    item {
        var mux by remember { mutableStateOf(repo.muxEnabled.value) }
        SmallToggleRow(
            title = "Mux (multiplexing)",
            checked = mux,
            onCheckedChange = {
                mux = it
                repo.setMuxEnabled(it)
            }
        )
    }

    sectionHeader("Diagnostics")
    item {
        var debug by remember { mutableStateOf(repo.debugModeEnabled.value) }
        LargeToggleRow(
            title = "Debug mode",
            checked = debug,
            onCheckedChange = {
                debug = it
                repo.setDebugModeEnabled(it)
            }
        )
    }
    item {
        val ctx = LocalContext.current
        PrismButton(
            label = "Open Bug Finder",
            onClick = {
                ctx.startActivity(Intent(ctx, BugFinderActivity::class.java))
            },
            tone = Aether.Cyan,
            variant = PrismButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/* ==========================================================================
   MAIN APP SHELL  --  wires the floating nav bar into the scaffold
   ========================================================================== */

@Composable
fun MarbleAppShell(repo: AppRepository) {
    var selectedTab by remember { mutableStateOf(NavTarget.HOME) }

    Box(modifier = Modifier.fillMaxSize()) {
        PrismBackdrop(modifier = Modifier.fillMaxSize())

        Crossfade(
            targetState = selectedTab,
            animationSpec = tween(280),
            label = "screen-crossfade"
        ) { tab ->
            when (tab) {
                NavTarget.HOME -> HomeScreen(repo = repo)
                NavTarget.LIBRARY -> LibraryScreen(repo = repo)
                NavTarget.SETTINGS -> CleanSettingsScreen(
                    repo = repo,
                    onBack = { selectedTab = NavTarget.HOME }
                )
            }
        }

        MarbleFloatingNavBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/* ==========================================================================
   STUBS for compilation -- these already exist in your repo
   ========================================================================== */

@Composable
internal fun HomeScreen(repo: AppRepository) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Home", style = MaterialTheme.typography.headlineLarge, color = Aether.Ink)
    }
}

@Composable
internal fun LibraryScreen(repo: AppRepository) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Library", style = MaterialTheme.typography.headlineLarge, color = Aether.Ink)
    }
}

class BugFinderActivity : android.app.Activity()
