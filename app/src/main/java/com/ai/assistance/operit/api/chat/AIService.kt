package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.util.stream.Stream
import okhttp3.*

/** AI服务接口，定义与不同AI提供商进行交互的标准方法 */
interface AIService {
    /** 输入token计数 */
    val inputTokenCount: Int

    /** 输出token计数 */
    val outputTokenCount: Int

    /** 重置token计数器 */
    fun resetTokenCounts()

    /** 取消当前流式传输 */
    fun cancelStreaming()

    /**
     * 获取模型列表
     * 
     * @return 模型列表结果，成功返回模型列表，失败返回错误信息
     */
    suspend fun getModelsList(): Result<List<ModelOption>>

    /**
     * 发送消息到AI服务
     *
     * @param message 用户消息内容
     * @param chatHistory 聊天历史记录，(角色, 内容)对的列表
     * @param modelParameters 模型参数列表
     * @return 流式响应内容的Stream
     */
    suspend fun sendMessage(
            message: String,
            chatHistory: List<Pair<String, String>> = emptyList(),
            modelParameters: List<ModelParameter<*>> = emptyList()
    ): Stream<String>
}
