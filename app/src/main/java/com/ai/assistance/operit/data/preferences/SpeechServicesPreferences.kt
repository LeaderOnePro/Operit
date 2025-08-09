package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.speechServicesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "speech_services_preferences")

/**
 * Manages preferences for speech-to-text (STT) and text-to-speech (TTS) services.
 */
class SpeechServicesPreferences(private val context: Context) {

    private val dataStore = context.speechServicesDataStore

    @Serializable
    data class TtsHttpConfig(
        val urlTemplate: String,
        val apiKey: String, // Keep apiKey for header-based auth
        val headers: Map<String, String>,
        val httpMethod: String = "GET", // HTTP方法：GET 或 POST
        val requestBody: String = "", // POST请求的body模板，支持占位符如{text}
        val contentType: String = "application/json" // POST请求的Content-Type
    )

    companion object {
        // TTS Preference Keys
        val TTS_SERVICE_TYPE = stringPreferencesKey("tts_service_type")
        val TTS_HTTP_CONFIG = stringPreferencesKey("tts_http_config")

        // STT Preference Keys
        val STT_SERVICE_TYPE = stringPreferencesKey("stt_service_type")
        val STT_HTTP_CONFIG = stringPreferencesKey("stt_http_config")

        // Default Values
        val DEFAULT_TTS_SERVICE_TYPE = VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS
        val DEFAULT_STT_SERVICE_TYPE = SpeechServiceFactory.SpeechServiceType.SHERPA_NCNN

        // Baidu TTS Preset - Now uses a URL template
        val BAIDU_TTS_PRESET = TtsHttpConfig(
            urlTemplate = "https://fanyi.baidu.com/gettts?lan=zh&text={text}&spd={rate}&pit={pitch}",
            apiKey = "",
            headers = emptyMap(),
            httpMethod = "GET",
            requestBody = "",
            contentType = "application/json"
        )
    }

    // --- TTS Flows ---
    val ttsServiceTypeFlow: Flow<VoiceServiceFactory.VoiceServiceType> = dataStore.data.map { prefs ->
        VoiceServiceFactory.VoiceServiceType.valueOf(
            prefs[TTS_SERVICE_TYPE] ?: DEFAULT_TTS_SERVICE_TYPE.name
        )
    }

    val ttsHttpConfigFlow: Flow<TtsHttpConfig> = dataStore.data.map { prefs ->
        val json = prefs[TTS_HTTP_CONFIG]
        if (json != null) {
            try {
                Json.decodeFromString<TtsHttpConfig>(json)
            } catch (e: Exception) {
                BAIDU_TTS_PRESET // Fallback to preset on parsing error
            }
        } else {
            BAIDU_TTS_PRESET
        }
    }

    // --- STT Flows ---
    val sttServiceTypeFlow: Flow<SpeechServiceFactory.SpeechServiceType> = dataStore.data.map { prefs ->
        SpeechServiceFactory.SpeechServiceType.valueOf(
            prefs[STT_SERVICE_TYPE] ?: DEFAULT_STT_SERVICE_TYPE.name
        )
    }
    
    // --- Save TTS Settings ---
    suspend fun saveTtsSettings(
        serviceType: VoiceServiceFactory.VoiceServiceType,
        httpConfig: TtsHttpConfig? = null
    ) {
        dataStore.edit { prefs ->
            prefs[TTS_SERVICE_TYPE] = serviceType.name
            
            // 根据服务类型保存相应的配置
            when (serviceType) {
                VoiceServiceFactory.VoiceServiceType.HTTP_TTS -> {
                    httpConfig?.let { prefs[TTS_HTTP_CONFIG] = Json.encodeToString(it) }
                }
                VoiceServiceFactory.VoiceServiceType.SIMPLE_TTS -> {
                    // 系统 TTS 不需要额外配置
                }
            }
        }
    }

    // --- Save STT Settings ---
    suspend fun saveSttSettings(
        serviceType: SpeechServiceFactory.SpeechServiceType
    ) {
        dataStore.edit { prefs ->
            prefs[STT_SERVICE_TYPE] = serviceType.name
        }
    }
} 