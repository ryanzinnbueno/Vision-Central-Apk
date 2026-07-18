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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.UUID

class VisionRepository(context: Context) {
    private val instanceHash = System.identityHashCode(this)
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

    val downloadProgress = downloadManager.downloadProgress
    val isDownloading = downloadManager.isDownloading
    val storageError = downloadManager.storageError
    val configFlow = dao.getConfigFlow()
    val playlistFlow = dao.getPlaylistFlow()

    init {
        Log.d("VisionCentral", "[AUDIT] VisionRepository INSTANCE CREATED - Time: ${java.util.Date()} - Hash: $instanceHash - Thread: ${Thread.currentThread().name}")
        realtimeManager.setListener(object : RealtimeManager.RealtimeListener {
            override fun onUpdateReceived(tvId: String) {
                Log.d("VisionCentral", "[SYNC-5] Recebi evento do Realtime")
                Log.d("VisionCentral", "[VisionRepository] Chamando ConfigRepository")
                CoroutineScope(Dispatchers.IO).launch {
                    syncTvSettings()
                }
            }
        })

        configFlow.onEach {
            Log.d("VisionCentral", "[SYNC-10] configFlow emitiu")
        }.launchIn(CoroutineScope(Dispatchers.IO))

        playlistFlow.onEach {
            Log.d("VisionCentral", "[SYNC-10] playlistFlow emitiu")
        }.launchIn(CoroutineScope(Dispatchers.IO))
    }

    fun startRealtimeSync(scope: CoroutineScope) {
        Log.d("VisionCentral", "[AUDIT] VisionRepository.startRealtimeSync called - Time: ${java.util.Date()} - Hash: $instanceHash - Thread: ${Thread.currentThread().name} - Caller: ${Log.getStackTraceString(Throwable())}")
        realtimeManager.start(scope)
    }

    fun stopRealtimeSync() {
        Log.d("VisionCentral", "[AUDIT] VisionRepository.stopRealtimeSync called - Time: ${java.util.Date()} - Hash: $instanceHash - Thread: ${Thread.currentThread().name} - Caller: ${Log.getStackTraceString(Throwable())}")
        realtimeManager.stop()
    }

    fun startHeartbeat(scope: CoroutineScope) {
        Log.d("VisionCentral", "[AUDIT] VisionRepository.startHeartbeat called - Time: ${java.util.Date()} - Hash: $instanceHash - Thread: ${Thread.currentThread().name} - Caller: ${Log.getStackTraceString(Throwable())}")
        heartbeatManager.start(scope)
    }

    fun stopHeartbeat() {
        Log.d("VisionCentral", "[AUDIT] VisionRepository.stopHeartbeat called - Time: ${java.util.Date()} - Hash: $instanceHash - Thread: ${Thread.currentThread().name} - Caller: ${Log.getStackTraceString(Throwable())}")
        heartbeatManager.stop()
    }

    suspend fun getOrCreateConfig(): DeviceConfig = configRepository.getOrCreateConfig()

    suspend fun validateToken(rawToken: String): DeviceConfig? = configRepository.validateToken(rawToken)

    suspend fun isConfigValid(): Boolean = configRepository.isConfigValid()

    suspend fun syncTvSettings(): Boolean {
        Log.d("VisionCentral", "[Configuração] Sincronizando...")
        val config = configRepository.syncTvSettings()
        if (config != null && config.clienteId != null) {
            playlistRepository.syncPlaylist(config.clienteId!!)
            heartbeatManager.sendHeartbeat(isSync = true)
            return true
        }
        heartbeatManager.sendHeartbeat(isSync = false)
        return config != null
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
