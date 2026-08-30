package com.marbleng.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marbleng.app.ui.screens.settings.sections.FreedomSettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _freedomState = MutableStateFlow(FreedomSettingsState())
    val freedomState: StateFlow<FreedomSettingsState> = _freedomState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsCategories
                .onEach { categories ->
                    _uiState.update { current ->
                        current.copy(
                            availableCategories = categories,
                            selectedCategory = if (current.selectedCategory in categories) {
                                current.selectedCategory
                            } else categories.firstOrNull() ?: SettingsCategory.GENERAL
                        )
                    }
                }
                .launchIn(viewModelScope)
        }
    }

    fun onCategorySelected(category: SettingsCategory) {
        _uiState.update { current ->
            current.copy(
                selectedCategory = category,
                contentRevision = current.contentRevision + 1
            )
        }
    }

    fun updateOuterPackets(value: String) = _freedomState.update { it.copy(outerPackets = value) }
    fun updateOuterLength(value: String) = _freedomState.update { it.copy(outerLength = value) }
    fun updateOuterInterval(value: String) = _freedomState.update { it.copy(outerInterval = value) }
    fun updateOuterMaxSplit(value: String) = _freedomState.update { it.copy(outerMaxSplit = value) }
    fun toggleMiddleHop(enabled: Boolean) = _freedomState.update { it.copy(middleHopEnabled = enabled) }
    fun updateInnerPackets(value: String) = _freedomState.update { it.copy(innerPackets = value) }
    fun updateInnerLength(value: String) = _freedomState.update { it.copy(innerLength = value) }
    fun updateInnerInterval(value: String) = _freedomState.update { it.copy(innerInterval = value) }
    fun updateInnerMaxSplit(value: String) = _freedomState.update { it.copy(innerMaxSplit = value) }
}

data class SettingsUiState(
    val selectedCategory: SettingsCategory = SettingsCategory.GENERAL,
    val availableCategories: List<SettingsCategory> = SettingsCategory.entries,
    val contentRevision: Int = 0
)

// Single source of truth for SettingsCategory
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

interface SettingsRepository {
    val settingsCategories: StateFlow<List<SettingsCategory>>
}
