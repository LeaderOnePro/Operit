package com.ai.assistance.operit.api.chat

import android.content.Context
import android.os.Environment
import android.util.Log
import com.ai.assistance.mnn.MNNModule
import com.ai.assistance.mnn.MNNForwardType
import com.ai.assistance.operit.data.model.ApiProviderType
import com.ai.assistance.operit.data.model.ModelOption
import com.ai.assistance.operit.data.model.ModelParameter
import com.ai.assistance.operit.util.stream.Stream
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MNN本地推理引擎的AI服务实现
 * 模拟远程API的行为，将MNN推理作为本地服务提供
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
         * 根据模型名称获取模型文件路径
         */
        fun getModelPath(context: Context, modelName: String): String {
            val modelsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Operit/models/mnn"
            )
            return File(File(modelsDir, modelName), "llm.mnn").absolutePath
        }
    }

    // MNN Module实例
    private var mnnModule: MNNModule? = null

    // Token计数（使用简单的字符数估算）
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
     * 初始化MNN模型（使用Module API）
     */
    private suspend fun initModel(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (mnnModule == null) {
                Log.d(TAG, "初始化MNN模型: $modelName")
                
                // 根据模型名称构建完整路径
                val actualModelPath = getModelPath(context, modelName)
                Log.d(TAG, "实际模型文件路径: $actualModelPath")
                
                // 检查文件是否存在
                if (!File(actualModelPath).exists()) {
                    return@withContext Result.failure(
                        Exception("模型文件不存在: $actualModelPath\n请确保模型已下载")
                    )
                }

                // 加载配置文件以获取输入输出名称
                val configFile = File(File(actualModelPath).parent, "llm_config.json")
                val (inputNames, outputNames) = if (configFile.exists()) {
                    parseModelConfig(configFile)
                } else {
                    // 使用默认的输入输出名称（标准LLM模型需要4个输入）
                    Log.d(TAG, "未找到配置文件，使用默认输入输出名称")
                    listOf("input_ids", "attention_mask", "position_ids", "logits_index") to listOf("logits")
                }

                // 创建Module配置
                val config = MNNModule.Config(
                    inputNames = inputNames,
                    outputNames = outputNames,
                    forwardType = forwardType,
                    numThread = threadCount,
                    precision = MNNModule.PrecisionMode.NORMAL,
                    memoryMode = MNNModule.MemoryMode.NORMAL
                )

                // 使用Module API加载模型
                mnnModule = MNNModule.load(actualModelPath, config)
                if (mnnModule == null) {
                    return@withContext Result.failure(
                        Exception("无法加载MNN模型，请检查模型文件格式")
                    )
                }

                Log.d(TAG, "MNN模型初始化成功 (Module API)")
                Log.d(TAG, "输入: ${inputNames.joinToString()}")
                Log.d(TAG, "输出: ${outputNames.joinToString()}")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "初始化MNN模型失败", e)
            Result.failure(e)
        }
    }

    /**
     * 解析模型配置文件
     */
    private fun parseModelConfig(configFile: File): Pair<List<String>, List<String>> {
        // TODO: 实现配置文件解析
        // 这里返回默认值，需要根据实际配置文件格式实现
        return listOf("input_ids", "attention_mask", "position_ids", "logits_index") to listOf("logits")
    }

    /**
     * 估算Token数（简单的字符数估算，假设平均4个字符为1个token）
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

    /**
     * 执行MNN推理（使用Module API）
     * 注意：这是一个简化的实现，实际需要根据具体的MNN模型进行调整
     */
    private suspend fun runInference(prompt: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val module = mnnModule ?: return@withContext Result.failure(
                Exception("MNN Module未初始化")
            )

            Log.d(TAG, "开始MNN推理，输入: $prompt")

            // TODO: 实际实现需要根据具体模型的输入输出格式进行调整
            // 这里是一个占位实现，展示如何使用Module API
            
            // 1. 将文本转换为token IDs（需要tokenizer）
            // val tokenIds = tokenizeText(prompt)
            
            // 2. 创建输入变量
            // val inputVar = module.createInputVariable(
            //     shape = intArrayOf(1, tokenIds.size),
            //     dataFormat = MNNModule.DataFormat.NCHW,
            //     dataType = MNNModule.DataType.INT32
            // )
            // inputVar?.setIntData(tokenIds)
            
            // 3. 执行推理
            // val outputs = module.forward(listOf(inputVar))
            
            // 4. 解码输出为文本
            // val outputData = outputs?.firstOrNull()?.getFloatData()
            // val responseText = detokenize(outputData)

            // 当前返回一个模拟响应，说明Module API集成已就绪
            val response = """这是MNN本地推理的模拟响应（Module API）。

✅ MNN Module API已成功集成！

您的输入：$prompt

MNN配置信息：
- 模型名称: $modelName
- 前向类型: $forwardType
- 线程数: $threadCount
- API类型: Module API（支持动态形状）

输入名称: ${module.getInputNames().joinToString()}
输出名称: ${module.getOutputNames().joinToString()}

要完成实际推理，需要：
1. 实现文本到token IDs的编码逻辑（tokenization）
2. 创建输入变量并设置正确的形状和数据
3. 调用 module.forward() 执行推理
4. 从输出变量中提取数据并解码为文本（detokenization）

Module API已经可以处理您模型中的动态形状问题！"""

            Result.success(response)
        } catch (e: Exception) {
            Log.e(TAG, "MNN推理失败", e)
            Result.failure(e)
        }
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

            // 构建提示词
            val prompt = buildPrompt(message, chatHistory)
            
            // 更新输入token计数
            _inputTokenCount = estimateTokens(prompt)
            onTokensUpdated(_inputTokenCount, 0, 0)

            // 执行推理
            val inferenceResult = runInference(prompt)
            
            if (inferenceResult.isFailure) {
                emit("错误: ${inferenceResult.exceptionOrNull()?.message ?: "推理失败"}")
                return@stream
            }

            val response = inferenceResult.getOrNull() ?: ""
            
            // 模拟流式输出：分块返回结果
            val chunkSize = 20 // 每块字符数
            var currentPos = 0
            
            while (currentPos < response.length && !isCancelled) {
                val endPos = minOf(currentPos + chunkSize, response.length)
                val chunk = response.substring(currentPos, endPos)
                
                emit(chunk)
                
                // 更新输出token计数
                _outputTokenCount = estimateTokens(response.substring(0, endPos))
                onTokensUpdated(_inputTokenCount, 0, _outputTokenCount)
                
                currentPos = endPos
                
                // 模拟网络延迟，使输出看起来更像流式
                delay(50)
            }

            Log.d(TAG, "MNN推理完成")

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

            // 根据模型名称构建路径
            val modelPath = getModelPath(context, modelName)
            val modelFile = File(modelPath)
            
            if (!modelFile.exists()) {
                return@withContext Result.failure(
                    Exception("模型文件不存在: $modelPath\n请先下载模型")
                )
            }

            // 获取模型文件夹信息
            val modelFolder = modelFile.parentFile ?: return@withContext Result.failure(
                Exception("无法获取模型文件夹")
            )
            val totalSize = modelFolder.listFiles()?.sumOf { it.length() } ?: 0L
            
            // 检查关键文件是否存在
            val weightFile = File(modelFolder, "llm.mnn.weight")
            val configFile = File(modelFolder, "llm_config.json")
            val tokenizerFile = File(modelFolder, "tokenizer.txt")
            
            val fileStatus = buildString {
                appendLine("文件状态:")
                appendLine("- llm.mnn: ✓")
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

            Result.success("MNN模型连接成功！\n\n模型: $modelName\n总大小: ${formatFileSize(totalSize)}\n\n$fileStatus")
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
        return estimateTokens(prompt)
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
            mnnModule?.release()
            mnnModule = null
            Log.d(TAG, "MNN资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放资源时出错", e)
        }
    }
}

