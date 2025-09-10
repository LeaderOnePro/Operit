package com.ai.assistance.operit.api.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 硅基流动TTS语音服务实现
 */
class SiliconFlowVoiceProvider(
    private val context: Context,
    private val apiKey: String,
    initialVoiceId: String
) : VoiceService {
    companion object {
        private const val TAG = "SiliconFlowVoiceProvider"
        private const val API_URL = "https://api.siliconflow.cn/v1/audio/speech"
        private const val RESPONSE_FORMAT = "mp3"
        private const val SAMPLE_RATE = 32000
        private const val SPEED = 1.0
        private const val GAIN = 0

        // 可用音色列表 - 根据硅基流动官方文档
        val AVAILABLE_VOICES = listOf(
            // FunAudioLLM/CosyVoice2-0.5B 模型音色
            VoiceService.Voice("FunAudioLLM/CosyVoice2-0.5B:alex", "Alex - 沉稳男声", "zh-CN", "MALE"),
            VoiceService.Voice("FunAudioLLM/CosyVoice2-0.5B:benjamin", "Benjamin - 低沉男声", "zh-CN", "MALE"),
            VoiceService.Voice("FunAudioLLM/CosyVoice2-0.5B:charles", "Charles - 磁性男声", "zh-CN", "MALE"),
            VoiceService.Voice("FunAudioLLM/CosyVoice2-0.5B:david", "David - 欢快男声", "zh-CN", "MALE"),
            VoiceService.Voice("FunAudioLLM/CosyVoice2-0.5B:anna", "Anna - 沉稳女声", "zh-CN", "FEMALE"),
            VoiceService.Voice("FunAudioLLM/CosyVoice2-0.5B:bella", "Bella - 激情女声", "zh-CN", "FEMALE"),
            VoiceService.Voice("FunAudioLLM/CosyVoice2-0.5B:claire", "Claire - 温柔女声", "zh-CN", "FEMALE"),
            VoiceService.Voice("FunAudioLLM/CosyVoice2-0.5B:diana", "Diana - 欢快女声", "zh-CN", "FEMALE"),
            
            // fishaudio/fish-speech-1.4 模型音色
            VoiceService.Voice("fishaudio/fish-speech-1.4:alex", "Alex - Fish模型沉稳男声", "zh-CN", "MALE"),
            VoiceService.Voice("fishaudio/fish-speech-1.4:benjamin", "Benjamin - Fish模型低沉男声", "zh-CN", "MALE"),
            VoiceService.Voice("fishaudio/fish-speech-1.4:charles", "Charles - Fish模型磁性男声", "zh-CN", "MALE"),
            VoiceService.Voice("fishaudio/fish-speech-1.4:david", "David - Fish模型欢快男声", "zh-CN", "MALE"),
            VoiceService.Voice("fishaudio/fish-speech-1.4:anna", "Anna - Fish模型沉稳女声", "zh-CN", "FEMALE"),
            VoiceService.Voice("fishaudio/fish-speech-1.4:bella", "Bella - Fish模型激情女声", "zh-CN", "FEMALE"),
            VoiceService.Voice("fishaudio/fish-speech-1.4:claire", "Claire - Fish模型温柔女声", "zh-CN", "FEMALE"),
            VoiceService.Voice("fishaudio/fish-speech-1.4:diana", "Diana - Fish模型欢快女声", "zh-CN", "FEMALE")
        )
        val DEFAULT_VOICE_ID = "FunAudioLLM/CosyVoice2-0.5B:charles"
    }

    // 当前音色
    private var voiceId: String = initialVoiceId.ifBlank { DEFAULT_VOICE_ID }


    // MediaPlayer用于播放音频
    private var mediaPlayer: MediaPlayer? = null

    // 初始化状态
    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: Boolean
        get() = _isInitialized.value

    // 播放状态
    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: Boolean
        get() = _isSpeaking.value

    // 播放状态Flow
    override val speakingStateFlow: Flow<Boolean> = _isSpeaking.asStateFlow()

    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                Log.e(TAG, "API密钥未设置")
                return@withContext false
            }
            if (voiceId.isBlank()) {
                Log.e(TAG, "音色ID未设置")
                return@withContext false
            }
            
            _isInitialized.value = true
            Log.i(TAG, "硅基流动TTS初始化成功")
            true
        } catch (e: Exception) {
            Log.e(TAG, "硅基流动TTS初始化失败", e)
            false
        }
    }

    override suspend fun speak(
        text: String,
        interrupt: Boolean,
        rate: Float,
        pitch: Float,
        extraParams: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!isInitialized) {
                Log.e(TAG, "TTS未初始化")
                return@withContext false
            }

            if (interrupt && isSpeaking) {
                stop()
            }

            _isSpeaking.value = true

            val model = voiceId.substringBeforeLast(':')

            // 构建请求体
            val requestBody = buildString {
                append("{")
                append("\"model\":\"$model\",")
                append("\"input\":\"${text.replace("\"", "\\\"")}\",")
                append("\"voice\":\"$voiceId\",")
                append("\"response_format\":\"$RESPONSE_FORMAT\",")
                append("\"sample_rate\":$SAMPLE_RATE,")
                append("\"speed\":$SPEED,")
                append("\"gain\":$GAIN")
                append("}")
            }

            // 发送HTTP请求
            val url = URL(API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // 写入请求体
            connection.outputStream.use { os ->
                os.write(requestBody.toByteArray())
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // 将音频数据保存到临时文件
                val tempFile = File.createTempFile("siliconflow_tts", ".mp3", context.cacheDir)
                
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // 播放音频文件
                withContext(Dispatchers.Main) {
                    playAudioFile(tempFile)
                }
                
                true
            } else {
                Log.e(TAG, "TTS请求失败，响应码: $responseCode")
                _isSpeaking.value = false
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS speak失败", e)
            _isSpeaking.value = false
            false
        }
    }

    private fun playAudioFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isSpeaking.value = false
                    file.delete() // 清理临时文件
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer错误: what=$what, extra=$extra")
                    _isSpeaking.value = false
                    file.delete() // 清理临时文件
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "播放音频失败", e)
            _isSpeaking.value = false
            file.delete() // 清理临时文件
        }
    }

    override suspend fun stop(): Boolean {
        return try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            _isSpeaking.value = false
            true
        } catch (e: Exception) {
            Log.e(TAG, "停止播放失败", e)
            false
        }
    }

    override suspend fun pause(): Boolean {
        return try {
            mediaPlayer?.pause()
            true
        } catch (e: Exception) {
            Log.e(TAG, "暂停播放失败", e)
            false
        }
    }

    override suspend fun resume(): Boolean {
        return try {
            mediaPlayer?.start()
            true
        } catch (e: Exception) {
            Log.e(TAG, "恢复播放失败", e)
            false
        }
    }

    override fun shutdown() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isSpeaking.value = false
        _isInitialized.value = false
    }

    override suspend fun getAvailableVoices(): List<VoiceService.Voice> {
        return AVAILABLE_VOICES
    }

    override suspend fun setVoice(voiceId: String): Boolean {
        if (AVAILABLE_VOICES.any { it.id == voiceId }) {
            this.voiceId = voiceId
            return true
        }
        return false
    }
} 