package com.aistudio.visioncentral.player.data.download

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.aistudio.visioncentral.player.data.local.DownloadedMedia
import com.aistudio.visioncentral.player.data.local.VisionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class MediaDownloadManager(
    private val context: Context,
    private val dao: VisionDao,
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _storageError = MutableStateFlow<String?>(null)
    val storageError: StateFlow<String?> = _storageError.asStateFlow()

    private val mediaDir: File by lazy {
        File(context.getExternalFilesDir(null), "media").apply {
            if (!exists()) {
                Log.d("VisionCentral", "Criando diretório de mídia: $absolutePath")
                mkdirs()
            }
        }
    }

    suspend fun downloadMedia(id: String, url: String, expectedHash: String? = null, expectedSize: Long? = null) {
        withContext(Dispatchers.IO) {
            Log.d("VisionCentral", "Iniciando downloadMedia para ID: $id, URL: $url")
            val existing = dao.getDownloadedMedia(id)
            val file = File(mediaDir, id + getExtension(url))

            if (existing != null && file.exists()) {
                val sizeMatch = expectedSize == null || existing.size == expectedSize
                val hashMatch = expectedHash == null || existing.hash == expectedHash
                if (sizeMatch && hashMatch) {
                    Log.d("VisionCentral", "Mídia já baixada e válida: $id")
                    return@withContext
                }
                Log.d("VisionCentral", "Mídia existente inválida (tamanho ou hash divergente). Baixando novamente.")
            }

            // Verifica espaço (50MB de folga mínima + tamanho aproximado se disponível)
            val spaceNeeded = expectedSize ?: (50 * 1024 * 1024)
            if (!hasSpace(spaceNeeded)) { 
                Log.e("VisionCentral", "Espaço insuficiente no dispositivo para o download: $id")
                _storageError.value = "Espaço insuficiente para download."
                return@withContext
            } else {
                _storageError.value = null
            }

            _isDownloading.value = true
            try {
                Log.d("VisionCentral", "Baixando $id de $url...")
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                    Log.e("VisionCentral", "Erro no download de $id: Código ${response.code}")
                    throw Exception("Erro no download: ${response.code}")
                }

                val body = response.body ?: throw Exception("Corpo da resposta vazio")
                val totalBytes = body.contentLength()
                
                if (totalBytes > 0 && !hasSpace(totalBytes + 10 * 1024 * 1024)) {
                    Log.e("VisionCentral", "Armazenamento ficaria muito cheio após o download de $id")
                    _storageError.value = "Armazenamento cheio."
                    return@withContext
                }

                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                var totalRead: Long = 0
                var lastProgressUpdate = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    
                    val currentTime = System.currentTimeMillis()
                    if (totalBytes > 0 && currentTime - lastProgressUpdate > 500) {
                        val progress = totalRead.toFloat() / totalBytes
                        _downloadProgress.value = _downloadProgress.value + (id to progress)
                        lastProgressUpdate = currentTime
                    }
                }

                outputStream.close()
                inputStream.close()

                val hash = if (expectedHash != null) calculateHash(file) else null
                
                dao.saveDownloadedMedia(
                    DownloadedMedia(
                        id = id,
                        url = url,
                        localPath = file.absolutePath,
                        size = totalRead,
                        hash = hash
                    )
                )
                Log.d("VisionCentral", "Download concluído e salvo: $id (Tamanho: $totalRead)")
            } catch (e: Exception) {
                Log.e("VisionCentral", "Erro no processamento do download: $id", e)
            } finally {
                _downloadProgress.value = _downloadProgress.value - id
                if (_downloadProgress.value.isEmpty()) {
                    _isDownloading.value = false
                }
            }
        }
    }

    private fun getExtension(url: String): String {
        val lastDot = url.lastIndexOf(".")
        if (lastDot != -1) {
            val ext = url.substring(lastDot)
            if (ext.contains("?")) return ext.substring(0, ext.indexOf("?"))
            return ext
        }
        return ".mp4" // Fallback comum
    }

    private fun hasSpace(minBytes: Long): Boolean {
        return try {
            val stat = StatFs(mediaDir.path)
            val bytesAvailable = stat.availableBlocksLong * stat.blockSizeLong
            bytesAvailable > minBytes
        } catch (e: Exception) {
            true // Assume que tem espaço se falhar a verificação
        }
    }

    private fun calculateHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun cleanupUnusedMedia(usedIds: Set<String>) {
        withContext(Dispatchers.IO) {
            Log.d("VisionCentral", "Iniciando limpeza de mídias não utilizadas. IDs em uso: $usedIds")
            val allMedia = dao.getAllDownloadedMedia()
            allMedia.forEach { media ->
                if (!usedIds.contains(media.id)) {
                    Log.d("VisionCentral", "Removendo mídia não utilizada: ${media.id} (${media.localPath})")
                    val file = File(media.localPath)
                    if (file.exists()) file.delete()
                    dao.deleteDownloadedMedia(media.id)
                }
            }
        }
    }
}
