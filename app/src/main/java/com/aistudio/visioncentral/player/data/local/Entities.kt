package com.aistudio.visioncentral.player.data.local

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
    val origem: String = "storage",
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
    val rotacao: Int = 0,
    val proporcao: String = "16:9",
    val modoExibicao: String = "CenterInside",
    val brilho: Int = 100,
    val contraste: Int = 100,
    val saturacao: Int = 100,
    val zoom: Int = 100,
    val volume: Int = 100,
    val tempoTransicao: Int = 500
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
