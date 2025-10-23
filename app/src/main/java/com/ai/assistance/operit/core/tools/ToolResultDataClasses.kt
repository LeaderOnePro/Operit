package com.ai.assistance.operit.core.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * This file contains all implementations of ToolResultData Centralized for easier maintenance and
 * integration
 */
@Serializable
sealed class ToolResultData {
    /** Converts the structured data to a string representation */
    abstract override fun toString(): String
    fun toJson(): String {
        val json = Json.encodeToString(this)
        return json
    }
}

// Basic result data types (moved from AITool.kt)
@Serializable
data class BooleanResultData(val value: Boolean) : ToolResultData() {
    override fun toString(): String = value.toString()
}

@Serializable
data class StringResultData(val value: String) : ToolResultData() {
    override fun toString(): String = value
}

@Serializable
data class IntResultData(val value: Int) : ToolResultData() {
    override fun toString(): String = value.toString()
}

@Serializable
data class BinaryResultData(val value: ByteArray) : ToolResultData() {
    override fun toString(): String = "Binary data (${value.size} bytes)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as BinaryResultData
        return value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        return value.contentHashCode()
    }
}

/** 文件分段读取结果数据 */
@Serializable
data class FilePartContentData(
        val path: String,
        val content: String,
        val partIndex: Int,
        val totalParts: Int,
        val startLine: Int,
        val endLine: Int,
        val totalLines: Int
) : ToolResultData() {
    override fun toString(): String {
        val partInfo =
                "Part ${partIndex + 1} of $totalParts (Lines ${startLine + 1}-$endLine of $totalLines)"
        return "$partInfo\n\n$content"
    }
}

/** ADB命令执行结果数据 */
@Serializable
data class ADBResultData(val command: String, val output: String, val exitCode: Int) :
        ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("ADB命令执行结果:")
        sb.appendLine("命令: $command")
        sb.appendLine("退出码: $exitCode")
        sb.appendLine("\n输出:")
        sb.appendLine(output)
        return sb.toString()
    }
}

/** 终端命令执行结果数据 */
@Serializable
data class TerminalCommandResultData(
        val command: String,
        val output: String,
        val exitCode: Int,
        val sessionId: String
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("终端命令执行结果:")
        sb.appendLine("命令: $command")
        sb.appendLine("会话: $sessionId")
        sb.appendLine("退出码: $exitCode")
        sb.appendLine("\n输出:")
        sb.appendLine(output)
        return sb.toString()
    }
}

/** 计算结果结构化数据 */
@Serializable
data class CalculationResultData(
        val expression: String,
        val result: Double,
        val formattedResult: String,
        val variables: Map<String, Double> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("表达式: $expression")
        sb.appendLine("结果: $formattedResult")

        if (variables.isNotEmpty()) {
            sb.appendLine("变量:")
            variables.forEach { (name, value) -> sb.appendLine("  $name = $value") }
        }

        return sb.toString()
    }
}

/** 日期结果结构化数据 */
@Serializable
data class DateResultData(val date: String, val format: String, val formattedDate: String) :
        ToolResultData() {
    override fun toString(): String {
        return formattedDate
    }
}

/** Connection result data */
@Serializable
data class ConnectionResultData(
        val connectionId: String,
        val isActive: Boolean,
        val timestamp: Long = System.currentTimeMillis()
) : ToolResultData() {
    override fun toString(): String {
        return "Simulated connection established. Demo connection ID: $connectionId"
    }
}

/** Represents a directory listing result */
@Serializable
data class DirectoryListingData(val path: String, val entries: List<FileEntry>) : ToolResultData() {
    @Serializable
    data class FileEntry(
            val name: String,
            val isDirectory: Boolean,
            val size: Long,
            val permissions: String,
            val lastModified: String
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Directory listing for $path:")
        entries.forEach { entry ->
            val typeIndicator = if (entry.isDirectory) "d" else "-"
            sb.appendLine(
                    "$typeIndicator${entry.permissions} ${
                    entry.size.toString().padStart(8)
                } ${entry.lastModified} ${entry.name}"
            )
        }
        return sb.toString()
    }
}

/** Represents a file content result */
@Serializable
data class FileContentData(val path: String, val content: String, val size: Long) :
        ToolResultData() {
    override fun toString(): String {
        return "Content of $path:\n$content"
    }
}

