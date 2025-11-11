package com.ai.assistance.operit.core.tools

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.packTool.PackageManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolInvocation
import com.ai.assistance.operit.data.model.ToolParameter
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import com.ai.assistance.operit.ui.common.displays.MessageContentParser
import com.ai.assistance.operit.ui.permissions.ToolPermissionSystem
import com.ai.assistance.operit.util.stream.splitBy
import com.ai.assistance.operit.util.stream.stream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Handles the extraction and execution of AI tools from responses Supports real-time streaming
 * extraction and execution of tools
 */
class AIToolHandler private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AIToolHandler"

        @Volatile private var INSTANCE: AIToolHandler? = null

        fun getInstance(context: Context): AIToolHandler {
            return INSTANCE
                    ?: synchronized(this) {
                        INSTANCE ?: AIToolHandler(context.applicationContext).also { INSTANCE = it }
                    }
        }
    }

    // Available tools registry
    private val availableTools = mutableMapOf<String, ToolExecutor>()

    // Tool permission system
    private val toolPermissionSystem = ToolPermissionSystem.getInstance(context)

    /** Get the tool permission system for UI use */
    fun getToolPermissionSystem(): ToolPermissionSystem {
        return toolPermissionSystem
    }
    
    /**
     * Get all registered tool names
     */
    fun getAllToolNames(): List<String> {
        return availableTools.keys.sorted()
    }

    /** Force refresh permission request state Can be called if permission dialog is not showing */
    fun refreshPermissionState(): Boolean {
        return toolPermissionSystem.refreshPermissionRequestState()
    }

    // 工具注册的唯一方法 - 提供完整信息的注册
    fun registerTool(
            name: String,
            dangerCheck: ((AITool) -> Boolean)? = null,
            descriptionGenerator: ((AITool) -> String)? = null,
            executor: ToolExecutor
    ) {
        availableTools[name] = executor

        // 注册危险操作检查（如果提供）
        if (dangerCheck != null) {
            toolPermissionSystem.registerDangerousOperation(name, dangerCheck)
        }

        // 注册描述生成器（如果提供）
        if (descriptionGenerator != null) {
            toolPermissionSystem.registerOperationDescription(name, descriptionGenerator)
        }
    }

    // 添加重载方法接受函数式接口作为executor的便捷写法
    fun registerTool(
            name: String,
            dangerCheck: ((AITool) -> Boolean)? = null,
            descriptionGenerator: ((AITool) -> String)? = null,
            executor: (AITool) -> ToolResult
    ) {
        registerTool(
                name = name,
                dangerCheck = dangerCheck,
                descriptionGenerator = descriptionGenerator,
                executor =
                        object : ToolExecutor {
                            override fun invoke(tool: AITool): ToolResult {
                                return executor(tool)
                            }
                        }
        )
    }

    // Register all default tools
    fun registerDefaultTools() {
        // Initialize the permission system with default rules
        toolPermissionSystem.initializeDefaultRules()

        registerAllTools(this, context)
    }

    // Package manager instance (lazy initialized)
    private var packageManagerInstance: PackageManager? = null

    /** Gets or creates the package manager instance */
    fun getOrCreatePackageManager(): PackageManager {
        return packageManagerInstance
                ?: run {
                    packageManagerInstance = PackageManager.getInstance(context, this)
                    packageManagerInstance!!
                }
    }

    /** Replace a tool invocation in the response with its result */
    private fun replaceToolInvocation(
            response: String,
            invocation: ToolInvocation,
            result: String
    ): String {
        val before = response.substring(0, invocation.responseLocation.first)
        val after = response.substring(invocation.responseLocation.last + 1)

        return "$before\n**Tool Result [${invocation.tool.name}]:** \n$result\n$after"
    }

    /**
     * Unescapes XML special characters
     * @param input The XML escaped string
     * @return Unescaped string
     */
    private fun unescapeXml(input: String): String {
        var result = input

        // 处理 CDATA 标记
        if (result.startsWith("<![CDATA[") && result.endsWith("]]>")) {
            result = result.substring(9, result.length - 3)
        }

        // 即使没有完整的 CDATA 标记，也尝试清理末尾的 ]]> 和开头的 <![CDATA[
        if (result.endsWith("]]>")) {
            result = result.substring(0, result.length - 3)
        }

        if (result.startsWith("<![CDATA[")) {
            result = result.substring(9)
        }

        return result.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'")
    }

    /** Reset the tool execution state */
    fun reset() {}

    /**
     * Get a registered tool executor by name
     * @param toolName The name of the tool
     * @return The tool executor or null if not found
     */
    fun getToolExecutor(toolName: String): ToolExecutor? {
        return availableTools[toolName]
    }

    /**
     * Extract tool invocations from the AI response Public method to be used by EnhancedAIService
     */
    fun extractToolInvocations(response: String): List<ToolInvocation> {
        val invocations = mutableListOf<ToolInvocation>()
        val content = response

        // 使用流式处理识别 XML 工具调用
        kotlinx.coroutines.runBlocking {
            val charStream = content.stream()

            val plugins = listOf(com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin())

            // 使用 splitBy 将字符流分割为 XML 和非 XML 部分
            charStream.splitBy(plugins).collect { group ->
                val chunkContent = StringBuilder()
                group.stream.collect { chunk -> chunkContent.append(chunk) }
                val chunkString = chunkContent.toString()

                if (chunkString.isEmpty()) return@collect

                // 只处理 XML 部分
                if (group.tag is com.ai.assistance.operit.util.stream.plugins.StreamXmlPlugin) {
                    // 检查是否是工具调用 XML (<tool>)
                    if (chunkString.startsWith("<tool") && chunkString.contains("</tool>")) {
                        // 提取工具名称
                        val nameMatch = MessageContentParser.namePattern.find(chunkString)
                        val toolName = nameMatch?.groupValues?.get(1) ?: return@collect

                        // 提取参数
                        val parameters = mutableListOf<ToolParameter>()
                        MessageContentParser.toolParamPattern.findAll(chunkString).forEach {
                                paramMatch: MatchResult ->
                            val paramName = paramMatch.groupValues[1]
                            val paramValue = paramMatch.groupValues[2]
                            parameters.add(ToolParameter(paramName, unescapeXml(paramValue)))
                        }

                        // 创建工具实例和调用
                        val tool = AITool(name = toolName, parameters = parameters)
                        invocations.add(ToolInvocation(tool, chunkString, chunkString.indices))
                    }
                }
            }
        }

        Log.d(
                TAG,
                "Found ${invocations.size} tool invocations: ${invocations.map { it.tool.name }}"
        )
        return invocations
    }

    /** Executes a tool directly */
    fun executeTool(tool: AITool): ToolResult {
        val executor = availableTools[tool.name]

        if (executor == null) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Tool not found: ${tool.name}"
            )
        }

        // Validate parameters
        val validationResult = executor.validateParameters(tool)
        if (!validationResult.valid) {
            return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = validationResult.errorMessage
            )
        }

        // Execute the tool
        return executor.invoke(tool)
    }
}

/** Interface for tool executors */
interface ToolExecutor {
    fun invoke(tool: AITool): ToolResult

    fun invokeAndStream(tool: AITool): Flow<ToolResult> = flowOf(invoke(tool))

    /**
     * Validates the parameters of a tool before execution Default implementation always returns
     * valid
     */
    fun validateParameters(tool: AITool): ToolValidationResult {
        return ToolValidationResult(valid = true)
    }
}
