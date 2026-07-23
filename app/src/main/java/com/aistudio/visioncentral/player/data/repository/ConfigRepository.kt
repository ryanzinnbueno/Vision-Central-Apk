package com.aistudio.visioncentral.player.data.repository

import android.util.Log
import com.aistudio.visioncentral.player.data.local.DeviceConfig
import com.aistudio.visioncentral.player.data.local.VisionDao
import com.aistudio.visioncentral.player.data.model.Tv
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest

import java.util.UUID

class ConfigRepository(private val dao: VisionDao) {

    suspend fun getOrCreateConfig(): DeviceConfig {
        val existing = dao.getConfig()
        if (existing != null) {
            return existing
        }
        val deviceId = UUID.randomUUID().toString()
        val config = DeviceConfig(deviceId = deviceId)
        dao.saveConfig(config)
        Log.d("VisionCentral", "[CONFIG] Nova configuração gerada: $deviceId")
        return config
    }

    suspend fun validateToken(rawToken: String): DeviceConfig? {
        val normalized = normalizeToken(rawToken)
        Log.d("VisionCentral", "[CONFIG] Validando token: $rawToken")
        try {
            val matchedTv = try {
                SupabaseClient.client.postgrest["tvs"]
                    .select {
                        filter {
                            eq("token", rawToken.trim())
                        }
                    }.decodeSingleOrNull<Tv>()
            } catch (e: Exception) {
                null
            } ?: try {
                SupabaseClient.client.postgrest["tvs"]
                    .select()
                    .decodeList<Tv>()
                    .find { normalizeToken(it.token) == normalized }
            } catch (e: Exception) {
                null
            }

            if (matchedTv != null) {
                Log.d("VisionCentral", "[CONFIG] TV encontrada: ${matchedTv.nome}")
                val currentConfig = dao.getConfig()!!
                val newConfig = currentConfig.copy(
                    token = matchedTv.token,
                    clienteId = matchedTv.clienteId,
                    tvId = matchedTv.id,
                    tvName = matchedTv.nome,
                    isLinked = true,
                    lastServerUpdate = matchedTv.updatedAt,
                    orientacao = matchedTv.orientacao ?: "Horizontal",
                    rotacao = matchedTv.rotacao ?: 0,
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
                return newConfig
            }
        } catch (e: Exception) {
            Log.e("VisionCentral", "[CONFIG] Erro ao validar token", e)
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
                Log.d("VisionCentral", "[CONFIG] TV removida do Supabase. Desvinculando...")
                dao.saveConfig(config.copy(isLinked = false, clienteId = null, tvId = null))
                return false
            }
            return true
        } catch (e: Exception) {
            return true
        }
    }

    suspend fun syncTvSettings(): DeviceConfig? {
        try {
            val config = dao.getConfig() ?: return null
            val tvId = config.tvId ?: return null
            
            val tv = SupabaseClient.client.postgrest["tvs"]
                .select { filter { eq("id", tvId) } }
                .decodeSingleOrNull<Tv>() ?: return null
                
            val current = dao.getConfig()!!
            
            val next = current.copy(
                tvName = tv.nome ?: current.tvName,
                clienteId = tv.clienteId ?: current.clienteId,
                lastServerUpdate = tv.updatedAt,
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

            if (next != current) {
                dao.saveConfig(next)
                Log.d("VisionCentral", "[CONFIG] Configuração atualizada no banco local.")
                return next
            } else {
                Log.d("VisionCentral", "[CONFIG] Nenhuma alteração detectada nas configurações locais.")
            }

            return current
        } catch (e: Exception) {
            Log.e("VisionCentral", "[CONFIG] Erro na sincronização da TV", e)
            return null
        }
    }

    suspend fun unlink() {
        val config = dao.getConfig() ?: return
        dao.saveConfig(config.copy(
            token = null,
            clienteId = null,
            tvId = null,
            tvName = null,
            isLinked = false
        ))
    }

    private fun normalizeToken(raw: String): String {
        return raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
    }
}
