package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.VisionRepository
import com.example.data.local.DeviceConfig
import com.example.data.local.LocalMediaItem
import com.example.data.local.LocalPlaylist
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiState {
    object Splash : UiState()
    data class Activation(val error: String? = null) : UiState()
    data class Syncing(val message: String) : UiState()
    data class Playing(val playlist: LocalPlaylist, val items: List<LocalMediaItem>) : UiState()
    data class Error(val message: String) : UiState()
    object Stopped : UiState()
    object TechnicalPanel : UiState()
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VisionRepository(application)
    
    private val _uiState = MutableStateFlow<UiState>(UiState.Splash)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val deviceConfig: StateFlow<DeviceConfig?> = repository.configFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val downloadProgress = repository.downloadProgress
    val isDownloading = repository.isDownloading
    val storageError = repository.storageError

    init {
        checkStatus()
    }

    fun checkStatus() {
        viewModelScope.launch {
            _uiState.value = UiState.Splash
            delay(2000)

            val config = repository.getOrCreateConfig()
            if (!config.isLinked) {
                _uiState.value = UiState.Activation()
            } else {
                if (repository.isConfigValid()) {
                    if (config.modoReproducaoAtivo) {
                        startSync(config.clienteId!!)
                    } else {
                        _uiState.value = UiState.Stopped
                    }
                } else {
                    _uiState.value = UiState.Activation("Dispositivo removido ou token inválido.")
                }
            }
        }
    }

    fun activate(token: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Syncing("Validando token...")
            // Small delay to allow keyboard animation to finish if hidden
            delay(300)
            val newConfig = repository.validateToken(token)
            if (newConfig?.isLinked == true) {
                startSync(newConfig.clienteId!!)
            } else {
                _uiState.value = UiState.Activation("Token inválido ou não encontrado.")
            }
        }
    }

    private fun startSync(clienteId: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            _uiState.value = UiState.Syncing("Sincronizando conteúdos...")
            val playlist = repository.syncPlaylist(clienteId)
            val config = deviceConfig.value
            if (config != null && !config.modoReproducaoAtivo) {
                _uiState.value = UiState.Stopped
                _isSyncing.value = false
                return@launch
            }
            
            if (playlist != null) {
                val items = repository.parseItems(playlist.itemsJson)
                if (items.isNotEmpty()) {
                    _uiState.value = UiState.Playing(playlist, items)
                    _isSyncing.value = false
                    startHeartbeat()
                } else {
                    _uiState.value = UiState.Error("Playlist vazia ou sem mídias válidas.")
                    _isSyncing.value = false
                }
            } else {
                // Try local
                val local = repository.getLocalPlaylist()
                if (local != null) {
                    val items = repository.parseItems(local.itemsJson)
                    if (items.isNotEmpty()) {
                        _uiState.value = UiState.Playing(local, items)
                        _isSyncing.value = false
                        startHeartbeat()
                    } else {
                        _uiState.value = UiState.Error("Não foi possível carregar a playlist.")
                        _isSyncing.value = false
                    }
                } else {
                    _uiState.value = UiState.Error("Não foi possível carregar a playlist.")
                    _isSyncing.value = false
                }
            }
        }
    }

    private fun startHeartbeat() {
        viewModelScope.launch {
            var syncCounter = 0
            while (true) {
                if (!repository.isConfigValid()) {
                    _uiState.value = UiState.Activation("Sessão expirada ou revogada.")
                    break
                }
                repository.updateStatus("Online")
                
                // Sync every 5 minutes (300 seconds / 30 seconds delay = 10 iterations)
                syncCounter++
                if (syncCounter >= 10) {
                    syncCounter = 0
                    val config = deviceConfig.value
                    if (config?.clienteId != null) {
                        repository.syncPlaylist(config.clienteId)
                        // Note: If we want to force refresh the UI without stopping playback, 
                        // we'd need to compare the new playlist with the current one.
                        // For now, syncing in the background is a good start.
                    }
                }
                
                delay(30000)
            }
        }
    }

    fun unlink() {
        viewModelScope.launch {
            repository.unlink()
            checkStatus()
        }
    }

    fun openTechnicalPanel() {
        _uiState.value = UiState.TechnicalPanel
    }

    fun closeTechnicalPanel() {
        val config = deviceConfig.value
        if (config != null && config.isLinked) {
            val lastState = _uiState.value
            if (lastState is UiState.TechnicalPanel) {
                // If it was in technical panel, try to go back to playing or sync
                checkStatus()
            }
        } else {
            _uiState.value = UiState.Activation()
        }
    }

    fun setPlaybackMode(active: Boolean) {
        viewModelScope.launch {
            repository.setPlaybackMode(active)
            if (active) {
                val config = repository.getOrCreateConfig()
                if (config.clienteId != null) {
                    startSync(config.clienteId)
                }
            } else {
                _uiState.value = UiState.Stopped
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val config = repository.getOrCreateConfig()
            if (config.clienteId != null) {
                startSync(config.clienteId)
            }
        }
    }
}
