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

import com.example.data.model.TvConfig

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
            // 1. Search directly in Supabase for the TV with this token
            // We use Case-Insensitive search if possible, or just exact match on normalized
            val matchedTv = SupabaseClient.client.postgrest["tvs"]
                .select {
                    filter {
                        eq("token", rawToken.trim()) // Try exact first
                    }
                }.decodeSingleOrNull<Tv>() 
                ?: SupabaseClient.client.postgrest["tvs"]
                    .select()
                    .decodeList<Tv>()
                    .find { normalizeToken(it.token) == normalized }

            if (matchedTv != null) {
                val currentConfig = dao.getConfig()!!
                val newConfig = currentConfig.copy(
                    token = matchedTv.token,
                    clienteId = matchedTv.clienteId,
                    tvId = matchedTv.id,
                    tvName = matchedTv.nome,
                    isLinked = true,
                    // Use existing settings from the TV record
                    orientacao = matchedTv.orientacao ?: "Horizontal",
                    rotacao = matchedTv.rotacao ?: "0",
                    proporcao = matchedTv.proporcao ?: "16:9",
                    resolucao = matchedTv.resolucao ?: "1080p",
                    ajusteTela = matchedTv.ajusteTela ?: "Cover",
                    modoReproducaoAtivo = matchedTv.modoReproducao ?: true
                )
                dao.saveConfig(newConfig)
                
                // Immediately sync playlist and full config to apply all panel settings
                matchedTv.clienteId?.let { syncPlaylist(it) }
                
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
            
            // Fetch separate config if exists (tabela configuracoes)
            val tvConfig: TvConfig? = try {
                SupabaseClient.client.postgrest["configuracoes"]
                    .select { filter { eq("tv_id", tvId) } }
                    .decodeSingleOrNull<TvConfig>()
            } catch (e: Exception) {
                null
            }

            // Update configuration
            val currentConfig = dao.getConfig()!!
            var updatedConfig = currentConfig
            
            if (tv != null) {
                if (currentConfig.tvName != tv.nome) {
                    updatedConfig = updatedConfig.copy(tvName = tv.nome)
                }
                // Sync from TV fields if available
                tv.rotacao?.let { updatedConfig = updatedConfig.copy(rotacao = it) }
                tv.orientacao?.let { updatedConfig = updatedConfig.copy(orientacao = it) }
                tv.proporcao?.let { updatedConfig = updatedConfig.copy(proporcao = it) }
                tv.resolucao?.let { updatedConfig = updatedConfig.copy(resolucao = it) }
                tv.ajusteTela?.let { updatedConfig = updatedConfig.copy(ajusteTela = it) }
                tv.modoReproducao?.let { updatedConfig = updatedConfig.copy(modoReproducaoAtivo = it) }
            }
            
            // Override with separate config if available
            tvConfig?.let { cfg ->
                updatedConfig = updatedConfig.copy(
                    rotacao = cfg.rotacao ?: updatedConfig.rotacao,
                    orientacao = cfg.orientacao ?: updatedConfig.orientacao,
                    proporcao = cfg.proporcao ?: updatedConfig.proporcao,
                    resolucao = cfg.resolucao ?: updatedConfig.resolucao,
                    ajusteTela = cfg.ajusteTela ?: updatedConfig.ajusteTela,
                    modoReproducaoAtivo = cfg.modoReproducao ?: updatedConfig.modoReproducaoAtivo
                )
            }
            
            cliente?.let {
                if (updatedConfig.orientacao != it.orientation) {
                    updatedConfig = updatedConfig.copy(orientacao = it.orientation)
                }
            }
            
            if (updatedConfig != currentConfig) {
                dao.saveConfig(updatedConfig)
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
            
            // Check if playlist actually changed before saving/triggering downloads
            val existingPlaylist = dao.getPlaylist()
            if (existingPlaylist?.itemsJson != localPlaylist.itemsJson || existingPlaylist?.id != localPlaylist.id) {
                dao.savePlaylist(localPlaylist)
                
                // Trigger background downloads
                CoroutineScope(Dispatchers.IO).launch {
                    items.forEach { item ->
                        val expectedSize = allMedia.find { it.id == item.id }?.tamanho?.toLongOrNull()
                        downloadManager.downloadMedia(item.id, item.url, expectedSize = expectedSize)
                    }
                    val usedIds = items.map { it.id }.toSet()
                    downloadManager.cleanupUnusedMedia(usedIds)
                }
            }

            updateHeartbeat(isSync = true)

            return localPlaylist

        } catch (e: Exception) {
            e.printStackTrace()
            return dao.getPlaylist()
        }
    }

    suspend fun updateHeartbeat(isSync: Boolean = false) {
        try {
            val config = dao.getConfig() ?: return
            val tvId = config.tvId ?: return
            
            val timestamp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.OffsetDateTime.now().toString()
            } else {
                java.util.Calendar.getInstance().time.toString()
            }
            
            val update = mutableMapOf<String, String>(
                "status" to "Online",
                "ultima_conexao" to timestamp
            )
            
            if (isSync) {
                update["ultima_sincronizacao"] = timestamp
            }
            
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
