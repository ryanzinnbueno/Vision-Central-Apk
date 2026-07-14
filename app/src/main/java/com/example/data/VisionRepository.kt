package com.example.data

import android.content.Context
import androidx.room.Room
import com.example.data.local.DeviceConfig
import com.example.data.local.LocalMediaItem
import com.example.data.local.LocalPlaylist
import com.example.data.local.VisionDatabase
import com.example.data.model.Midia
import com.example.data.model.Playlist
import com.example.data.model.PlaylistMidia
import com.example.data.model.Tv
import com.example.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.example.data.download.MediaDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class VisionRepository(context: Context) {
    private val db = Room.databaseBuilder(
        context.applicationContext,
        VisionDatabase::class.java, "vision_central.db"
    ).fallbackToDestructiveMigration().build()
    private val dao = db.dao()
    private val json = Json { ignoreUnknownKeys = true }
    private val downloadManager = MediaDownloadManager(context, dao)

    val downloadProgress = downloadManager.downloadProgress
    val isDownloading = downloadManager.isDownloading
    val storageError = downloadManager.storageError
    val configFlow = dao.getConfigFlow()

    suspend fun getOrCreateConfig(): DeviceConfig {
        val existing = dao.getConfig()
        if (existing != null) return existing

        val deviceId = UUID.randomUUID().toString()
        val config = DeviceConfig(deviceId = deviceId)
        dao.saveConfig(config)
        return config
    }

    fun normalizeToken(raw: String): String {
        return raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
    }

    suspend fun validateToken(rawToken: String): DeviceConfig? {
        val normalized = normalizeToken(rawToken)
        try {
            val tvs = SupabaseClient.client.postgrest["tvs"]
                .select()
                .decodeList<Tv>()
            
            val matchedTv = tvs.find { normalizeToken(it.token) == normalized }
            
            if (matchedTv != null) {
                val currentConfig = dao.getConfig()!!
                val newConfig = currentConfig.copy(
                    token = matchedTv.token,
                    clienteId = matchedTv.clienteId,
                    tvId = matchedTv.id,
                    tvName = matchedTv.nome,
                    isLinked = true
                )
                dao.saveConfig(newConfig)
                return newConfig
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    suspend fun isConfigValid(): Boolean {
        val config = dao.getConfig() ?: return false
        if (!config.isLinked || config.tvId == null) return false
        
        try {
            val tv = SupabaseClient.client.postgrest["tvs"]
                .select {
                    filter { eq("id", config.tvId) }
                }.decodeSingleOrNull<Tv>()
            
            if (tv == null) {
                // TV was deleted from Supabase
                dao.saveConfig(config.copy(isLinked = false, clienteId = null, tvId = null))
                return false
            }
            return true
        } catch (e: Exception) {
            // If network error, assume it's still valid but offline
            return true
        }
    }

    suspend fun syncPlaylist(clienteId: String): LocalPlaylist? {
        try {
            val config = dao.getConfig()!!
            val tvId = config.tvId ?: return null
            
            val tv = SupabaseClient.client.postgrest["tvs"]
                .select { filter { eq("id", tvId) } }
                .decodeSingleOrNull<Tv>()

            val cliente = SupabaseClient.client.postgrest["clientes"]
                .select { filter { eq("id", clienteId) } }
                .decodeSingleOrNull<com.example.data.model.Cliente>()

            val playlistId = tv?.playlistId ?: cliente?.playlistId ?: return null

            // Update configuration with orientation from cliente
            cliente?.let {
                val currentConfig = dao.getConfig()!!
                if (currentConfig.orientacao != it.orientation) {
                    dao.saveConfig(currentConfig.copy(orientacao = it.orientation))
                }
            }

            // 2. Get Playlist details
            val playlist = SupabaseClient.client.postgrest["playlists"]
                .select { filter { eq("id", playlistId) } }
                .decodeSingleOrNull<Playlist>() ?: return null

            // 3. Get Media relations
            val relations = SupabaseClient.client.postgrest["playlist_midias"]
                .select {
                    filter { eq("playlist_id", playlistId) }
                    order("ordem", Order.ASCENDING)
                }.decodeList<PlaylistMidia>()

            // 4. Get Media details
            val mediaIds = relations.map { it.midiaId }.distinct()
            val allMedia = SupabaseClient.client.postgrest["midias"]
                .select {
                    filter {
                        isIn("id", mediaIds)
                    }
                }.decodeList<Midia>()

            // 5. Map to LocalMediaItem
            val items = relations.mapNotNull { rel ->
                val media = allMedia.find { it.id == rel.midiaId } ?: return@mapNotNull null
                val downloaded = dao.getDownloadedMedia(media.id)
                LocalMediaItem(
                    id = media.id,
                    nome = media.nome,
                    tipo = media.tipo,
                    url = media.urlStorage,
                    localPath = if (downloaded != null && java.io.File(downloaded.localPath).exists()) downloaded.localPath else null,
                    duracao = rel.duracao,
                    ordem = rel.ordem
                )
            }

            val localPlaylist = LocalPlaylist(
                id = playlist.id,
                nome = playlist.nome,
                itemsJson = json.encodeToString(items)
            )
            dao.savePlaylist(localPlaylist)

            // Trigger background downloads
            CoroutineScope(Dispatchers.IO).launch {
                items.forEach { item ->
                    // Attempt to pass size if available in the model
                    val expectedSize = allMedia.find { it.id == item.id }?.tamanho?.toLongOrNull()
                    downloadManager.downloadMedia(item.id, item.url, expectedSize = expectedSize)
                }
                // Cleanup media not in current playlist
                val usedIds = items.map { it.id }.toSet()
                downloadManager.cleanupUnusedMedia(usedIds)
            }

            return localPlaylist

        } catch (e: Exception) {
            e.printStackTrace()
            return dao.getPlaylist()
        }
    }

    suspend fun updateStatus(status: String) {
        try {
            val config = dao.getConfig() ?: return
            val tvId = config.tvId ?: return
            val update = mapOf(
                "status" to status,
                "ultima_conexao" to if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    java.time.OffsetDateTime.now().toString()
                } else {
                    java.util.Calendar.getInstance().time.toString()
                }
            )
            SupabaseClient.client.postgrest["tvs"].update(update) {
                filter { eq("id", tvId) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun setPlaybackMode(active: Boolean) {
        val config = dao.getConfig() ?: return
        dao.saveConfig(config.copy(modoReproducaoAtivo = active))
    }

    suspend fun getLocalPlaylist(): LocalPlaylist? = dao.getPlaylist()

    suspend fun unlink() {
        dao.clearPlaylist()
        val config = dao.getConfig() ?: return
        val newConfig = config.copy(
            token = null,
            clienteId = null,
            tvId = null,
            tvName = null,
            isLinked = false
        )
        dao.saveConfig(newConfig)
    }
    
    fun parseItems(itemsJson: String): List<LocalMediaItem> {
        return try {
            json.decodeFromString(itemsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
