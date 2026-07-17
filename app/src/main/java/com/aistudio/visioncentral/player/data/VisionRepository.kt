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
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.aistudio.visioncentral.player.data.download.MediaDownloadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

import com.aistudio.visioncentral.player.data.model.TvConfig

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
            Log.d("VisionCentral", "Configuração recuperada do banco: token=${existing.token}, tvId=${existing.tvId}")
            return existing
        }

        Log.d("VisionCentral", "Nenhuma configuração encontrada. Gerando novo deviceId.")
        val deviceId = UUID.randomUUID().toString()
        val config = DeviceConfig(deviceId = deviceId)
        dao.saveConfig(config)
        Log.d("VisionCentral", "Nova configuração salva com deviceId: $deviceId")
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
            Log.d("VisionCentral", "Consultando tabela 'tvs' no Supabase...")
            val matchedTv = try {
                SupabaseClient.client.postgrest["tvs"]
                    .select {
                        filter {
                            eq("token", rawToken.trim()) // Try exact first
                        }
                    }.decodeSingleOrNull<Tv>()
            } catch (e: Exception) {
                Log.e("VisionCentral", "Erro na consulta exata de token", e)
                null
            } ?: try {
                Log.d("VisionCentral", "Tentando busca por lista e normalização...")
                SupabaseClient.client.postgrest["tvs"]
                    .select()
                    .decodeList<Tv>()
                    .find { normalizeToken(it.token) == normalized }
            } catch (e: Exception) {
                Log.e("VisionCentral", "Erro na consulta por lista", e)
                null
            }

            if (matchedTv != null) {
                Log.d("VisionCentral", "TV encontrada: id=${matchedTv.id}, nome=${matchedTv.nome}")
                val currentConfig = dao.getConfig()!!
                val newConfig = currentConfig.copy(
                    token = matchedTv.token,
                    clienteId = matchedTv.clienteId,
                    tvId = matchedTv.id,
                    tvName = matchedTv.nome,
                    isLinked = true,
                    // Initial sync from tvs table
                    orientacao = matchedTv.orientacao ?: "Horizontal",
                    rotacao = matchedTv.rotacao ?: "0",
                    proporcao = matchedTv.proporcao ?: "16:9",
                    modoExibicao = matchedTv.modoExibicao ?: "CenterInside",
                    brilho = matchedTv.brilho ?: 100,
                    contraste = matchedTv.contraste ?: 100,
                    saturacao = matchedTv.saturacao ?: 100,
                    zoom = matchedTv.zoom ?: 100,
                    volume = matchedTv.volume ?: 100,
                    tempoTransicao = matchedTv.tempoTransicao ?: 500
                )
                dao.saveConfig(newConfig)
                Log.d("VisionCentral", "Configuração atualizada e salva no banco local")
                
                // Immediately sync playlist and full config to apply all panel settings
                matchedTv.clienteId?.let { 
                    Log.d("VisionCentral", "Iniciando sincronização imediata de playlist para cliente: $it")
                    syncPlaylist(it) 
                }
                
                return newConfig
            } else {
                Log.d("VisionCentral", "Nenhuma TV correspondente encontrada para o token")
            }
        } catch (e: Exception) {
            Log.e("VisionCentral", "Exceção inesperada em validateToken", e)
        }
        return null
    }

    suspend fun isConfigValid(): Boolean {
        val config = dao.getConfig() ?: return false
        if (!config.isLinked || config.tvId == null) return false
        
        try {
            Log.d("VisionCentral", "Verificando se a TV ainda existe no Supabase (id=${config.tvId})")
            val tv = SupabaseClient.client.postgrest["tvs"]
                .select {
                    filter { eq("id", config.tvId) }
                }.decodeSingleOrNull<Tv>()
            
            if (tv == null) {
                Log.d("VisionCentral", "TV não encontrada no Supabase. Desvinculando localmente.")
                // TV was deleted from Supabase
                dao.saveConfig(config.copy(isLinked = false, clienteId = null, tvId = null))
                return false
            }
            return true
        } catch (e: Exception) {
            Log.e("VisionCentral", "Erro ao validar configuração no Supabase (offline?)", e)
            // If network error, assume it's still valid but offline
            return true
        }
    }

    suspend fun syncTvSettings(): Boolean {
        try {
            val config = dao.getConfig() ?: run {
                Log.e("VisionCentral", "[Sync] Falha: Config não encontrada")
                return false
            }
            val tvId = config.tvId ?: run {
                Log.e("VisionCentral", "[Sync] Falha: TV ID não vinculado")
                return false
            }

            Log.d("VisionCentral", "[Sync] Iniciando auditoria de sincronização para TV: $tvId")

            // 1. Fetch from TVS table
            val tv = SupabaseClient.client.postgrest["tvs"]
                .select { filter { eq("id", tvId) } }
                .decodeSingleOrNull<Tv>() ?: run {
                    Log.e("VisionCentral", "[Sync] Falha: Registro TV não encontrado no Supabase")
                    return false
                }

            Log.d("VisionCentral", "[CONFIG TVS] Recebido da tabela 'tvs':")
            Log.d("VisionCentral", "  - orientacao: ${tv.orientacao}")
            Log.d("VisionCentral", "  - proporcao: ${tv.proporcao}")
            Log.d("VisionCentral", "  - modo_exibicao: ${tv.modoExibicao}")
            Log.d("VisionCentral", "  - brilho: ${tv.brilho}")
            Log.d("VisionCentral", "  - contraste: ${tv.contraste}")
            Log.d("VisionCentral", "  - saturacao: ${tv.saturacao}")
            Log.d("VisionCentral", "  - zoom: ${tv.zoom}")
            Log.d("VisionCentral", "  - volume: ${tv.volume}")
            Log.d("VisionCentral", "  - tempo_transicao: ${tv.tempoTransicao}")
            Log.d("VisionCentral", "  - rotacao: ${tv.rotacao}")

            // 2. Fetch from CONFIGURACOES table (Resolucao, Autoplay, Orientacao-Override)
            val tvConfig: TvConfig? = try {
                val cfg = SupabaseClient.client.postgrest["configuracoes"]
                    .select { 
                        filter { 
                            if (tv.clienteId != null) eq("cliente_id", tv.clienteId)
                            else eq("id", "none") // Fallback if no client
                        } 
                    }.decodeSingleOrNull<TvConfig>()
                
                if (cfg != null) {
                    Log.d("VisionCentral", "[CONFIG CONFIGURACOES] Recebido da tabela 'configuracoes':")
                    Log.d("VisionCentral", "  - resolucao: ${cfg.resolucao}")
                    Log.d("VisionCentral", "  - autoplay: ${cfg.autoplay}")
                    Log.d("VisionCentral", "  - orientacao (override): ${cfg.orientacao}")
                }
                cfg
            } catch (e: Exception) {
                Log.d("VisionCentral", "[Sync] Tabela 'configuracoes' não acessível ou vazia: ${e.message}")
                null
            }

            // 3. Prepare updated configuration with strict mapping
            val current = dao.getConfig()!!
            
            // Apply fields from TVS
            var next = current.copy(
                tvName = tv.nome ?: current.tvName,
                clienteId = tv.clienteId ?: current.clienteId,
                orientacao = tv.orientacao ?: current.orientacao,
                rotacao = tv.rotacao ?: current.rotacao,
                proporcao = tv.proporcao ?: current.proporcao,
                modoExibicao = tv.modoExibicao ?: current.modoExibicao,
                brilho = tv.brilho ?: current.brilho,
                contraste = tv.contraste ?: current.contraste,
                saturacao = tv.saturacao ?: current.saturacao,
                zoom = tv.zoom ?: current.zoom,
                volume = tv.volume ?: current.volume,
                tempoTransicao = tv.tempoTransicao ?: current.tempoTransicao
            )

            // Apply overrides/exclusive fields from CONFIGURACOES
            tvConfig?.let { cfg ->
                next = next.copy(
                    resolucao = cfg.resolucao ?: next.resolucao,
                    autoplay = cfg.autoplay ?: next.autoplay,
                    orientacao = cfg.orientacao ?: next.orientacao // Override if present in config
                )
            }

            // Logging changes
            Log.d("VisionCentral", "[CONFIG FINAL] Comparando com configuração local:")
            if (next.orientacao != current.orientacao) Log.d("VisionCentral", "  - CAMPO ALTERADO: orientacao | VALOR: ${current.orientacao} -> ${next.orientacao}")
            if (next.rotacao != current.rotacao) Log.d("VisionCentral", "  - CAMPO ALTERADO: rotacao | VALOR: ${current.rotacao} -> ${next.rotacao}")
            if (next.resolucao != current.resolucao) Log.d("VisionCentral", "  - CAMPO ALTERADO: resolucao | VALOR: ${current.resolucao} -> ${next.resolucao}")
            if (next.autoplay != current.autoplay) Log.d("VisionCentral", "  - CAMPO ALTERADO: autoplay | VALOR: ${current.autoplay} -> ${next.autoplay}")
            if (next.modoExibicao != current.modoExibicao) Log.d("VisionCentral", "  - CAMPO ALTERADO: modoExibicao | VALOR: ${current.modoExibicao} -> ${next.modoExibicao}")
            if (next.zoom != current.zoom) Log.d("VisionCentral", "  - CAMPO ALTERADO: zoom | VALOR: ${current.zoom} -> ${next.zoom}")
            if (next.volume != current.volume) Log.d("VisionCentral", "  - CAMPO ALTERADO: volume | VALOR: ${current.volume} -> ${next.volume}")

            // Save if changed
            if (next != current) {
                Log.d("VisionCentral", "[Sync] Alterações detectadas. Salvando no banco local.")
                dao.saveConfig(next)
            } else {
                Log.d("VisionCentral", "[Sync] Nenhuma alteração detectada nas configurações.")
            }

            // 4. Sync Playlist if we have a client ID
            next.clienteId?.let { 
                syncPlaylistInternal(it, tv) 
            } ?: run {
                updateHeartbeat(isSync = false)
            }

            return true
        } catch (e: Exception) {
            Log.e("VisionCentral", "[Sync] Erro crítico na sincronização", e)
            return false
        }
    }

    private suspend fun syncPlaylistInternal(clienteId: String, tv: Tv): LocalPlaylist? {
        try {
            Log.d("VisionCentral", "[Sync] Sincronizando playlist para clienteId: $clienteId")
            val cliente = SupabaseClient.client.postgrest["clientes"]
                .select { filter { eq("id", clienteId) } }
                .decodeSingleOrNull<com.aistudio.visioncentral.player.data.model.Cliente>()

            val playlistId = tv.playlistId ?: cliente?.playlistId ?: run {
                Log.d("VisionCentral", "[Sync] Nenhuma playlist vinculada ao cliente ou TV.")
                updateHeartbeat(isSync = false)
                return null
            }

            // Get Playlist details
            Log.d("VisionCentral", "[Sync] Buscando detalhes da playlist: $playlistId")
            val playlist = SupabaseClient.client.postgrest["playlists"]
                .select { filter { eq("id", playlistId) } }
                .decodeSingleOrNull<Playlist>() ?: run {
                    Log.e("VisionCentral", "[Sync] Playlist $playlistId não encontrada no Supabase.")
                    updateHeartbeat(isSync = false)
                    return null
                }

            // Get Playlist Media relations
            val relations = SupabaseClient.client.postgrest["playlist_midias"]
                .select {
                    filter { eq("playlist_id", playlistId) }
                    order("ordem", Order.ASCENDING)
                }.decodeList<PlaylistMidia>()

            Log.d("VisionCentral", "[Sync] Playlist contém ${relations.size} relações de mídia.")

            // Get Media details
            val mediaIds = relations.map { it.midiaId }.distinct()
            val allMedia = if (mediaIds.isNotEmpty()) {
                SupabaseClient.client.postgrest["midias"]
                    .select {
                        filter {
                            isIn("id", mediaIds)
                        }
                    }.decodeList<Midia>()
            } else emptyList()

            // Map to LocalMediaItem
            val items = relations.mapNotNull { rel ->
                val media = allMedia.find { it.id == rel.midiaId } ?: return@mapNotNull null

                val mediaUrl = when (media.origem?.lowercase()) {
                    "url" -> media.urlExterna
                    else -> media.urlStorage
                }

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
            
            val existingPlaylist = dao.getPlaylist()
            if (existingPlaylist?.itemsJson != localPlaylist.itemsJson || existingPlaylist?.id != localPlaylist.id) {
                Log.d("VisionCentral", "[Sync] Playlist alterada. Atualizando banco local.")
                dao.savePlaylist(localPlaylist)
                
                CoroutineScope(Dispatchers.IO).launch {
                    items.forEach { item ->
                        if (item.origem.lowercase() != "url") {
                            val expectedSize = allMedia.find { it.id == item.id }?.tamanho?.toLongOrNull()
                            downloadManager.downloadMedia(item.id, item.url, expectedSize = expectedSize)
                        }
                    }
                    val usedIds = items.map { it.id }.toSet()
                    downloadManager.cleanupUnusedMedia(usedIds)
                }
            }

            // IMPORTANTE: Atualiza o heartbeat com status de sincronização
            updateHeartbeat(isSync = true)
            return localPlaylist

        } catch (e: Exception) {
            Log.e("VisionCentral", "[Sync] Erro crítico em syncPlaylistInternal", e)
            updateHeartbeat(isSync = false)
            return null
        }
    }

    suspend fun syncPlaylist(clienteId: String): LocalPlaylist? {
        syncTvSettings()
        return dao.getPlaylist()
    }

    suspend fun updateHeartbeat(isSync: Boolean = false) {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        try {
            val config = dao.getConfig() ?: return
            val tvId = config.tvId ?: return
            val token = config.token ?: "N/A"
            
            Log.d("VisionCentral", "[Heartbeat] [$now] Preparando heartbeat para TV ID: $tvId (Token: $token)")
            
            // Use ISO 8601 UTC timestamp
            val timestamp = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.Instant.now().toString()
            } else {
                java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).let {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    sdf.format(it.time)
                }
            }
            
            val update = mutableMapOf<String, String>(
                "status" to "Online",
                "ultima_conexao" to timestamp
            )
            
            if (isSync) {
                update["ultima_sincronizacao"] = timestamp
            }
            
            Log.d("VisionCentral", "[Heartbeat] [$now] Executando update (Online) no Supabase...")
            SupabaseClient.client.postgrest["tvs"].update(update) {
                filter { eq("id", tvId) }
            }
            Log.d("VisionCentral", "[Heartbeat] [$now] Status ONLINE atualizado com sucesso no Supabase.")
        } catch (e: Exception) {
            Log.e("VisionCentral", "[Heartbeat] [$now] Falha ao atualizar heartbeat no Supabase", e)
        }
    }

    suspend fun setPlaybackMode(active: Boolean) {
        val config = dao.getConfig() ?: return
        dao.saveConfig(config.copy(autoplay = active))
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
