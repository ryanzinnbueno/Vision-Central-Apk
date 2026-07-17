package com.aistudio.visioncentral.player.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.visioncentral.player.data.VisionRepository
import com.aistudio.visioncentral.player.data.local.DeviceConfig
import com.aistudio.visioncentral.player.data.local.LocalMediaItem
import com.aistudio.visioncentral.player.data.local.LocalPlaylist
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
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
        
        // Observe playlist changes to update UI automatically
        repository.playlistFlow.onEach { playlist ->
            if (playlist != null) {
                val items = repository.parseItems(playlist.itemsJson)
                val currentState = _uiState.value
                
                if (currentState is UiState.Playing) {
                    if (items != currentState.items || playlist.id != currentState.playlist.id) {
                        Log.d("VisionCentral", "[Observer] Playlist alterada detectada via Flow. Atualizando UI.")
                        _uiState.value = UiState.Playing(playlist, items)
                    }
                } else if (currentState !is UiState.Splash && currentState !is UiState.Activation && currentState !is UiState.Syncing) {
                    if (items.isNotEmpty()) {
                        Log.d("VisionCentral", "[Observer] Mudando para Playing via Flow.")
                        _uiState.value = UiState.Playing(playlist, items)
                    }
                }
            }
        }.launchIn(viewModelScope)

        checkStatus()
    }

    fun checkStatus() {
        viewModelScope.launch {
            Log.d("VisionCentral", "checkStatus() chamado")
            _uiState.value = UiState.Splash
            delay(2000)

            try {
                val config = repository.getOrCreateConfig()
                Log.d("VisionCentral", "Configuração atual: linked=${config.isLinked}, tvId=${config.tvId}")
                
                if (!config.isLinked) {
                    Log.d("VisionCentral", "Dispositivo não vinculado, indo para tela de ativação")
                    _uiState.value = UiState.Activation()
                } else {
                    Log.d("VisionCentral", "Validando sessão no Supabase...")
                    if (repository.isConfigValid()) {
                        Log.d("VisionCentral", "Supabase conectado")
                        // Inicia o heartbeat e o realtime sync
                        startHeartbeat()
                        repository.startRealtimeSync(viewModelScope)
                        
                        Log.d("VisionCentral", "Iniciando sincronização inicial...")
                        startSync(config.clienteId!!)
                    } else {
                        Log.d("VisionCentral", "Configuração inválida ou dispositivo removido")
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
            Log.d("VisionCentral", "Iniciando ativação com token: $token")
            // Small delay to allow keyboard animation to finish if hidden in the UI
            delay(1000)
            _uiState.value = UiState.Syncing("Validando token...")
            try {
                val newConfig = repository.validateToken(token)
                if (newConfig?.isLinked == true) {
                    Log.d("VisionCentral", "Supabase conectado")
                    Log.d("VisionCentral", "Ativação bem-sucedida para clienteId: ${newConfig.clienteId}")
                    startHeartbeat()
                    repository.startRealtimeSync(viewModelScope)
                    startSync(newConfig.clienteId!!)
                } else {
                    Log.d("VisionCentral", "Token inválido")
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
            Log.d("VisionCentral", "Sincronizando...")
            _isSyncing.value = true
            _uiState.value = UiState.Syncing("Sincronizando conteúdos...")
            try {
                val playlist = repository.syncPlaylist(clienteId)
                
                if (playlist != null) {
                    val items = repository.parseItems(playlist.itemsJson)
                    if (items.isNotEmpty()) {
                        Log.d("VisionCentral", "Configuração aplicada")
                        _uiState.value = UiState.Playing(playlist, items)
                        _isSyncing.value = false
                    } else {
                        Log.d("VisionCentral", "Playlist carregada, mas está vazia")
                        _uiState.value = UiState.Error("Playlist vazia ou sem mídias válidas.")
                        _isSyncing.value = false
                    }
                } else {
                    Log.d("VisionCentral", "Playlist remota falhou, tentando local...")
                    // Try local
                    val local = repository.getLocalPlaylist()
                    if (local != null) {
                        val items = repository.parseItems(local.itemsJson)
                        if (items.isNotEmpty()) {
                            Log.d("VisionCentral", "Playlist local carregada com ${items.size} itens")
                            _uiState.value = UiState.Playing(local, items)
                            _isSyncing.value = false
                        } else {
                            Log.d("VisionCentral", "Playlist local também está vazia")
                            _uiState.value = UiState.Error("Não foi possível carregar a playlist.")
                            _isSyncing.value = false
                        }
                    } else {
                        Log.d("VisionCentral", "Nenhuma playlist local encontrada")
                        _uiState.value = UiState.Error("Não foi possível carregar a playlist.")
                        _isSyncing.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e("VisionCentral", "Erro em startSync", e)
                _uiState.value = UiState.Error("Erro na sincronização: ${e.message}")
                _isSyncing.value = false
            }
        }
    }

    private var heartbeatJob: kotlinx.coroutines.Job? = null

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            Log.d("VisionCentral", "[Heartbeat] Loop de batimento cardíaco iniciado.")
            while (true) {
                try {
                    val config = repository.getOrCreateConfig()
                    if (!config.isLinked || config.tvId == null) {
                        Log.w("VisionCentral", "[Heartbeat] Dispositivo não vinculado. Parando heartbeat.")
                        _uiState.value = UiState.Activation()
                        break
                    }

                    // Check session validity
                    if (!repository.isConfigValid()) {
                        Log.w("VisionCentral", "[Heartbeat] Sessão inválida no Supabase. Parando heartbeat.")
                        _uiState.value = UiState.Activation("Dispositivo desvinculado.")
                        break
                    }
                    
                    // Heartbeat only for online status every 30 seconds (standard heartbeat)
                    Log.d("VisionCentral", "[Heartbeat] Atualizando status ONLINE...")
                    repository.updateHeartbeat(isSync = false)
                } catch (e: Exception) {
                    Log.e("VisionCentral", "[Heartbeat] Erro no loop de heartbeat", e)
                }
                
                delay(30000)
            }
        }
    }

    fun unlink() {
        viewModelScope.launch {
            repository.stopRealtimeSync()
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
        heartbeatJob?.cancel()
        Log.d("VisionCentral", "[Heartbeat] PlayerViewModel onCleared. Heartbeat cancelado localmente.")
    }
}
