package com.aistudio.visioncentral.player.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface VisionDao {
    @Query("SELECT * FROM local_playlists LIMIT 1")
    suspend fun getPlaylist(): LocalPlaylist?

    @Query("SELECT * FROM local_playlists LIMIT 1")
    fun getPlaylistFlow(): Flow<LocalPlaylist?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePlaylist(playlist: LocalPlaylist)

    @Query("SELECT * FROM device_config WHERE id = 0")
    suspend fun getConfig(): DeviceConfig?

    @Query("SELECT * FROM device_config WHERE id = 0")
    fun getConfigFlow(): Flow<DeviceConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: DeviceConfig)

    @Query("DELETE FROM local_playlists")
    suspend fun clearPlaylist()

    @Query("SELECT * FROM downloaded_media")
    suspend fun getAllDownloadedMedia(): List<DownloadedMedia>

    @Query("SELECT * FROM downloaded_media WHERE id = :id")
    suspend fun getDownloadedMedia(id: String): DownloadedMedia?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDownloadedMedia(media: DownloadedMedia)

    @Query("DELETE FROM downloaded_media WHERE id = :id")
    suspend fun deleteDownloadedMedia(id: String)
}

@Database(entities = [LocalPlaylist::class, DeviceConfig::class, DownloadedMedia::class], version = 11, exportSchema = false)
abstract class VisionDatabase : RoomDatabase() {
    abstract fun dao(): VisionDao
}
