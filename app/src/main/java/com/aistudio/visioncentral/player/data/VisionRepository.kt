package com.aistudio.visioncentral.player.data

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.aistudio.visioncentral.player.data.local.DeviceConfig
import com.aistudio.visioncentral.player.data.local.LocalMediaItem
import com.aistudio.visioncentral.player.data.local.LocalPlaylist
import com.aistudio.visioncentral.player.data.local.VisionDatabase
import com.aistudio.visioncentral.player.data.download.MediaDownloadManager
import com.aistudio.visioncentral.player.data.repository.ConfigRepository
import com.aistudio.visioncentral.player.data.repository.PlaylistRepository
import com.aistudio.visioncentral.player.data.sync.HeartbeatManager
import com.aistudio.visioncentral.player.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VisionRepository(context: Context) {

    private val db = Room.databaseBuilder(
        context.applicationContext,
        VisionDatabase::class.java, "vision_central.db"
    ).fallbackToDestructiveMigration().build()

    private val dao = db.dao()
    private val downloadManager = MediaDownloadManager(context, dao)

    private val configRepository = ConfigRepository(dao)
    private val playlistRepository = PlaylistRepository(dao, downloadManager)
    private val heartbeatManager = HeartbeatManager(dao)
    private val syncScheduler = SyncScheduler(dao) {
        syncTvSettings()
    }
    
    private val syncMutex = Mutex()

    val downloadProgress = downloadManager.downloadProgress
    val isDownloading = downloadManager.isDownloading
    val storageError = downloadManager.storageError

    val configFlow = dao.getConfigFlow()
    val playlistFlow = dao.getPlaylistFlow()

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
        return syncMutex.withLock {
            Log.d("VisionCentral", "[SYNC] Iniciando sincronização...")
            try {
                val config = configRepository.syncTvSettings()
                if (config != null) {
                    if (config.clienteId != null) {
                        playlistRepository.syncPlaylist(config.clienteId)
                        heartbeatManager.sendHeartbeat(isSync = true)
                    } else {
                        heartbeatManager.sendHeartbeat(isSync = false)
                    }
                    Log.d("VisionCentral", "[SYNC] Sincronização concluída com sucesso.")
                    return@withLock true
                } else {
                    Log.d("VisionCentral", "[SYNC] Sincronização falhou (config nula).")
                }
            } catch (e: Exception) {
                Log.e("VisionCentral", "[SYNC] Erro na sincronização", e)
            }
            return@withLock false
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
        stopHeartbeat()
    }

    fun parseItems(itemsJson: String): List<LocalMediaItem> = playlistRepository.parseItems(itemsJson)
}
