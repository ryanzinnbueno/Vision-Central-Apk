package com.aistudio.visioncentral.player.data.sync

import android.util.Log
import com.aistudio.visioncentral.player.data.local.VisionDao
import com.aistudio.visioncentral.player.data.model.HeartbeatUpdate
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HeartbeatManager(private val dao: VisionDao) {
    private var heartbeatJob: Job? = null

    fun start(scope: CoroutineScope) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                try {
                    sendHeartbeat(false)
                } catch (e: Exception) {
                    Log.e("VisionCentral", "[Batimento cardíaco] Erro no loop", e)
                }
                delay(30000)
            }
        }
    }

    fun stop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    suspend fun sendHeartbeat(isSync: Boolean) {
        try {
            val config = dao.getConfig() ?: return
            val tvId = config.tvId ?: return
            
            val timestamp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.Instant.now().toString()
            } else {
                java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).let {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    sdf.format(it.time)
                }
            }
            
            val update = HeartbeatUpdate(
                status = "Online",
                ultimaConexao = timestamp,
                ultimaSincronizacao = if (isSync) timestamp else null
            )
            
            Log.d("VisionCentral", "[Batimento cardíaco] Enviando heartbeat")
            SupabaseClient.client.postgrest["tvs"].update(update) {
                filter { eq("id", tvId) }
            }
            Log.d("VisionCentral", "[Batimento cardíaco] Batimento cardíaco enviado")
        } catch (e: Exception) {
            Log.e("VisionCentral", "[Batimento cardíaco] Heartbeat falhou", e)
        }
    }
}