/** Represents file existence check result */
@Serializable
data class FileExistsData(
        val path: String,
        val exists: Boolean,
        val isDirectory: Boolean = false,
        val size: Long = 0
) : ToolResultData() {
    override fun toString(): String {
        val fileType = if (isDirectory) "Directory" else "File"
        return if (exists) {
            "$fileType exists at path: $path (size: $size bytes)"
        } else {
            "No file or directory exists at path: $path"
        }
    }
}

/** Represents detailed file information */
@Serializable
data class FileInfoData(
        val path: String,
        val exists: Boolean,
        val fileType: String, // "file", "directory", or "other"
        val size: Long,
        val permissions: String,
        val owner: String,
        val group: String,
        val lastModified: String,
        val rawStatOutput: String
) : ToolResultData() {
    override fun toString(): String {
        if (!exists) {
            return "File or directory does not exist at path: $path"
        }

        val sb = StringBuilder()
        sb.appendLine("File information for $path:")
        sb.appendLine("Type: $fileType")
        sb.appendLine("Size: $size bytes")
        sb.appendLine("Permissions: $permissions")
        sb.appendLine("Owner: $owner")
        sb.appendLine("Group: $group")
        sb.appendLine("Last modified: $lastModified")
        return sb.toString()
    }
}

/** Represents a file operation result */
@Serializable
data class FileOperationData(
        val operation: String,
        val path: String,
        val successful: Boolean,
        val details: String
) : ToolResultData() {
    override fun toString(): String {
        return details
    }
}

/** Represents the result of an 'apply_file' operation, including the AI-generated diff */
@Serializable
data class FileApplyResultData(
    val operation: FileOperationData, 
    val aiDiffInstructions: String,
    val syntaxCheckResult: String? = null
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine(operation.details)
        if (aiDiffInstructions.isNotEmpty() && !aiDiffInstructions.startsWith("Error")) {
            sb.appendLine("\n--- AI-Generated Diff ---")
            sb.appendLine(aiDiffInstructions)
        }
        if (!syntaxCheckResult.isNullOrEmpty()) {
            sb.appendLine("\n--- Syntax Check ---")
            sb.appendLine(syntaxCheckResult)
        }
        return sb.toString()
    }
}

/** HTTP响应结果结构化数据 */
@Serializable
data class HttpResponseData(
        val url: String,
        val statusCode: Int,
        val statusMessage: String,
        val headers: Map<String, String>,
        val contentType: String,
        val content: String,
        val contentBase64: String? = null,
        val size: Int,
        val cookies: Map<String, String> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("HTTP Response:")
        sb.appendLine("URL: $url")
        sb.appendLine("Status: $statusCode $statusMessage")
        sb.appendLine("Content-Type: $contentType")
        sb.appendLine("Size: $size bytes")

        // 添加Cookie信息
        if (cookies.isNotEmpty()) {
            sb.appendLine("Cookies: ${cookies.size}")
            cookies.entries.take(5).forEach { (name, value) ->
                sb.appendLine("  $name: ${value.take(30)}${if (value.length > 30) "..." else ""}")
            }
            if (cookies.size > 5) {
                sb.appendLine("  ... and ${cookies.size - 5} more cookies")
            }
        }

        sb.appendLine()
        sb.appendLine("Content Summary:")
        sb.append(content)
        return sb.toString()
    }
}

/** 系统设置数据 */
@Serializable
data class SystemSettingData(val namespace: String, val setting: String, val value: String) :
        ToolResultData() {
    override fun toString(): String {
        return "$namespace.$setting 的当前值是: $value"
    }
}

/** 应用操作结果数据 */
@Serializable
data class AppOperationData(
        val operationType: String,
        val packageName: String,
        val success: Boolean,
        val details: String = ""
) : ToolResultData() {
    override fun toString(): String {
        return when (operationType) {
            "install" -> "成功安装应用: $packageName $details"
            "uninstall" -> "成功卸载应用: $packageName $details"
            "start" -> "成功启动应用: $packageName $details"
            "stop" -> "成功停止应用: $packageName $details"
            else -> details
        }
    }
}

/** 应用列表数据 */
@Serializable
data class AppListData(val includesSystemApps: Boolean, val packages: List<String>) :
        ToolResultData() {
    override fun toString(): String {
        val appType = if (includesSystemApps) "所有应用" else "第三方应用"
        return "已安装${appType}列表:\n${packages.joinToString("\n")}"
    }
}

