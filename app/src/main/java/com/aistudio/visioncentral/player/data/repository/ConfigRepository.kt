package com.aistudio.visioncentral.player.data.repository

import android.util.Log
import com.aistudio.visioncentral.player.data.local.DeviceConfig
import com.aistudio.visioncentral.player.data.local.VisionDao
import com.aistudio.visioncentral.player.data.model.Tv
import com.aistudio.visioncentral.player.data.remote.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        Log.d("VisionCentral", "[Configuração] Nova configuração gerada: $deviceId")
        return config
    }

    suspend fun validateToken(rawToken: String): DeviceConfig? {
        val normalized = normalizeToken(rawToken)
        Log.d("VisionCentral", "[Configuração] Validando token: $rawToken")
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
                Log.d("VisionCentral", "[Configuração] TV encontrada: ${matchedTv.nome}")
                val currentConfig = dao.getConfig()!!
                val newConfig = currentConfig.copy(
                    token = matchedTv.token,
                    clienteId = matchedTv.clienteId,
                    tvId = matchedTv.id,
                    tvName = matchedTv.nome,
                    isLinked = true,
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
                return newConfig
            }
        } catch (e: Exception) {
            Log.e("VisionCentral", "[Configuração] Erro ao validar token", e)
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
                Log.d("VisionCentral", "[Configuração] TV removida do Supabase. Desvinculando...")
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

            Log.d("VisionCentral", "[SYNC-6] Consultando Supabase")
            Log.d("VisionCentral", "  - TV ID: $tvId")
            Log.d("VisionCentral", "  - TOKEN: ${config.token}")

            val tv = SupabaseClient.client.postgrest["tvs"]
                .select { filter { eq("id", tvId) } }
                .decodeSingleOrNull<Tv>() ?: return null

            Log.d("VisionCentral", "[SYNC-7] JSON COMPLETO recebido")
            try {
                val fullJson = Json { prettyPrint = true }.encodeToString(tv)
                Log.d("VisionCentral", fullJson)
            } catch (e: Exception) {
                Log.d("VisionCentral", tv.toString())
            }

            val current = dao.getConfig()!!
            
            Log.d("VisionCentral", "[Comparação de Campos]")
            val fields = listOf(
                Triple("nome", current.tvName, tv.nome),
                Triple("orientacao", current.orientacao, tv.orientacao),
                Triple("rotacao", current.rotacao, tv.rotacao),
                Triple("proporcao", current.proporcao, tv.proporcao),
                Triple("modo_exibicao", current.modoExibicao, tv.modoExibicao),
                Triple("brilho", current.brilho, tv.brilho),
                Triple("contraste", current.contraste, tv.contraste),
                Triple("saturacao", current.saturacao, tv.saturacao),
                Triple("zoom", current.zoom, tv.zoom),
                Triple("volume", current.volume, tv.volume),
                Triple("tempo_transicao", current.tempoTransicao, tv.tempoTransicao),
                Triple("playlist", current.clienteId, tv.clienteId)
            )

            fields.forEach { (name, old, new) ->
                val changed = old != new
                Log.d("VisionCentral", "  - Campo: $name")
                Log.d("VisionCentral", "    Valor antigo: $old")
                Log.d("VisionCentral", "    Valor novo: $new")
                Log.d("VisionCentral", "    Mudou? ${if (changed) "SIM" else "NÃO"}")
            }

            val next = current.copy(
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

            if (next != current) {
                Log.d("VisionCentral", "[SYNC-8] Salvando Room")
                dao.saveConfig(next)
                Log.d("VisionCentral", "[SYNC-9] Room atualizado")
                Log.d("VisionCentral", "[Configuração] Configuração aplicada")
                return next
            } else {
                Log.d("VisionCentral", "[Configuração] Nenhuma alteração detectada")
            }
            return current
        } catch (e: Exception) {
            Log.e("VisionCentral", "[Configuração] Erro na sincronização", e)
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

    private fun compareAndLog(field: String, old: Any?, new: Any?) {
        if (old != new) {
            Log.d("VisionCentral", "Campo: $field")
            Log.d("VisionCentral", "Valor antigo: $old")
            Log.d("VisionCentral", "Valor novo: $new")
            Log.d("VisionCentral", "↓")
        }
    }
}
