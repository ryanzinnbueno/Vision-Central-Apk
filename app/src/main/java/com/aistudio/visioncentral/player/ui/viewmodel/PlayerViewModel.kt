package com.aistudio.visioncentral.player.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.visioncentral.player.data.VisionRepository
import com.aistudio.visioncentral.player.data.local.DeviceConfig
import com.aistudio.visioncentral.player.data.local.LocalMediaItem
import com.aistudio.visioncentral.player.data.local.LocalPlaylist
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiState {
    object Splash : UiState()
    data class Activation(val error: String? = null) : UiState()
    data class Syncing(val message: String) : UiState()
    data class Playing(val playlist: LocalPlaylist, val items: List<LocalMediaItem>) : UiState()
    data class Error(val message: String) : UiState()
    object TechnicalPanel : UiState()
}

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val instanceHash = System.identityHashCode(this)
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
        Log.d("VisionCentral", "PlayerViewModel inicializado")
        
        // Observador de Configuração
        repository.configFlow.onEach { config ->
            if (config != null) {
                Log.d("VisionCentral", "[CONFIG] Nova configuração recebida: $config")
                if (!config.isLinked && _uiState.value !is UiState.Activation && _uiState.value !is UiState.Splash) {
                    Log.d("VisionCentral", "[PLAYER] Dispositivo desvinculado detectado. Mudando para Ativação.")
                    _uiState.value = UiState.Activation()
                }
            }
        }.launchIn(viewModelScope)

        // Observador de Playlist
        repository.playlistFlow.onEach { playlist ->
            if (playlist != null) {
                Log.d("VisionCentral", "[PLAYER] Nova playlist aplicada")
                val items = repository.parseItems(playlist.itemsJson)
                val currentState = _uiState.value
                
                if (currentState is UiState.Playing) {
                    if (items != currentState.items || playlist.id != currentState.playlist.id) {
                        _uiState.value = UiState.Playing(playlist, items)
                    }
                } else if (currentState !is UiState.Splash && currentState !is UiState.Activation) {
                    if (items.isNotEmpty()) {
                        _uiState.value = UiState.Playing(playlist, items)
                    }
                }
            }
        }.launchIn(viewModelScope)

        checkStatus()
    }

    fun checkStatus() {
        viewModelScope.launch {
            Log.d("VisionCentral", "[PLAYER] Verificando status...")
            _uiState.value = UiState.Splash
            delay(2000)
            try {
                val config = repository.getOrCreateConfig()
                if (!config.isLinked) {
                    _uiState.value = UiState.Activation()
                } else {
                    if (repository.isConfigValid()) {
                        repository.startHeartbeat(viewModelScope)
                        startSync(config.clienteId!!)
                    } else {
                        _uiState.value = UiState.Activation("Dispositivo removido ou token inválido.")
                    }
                }
            } catch (e: Exception) {
                Log.e("VisionCentral", "Erro em checkStatus", e)
                _uiState.value = UiState.Error("Erro ao verificar status: ${e.message}")
            }
        }
    }

    fun activate(token: String) {
        viewModelScope.launch {
            Log.d("VisionCentral", "[PLAYER] Iniciando ativação...")
            delay(1000)
            _uiState.value = UiState.Syncing("Validando token...")
            try {
                val newConfig = repository.validateToken(token)
                if (newConfig?.isLinked == true) {
                    repository.startHeartbeat(viewModelScope)
                    startSync(newConfig.clienteId!!)
                } else {
                    _uiState.value = UiState.Activation("Token inválido ou não encontrado.")
                }
            } catch (e: Exception) {
                Log.e("VisionCentral", "Erro na ativação", e)
                _uiState.value = UiState.Activation("Erro na ativação: ${e.message}")
            }
        }
    }

    private fun startSync(clienteId: String) {
        viewModelScope.launch {
            Log.d("VisionCentral", "[PLAYER] Iniciando sincronização...")
            _isSyncing.value = true
            _uiState.value = UiState.Syncing("Sincronizando conteúdos...")
            try {
                repository.syncTvSettings()
                _isSyncing.value = false
            } catch (e: Exception) {
                Log.e("VisionCentral", "Erro em startSync", e)
                _uiState.value = UiState.Error("Erro na sincronização: ${e.message}")
                _isSyncing.value = false
            }
        }
    }

    fun unlink() {
        viewModelScope.launch {
            repository.unlink()
            checkStatus()
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

    override fun onCleared() {
        super.onCleared()
        repository.stopHeartbeat()
    }
}
