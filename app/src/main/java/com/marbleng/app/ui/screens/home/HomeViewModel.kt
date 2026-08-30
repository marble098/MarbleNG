package com.marbleng.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val lastRouteStore: LastRouteStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectionManager.connectionState
                .onEach { connState ->
                    _uiState.update { it.copy(connectionState = connState) }
                }
                .launchIn(viewModelScope)

            settingsRepository.isMarbleFreedomEnabled
                .onEach { enabled ->
                    _uiState.update { it.copy(isMarbleFreedomEnabled = enabled) }
                }
                .launchIn(viewModelScope)
        }
    }

    /**
     * FIX: When user clicks Connect, properly handle last-node reconnect
     * when Marble Freedom is turned OFF.
     */
    fun onConnectClicked() {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true, error = null) }

            try {
                val currentState = _uiState.value
                val lastNodeId = lastRouteStore.getLastConnectedNodeId()

                if (currentState.isMarbleFreedomEnabled) {
                    connectionManager.connectWithFreedom()
                } else {
                    if (lastNodeId != null) {
                        val profile = profileRepository.getProfileById(lastNodeId)
                        if (profile != null) {
                            connectionManager.connect(profile)
                        } else {
                            connectionManager.connectAutoSelect()
                        }
                    } else {
                        connectionManager.connectAutoSelect()
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isConnecting = false,
                        error = e.message ?: "Connection failed"
                    ) 
                }
            }
        }
    }

    fun onDisconnectClicked() {
        viewModelScope.launch {
            connectionManager.disconnect()
        }
    }

    fun onMarbleFreedomToggled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setMarbleFreedomEnabled(enabled)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

interface ConnectionManager {
    val connectionState: StateFlow<ConnectionState>
    suspend fun connect(profile: Profile)
    suspend fun connectAutoSelect()
    suspend fun connectWithFreedom()
    suspend fun disconnect()
}

interface ProfileRepository {
    suspend fun getProfileById(id: String): Profile?
}

interface SettingsRepository {
    val isMarbleFreedomEnabled: StateFlow<Boolean>
    suspend fun setMarbleFreedomEnabled(enabled: Boolean)
}

interface LastRouteStore {
    suspend fun getLastConnectedNodeId(): String?
}

data class Profile(val id: String, val name: String)
sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

data class HomeUiState(
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val isMarbleFreedomEnabled: Boolean = false,
    val isConnecting: Boolean = false,
    val error: String? = null
)
