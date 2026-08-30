package com.marbleng.app.ui.screens.settings.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.marbleng.app.ui.screens.settings.SettingsViewModel

@Composable
fun FreedomSettingsSection(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.freedomState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FreedomEngineCard(state, viewModel)
        FragmentChainCard(state, viewModel)
        EncryptedDnsCard(state, viewModel)
    }
}

@Composable
private fun FragmentChainCard(
    state: FreedomSettingsState,
    viewModel: SettingsViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Fragment chain",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Two hops stay fast for multi-CDN pages. Enable the middle hop only when you need extra resistance.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Outer hop",
                style = MaterialTheme.typography.labelLarge
            )

            // FIX: Use weight-based Row instead of spacedBy to prevent label overlap
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FragmentInputField(
                    label = "Packets",
                    value = state.outerPackets,
                    onValueChange = { viewModel.updateOuterPackets(it) },
                    modifier = Modifier.weight(1f)
                )
                FragmentInputField(
                    label = "Length",
                    value = state.outerLength,
                    onValueChange = { viewModel.updateOuterLength(it) },
                    modifier = Modifier.weight(1f)
                )
                FragmentInputField(
                    label = "Interval",
                    value = state.outerInterval,
                    onValueChange = { viewModel.updateOuterInterval(it) },
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = state.outerMaxSplit,
                onValueChange = { viewModel.updateOuterMaxSplit(it) },
                label = { Text("maxSplit (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Middle hop",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Optional third stage • off by default (stalls multi-CDN)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.middleHopEnabled,
                    onCheckedChange = { viewModel.toggleMiddleHop(it) }
                )
            }

            AnimatedVisibility(visible = state.middleHopEnabled) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Inner hop",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FragmentInputField(
                            label = "Packets",
                            value = state.innerPackets,
                            onValueChange = { viewModel.updateInnerPackets(it) },
                            modifier = Modifier.weight(1f)
                        )
                        FragmentInputField(
                            label = "Length",
                            value = state.innerLength,
                            onValueChange = { viewModel.updateInnerLength(it) },
                            modifier = Modifier.weight(1f)
                        )
                        FragmentInputField(
                            label = "Interval",
                            value = state.innerInterval,
                            onValueChange = { viewModel.updateInnerInterval(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = state.innerMaxSplit,
                        onValueChange = { viewModel.updateInnerMaxSplit(it) },
                        label = { Text("maxSplit (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }
    }
}

@Composable
private fun FragmentInputField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun FreedomEngineCard(state: FreedomSettingsState, viewModel: SettingsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Marble Freedom Engine", style = MaterialTheme.typography.titleLarge)
            Text(
                "Serverless DPI bypass, multi-layer fragmentation & smart multi-DNS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EncryptedDnsCard(state: FreedomSettingsState, viewModel: SettingsViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Encrypted DNS", style = MaterialTheme.typography.titleMedium)
        }
    }
}

data class FreedomSettingsState(
    val outerPackets: String = "1-1",
    val outerLength: String = "1-3",
    val outerInterval: String = "5-10",
    val outerMaxSplit: String = "",
    val middleHopEnabled: Boolean = false,
    val innerPackets: String = "1-1",
    val innerLength: String = "1",
    val innerInterval: String = "4",
    val innerMaxSplit: String = "517"
)
