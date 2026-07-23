package com.aistudio.visioncentral.player.data.sync

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SyncScheduler(private val onSyncRequested: suspend () -> Unit) {
    private var syncJob: Job? = null

    fun start(scope: CoroutineScope) {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (true) {
                delay(300000) // 5 minutes
                try {
                    Log.d("VisionCentral", "[SYNC] Iniciando polling de segurança...")
                    onSyncRequested()
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
