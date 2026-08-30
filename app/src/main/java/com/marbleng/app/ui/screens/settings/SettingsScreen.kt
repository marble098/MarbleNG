package com.marbleng.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // FIX: Use mutableIntStateOf with explicit recomposition key
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Sync with ViewModel state
    LaunchedEffect(uiState.selectedCategory) {
        val newIndex = SettingsCategory.entries.indexOf(uiState.selectedCategory)
        if (newIndex != selectedTabIndex && newIndex >= 0) {
            selectedTabIndex = newIndex
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // FIX: ScrollableTabRow with proper edge padding
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.fillMaxWidth(),
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                divider = {}
            ) {
                SettingsCategory.entries.forEachIndexed { index, category ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { 
                            selectedTabIndex = index
                            viewModel.onCategorySelected(category)
                        },
                        text = { 
                            Text(
                                text = category.displayName,
                                maxLines = 1,
                                softWrap = false
                            ) 
                        },
                        icon = {
                            Icon(
                                imageVector = category.icon,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            // FIX: Animated content with forced recomposition key
            Box(modifier = Modifier.weight(1f)) {
                key(selectedTabIndex) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            when (SettingsCategory.entries[selectedTabIndex]) {
                                SettingsCategory.GENERAL -> GeneralSettingsSection()
                                SettingsCategory.FREEDOM -> FreedomSettingsSection()
                                SettingsCategory.TESTING -> TestingSettingsSection()
                                SettingsCategory.NETWORK -> NetworkSettingsSection()
                                SettingsCategory.EXPERT -> ExpertSettingsSection()
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class SettingsCategory(
    val displayName: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    GENERAL("General", androidx.compose.material.icons.Icons.Default.Settings),
    FREEDOM("Freedom", androidx.compose.material.icons.Icons.Default.Security),
    TESTING("Testing", androidx.compose.material.icons.Icons.Default.NetworkCheck),
    NETWORK("Network", androidx.compose.material.icons.Icons.Default.SignalCellularAlt),
    EXPERT("Expert", androidx.compose.material.icons.Icons.Default.Build)
}