/** Represents UI node structure for hierarchical display */
@Serializable
data class SimplifiedUINode(
        val className: String?,
        val text: String?,
        val contentDesc: String?,
        val resourceId: String?,
        val bounds: String?,
        val isClickable: Boolean,
        val children: List<SimplifiedUINode>
) {
    fun toTreeString(indent: String = ""): String {
        if (!shouldKeepNode()) return ""

        val sb = StringBuilder()

        // Node identifier
        sb.append(indent)
        if (isClickable) sb.append("▶ ") else sb.append("◢ ")

        // Class name
        className?.let { sb.append("[$it] ") }

        // Text content (maximum 30 characters)
        text?.takeIf { it.isNotBlank() }?.let {
            val displayText = if (it.length > 30) "${it.take(27)}..." else it
            sb.append("T: \"$displayText\" ")
        }

        // Content description
        contentDesc?.takeIf { it.isNotBlank() }?.let { sb.append("D: \"$it\" ") }

        // Resource ID
        resourceId?.takeIf { it.isNotBlank() }?.let { sb.append("ID: $it ") }

        // Bounds
        bounds?.let { sb.append("⮞ $it") }

        sb.append("\n")

        // Process children recursively
        children.forEach { sb.append(it.toTreeString("$indent  ")) }

        return sb.toString()
    }

    private fun shouldKeepNode(): Boolean {
        // Keep conditions: key element types or has content or clickable or has children that
        // should be kept
        val isKeyElement =
                className in
                        setOf("Button", "TextView", "EditText", "ScrollView", "Switch", "ImageView")
        val hasContent = !text.isNullOrBlank() || !contentDesc.isNullOrBlank()

        return isKeyElement || hasContent || isClickable || children.any { it.shouldKeepNode() }
    }
}

/** Represents UI page information result data */
@Serializable
data class UIPageResultData(
        val packageName: String,
        val activityName: String,
        val uiElements: SimplifiedUINode
) : ToolResultData() {
    override fun toString(): String {
        return """
            |Current Application: $packageName
            |Current Activity: $activityName
            |
            |UI Elements:
            |${uiElements.toTreeString()}
            """.trimMargin()
    }
}

/** Represents a UI action result data */
@Serializable
data class UIActionResultData(
        val actionType: String,
        val actionDescription: String,
        val coordinates: Pair<Int, Int>? = null,
        val elementId: String? = null
) : ToolResultData() {
    override fun toString(): String {
        return actionDescription
    }
}

/** Represents a combined operation result data */
@Serializable
data class CombinedOperationResultData(
        val operationSummary: String,
        val waitTime: Int,
        val pageInfo: UIPageResultData
) : ToolResultData() {
    override fun toString(): String {
        return "$operationSummary (waited ${waitTime}ms)\n\n$pageInfo"
    }
}

/** Device information result data */
@Serializable
data class DeviceInfoResultData(
        val deviceId: String,
        val model: String,
        val manufacturer: String,
        val androidVersion: String,
        val sdkVersion: Int,
        val screenResolution: String,
        val screenDensity: Float,
        val totalMemory: String,
        val availableMemory: String,
        val totalStorage: String,
        val availableStorage: String,
        val batteryLevel: Int,
        val batteryCharging: Boolean,
        val cpuInfo: String,
        val networkType: String,
        val additionalInfo: Map<String, String> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("设备信息:")
        sb.appendLine("设备型号: $manufacturer $model")
        sb.appendLine("Android版本: $androidVersion (SDK $sdkVersion)")
        sb.appendLine("设备ID: $deviceId")
        sb.appendLine("屏幕: $screenResolution (${screenDensity}dp)")
        sb.appendLine("内存: 可用 $availableMemory / 总计 $totalMemory")
        sb.appendLine("存储: 可用 $availableStorage / 总计 $totalStorage")
        sb.appendLine("电池: ${batteryLevel}% ${if (batteryCharging) "(充电中)" else ""}")
        sb.appendLine("网络: $networkType")
        sb.appendLine("处理器: $cpuInfo")

        if (additionalInfo.isNotEmpty()) {
            sb.appendLine("\n其他信息:")
            additionalInfo.forEach { (key, value) -> sb.appendLine("$key: $value") }
        }

        return sb.toString()
    }
}

