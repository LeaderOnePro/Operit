package com.ai.assistance.operit.api.chat

import android.content.Context
import android.os.Environment
import android.util.Log
import com.ai.assistance.mnn.MNNLlmSession
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.io.File
import kotlinx.coroutines.runBlocking

/**
 * MNN本地推理引擎的AI服务实现
 * 使用 MNN 官方 LLM 引擎进行实际推理
 */
class MNNProvider(
    private val context: Context,
    private val modelName: String,  // 模型文件夹名称（如 "Qwen2-1.5B-Instruct-MNN"）
    private val forwardType: Int,
    private val threadCount: Int,
    private val providerType: ApiProviderType = ApiProviderType.MNN
) : AIService {

    companion object {
        private const val TAG = "MNNProvider"
        
        /**
         * 根据模型名称获取模型目录路径
         */
        fun getModelDir(context: Context, modelName: String): String {
            val modelsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Operit/models/mnn"
            )
            return File(modelsDir, modelName).absolutePath
        }
    }

    // MNN LLM Session 实例
    private var llmSession: MNNLlmSession? = null

    // Token计数
    private var _inputTokenCount = 0
    private var _outputTokenCount = 0
    private var _cachedInputTokenCount = 0

    @Volatile
    private var isCancelled = false

    override val inputTokenCount: Int
        get() = _inputTokenCount

    override val outputTokenCount: Int
        get() = _outputTokenCount

    override val cachedInputTokenCount: Int
        get() = _cachedInputTokenCount

    override val providerModel: String
        get() = "${providerType.name}:$modelName"

    override fun resetTokenCounts() {
        _inputTokenCount = 0
        _outputTokenCount = 0
        _cachedInputTokenCount = 0
    }

    override fun cancelStreaming() {
        isCancelled = true
        Log.d(TAG, "已取消MNN推理")
    }

    /**
     * 初始化 MNN LLM 模型
     */
    private suspend fun initModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (llmSession == null) {
                Log.d(TAG, "初始化MNN LLM模型: $modelName")
                
                // 获取模型目录
                val modelDir = getModelDir(context, modelName)
                Log.d(TAG, "模型目录: $modelDir")
                
                // 检查目录是否存在
                val modelDirFile = File(modelDir)
                if (!modelDirFile.exists() || !modelDirFile.isDirectory) {
                    return@withContext Result.failure(
                        Exception("模型目录不存在: $modelDir\n请确保模型已下载")
                    )
                }

                // 检查配置文件是否存在
                val configFile = File(modelDir, "llm_config.json")
                if (!configFile.exists()) {
                    return@withContext Result.failure(
                        Exception("配置文件不存在: ${configFile.absolutePath}\n请确保模型完整下载")
                    )
                }

                // 创建 LLM Session
                llmSession = MNNLlmSession.create(modelDir)
                if (llmSession == null) {
                    return@withContext Result.failure(
                        Exception("无法创建MNN LLM会话，请检查模型文件")
                    )
                }

                Log.i(TAG, "MNN LLM模型初始化成功")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "初始化MNN LLM模型失败", e)
            Result.failure(e)
        }
    }

    /**
     * 使用 LLM Session 的实际 tokenizer 计算 Token 数
     */
    private suspend fun countTokens(text: String): Int = withContext(Dispatchers.IO) {
        try {
            val session = llmSession ?: return@withContext estimateTokens(text)
            session.tokenize(text).size
        } catch (e: Exception) {
            Log.w(TAG, "Token计数失败，使用估算", e)
            estimateTokens(text)
        }
    }

    /**
     * 估算Token数（备用方法，假设平均4个字符为1个token）
     */
    private fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

    /**
     * 构建完整的提示词（包含历史记录）
     */
    private fun buildPrompt(
        message: String,
        chatHistory: List<Pair<String, String>>
    ): String {
        val promptBuilder = StringBuilder()
        
        // 添加历史记录
        for ((role, content) in chatHistory) {
            when (role.lowercase()) {
                "user" -> promptBuilder.append("用户: $content\n")
                "assistant" -> promptBuilder.append("助手: $content\n")
                "system" -> promptBuilder.append("系统: $content\n")
                else -> promptBuilder.append("$role: $content\n")
            }
        }
        
        // 添加当前消息
        promptBuilder.append("用户: $message\n")
        promptBuilder.append("助手: ")
        
        return promptBuilder.toString()
    }

    override suspend fun sendMessage(
        message: String,
        chatHistory: List<Pair<String, String>>,
        modelParameters: List<ModelParameter<*>>,
        enableThinking: Boolean,
        onTokensUpdated: suspend (input: Int, cachedInput: Int, output: Int) -> Unit,
        onNonFatalError: suspend (error: String) -> Unit
    ): Stream<String> = stream {
        isCancelled = false

        try {
            // 初始化模型
            val initResult = initModel()
            if (initResult.isFailure) {
                emit("错误: ${initResult.exceptionOrNull()?.message ?: "未知错误"}")
                return@stream
            }

            val session = llmSession ?: run {
                emit("错误: LLM会话未初始化")
                return@stream
            }

            // 构建历史记录（添加当前消息）
            val fullHistory = chatHistory.toMutableList().apply {
                add("user" to message)
            }
            
            // 估算输入token计数（用于显示）
            val estimatedPrompt = buildPrompt(message, chatHistory)
            _inputTokenCount = countTokens(estimatedPrompt)
            onTokensUpdated(_inputTokenCount, 0, 0)

            Log.d(TAG, "开始MNN LLM推理，历史消息数: ${fullHistory.size}")

            // 从模型参数中获取 max_tokens（如果有的话）
            val maxTokens = modelParameters
                .find { it.name == "max_tokens" }
                ?.let { (it.currentValue as? Number)?.toInt() }
                ?: -1  // -1 表示使用默认值

            // 使用流式生成（传递历史记录，让LLM内部应用chat template）
            var outputTokenCount = 0
            val success = session.generateStream(fullHistory, maxTokens) { token ->
                if (isCancelled) {
                    false  // 停止生成
                } else {
                    // 更新输出token计数（估算）
                    outputTokenCount += 1
                    _outputTokenCount = outputTokenCount
                    
                    // 发送 token
                    runBlocking { emit(token) }
                    
                    // 更新token统计（在IO线程中异步执行）
                    kotlin.runCatching {
                        kotlinx.coroutines.runBlocking {
                            onTokensUpdated(_inputTokenCount, 0, _outputTokenCount)
                        }
                    }
                    
                    true  // 继续生成
                }
            }

            if (!success && !isCancelled) {
                emit("\n\n[推理过程出现错误]")
            }

            Log.i(TAG, "MNN LLM推理完成，输出token数: $_outputTokenCount")

        } catch (e: Exception) {
            Log.e(TAG, "发送消息时出错", e)
            emit("错误: ${e.message}")
        }
    }

    override suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 检查模型名称
            if (modelName.isEmpty()) {
                return@withContext Result.failure(Exception("未配置模型名称"))
            }

            // 获取模型目录
            val modelDir = getModelDir(context, modelName)
            val modelDirFile = File(modelDir)
            
            if (!modelDirFile.exists() || !modelDirFile.isDirectory) {
                return@withContext Result.failure(
                    Exception("模型目录不存在: $modelDir\n请先下载模型")
                )
            }

            // 计算模型总大小
            val totalSize = modelDirFile.listFiles()?.sumOf { it.length() } ?: 0L
            
            // 检查关键文件是否存在
            val modelFile = File(modelDir, "llm.mnn")
            val weightFile = File(modelDir, "llm.mnn.weight")
            val configFile = File(modelDir, "llm_config.json")
            val tokenizerFile = File(modelDir, "tokenizer.txt")
            
            val fileStatus = buildString {
                appendLine("文件状态:")
                appendLine("- llm.mnn: ${if (modelFile.exists()) "✓" else "✗"}")
                appendLine("- llm.mnn.weight: ${if (weightFile.exists()) "✓" else "✗"}")
                appendLine("- llm_config.json: ${if (configFile.exists()) "✓" else "✗"}")
                appendLine("- tokenizer.txt: ${if (tokenizerFile.exists()) "✓" else "✗"}")
            }

            // 尝试初始化模型
            val initResult = initModel()
            if (initResult.isFailure) {
                return@withContext Result.failure(
                    initResult.exceptionOrNull() ?: Exception("模型初始化失败")
                )
            }

            Result.success("MNN LLM模型连接成功！\n\n模型: $modelName\n目录: $modelDir\n总大小: ${formatFileSize(totalSize)}\n\n$fileStatus")
        } catch (e: Exception) {
            Log.e(TAG, "测试连接失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(sizeBytes: Long): String {
        return when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> String.format("%.2f KB", sizeBytes / 1024.0)
            sizeBytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", sizeBytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", sizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    override suspend fun calculateInputTokens(
        message: String,
        chatHistory: List<Pair<String, String>>
    ): Int {
        val prompt = buildPrompt(message, chatHistory)
        return countTokens(prompt)
    }

    override suspend fun getModelsList(): Result<List<ModelOption>> {
        // MNN使用本地模型，从固定目录读取已下载的模型
        return ModelListFetcher.getMnnLocalModels(context)
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            llmSession?.release()
            llmSession = null
            Log.d(TAG, "MNN LLM资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时出错", e)
        }
    }
}

