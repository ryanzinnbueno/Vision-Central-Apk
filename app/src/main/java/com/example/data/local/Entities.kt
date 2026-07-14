package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "local_playlists")
@Serializable
data class LocalPlaylist(
    @PrimaryKey val id: String,
    val nome: String,
    val itemsJson: String // Serialized list of LocalMediaItem
)

@Serializable
data class LocalMediaItem(
    val id: String,
    val nome: String,
    val tipo: String,
    val url: String,
    val localPath: String? = null,
    val duracao: Int,
    val ordem: Int
)

@Entity(tableName = "device_config")
data class DeviceConfig(
    @PrimaryKey val id: Int = 0,
    val deviceId: String,
    val token: String? = null,
    val clienteId: String? = null,
    val isLinked: Boolean = false,
    val lastSync: Long = 0,
    val tvId: String? = null,
    val tvName: String? = null,
    val orientacao: String = "Horizontal",
    val modoReproducaoAtivo: Boolean = true,
    val rotacao: String = "0",
    val proporcao: String = "16:9",
    val resolucao: String = "1080p",
    val ajusteTela: String = "Cover"
)

@Entity(tableName = "downloaded_media")
data class DownloadedMedia(
    @PrimaryKey val id: String,
    val url: String,
    val localPath: String,
    val size: Long,
    val hash: String?,
    val lastAccessed: Long = System.currentTimeMillis()
)