/** Web page visit result data */
@Serializable
data class VisitWebResultData(
        val url: String,
        val title: String,
        val content: String,
        val metadata: Map<String, String> = emptyMap()
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("网页内容提取结果:")
        sb.appendLine("URL: $url")
        sb.appendLine("标题: $title")

        if (metadata.isNotEmpty()) {
            sb.appendLine("\n元数据:")
            metadata.entries.take(5).forEach { (key, value) -> sb.appendLine("$key: $value") }
            if (metadata.size > 5) {
                sb.appendLine("... 以及 ${metadata.size - 5} 个其他元数据项")
            }
            sb.appendLine()
        }

        sb.appendLine("\n页面内容:")

        // 如果内容太长，只显示部分
        val maxContentLength = 1000
        if (content.length > maxContentLength) {
            sb.append(content.substring(0, maxContentLength))
            sb.appendLine("\n...(内容已截断，共 ${content.length} 字符)")
        } else {
            sb.append(content)
        }

        return sb.toString()
    }
}

/** Intent execution result data */
@Serializable
data class IntentResultData(
        val action: String,
        val uri: String,
        val package_name: String,
        val component: String,
        val flags: Int,
        val extras_count: Int,
        val result: String
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Intent执行结果:")
        sb.appendLine("Action: $action")
        if (uri != "null") sb.appendLine("URI: $uri")
        if (package_name != "null") sb.appendLine("包名: $package_name")
        if (component != "null") sb.appendLine("组件: $component")
        sb.appendLine("Flags: $flags")
        sb.appendLine("附加数据数量: $extras_count")
        sb.appendLine("\n执行结果: $result")
        return sb.toString()
    }
}

/** 文件查找结果数据 */
@Serializable
data class FindFilesResultData(val path: String, val pattern: String, val files: List<String>) :
        ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("文件查找结果:")
        sb.appendLine("搜索路径: $path")
        sb.appendLine("匹配模式: $pattern")

        sb.appendLine("找到 ${files.size} 个文件:")
        files.forEachIndexed { index, file ->
            if (index < 10 || files.size <= 20) {
                sb.appendLine("- $file")
            } else if (index == 10 && files.size > 20) {
                sb.appendLine("... 以及 ${files.size - 10} 个其他文件")
            }
        }

        return sb.toString()
    }
}

/** FFmpeg处理结果数据 */
@Serializable
data class FFmpegResultData(
        val command: String,
        val returnCode: Int,
        val output: String,
        val duration: Long,
        val outputFile: String? = null,
        val mediaInfo: MediaInfo? = null
) : ToolResultData() {
    @Serializable
    data class MediaInfo(
            val format: String,
            val duration: String,
            val bitrate: String,
            val videoStreams: List<StreamInfo>,
            val audioStreams: List<StreamInfo>
    )

    @Serializable
    data class StreamInfo(
            val index: Int,
            val codecType: String,
            val codecName: String,
            val resolution: String? = null,
            val frameRate: String? = null,
            val sampleRate: String? = null,
            val channels: Int? = null
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("FFmpeg执行结果:")
        sb.appendLine("命令: $command")
        sb.appendLine("返回码: $returnCode")
        sb.appendLine("执行时间: ${duration}ms")

        outputFile?.let { sb.appendLine("输出文件: $it") }

        mediaInfo?.let { info ->
            sb.appendLine("\n媒体信息:")
            sb.appendLine("格式: ${info.format}")
            sb.appendLine("时长: ${info.duration}")
            sb.appendLine("比特率: ${info.bitrate}")

            if (info.videoStreams.isNotEmpty()) {
                sb.appendLine("\n视频流:")
                info.videoStreams.forEach { stream ->
                    sb.appendLine("  索引: ${stream.index}")
                    sb.appendLine("  编解码器: ${stream.codecName}")
                    stream.resolution?.let { sb.appendLine("  分辨率: $it") }
                    stream.frameRate?.let { sb.appendLine("  帧率: $it") }
                    sb.appendLine()
                }
            }

            if (info.audioStreams.isNotEmpty()) {
                sb.appendLine("\n音频流:")
                info.audioStreams.forEach { stream ->
                    sb.appendLine("  索引: ${stream.index}")
                    sb.appendLine("  编解码器: ${stream.codecName}")
                    stream.sampleRate?.let { sb.appendLine("  采样率: $it") }
                    stream.channels?.let { sb.appendLine("  声道数: $it") }
                    sb.appendLine()
                }
            }
        }

        sb.appendLine("\n输出日志:")
        sb.append(output)

        return sb.toString()
    }
}



