package com.example.data.model

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
    @SerialName("criado_em") val criadoEm: String? = null
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
    @SerialName("url_storage") val urlStorage: String,
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
