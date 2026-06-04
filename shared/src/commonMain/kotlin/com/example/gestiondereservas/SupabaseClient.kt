package com.example.gestiondereservas

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

val supabase = createSupabaseClient(
    supabaseUrl = "https://adtybjjcugjtgimacoyj.supabase.co",
    supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFkdHliampjdWdqdGdpbWFjb3lqIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzk1MDAxMjgsImV4cCI6MjA5NTA3NjEyOH0.N8_ex2wkEWqE3g-rnjwNaQnTbRaPfjoqm2dytmGL-xA"
) {
    install(Postgrest)
    install(Realtime)
    defaultSerializer = KotlinXSerializer(Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = false
    })
}

interface PlatformActions {
    fun makeCall(phone: String?)
    fun shareLink(message: String)
    fun sendWhatsApp(phone: String?, message: String)
    fun showToast(message: String)
}