/** 通知数据结构 */
@Serializable
data class NotificationData(val notifications: List<Notification>, val timestamp: Long) :
        ToolResultData() {
    @Serializable
    data class Notification(val packageName: String, val text: String, val timestamp: Long)

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("设备通知 (共 ${notifications.size} 条):")

        notifications.forEachIndexed { index, notification ->
            sb.appendLine("${index + 1}. 应用包名: ${notification.packageName}")
            sb.appendLine("   内容: ${notification.text}")
            sb.appendLine()
        }

        if (notifications.isEmpty()) {
            sb.appendLine("当前没有通知")
        }

        return sb.toString()
    }
}

/** 位置数据结构 */
@Serializable
data class LocationData(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val provider: String,
        val timestamp: Long,
        val rawData: String,
        val address: String = "",
        val city: String = "",
        val province: String = "",
        val country: String = ""
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("设备位置信息:")
        sb.appendLine("经度: $longitude")
        sb.appendLine("纬度: $latitude")
        sb.appendLine("精度: $accuracy 米")
        sb.appendLine("提供者: $provider")
        sb.appendLine(
                "获取时间: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(timestamp))}"
        )

        if (address.isNotEmpty()) {
            sb.appendLine("地址: $address")
        }
        if (city.isNotEmpty()) {
            sb.appendLine("城市: $city")
        }
        if (province.isNotEmpty()) {
            sb.appendLine("省/州: $province")
        }
        if (country.isNotEmpty()) {
            sb.appendLine("国家: $country")
        }

        return sb.toString()
    }
}



/** Represents a simplified HTML node for computer desktop actions, focusing on interactability */
@Serializable
data class ComputerPageInfoNode(
    val interactionId: Int?,
    val type: String, // e.g., "container", "button", "link", "text", "input"
    val description: String,
    val children: List<ComputerPageInfoNode>
) {
    fun toTreeString(level: Int = 0): String {
        val indent = "  ".repeat(level)
        val idPrefix = interactionId?.let { "($it) " } ?: ""
        val typePrefix = if (type != "container" && type != "text") "▶ $type: " else ""
        val selfStr = "$indent$idPrefix$typePrefix'${description.trim()}'"

        val childrenStr = if (children.isNotEmpty()) {
            "\n" + children.joinToString("\n") { it.toTreeString(level + 1) }
        } else {
            ""
        }
        return selfStr + childrenStr
    }
}


/** Represents the result of a computer desktop action */
@Serializable
data class ComputerDesktopActionResultData(
    val action: String,
    val target: String? = null,
    val resultSummary: String,
    val tabs: List<ComputerTabInfo>? = null,
    val pageContent: ComputerPageInfoNode? = null
) : ToolResultData() {
    @Serializable
    data class ComputerTabInfo(
        val id: String,
        val title: String,
        val url: String,
        val isActive: Boolean
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Computer Desktop Action: '$action'")
        target?.let { sb.appendLine("Target: $it") }
        sb.appendLine("Result: $resultSummary")
        tabs?.let {
            sb.appendLine("\nOpen Tabs (${it.size}):")
            it.forEach { tab ->
                sb.appendLine("- [${if (tab.isActive) "*" else " "}] ${tab.title} (${tab.url})")
            }
        }
        pageContent?.let {
            sb.appendLine("\n--- Page Content (Interactable Elements marked with ▶) ---")
            sb.append(it.toTreeString())
        }
        return sb.toString()
    }
}

/** Represents the result of a memory query */
@Serializable
data class MemoryQueryResultData(
    val memories: List<MemoryInfo>
) : ToolResultData() {

    @Serializable
    data class MemoryInfo(
        val title: String,
        val content: String,
        val source: String,
        val tags: List<String>,
        val createdAt: String,
        val chunkInfo: String? = null,
        val chunkIndices: List<Int>? = null
    )

    override fun toString(): String {
        if (memories.isEmpty()) {
            return "No relevant memories found."
        }
        return memories.joinToString("\n---\n") { memory ->
            """
            Title: ${memory.title}
            Content: ${memory.content.take(200)}...
            Source: ${memory.source}
            Tags: ${memory.tags.joinToString(", ")}
            Created: ${memory.createdAt}
            """.trimIndent()
        }
    }
}

