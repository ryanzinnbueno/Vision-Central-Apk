package com.aistudio.visioncentral.player.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Tv(
    @SerialName("id") val id: String,
    @SerialName("cliente_id") val clienteId: String? = null,
    @SerialName("nome") val nome: String,
    @SerialName("token") val token: String,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("status") val status: String = "Offline",
    @SerialName("uptime") val uptime: String? = "0h 0m",
    @SerialName("ultima_sincronizacao") val ultimaSincronizacao: String? = null,
    @SerialName("ultima_conexao") val ultimaConexao: String? = null,
    @SerialName("criado_em") val criadoEm: String? = null,
    // Add fields that might be directly in TV table
    @SerialName("rotacao") val rotacao: String? = "0",
    @SerialName("orientacao") val orientacao: String? = "Horizontal",
    @SerialName("proporcao") val proporcao: String? = "16:9",
    @SerialName("resolucao") val resolucao: String? = "1080p",
    @SerialName("ajuste_tela") val ajusteTela: String? = "Cover",
    @SerialName("modo_exibicao") val modoExibicao: String? = "CenterInside",
    @SerialName("brilho") val brilho: Int? = 100,
    @SerialName("contraste") val contraste: Int? = 100,
    @SerialName("saturacao") val saturacao: Int? = 100,
    @SerialName("zoom") val zoom: Int? = 100,
    @SerialName("volume") val volume: Int? = 100,
    @SerialName("tempo_transicao") val tempoTransicao: Int? = 500,
    @SerialName("modo_reproducao") val modoReproducao: Boolean? = true
)

@Serializable
data class TvConfig(
    @SerialName("id") val id: String,
    @SerialName("tv_id") val tvId: String,
    @SerialName("rotacao") val rotacao: String? = "0",
    @SerialName("orientacao") val orientacao: String? = "Horizontal",
    @SerialName("proporcao") val proporcao: String? = "16:9",
    @SerialName("resolucao") val resolucao: String? = "1080p",
    @SerialName("ajuste_tela") val ajusteTela: String? = "Cover",
    @SerialName("modo_exibicao") val modoExibicao: String? = "CenterInside",
    @SerialName("brilho") val brilho: Int? = 100,
    @SerialName("contraste") val contraste: Int? = 100,
    @SerialName("saturacao") val saturacao: Int? = 100,
    @SerialName("zoom") val zoom: Int? = 100,
    @SerialName("volume") val volume: Int? = 100,
    @SerialName("tempo_transicao") val tempoTransicao: Int? = 500,
    @SerialName("modo_reproducao") val modoReproducao: Boolean? = true
)

@Serializable
data class Cliente(
    @SerialName("id") val id: String,
    @SerialName("nome") val nome: String,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("ticker_text") val tickerText: String? = null,
    @SerialName("orientation") val orientation: String = "Horizontal"
)

@Serializable
data class Playlist(
    @SerialName("id") val id: String,
    @SerialName("nome") val nome: String,
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
