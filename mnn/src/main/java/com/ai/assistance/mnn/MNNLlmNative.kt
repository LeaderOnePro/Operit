package com.ai.assistance.mnn

/**
 * MNN LLM Engine Native JNI 接口
 * 基于 MNN 官方 LLM 引擎实现
 */
object MNNLlmNative {
    
    init {
        MNNLibraryLoader.loadLibraries()
    }
    
    /**
     * 从配置文件创建 LLM 实例
     * @param configPath 配置文件路径 (llm_config.json)
     * @return LLM 指针，失败返回 0
     */
    @JvmStatic
    external fun nativeCreateLlm(configPath: String): Long
    
    /**
     * 释放 LLM 实例
     * @param llmPtr LLM 指针
     */
    @JvmStatic
    external fun nativeReleaseLlm(llmPtr: Long)
    
    /**
     * 将文本编码为 token IDs
     * @param llmPtr LLM 指针
     * @param text 输入文本
     * @return token ID 数组
     */
    @JvmStatic
    external fun nativeTokenize(llmPtr: Long, text: String): IntArray?
    
    /**
     * 将单个 token ID 解码为文本
     * @param llmPtr LLM 指针
     * @param token token ID
     * @return 解码后的文本
     */
    @JvmStatic
    external fun nativeDetokenize(llmPtr: Long, token: Int): String?
    
    /**
     * 生成文本（非流式）
     * @param llmPtr LLM 指针
     * @param prompt 输入提示
     * @param maxTokens 最大生成 token 数
     * @param callback 回调接口（用于流式输出）
     * @return 生成的完整文本
     */
    @JvmStatic
    external fun nativeGenerate(
        llmPtr: Long,
        prompt: String,
        maxTokens: Int,
        callback: GenerationCallback?
    ): String?
    
    /**
     * 流式生成文本（带历史记录）
     * @param llmPtr LLM 指针
     * @param history 对话历史 (Pair<role, content>)
     * @param maxTokens 最大生成 token 数
     * @param callback 回调接口
     * @return 是否成功
     */
    @JvmStatic
    external fun nativeGenerateStream(
        llmPtr: Long,
        history: List<Pair<String, String>>,
        maxTokens: Int,
        callback: GenerationCallback
    ): Boolean
    
    /**
     * 应用聊天模板
     * @param llmPtr LLM 指针
     * @param userContent 用户输入内容
     * @return 应用模板后的文本
     */
    @JvmStatic
    external fun nativeApplyChatTemplate(llmPtr: Long, userContent: String): String?
    
    /**
     * 重置 LLM 状态（清除 KV-Cache 和历史）
     * @param llmPtr LLM 指针
     */
    @JvmStatic
    external fun nativeReset(llmPtr: Long)
    
    /**
     * 生成回调接口
     */
    interface GenerationCallback {
        /**
         * 当生成新的 token 时调用
         * @param token 生成的文本片段
         * @return true 继续生成，false 停止生成
         */
        fun onToken(token: String): Boolean
    }
}

