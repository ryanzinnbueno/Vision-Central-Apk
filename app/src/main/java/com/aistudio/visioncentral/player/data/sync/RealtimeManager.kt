package com.aistudio.visioncentral.player.data.sync

import android.util.Log
import com.aistudio.visioncentral.player.data.local.VisionDao
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class RealtimeManager(private val dao: VisionDao) {
    private var realtimeJob: Job? = null
    private var currentChannel: RealtimeChannel? = null

    interface RealtimeListener {
        fun onUpdateReceived(tvId: String)
    }

    private var listener: RealtimeListener? = null

    fun setListener(listener: RealtimeListener) {
        this.listener = listener
    }

    fun start(scope: CoroutineScope) {
        realtimeJob?.cancel()
        
        realtimeJob = scope.launch {
            try {
                // Clear any previous channel
                currentChannel?.let {
                    try {
                        it.unsubscribe()
                        SupabaseClient.client.realtime.removeChannel(it)
                    } catch (e: Exception) {
                        Log.e("VisionCentral", "[Em tempo real] Erro ao limpar canal anterior", e)
                    }
                }
                currentChannel = null
            } catch (e: Exception) {
                // Ignore
            }

            val config = dao.getConfig() ?: run {
                Log.e("VisionCentral", "[Em tempo real] Erro: Configuração local ausente")
                return@launch
            }
            val tvId = config.tvId ?: run {
                Log.e("VisionCentral", "[Em tempo real] Erro: TV ID não vinculado")
                return@launch
            }
            
            Log.d("VisionCentral", "[Em tempo real] Iniciando monitoramento para TV: $tvId")
            
            try {
                SupabaseClient.client.realtime.status.onEach { status ->
                    when (status) {
                        Realtime.Status.CONNECTED -> {
                            Log.i("VisionCentral", "[Em tempo real] WebSocket conectado")
                        }
                        Realtime.Status.DISCONNECTED -> Log.w("VisionCentral", "[Em tempo real] Realtime desconectado")
                        Realtime.Status.CONNECTING -> Log.d("VisionCentral", "[Em tempo real] Reconectando")
                        else -> {}
                    }
                }.launchIn(this)

                SupabaseClient.client.realtime.connect()
                val channel = SupabaseClient.client.realtime.channel("tvs_changes_${tvId}")
                currentChannel = channel
                
                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "tvs"
                }.onEach { action ->
                    val record = action.record
                    val oldRecord = action.oldRecord
                    val recordId = record["id"]?.jsonPrimitive?.contentOrNull
                    
                    if (recordId == tvId) {
                        val ignoredKeys = setOf("ultima_conexao", "ultima_sincronizacao", "status", "uptime")
                        var hasRelevantChange = false
                        
                        for ((key, newValue) in record) {
                            if (key in ignoredKeys) continue
                            val oldValue = oldRecord[key]
                            if (oldValue != newValue) {
                                hasRelevantChange = true
                                break
                            }
                        }
                        
                        if (hasRelevantChange) {
                            Log.i("VisionCentral", "[Em tempo real] Alteração relevante recebida para esta TV")
                            listener?.onUpdateReceived(recordId)
                        } else {
                            Log.d("VisionCentral", "[Em tempo real] Alteração apenas de heartbeat. Ignorando.")
                        }
                    }
                }.launchIn(this)
                
                channel.subscribe()
                Log.d("VisionCentral", "[Em tempo real] Subscription ativa")
            } catch (e: Exception) {
                // Ignore WebSocketCapability errors, do not crash or block
                if (e.message?.contains("WebSocketCapability") == true) {
                    Log.e("VisionCentral", "[Em tempo real] Engine doesn't support WebSocketCapability. Reprodução continua via HTTP.")
                } else {
                    Log.e("VisionCentral", "[Em tempo real] Erro ao iniciar Realtime", e)
                }
            }
        }
    }

    fun stop() {
        realtimeJob?.cancel()
        realtimeJob = null
        val channelToClose = currentChannel
        currentChannel = null
        
        if (channelToClose != null) {
            CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    channelToClose.unsubscribe()
                    SupabaseClient.client.realtime.removeChannel(channelToClose)
                } catch (e: Exception) {
                    Log.e("VisionCentral", "[Em tempo real] Erro ao parar Realtime", e)
                }
            }
        }
        Log.d("VisionCentral", "[Em tempo real] Realtime interrompido")
    }
}
