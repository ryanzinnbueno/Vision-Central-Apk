package com.aistudio.visioncentral.player.data.sync

import android.util.Log
import com.aistudio.visioncentral.player.data.local.VisionDao
import com.aistudio.visioncentral.player.data.model.Tv
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SyncScheduler(
    private val dao: VisionDao,
    private val onSyncRequested: suspend () -> Unit
) {
    private var syncJob: Job? = null

    fun start(scope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (true) {
                delay(15000) // 15 seconds
                try {
                    val config = dao.getConfig()
                    val tvId = config?.tvId
                    if (tvId != null) {
                        val tv = SupabaseClient.client.postgrest["tvs"]
                            .select {
                                filter { eq("id", tvId) }
                            }.decodeSingleOrNull<Tv>()
                            
                        if (tv != null) {
                            val remoteUpdate = tv.updatedAt
                            val localUpdate = config.lastServerUpdate
                            
                            if (remoteUpdate != null && remoteUpdate != localUpdate) {
                                Log.d("VisionCentral", "[SYNC] Alteração detectada no servidor (updated_at). Executando sincronização...")
                                onSyncRequested()
                            } else {
                                Log.d("VisionCentral", "[SYNC] Nenhuma alteração detectada. Ignorando sincronização.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("VisionCentral", "[SYNC] Erro no polling de segurança", e)
                }
            }
        }
    }

    fun stop() {
        syncJob?.cancel()
        syncJob = null
    }
}
