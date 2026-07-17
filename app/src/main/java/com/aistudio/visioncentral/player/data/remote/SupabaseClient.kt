package com.aistudio.visioncentral.player.data.remote

import android.util.Log
import com.aistudio.visioncentral.player.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object SupabaseClient {
    init {
        Log.d("VisionCentral", "Iniciando SupabaseClient...")
        Log.d("VisionCentral", "Supabase URL: ${BuildConfig.SUPABASE_URL}")
        val keyPreview = BuildConfig.SUPABASE_ANON_KEY.take(10) + "..."
        Log.d("VisionCentral", "Supabase Key (Preview): $keyPreview")
    }
    
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Storage)
        install(Realtime)
    }
}
