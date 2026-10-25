package com.marbleng.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marbleng.app.AppRepository
import com.marbleng.app.model.DockSize
import com.marbleng.app.model.ProxyProfile
import com.marbleng.app.model.dockShowsIcons
import com.marbleng.app.model.dockShowsLabels
import com.marbleng.app.model.dockSlotIndex
import com.marbleng.app.model.dockSlots
import com.marbleng.app.model.parseDockSize
import com.marbleng.app.model.parseDockSlotAccent
import com.marbleng.app.model.parseDockSlotIcon
import kotlinx.coroutines.delay

enum class IosRootTab {
    HOME,
    SERVERS,
    CUSTOM_SLOT,
    SETTINGS
}

@Composable
fun MarbleApp(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit = {}
) {
    ProvideMarbleLanguage(repo.settings.appLanguage) {
        ProvideMarbleTextScale(repo.settings.textScalePercent) {
            MarbleIosTheme(
                themeId = repo.settings.theme,
                accentId = repo.settings.accentTheme,
                fontId = repo.settings.fontFamily,
                flatSurfaces = repo.settings.flatInterface
            ) {
                MarbleIosRootShell(
                    repo = repo,
                    onConnect = onConnect,
                    onImportFile = onImportFile,
                    onContentScrollChanged = onContentScrollChanged
                )
            }
        }
    }
}

