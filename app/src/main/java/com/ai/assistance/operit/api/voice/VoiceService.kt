package com.ai.assistance.operit.api.voice

import kotlinx.coroutines.flow.Flow

/** 语音服务接口，定义与不同语音引擎进行交互的标准方法 */
interface VoiceService {
    /** 当前语音引擎是否初始化完成 */
    val isInitialized: Boolean

    /** 当前语音引擎是否正在播放 */
    val isSpeaking: Boolean

    /** 语音状态Flow，用于UI观察状态变化 */
    val speakingStateFlow: Flow<Boolean>

    /**
     * 初始化语音引擎
     *
     * @return 初始化是否成功
     */
    suspend fun initialize(): Boolean

    /**
     * 文本转语音，将文本通过TTS引擎转换为语音并播放
     *
     * @param text 要转换为语音的文本
     * @param interrupt 是否中断当前正在播放的语音，默认为true
     * @param rate 语速，范围通常为0.5-2.0，默认为1.0
     * @param pitch 音调，范围通常为0.5-2.0，默认为1.0
     * @return 操作是否成功
     */
    suspend fun speak(
            text: String,
            interrupt: Boolean = true,
            rate: Float = 1.0f,
            pitch: Float = 1.0f
    ): Boolean

    /**
     * 停止当前正在播放的语音
     *
     * @return 操作是否成功
     */
    suspend fun stop(): Boolean

    /**
     * 暂停当前正在播放的语音
     *
     * @return 操作是否成功
     */
    suspend fun pause(): Boolean

    /**
     * 继续播放暂停的语音
     *
     * @return 操作是否成功
     */
    suspend fun resume(): Boolean

    /** 释放语音引擎资源 */
    fun shutdown()

    /**
     * 获取当前可用的语音列表
     *
     * @return 语音列表，包含语音ID和语音名称
     */
    suspend fun getAvailableVoices(): List<Voice>

    /**
     * 设置当前使用的语音
     *
     * @param voiceId 语音ID
     * @return 设置是否成功
     */
    suspend fun setVoice(voiceId: String): Boolean

    /**
     * 表示TTS语音的数据类
     *
     * @property id 语音唯一标识符
     * @property name 语音名称
     * @property locale 语音对应的语言和地区
     * @property gender 语音性别，可能为MALE、FEMALE或NEUTRAL
     */
    data class Voice(val id: String, val name: String, val locale: String, val gender: String)
}
