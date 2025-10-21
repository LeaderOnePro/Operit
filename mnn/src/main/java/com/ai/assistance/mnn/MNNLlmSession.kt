package com.ai.assistance.mnn

import android.util.Log
import java.io.File

/**
 * MNN LLM 会话封装
 * 提供高级 API 来管理 LLM 推理会话
 */
class MNNLlmSession private constructor(
    private var llmPtr: Long,
    private val modelPath: String
) {
    companion object {
        private const val TAG = "MNNLlmSession"
        
        /**
         * 从模型目录创建 LLM 会话
         * @param modelDir 模型目录（包含 llm_config.json）
         * @return MNNLlmSession 实例，失败返回 null
         */
        @JvmStatic
        fun create(modelDir: String): MNNLlmSession? {
            val configFile = File(modelDir, "llm_config.json")
            
            if (!configFile.exists()) {
                Log.e(TAG, "Config file not found: ${configFile.absolutePath}")
                return null
            }
            
            Log.d(TAG, "Creating LLM session from: ${configFile.absolutePath}")
            
            val llmPtr = MNNLlmNative.nativeCreateLlm(configFile.absolutePath)
            if (llmPtr == 0L) {
                Log.e(TAG, "Failed to create LLM native instance")
                return null
            }
            
            Log.i(TAG, "LLM session created successfully")
            return MNNLlmSession(llmPtr, modelDir)
        }
    }
    
    @Volatile
    private var released = false
    
    private val lock = Any()
    
    /**
     * 检查会话是否有效
     */
    private fun checkValid() {
        if (released || llmPtr == 0L) {
            throw RuntimeException("LLM session has been released")
        }
    }
    
    /**
     * 将文本编码为 token IDs
     */
    fun tokenize(text: String): IntArray {
        synchronized(lock) {
            checkValid()
            return MNNLlmNative.nativeTokenize(llmPtr, text)
                ?: throw RuntimeException("Tokenization failed")
        }
    }
    
    /**
     * 将 token ID 解码为文本
     */
    fun detokenize(token: Int): String {
        synchronized(lock) {
            checkValid()
            return MNNLlmNative.nativeDetokenize(llmPtr, token)
                ?: throw RuntimeException("Detokenization failed")
        }
    }
    
    /**
     * 应用聊天模板
     */
    fun applyChatTemplate(userContent: String): String {
        synchronized(lock) {
            checkValid()
            return MNNLlmNative.nativeApplyChatTemplate(llmPtr, userContent)
                ?: userContent // 如果失败，返回原始内容
        }
    }
    
    /**
     * 非流式生成
     * @param prompt 输入提示
     * @param maxTokens 最大生成 token 数（-1 表示使用默认值）
     * @return 生成的文本
     */
    fun generate(prompt: String, maxTokens: Int = -1): String {
        synchronized(lock) {
            checkValid()
            return MNNLlmNative.nativeGenerate(llmPtr, prompt, maxTokens, null)
                ?: throw RuntimeException("Generation failed")
        }
    }
    
    /**
     * 流式生成（带历史记录）
     * @param history 对话历史 (Pair<role, content>)
     * @param maxTokens 最大生成 token 数（-1 表示使用默认值）
     * @param onToken 每个 token 的回调，返回 false 可以停止生成
     * @return 是否成功
     */
    fun generateStream(
        history: List<Pair<String, String>>,
        maxTokens: Int = -1,
        onToken: (String) -> Boolean
    ): Boolean {
        synchronized(lock) {
            checkValid()
            
            val callback = object : MNNLlmNative.GenerationCallback {
                override fun onToken(token: String): Boolean {
                    return try {
                        onToken(token)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in token callback", e)
                        false
                    }
                }
            }
            
            return MNNLlmNative.nativeGenerateStream(llmPtr, history, maxTokens, callback)
        }
    }
    
    /**
     * 聊天生成（应用模板后生成）
     * @param userContent 用户输入
     * @param maxTokens 最大生成 token 数
     * @param onToken 流式回调
     * @return 是否成功
     */
    fun chat(
        userContent: String,
        maxTokens: Int = -1,
        onToken: (String) -> Boolean
    ): Boolean {
        // 将单个用户消息转换为历史记录格式
        val history = listOf("user" to userContent)
        return generateStream(history, maxTokens, onToken)
    }
    
    /**
     * 重置会话（清除历史和 KV-Cache）
     */
    fun reset() {
        synchronized(lock) {
            checkValid()
            MNNLlmNative.nativeReset(llmPtr)
            Log.d(TAG, "Session reset")
        }
    }
    
    /**
     * 设置 LLM 配置
     * @param configJson JSON 格式的配置字符串
     * @return 是否设置成功
     */
    fun setConfig(configJson: String): Boolean {
        synchronized(lock) {
            checkValid()
            val success = MNNLlmNative.nativeSetConfig(llmPtr, configJson)
            if (success) {
                Log.d(TAG, "Config set successfully: $configJson")
            } else {
                Log.e(TAG, "Failed to set config: $configJson")
            }
            return success
        }
    }
    
    /**
     * 启用或禁用 thinking 模式（仅对支持的模型有效，如 Qwen3）
     * @param enabled 是否启用 thinking 模式
     * @return 是否设置成功
     */
    fun setThinkingMode(enabled: Boolean): Boolean {
        val configJson = """
        {
            "jinja": {
                "context": {
                    "enable_thinking": $enabled
                }
            }
        }
        """.trimIndent()
        return setConfig(configJson)
    }
    
    /**
     * 释放会话
     */
    fun release() {
        synchronized(lock) {
            if (!released && llmPtr != 0L) {
                MNNLlmNative.nativeReleaseLlm(llmPtr)
                llmPtr = 0L
                released = true
                Log.d(TAG, "Session released")
            }
        }
    }
    
    /**
     * 获取模型路径
     */
    fun getModelPath(): String = modelPath
    
    /**
     * 检查会话是否已释放
     */
    fun isReleased(): Boolean = released
    
    protected fun finalize() {
        release()
    }
}