/** 自动化配置搜索结果数据 */
@Serializable
data class AutomationConfigSearchResult(
    val searchPackageName: String?,
    val searchAppName: String?,
    val foundConfigs: List<ConfigInfo>,
    val totalFound: Int
) : ToolResultData() {
    
    @Serializable
    data class ConfigInfo(
        val appName: String,
        val packageName: String,
        val description: String,
        val isBuiltIn: Boolean,
        val fileName: String,
        val matchType: String  // "packageName" or "appName"
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("自动化配置搜索结果:")
        
        if (!searchPackageName.isNullOrBlank()) {
            sb.appendLine("搜索包名: $searchPackageName")
        }
        if (!searchAppName.isNullOrBlank()) {
            sb.appendLine("搜索应用名: $searchAppName")
        }
        
        sb.appendLine("找到 $totalFound 个匹配的配置:")
        
        if (foundConfigs.isEmpty()) {
            sb.appendLine("未找到匹配的自动化配置")
        } else {
            foundConfigs.forEach { config ->
                sb.appendLine()
                sb.appendLine("应用名: ${config.appName}")
                sb.appendLine("包名: ${config.packageName}")
                sb.appendLine("描述: ${config.description}")
                sb.appendLine("类型: ${if (config.isBuiltIn) "内置" else "用户导入"}")
                sb.appendLine("匹配方式: ${if (config.matchType == "packageName") "包名匹配" else "应用名匹配"}")
            }
        }
        
        return sb.toString()
    }
}

/** 自动化计划参数结果数据 */
@Serializable
data class AutomationPlanParametersResult(
    val functionName: String,
    val targetPackageName: String?,
    val requiredParameters: List<ParameterInfo>,
    val planSteps: Int,
    val planDescription: String
) : ToolResultData() {
    
    @Serializable
    data class ParameterInfo(
        val key: String,
        val description: String,
        val type: String,
        val isRequired: Boolean,
        val defaultValue: String?
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("自动化计划参数信息:")
        sb.appendLine("功能名称: $functionName")
        targetPackageName?.let { sb.appendLine("目标应用: $it") }
        sb.appendLine("计划描述: $planDescription")
        sb.appendLine()
        
        if (requiredParameters.isEmpty()) {
            sb.appendLine("此功能不需要额外参数，可以直接执行。")
        } else {
            sb.appendLine("需要的参数 (${requiredParameters.size} 个):")
            requiredParameters.forEach { param ->
                sb.appendLine()
                sb.appendLine("参数名: ${param.key}")
                sb.appendLine("描述: ${param.description}")
                sb.appendLine("类型: ${param.type}")
                sb.appendLine("必需: ${if (param.isRequired) "是" else "否"}")
                param.defaultValue?.let { sb.appendLine("默认值: $it") }
            }
        }
        
        return sb.toString()
    }
}

/** 自动化执行结果数据 */
@Serializable
data class AutomationExecutionResult(
    val functionName: String,
    val providedParameters: Map<String, String>,
    val executionSuccess: Boolean,
    val executionMessage: String,
    val executionError: String?,
    val finalState: UIStateInfo?,
    val executionSteps: Int
) : ToolResultData() {
    
    @Serializable
    data class UIStateInfo(
        val nodeId: String,
        val packageName: String,
        val activityName: String
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("自动化执行结果:")
        sb.appendLine("功能名称: $functionName")
        sb.appendLine("执行状态: ${if (executionSuccess) "成功" else "失败"}")
        sb.appendLine("执行步骤: $executionSteps")
        sb.appendLine("结果消息: $executionMessage")
        
        if (!executionError.isNullOrBlank()) {
            sb.appendLine("错误信息: $executionError")
        }
        
        if (providedParameters.isNotEmpty()) {
            sb.appendLine("\n使用的参数:")
            providedParameters.forEach { (key, value) ->
                sb.appendLine("  $key: $value")
            }
        }
        
        finalState?.let { state ->
            sb.appendLine("\n最终状态:")
            sb.appendLine("  节点ID: ${state.nodeId}")
            sb.appendLine("  应用包名: ${state.packageName}")
            sb.appendLine("  Activity: ${state.activityName}")
        }
        
        return sb.toString()
    }
}

