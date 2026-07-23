package com.aistudio.visioncentral.player.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tv(
    @SerialName("id") val id: String,
    @SerialName("cliente_id") val clienteId: String? = null,
    @SerialName("nome") val nome: String? = null,
    @SerialName("token") val token: String,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("status") val status: String = "Offline",
    @SerialName("uptime") val uptime: String? = null,
    @SerialName("ultima_sincronizacao") val ultimaSincronizacao: String? = null,
    @SerialName("ultima_conexao") val ultimaConexao: String? = null,
    @SerialName("criado_em") val criadoEm: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("orientacao") val orientacao: String? = null,
    @SerialName("proporcao") val proporcao: String? = null,
    @SerialName("modo_exibicao") val modoExibicao: String? = null,
    @SerialName("brilho") val brilho: Int? = null,
    @SerialName("contraste") val contraste: Int? = null,
    @SerialName("saturacao") val saturacao: Int? = null,
    @SerialName("zoom") val zoom: Int? = null,
    @SerialName("volume") val volume: Int? = null,
    @SerialName("tempo_transicao") val tempoTransicao: Int? = null,
    @SerialName("rotacao") val rotacao: Int? = null
)

@Serializable
data class Cliente(
    @SerialName("id") val id: String,
    @SerialName("nome") val nome: String,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("ticker_text") val tickerText: String? = null,
    @SerialName("orientation") val orientation: String = "Horizontal"
)

@Serializable
data class Playlist(
    @SerialName("id") val id: String,
    @SerialName("nome") val nome: String,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("cliente_id") val clienteId: String? = null
)

@Serializable
data class Midia(
    @SerialName("id") val id: String,
    @SerialName("nome") val nome: String,
    @SerialName("tipo") val tipo: String, // 'image' or 'video'
    @SerialName("url_storage") val urlStorage: String? = null,
    @SerialName("origem") val origem: String? = "storage",
    @SerialName("url_externa") val urlExterna: String? = null,
    @SerialName("duracao") val duracao: Int = 10,
    @SerialName("tamanho") val tamanho: String? = null
)

@Serializable
data class PlaylistMidia(
    @SerialName("id") val id: String,
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("midia_id") val midiaId: String,
    @SerialName("ordem") val ordem: Int,
    @SerialName("duracao") val duracao: Int
)

@Serializable
data class HeartbeatUpdate(
    @SerialName("status") val status: String,
    @SerialName("ultima_conexao") val ultimaConexao: String,
    @SerialName("ultima_sincronizacao") val ultimaSincronizacao: String? = null
)
