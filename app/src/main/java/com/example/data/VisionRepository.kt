package com.example.data

import android.content.Context
import android.util.Log
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
        if (existing != null) {
            Log.d("VisionCentral", "Token carregado do banco local: ${existing.token}")
            return existing
        }

        Log.d("VisionCentral", "Nenhuma configuração encontrada. Criando novo deviceId.")
        val deviceId = UUID.randomUUID().toString()
        val config = DeviceConfig(deviceId = deviceId)
        dao.saveConfig(config)
        return config
    }

    suspend fun getPlaylist(): LocalPlaylist? = dao.getPlaylist()

    fun normalizeToken(raw: String): String {
        return raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
    }

    suspend fun validateToken(rawToken: String): DeviceConfig? {
        val normalized = normalizeToken(rawToken)
        Log.d("VisionCentral", "Validando token: $rawToken (Normalizado: $normalized)")
        try {
            // 1. Search directly in Supabase for the TV with this token
            Log.d("VisionCentral", "Consultando Supabase para o token: $rawToken")
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
                    modoExibicao = matchedTv.modoExibicao ?: "CenterInside",
                    brilho = matchedTv.brilho ?: 100,
                    contraste = matchedTv.contraste ?: 100,
                    saturacao = matchedTv.saturacao ?: 100,
                    zoom = matchedTv.zoom ?: 100,
                    volume = matchedTv.volume ?: 100,
                    tempoTransicao = matchedTv.tempoTransicao ?: 500,
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

    suspend fun syncTvSettings(): Boolean {
        try {
            val config = dao.getConfig() ?: run {
                Log.d("VisionCentral", "syncTvSettings interrompido: Config não encontrada no DAO")
                return false
            }
            val token = config.token ?: run {
                Log.d("VisionCentral", "syncTvSettings interrompido: Token não encontrado na configuração")
                return false
            }

            Log.d("VisionCentral", "Iniciando syncTvSettings() para o token: $token")

            // 1. Fetch TV by token (Always the source of truth)
            Log.d("VisionCentral", "Consultando Supabase (tabela tvs) pelo token: $token")
            val tv = SupabaseClient.client.postgrest["tvs"]
                .select { filter { eq("token", token) } }
                .decodeSingleOrNull<Tv>()

            if (tv == null) {
                Log.d("VisionCentral", "Sync falhou: TV não encontrada no Supabase para o token: $token")
                return false
            }

            Log.d("VisionCentral", "Supabase retornou TV: id=${tv.id}, nome=${tv.nome}")
            Log.d("VisionCentral", "Propriedades da TV:")
            Log.d("VisionCentral", "  - orientacao: ${tv.orientacao}")
            Log.d("VisionCentral", "  - proporcao: ${tv.proporcao}")
            Log.d("VisionCentral", "  - modo_exibicao: ${tv.modoExibicao}")
            Log.d("VisionCentral", "  - brilho: ${tv.brilho}")
            Log.d("VisionCentral", "  - contraste: ${tv.contraste}")
            Log.d("VisionCentral", "  - saturacao: ${tv.saturacao}")
            Log.d("VisionCentral", "  - zoom: ${tv.zoom}")
            Log.d("VisionCentral", "  - volume: ${tv.volume}")
            Log.d("VisionCentral", "  - tempo_transicao: ${tv.tempoTransicao}")
            Log.d("VisionCentral", "  - playlist_id: ${tv.playlistId}")
            Log.d("VisionCentral", "  - cliente_id: ${tv.clienteId}")

            // 2. Fetch separate config if exists (optional table configuracoes)
            Log.d("VisionCentral", "Consultando Supabase (tabela configuracoes) para tv_id=${tv.id}")
            val tvConfig: TvConfig? = try {
                SupabaseClient.client.postgrest["configuracoes"]
                    .select { filter { eq("tv_id", tv.id) } }
                    .decodeSingleOrNull<TvConfig>()
            } catch (e: Exception) {
                Log.d("VisionCentral", "Sem registros na tabela configuracoes (opcional)")
                null
            }

            if (tvConfig != null) {
                Log.d("VisionCentral", "Tabela configuracoes retornou valores. Sobrepondo...")
            }

            // 3. Prepare updated configuration
            val currentConfig = dao.getConfig()!!
            var updatedConfig = currentConfig.copy(
                tvId = tv.id,
                tvName = tv.nome ?: currentConfig.tvName,
                clienteId = tv.clienteId ?: currentConfig.clienteId,
                orientacao = tv.orientacao ?: currentConfig.orientacao ?: "Horizontal",
                rotacao = tv.rotacao ?: currentConfig.rotacao ?: "0",
                proporcao = tv.proporcao ?: currentConfig.proporcao ?: "16:9",
                resolucao = tv.resolucao ?: currentConfig.resolucao ?: "1080p",
                ajusteTela = tv.ajusteTela ?: currentConfig.ajusteTela ?: "Cover",
                modoExibicao = tv.modoExibicao ?: currentConfig.modoExibicao ?: "CenterInside",
                brilho = tv.brilho ?: currentConfig.brilho ?: 100,
                contraste = tv.contraste ?: currentConfig.contraste ?: 100,
                saturacao = tv.saturacao ?: currentConfig.saturacao ?: 100,
                zoom = tv.zoom ?: currentConfig.zoom ?: 100,
                volume = tv.volume ?: currentConfig.volume ?: 100,
                tempoTransicao = tv.tempoTransicao ?: currentConfig.tempoTransicao ?: 500,
                modoReproducaoAtivo = tv.modoReproducao ?: currentConfig.modoReproducaoAtivo ?: true
            )

            // Override with separate config if available
            tvConfig?.let { cfg ->
                println("VisionCentral: Aplicando sobreposição da tabela configuracoes para tvId=${tv.id}")
                updatedConfig = updatedConfig.copy(
                    rotacao = cfg.rotacao ?: updatedConfig.rotacao,
                    orientacao = cfg.orientacao ?: updatedConfig.orientacao,
                    proporcao = cfg.proporcao ?: updatedConfig.proporcao,
                    resolucao = cfg.resolucao ?: updatedConfig.resolucao,
                    ajusteTela = cfg.ajusteTela ?: updatedConfig.ajusteTela,
                    modoExibicao = cfg.modoExibicao ?: updatedConfig.modoExibicao,
                    brilho = cfg.brilho ?: updatedConfig.brilho,
                    contraste = cfg.contraste ?: updatedConfig.contraste,
                    saturacao = cfg.saturacao ?: updatedConfig.saturacao,
                    zoom = cfg.zoom ?: updatedConfig.zoom,
                    volume = cfg.volume ?: updatedConfig.volume,
                    tempoTransicao = cfg.tempoTransicao ?: updatedConfig.tempoTransicao,
                    modoReproducaoAtivo = cfg.modoReproducao ?: updatedConfig.modoReproducaoAtivo
                )
            }

            // Save if changed
            if (updatedConfig != currentConfig) {
                Log.d("VisionCentral", "Novas configurações detectadas. Salvando localmente.")
                dao.saveConfig(updatedConfig)
            } else {
                Log.d("VisionCentral", "Configurações sem alterações.")
            }

            // 4. Sync Playlist if we have a client ID
            updatedConfig.clienteId?.let { 
                syncPlaylistInternal(it, tv) 
            }

            return true
        } catch (e: Exception) {
            println("VisionCentral: Erro na sincronização: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    private suspend fun syncPlaylistInternal(clienteId: String, tv: Tv): LocalPlaylist? {
        try {
            val cliente = SupabaseClient.client.postgrest["clientes"]
                .select { filter { eq("id", clienteId) } }
                .decodeSingleOrNull<com.example.data.model.Cliente>()

            val playlistId = tv.playlistId ?: cliente?.playlistId ?: return null

            // Get Playlist details
            val playlist = SupabaseClient.client.postgrest["playlists"]
                .select { filter { eq("id", playlistId) } }
                .decodeSingleOrNull<Playlist>() ?: return null

            // Get Playlist Media relations
            val relations = SupabaseClient.client.postgrest["playlist_midias"]
                .select {
                    filter { eq("playlist_id", playlistId) }
                    order("ordem", Order.ASCENDING)
                }.decodeList<PlaylistMidia>()

            // Get Media details
            val mediaIds = relations.map { it.midiaId }.distinct()
            val allMedia = SupabaseClient.client.postgrest["midias"]
                .select {
                    filter {
                        isIn("id", mediaIds)
                    }
                }.decodeList<Midia>()

            // Map to LocalMediaItem
            val items = relations.mapNotNull { rel ->
                val media = allMedia.find { it.id == rel.midiaId } ?: run {
                    Log.w("VisionCentral", "Mídia ID ${rel.midiaId} não encontrada na tabela de mídias.")
                    return@mapNotNull null
                }

                val mediaUrl = when (media.origem?.lowercase()) {
                    "url" -> {
                        Log.d("VisionCentral", "Mapeando mídia URL: id=${media.id}, nome=${media.nome}, url_externa=${media.urlExterna}")
                        media.urlExterna
                    }
                    else -> {
                        Log.d("VisionCentral", "Mapeando mídia Storage: id=${media.id}, nome=${media.nome}, url_storage=${media.urlStorage}")
                        media.urlStorage
                    }
                }

                if (mediaUrl.isNullOrEmpty()) {
                    Log.w("VisionCentral", "Mídia ${media.nome} (ID: ${media.id}) ignorada: URL de origem (${media.origem}) está vazia.")
                    return@mapNotNull null
                }

                val downloaded = dao.getDownloadedMedia(media.id)
                Log.d("VisionCentral", "Dados brutos da mídia (Supabase): id=${media.id}, tipo=${media.tipo}, origem=${media.origem}, url_storage=${media.urlStorage}, url_externa=${media.urlExterna}")
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
            
            val existingPlaylist = dao.getPlaylist()
            if (existingPlaylist?.itemsJson != localPlaylist.itemsJson || existingPlaylist?.id != localPlaylist.id) {
                println("VisionCentral: Playlist alterada. Atualizando.")
                dao.savePlaylist(localPlaylist)
                
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
            return null
        }
    }

    suspend fun syncPlaylist(clienteId: String): LocalPlaylist? {
        syncTvSettings()
        return dao.getPlaylist()
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
