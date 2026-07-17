package com.aistudio.visioncentral.player.data.repository

import android.util.Log
import com.aistudio.visioncentral.player.data.download.MediaDownloadManager
import com.aistudio.visioncentral.player.data.local.LocalMediaItem
import com.aistudio.visioncentral.player.data.local.LocalPlaylist
import com.aistudio.visioncentral.player.data.local.VisionDao
import com.aistudio.visioncentral.player.data.model.Midia
import com.aistudio.visioncentral.player.data.model.Playlist
import com.aistudio.visioncentral.player.data.model.PlaylistMidia
import com.aistudio.visioncentral.player.data.model.Tv
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PlaylistRepository(
    private val dao: VisionDao,
    private val downloadManager: MediaDownloadManager
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun syncPlaylist(clienteId: String): LocalPlaylist? {
        try {
            Log.d("VisionCentral", "[Lista de reprodução] Sincronizando playlist para cliente: $clienteId")
            
            // Get TV to find linked playlist
            val config = dao.getConfig() ?: return null
            val tvId = config.tvId ?: return null
            val tv = SupabaseClient.client.postgrest["tvs"]
                .select { filter { eq("id", tvId) } }
                .decodeSingleOrNull<Tv>() ?: return null

            val cliente = SupabaseClient.client.postgrest["clientes"]
                .select { filter { eq("id", clienteId) } }
                .decodeSingleOrNull<com.aistudio.visioncentral.player.data.model.Cliente>()

            val playlistId = tv.playlistId ?: cliente?.playlistId ?: run {
                Log.d("VisionCentral", "[Lista de reprodução] Nenhuma playlist vinculada")
                return null
            }

            val playlist = SupabaseClient.client.postgrest["playlists"]
                .select { filter { eq("id", playlistId) } }
                .decodeSingleOrNull<Playlist>() ?: return null

            val relations = SupabaseClient.client.postgrest["playlist_midias"]
                .select {
                    filter { eq("playlist_id", playlistId) }
                    order("ordem", Order.ASCENDING)
                }.decodeList<PlaylistMidia>()

            val mediaIds = relations.map { it.midiaId }.distinct()
            val allMedia = if (mediaIds.isNotEmpty()) {
                SupabaseClient.client.postgrest["midias"]
                    .select { filter { isIn("id", mediaIds) } }
                    .decodeList<Midia>()
            } else emptyList()

            val items = relations.mapNotNull { rel ->
                val media = allMedia.find { it.id == rel.midiaId } ?: return@mapNotNull null
                val mediaUrl = if (media.origem?.lowercase() == "url") media.urlExterna else media.urlStorage
                if (mediaUrl.isNullOrEmpty()) return@mapNotNull null

                val downloaded = dao.getDownloadedMedia(media.id)
                LocalMediaItem(
                    id = media.id,
                    nome = media.nome,
                    tipo = media.tipo,
                    url = mediaUrl,
                    origem = media.origem ?: "storage",
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
            
            val existing = dao.getPlaylist()
            if (existing?.itemsJson != localPlaylist.itemsJson || existing?.id != localPlaylist.id) {
                Log.d("VisionCentral", "[Lista de reprodução] Lista de reprodução alterada")
                dao.savePlaylist(localPlaylist)
                
                CoroutineScope(Dispatchers.IO).launch {
                    Log.d("VisionCentral", "[Lista de reprodução] Download iniciado")
                    items.forEach { item ->
                        if (item.origem.lowercase() != "url") {
                            val expectedSize = allMedia.find { it.id == item.id }?.tamanho?.toLongOrNull()
                            downloadManager.downloadMedia(item.id, item.url, expectedSize = expectedSize)
                        }
                    }
                    downloadManager.cleanupUnusedMedia(items.map { it.id }.toSet())
                    Log.d("VisionCentral", "[Lista de reprodução] Download concluído")
                }
            } else {
                Log.d("VisionCentral", "[Lista de reprodução] Nenhuma alteração na playlist")
            }
            return localPlaylist
        } catch (e: Exception) {
            Log.e("VisionCentral", "[Lista de reprodução] Erro na sincronização", e)
            return null
        }
    }

    suspend fun clear() {
        dao.clearPlaylist()
    }

    fun parseItems(itemsJson: String): List<LocalMediaItem> {
        return try {
            json.decodeFromString(itemsJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
