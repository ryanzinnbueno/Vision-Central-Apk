package com.aistudio.visioncentral.player.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.aistudio.visioncentral.player.data.local.DeviceConfig
import com.aistudio.visioncentral.player.data.local.LocalMediaItem
import com.aistudio.visioncentral.player.data.local.LocalPlaylist
import com.aistudio.visioncentral.player.data.local.VisionDatabase
import com.aistudio.visioncentral.player.data.model.Midia
import com.aistudio.visioncentral.player.data.model.Playlist
import com.aistudio.visioncentral.player.data.model.PlaylistMidia
import com.aistudio.visioncentral.player.data.model.Tv
import com.aistudio.visioncentral.player.data.model.HeartbeatUpdate
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.aistudio.visioncentral.player.data.download.MediaDownloadManager
import com.aistudio.visioncentral.player.data.repository.ConfigRepository
import com.aistudio.visioncentral.player.data.repository.PlaylistRepository
import com.aistudio.visioncentral.player.data.sync.RealtimeManager
import com.aistudio.visioncentral.player.data.sync.HeartbeatManager
import com.aistudio.visioncentral.player.data.sync.SyncScheduler
import com.aistudio.visioncentral.player.data.sync.SyncType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID

class VisionRepository(context: Context) {

    private val db = Room.databaseBuilder(
        context.applicationContext,
        VisionDatabase::class.java, "vision_central.db"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.dao()
    private val downloadManager = MediaDownloadManager(context, dao)

    private val configRepository = ConfigRepository(dao)
    private val playlistRepository = PlaylistRepository(dao, downloadManager)
    private val realtimeManager = RealtimeManager(dao)
    private val heartbeatManager = HeartbeatManager(dao)
    private val syncScheduler = SyncScheduler {
        checkAndSyncIfNeeded()
    }
    
    private val isSyncRunning = AtomicBoolean(false)
    private var pendingSyncType: SyncType? = null
    private var lastSyncTime = 0L

    val downloadProgress = downloadManager.downloadProgress
    val isDownloading = downloadManager.isDownloading
    val storageError = downloadManager.storageError

    val configFlow = dao.getConfigFlow()
    val playlistFlow = dao.getPlaylistFlow()

    init {
        realtimeManager.setListener(object : RealtimeManager.RealtimeListener {
            override fun onUpdateReceived(tvId: String, syncType: SyncType) {
                CoroutineScope(Dispatchers.IO).launch {
                    syncTvSettings(syncType)
                }
            }
        })
    }

    fun startRealtimeSync(scope: CoroutineScope) {
        realtimeManager.start(scope)
    }

    fun stopRealtimeSync() {
        realtimeManager.stop()
    }

    fun startHeartbeat(scope: CoroutineScope) {
        heartbeatManager.start(scope)
        syncScheduler.start(scope)
    }

    fun stopHeartbeat() {
        heartbeatManager.stop()
        syncScheduler.stop()
    }

    suspend fun getOrCreateConfig(): DeviceConfig = configRepository.getOrCreateConfig()

    suspend fun validateToken(rawToken: String): DeviceConfig? = configRepository.validateToken(rawToken)

    suspend fun isConfigValid(): Boolean = configRepository.isConfigValid()

    private suspend fun checkAndSyncIfNeeded() {
        val hasPending = synchronized(this) { pendingSyncType != null }
        if (hasPending) {
            Log.d("VisionCentral", "[SYNC] Scheduler encontrou sincronização pendente. Executando...")
            syncTvSettings(SyncType.FULL_SYNC)
        } else {
            val timeSinceLastSync = System.currentTimeMillis() - lastSyncTime
            if (timeSinceLastSync > 280000) { // slightly less than 5 mins
                Log.d("VisionCentral", "[SYNC] Timeout atingido. Executando sincronização de segurança...")
                syncTvSettings(SyncType.FULL_SYNC)
            } else {
                Log.d("VisionCentral", "[SYNC] Sincronização recente ($timeSinceLastSync ms atrás). Ignorando polling.")
            }
        }
    }

    suspend fun syncTvSettings(type: SyncType = SyncType.FULL_SYNC): Boolean {
        if (!isSyncRunning.compareAndSet(false, true)) {
            synchronized(this) {
                if (type == SyncType.FULL_SYNC) {
                    pendingSyncType = SyncType.FULL_SYNC
                } else if (pendingSyncType == null) {
                    pendingSyncType = SyncType.CONFIG_ONLY
                }
            }
            Log.d("VisionCentral", "[SYNC] Sincronização em andamento. Adicionado à fila: $type")
            return true
        }
        
        var currentType = type
        var success = false
        
        while (true) {
            Log.d("VisionCentral", "[SYNC] Iniciando sincronização: $currentType")
            try {
                val config = configRepository.syncTvSettings()
                if (config != null) {
                    if (currentType == SyncType.FULL_SYNC && config.clienteId != null) {
                        playlistRepository.syncPlaylist(config.clienteId!!)
                        heartbeatManager.sendHeartbeat(isSync = true)
                    } else {
                        heartbeatManager.sendHeartbeat(isSync = false)
                    }
                    Log.d("VisionCentral", "[SYNC] Sincronização concluída com sucesso.")
                    lastSyncTime = System.currentTimeMillis()
                    success = true
                } else {
                    Log.d("VisionCentral", "[SYNC] Sincronização falhou (config nula).")
                }
            } catch (e: Exception) {
                Log.e("VisionCentral", "[SYNC] Erro na sincronização", e)
            } finally {
                var nextType: SyncType? = null
                synchronized(this) {
                    nextType = pendingSyncType
                    pendingSyncType = null
                    if (nextType == null) {
                        isSyncRunning.set(false)
                    }
                }
                if (nextType == null) break
                currentType = nextType!!
            }
        }
        return success
    }

    suspend fun syncPlaylist(clienteId: String): LocalPlaylist? {
        return playlistRepository.syncPlaylist(clienteId).also {
            heartbeatManager.sendHeartbeat(isSync = it != null)
        }
    }

    suspend fun unlink() {
        configRepository.unlink()
        playlistRepository.clear()
        stopRealtimeSync()
        stopHeartbeat()
    }

    fun parseItems(itemsJson: String): List<LocalMediaItem> = playlistRepository.parseItems(itemsJson)
}
