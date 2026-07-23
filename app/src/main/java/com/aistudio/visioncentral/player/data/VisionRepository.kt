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
        syncTvSettings()
    }
    private val isSyncRunning = AtomicBoolean(false)

    val downloadProgress = downloadManager.downloadProgress
    val isDownloading = downloadManager.isDownloading
    val storageError = downloadManager.storageError

    val configFlow = dao.getConfigFlow()
    val playlistFlow = dao.getPlaylistFlow()

    init {
        realtimeManager.setListener(object : RealtimeManager.RealtimeListener {
            override fun onUpdateReceived(tvId: String) {
                CoroutineScope(Dispatchers.IO).launch {
                    syncTvSettings()
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

    suspend fun syncTvSettings(): Boolean {
        if (!isSyncRunning.compareAndSet(false, true)) {
            Log.d("VisionCentral", "[Configuração] Sincronização já em andamento. Ignorando.")
            return false
        }
        
        Log.d("VisionCentral", "[Configuração] Iniciando sincronização...")
        try {
            val config = configRepository.syncTvSettings()
            if (config != null && config.clienteId != null) {
                playlistRepository.syncPlaylist(config.clienteId!!)
                heartbeatManager.sendHeartbeat(isSync = true)
                Log.d("VisionCentral", "[Configuração] Sincronização concluída com sucesso.")
                return true
            }
            heartbeatManager.sendHeartbeat(isSync = false)
            Log.d("VisionCentral", "[Configuração] Sincronização parcial (sem playlist).")
            return config != null
        } catch (e: Exception) {
            Log.e("VisionCentral", "[Configuração] Erro na sincronização", e)
            return false
        } finally {
            isSyncRunning.set(false)
        }
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