/** 自动化功能列表结果数据 */
@Serializable
data class AutomationFunctionListResult(
    val packageName: String?,
    val functions: List<FunctionInfo>,
    val totalCount: Int
) : ToolResultData() {
    
    @Serializable
    data class FunctionInfo(
        val name: String,
        val description: String,
        val targetNodeName: String
    )

    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("可用的自动化功能:")
        packageName?.let { sb.appendLine("应用包名: $it") }
        sb.appendLine("功能数量: $totalCount")
        sb.appendLine()
        
        if (functions.isEmpty()) {
            sb.appendLine("当前没有可用的自动化功能")
        } else {
            functions.forEach { func ->
                sb.appendLine("功能名: ${func.name}")
                sb.appendLine("描述: ${func.description}")
                sb.appendLine("目标页面: ${func.targetNodeName}")
                sb.appendLine()
            }
        }
        
        return sb.toString()
    }
}

/** 终端会话创建结果数据 */
@Serializable
data class TerminalSessionCreationResultData(
    val sessionId: String,
    val sessionName: String,
    val isNewSession: Boolean
) : ToolResultData() {
    override fun toString(): String {
        return if (isNewSession) {
            "成功创建新的终端会话。会话名称: '$sessionName', 会话ID: $sessionId"
        } else {
            "成功获取现有的终端会话。会话名称: '$sessionName', 会话ID: $sessionId"
        }
    }
}

/** 终端会话关闭结果数据 */
@Serializable
data class TerminalSessionCloseResultData(
    val sessionId: String,
    val success: Boolean,
    val message: String
) : ToolResultData() {
    override fun toString(): String = message
}

/** Grep代码搜索结果数据 */
@Serializable
data class GrepResultData(
    val searchPath: String,
    val pattern: String,
    val matches: List<FileMatch>,
    val totalMatches: Int,
    val filesSearched: Int
) : ToolResultData() {
    
    @Serializable
    data class FileMatch(
        val filePath: String,
        val lineMatches: List<LineMatch>
    )
    
    @Serializable
    data class LineMatch(
        val lineNumber: Int,
        val lineContent: String,
        val matchContext: String? = null
    )
    
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Grep搜索结果:")
        sb.appendLine("搜索路径: $searchPath")
        sb.appendLine("搜索模式: $pattern")
        sb.appendLine("匹配总数: $totalMatches (在 ${matches.size} 个文件中)")
        sb.appendLine("搜索文件数: $filesSearched")
        sb.appendLine()
        
        if (matches.isEmpty()) {
            sb.appendLine("未找到匹配项")
        } else {
            matches.forEach { fileMatch ->
                sb.appendLine("文件: ${fileMatch.filePath}")
                fileMatch.lineMatches.forEach { lineMatch ->
                    // 如果有上下文，显示完整的上下文
                    if (lineMatch.matchContext != null && lineMatch.matchContext.isNotBlank()) {
                        val contextLines = lineMatch.matchContext.lines()
                        val centerIndex = contextLines.size / 2
                        
                        contextLines.forEachIndexed { idx, contextLine ->
                            // 计算每行的实际行号
                            val actualLineNum = lineMatch.lineNumber - centerIndex + idx
                            val lineNumStr = String.format("%6d", actualLineNum)
                            
                            // 匹配行用 > 标记
                            if (idx == centerIndex) {
                                sb.appendLine("$lineNumStr|>${contextLine}")
                            } else {
                                sb.appendLine("$lineNumStr| ${contextLine}")
                            }
                        }
                        sb.appendLine() // 在每个匹配块后添加空行
                    } else {
                        // 没有上下文，只显示匹配行
                        val lineNumStr = String.format("%6d", lineMatch.lineNumber)
                        sb.appendLine("$lineNumStr| ${lineMatch.lineContent}")
                    }
                }
                sb.appendLine()
            }
            
            if (matches.size > 20) {
                sb.appendLine("... 结果过多，仅显示前20个文件的匹配")
            }
        }
        
        return sb.toString()
    }
}

/** Tasker事件触发结果数据 */
@Serializable
data class TaskerResultData(
    val taskType: String,
    val args: Map<String, String>
) : ToolResultData() {
    override fun toString(): String {
        val sb = StringBuilder()
        sb.appendLine("Tasker事件触发结果:")
        sb.appendLine("事件类型: $taskType")
        
        if (args.isNotEmpty()) {
            sb.appendLine("参数:")
            args.forEach { (key, value) ->
                sb.appendLine("  $key: $value")
            }
        }
        
        return sb.toString()
    }
}