@Composable
private fun MarbleIosRootShell(
    repo: AppRepository,
    onConnect: (ProxyProfile) -> Unit,
    onImportFile: () -> Unit,
    onContentScrollChanged: (Boolean) -> Unit
) {
    val colors = IosTheme.colors
    val settings = repo.settings

    var rememberedTab by rememberSaveable { mutableStateOf(IosRootTab.HOME) }
    var activeSettingsPage by rememberSaveable { mutableStateOf<SettingsDestination?>(null) }
    var inspectingProfileId by rememberSaveable { mutableStateOf("") }
    var inspectingProfileSource by rememberSaveable { mutableStateOf("") }
    var showAddSheet by remember { mutableStateOf(false) }
    var showQrSheet by remember { mutableStateOf(false) }
    var showExitIpSheet by remember { mutableStateOf(false) }

    val visibleTabs = dockSlots(
        all = IosRootTab.entries,
        optional = IosRootTab.CUSTOM_SLOT,
        showOptional = settings.dockSlotEnabled
    )
    val activeTab = visibleTabs[dockSlotIndex(visibleTabs, rememberedTab)]

    val detailProfile = if (inspectingProfileId.isNotBlank()) {
        repo.profile(inspectingProfileId, inspectingProfileSource)
            ?: repo.profile(inspectingProfileId)
    } else {
        null
    }

    val canNavigateBack = detailProfile != null ||
        (activeTab == IosRootTab.SETTINGS && activeSettingsPage != null) ||
        activeTab != IosRootTab.HOME

    BackHandler(enabled = canNavigateBack) {
        when {
            detailProfile != null -> {
                inspectingProfileId = ""
                inspectingProfileSource = ""
            }
            activeTab == IosRootTab.SETTINGS && activeSettingsPage != null -> {
                activeSettingsPage = null
            }
            else -> {
                rememberedTab = IosRootTab.HOME
            }
        }
    }

    LaunchedEffect(repo.message) {
        if (repo.message.isNotBlank()) {
            val currentMsg = repo.message
            delay(3_600L)
            if (repo.message == currentMsg) {
                repo.clearMessage()
            }
        }
    }

    val openDetail: (ProxyProfile) -> Unit = { profile ->
        inspectingProfileId = profile.id
        inspectingProfileSource = profile.subscriptionId
    }

    val openSettingsDest: (SettingsDestination) -> Unit = { dest ->
        inspectingProfileId = ""
        inspectingProfileSource = ""
        activeSettingsPage = dest
        rememberedTab = IosRootTab.SETTINGS
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        if (detailProfile != null) {
            ServerDetailPage(
                repo = repo,
                profile = detailProfile,
                onBack = {
                    inspectingProfileId = ""
                    inspectingProfileSource = ""
                },
                onConnect = onConnect
            )
        } else {
            when (activeTab) {
                IosRootTab.HOME -> HomeScreen(
                    repo = repo,
                    onConnect = onConnect,
                    onOpenServers = { rememberedTab = IosRootTab.SERVERS },
                    onOpenServerDetail = openDetail,
                    onOpenQrImport = { showQrSheet = true },
                    onOpenAddSheet = { showAddSheet = true },
                    onOpenExitIpSheet = { showExitIpSheet = true },
                    onOpenDiagnostics = { openSettingsDest(SettingsDestination.DIAGNOSTICS) },
                    onOpenSettingsPage = openSettingsDest,
                    onContentScrollChanged = onContentScrollChanged
                )

                IosRootTab.SERVERS -> ServersScreen(
                    repo = repo,
                    onConnect = onConnect,
                    onOpenDetail = openDetail,
                    onOpenAddSheet = { showAddSheet = true },
                    onOpenQrImport = { showQrSheet = true },
                    onContentScrollChanged = onContentScrollChanged
                )

                IosRootTab.CUSTOM_SLOT -> CustomSlotScreen(
                    repo = repo,
                    onConnect = onConnect,
                    onOpenDetail = openDetail,
                    onCustomizeSlot = { openSettingsDest(SettingsDestination.TAB_BAR) },
                    onOpenDiagnostics = { openSettingsDest(SettingsDestination.DIAGNOSTICS) },
                    onContentScrollChanged = onContentScrollChanged
                )

                IosRootTab.SETTINGS -> SettingsScreen(
                    repo = repo,
                    activeDestination = activeSettingsPage,
                    onOpenDestination = { activeSettingsPage = it },
                    onCloseDestination = { activeSettingsPage = null },
                    onContentScrollChanged = onContentScrollChanged
                )
            }
        }

        AnimatedVisibility(
            visible = repo.message.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.card)
                    .border(1.dp, colors.divider, RoundedCornerShape(16.dp))
                    .clickable { repo.clearMessage() }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = trx(repo.message),
                    color = colors.title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IosBottomTabBar(
            repo = repo,
            tabs = visibleTabs,
            selected = activeTab,
            onSelect = { chosen ->
                inspectingProfileId = ""
                inspectingProfileSource = ""
                if (chosen == IosRootTab.SETTINGS && activeTab == IosRootTab.SETTINGS) {
                    activeSettingsPage = null
                }
                rememberedTab = chosen
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showAddSheet) {
        ServerAddSheet(
            repo = repo,
            onImportFile = onImportFile,
            onOpenQrImport = { showQrSheet = true },
            onDismiss = { showAddSheet = false }
        )
    }

    QrImportSourceDialogController(
        open = showQrSheet,
        repo = repo,
        onDismiss = { showQrSheet = false }
    )

    if (showExitIpSheet) {
        ExitIpDetailsSheet(
            repo = repo,
            onDismiss = { showExitIpSheet = false }
        )
    }

    repo.availableUpdate?.let { update ->
        AppUpdateAvailableDialog(
            update = update,
            onDismiss = { repo.dismissAppUpdate() }
        )
    }
}

@Composable
private fun IosBottomTabBar(
    repo: AppRepository,
    tabs: List<IosRootTab>,
    selected: IosRootTab,
    onSelect: (IosRootTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = IosTheme.colors
    val strings = Tr.now
    val s = repo.settings
    val size = parseDockSize(s.dockSize)
    val showIcons = dockShowsIcons(s.dockShowIcons, s.dockShowLabels)
    val showLabels = dockShowsLabels(s.dockShowIcons, s.dockShowLabels)
    val slot = resolveDockSlot(repo)
    val slotIcon = dockSlotMarbleGlyph(parseDockSlotIcon(s.dockSlotIcon))
    val slotColor = dockSlotIosColor(parseDockSlotAccent(s.dockSlotAccent))

    val barHeight = when (size) {
        DockSize.SMALL -> 52.dp
        DockSize.MEDIUM -> 60.dp
        DockSize.LARGE -> 68.dp
    }
    val iconSize = when (size) {
        DockSize.SMALL -> 20.dp
        DockSize.MEDIUM -> 23.dp
        DockSize.LARGE -> 26.dp
    }

    val statusDotColor: Color? = if (s.dockSlotShowStatusBadge) {
        when (repo.state) {
            "CONNECTED" -> colors.green
            "CONNECTING", "DISCONNECTING" -> colors.orange
            else -> null
        }
    } else {
        null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tabBar)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 0.75.dp, color = colors.tabBarBorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selected
                val activeColor = if (tab == IosRootTab.CUSTOM_SLOT) slotColor else colors.accent
                val tint = if (isSelected) activeColor else colors.subtitle
                val glyph = when (tab) {
                    IosRootTab.HOME -> MarbleGlyph.HOME
                    IosRootTab.SERVERS -> MarbleGlyph.SERVER
                    IosRootTab.CUSTOM_SLOT -> slotIcon
                    IosRootTab.SETTINGS -> MarbleGlyph.TUNERS
                }
                val label = when (tab) {
                    IosRootTab.HOME -> strings.tabHome
                    IosRootTab.SERVERS -> strings.tabLibrary
                    IosRootTab.CUSTOM_SLOT -> trx(slot.title)
                    IosRootTab.SETTINGS -> strings.tabSettings
                }

                val pillShape = RoundedCornerShape(IosSpec.DOCK_ACTIVE_PILL_CORNER_DP.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(pillShape)
                        .background(
                            if (isSelected) activeColor.copy(alpha = if (colors.dark) 0.18f else 0.10f) else Color.Transparent
                        )
                        .clickable(
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(tab) }
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (showIcons) {
                            Box(contentAlignment = Alignment.Center) {
                                MarbleGlyphIcon(
                                    glyph = glyph,
                                    color = tint,
                                    modifier = Modifier.size(iconSize),
                                    weight = if (isSelected) 1.18f else 1f
                                )
                                if (tab == IosRootTab.CUSTOM_SLOT && statusDotColor != null) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-3).dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(statusDotColor)
                                            .border(1.2.dp, colors.tabBar, CircleShape)
                                    )
                                }
                                // Selected dot under active tab (6dp pill lens)
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .offset(y = 10.dp)
                                            .size(width = 14.dp, height = 3.dp)
                                            .clip(CircleShape)
                                            .background(activeColor.copy(alpha = 0.55f))
                                    )
                                }
                            }
                        }
                        if (showIcons && showLabels) {
                            Spacer(Modifier.height(3.dp))
                        }
                        if (showLabels) {
                            Text(
                                text = label,
                                color = tint,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
