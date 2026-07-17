package com.aistudio.visioncentral.player.data.sync

import android.util.Log
import com.aistudio.visioncentral.player.data.local.VisionDao
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
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
            val config = dao.getConfig() ?: run {
                Log.e("VisionCentral", "[Em tempo real] Erro: Configuração local ausente")
                return@launch
            }
            val tvId = config.tvId ?: run {
                Log.e("VisionCentral", "[Em tempo real] Erro: TV ID não vinculado")
                return@launch
            }
            
            Log.d("VisionCentral", "[SYNC-1] Realtime iniciado")
            Log.d("VisionCentral", "[Em tempo real] Iniciando monitoramento para TV: $tvId")
            
            try {
                SupabaseClient.client.realtime.status.onEach { status ->
                    when (status) {
                        Realtime.Status.CONNECTED -> {
                            Log.i("VisionCentral", "[SYNC-2] WebSocket conectado")
                            Log.i("VisionCentral", "[Em tempo real] WebSocket conectado")
                        }
                        Realtime.Status.DISCONNECTED -> Log.w("VisionCentral", "[Em tempo real] Realtime desconectado")
                        Realtime.Status.CONNECTING -> Log.d("VisionCentral", "[Em tempo real] Reconectando")
                        else -> {}
                    }
                }.launchIn(this)

                SupabaseClient.client.realtime.connect()

                val channel = SupabaseClient.client.realtime.channel("tvs_changes")
                Log.d("VisionCentral", "[Em tempo real] Canal registrado")
                
                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "tvs"
                }.onEach { action ->
                    val record = action.record
                    val oldRecord = action.oldRecord
                    val recordId = record["id"]?.jsonPrimitive?.contentOrNull
                    val token = record["token"]?.jsonPrimitive?.contentOrNull
                    
                    Log.d("VisionCentral", "[SYNC-4] JSON COMPLETO recebido")
                    Log.d("VisionCentral", "  - Table: tvs")
                    Log.d("VisionCentral", "  - Schema: public")
                    Log.d("VisionCentral", "  - Event: UPDATE")
                    Log.d("VisionCentral", "  - Record Novo: $record")
                    Log.d("VisionCentral", "  - Record Antigo: $oldRecord")
                    Log.d("VisionCentral", "  - TV ID: $recordId")
                    Log.d("VisionCentral", "  - Token: $token")
                    
                    if (recordId == tvId) {
                        Log.i("VisionCentral", "[Em tempo real] Evento recebido para esta TV")
                        listener?.onUpdateReceived(recordId)
                    } else {
                        Log.d("VisionCentral", "[Em tempo real] Evento ignorado (TV: $recordId)")
                    }
                }.launchIn(this)
                
                channel.subscribe()
                Log.d("VisionCentral", "[SYNC-3] Subscription ativa")
                Log.d("VisionCentral", "[Em tempo real] Subscription ativa")

            } catch (e: Exception) {
                Log.e("VisionCentral", "[Em tempo real] Erro ao iniciar Realtime", e)
            }
        }
    }

    fun stop() {
        realtimeJob?.cancel()
        realtimeJob = null
        Log.d("VisionCentral", "[Em tempo real] Realtime interrompido")
    }
}
