package com.ai.assistance.operit.ui.features.toolbox.screens.terminal.model

// Updated to use streaming commands exclusively
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor.CommandResult
import com.ai.assistance.operit.core.tools.system.TermuxCommandExecutor
import com.ai.assistance.operit.core.tools.system.termux.TermuxCommandOptions
import com.ai.assistance.operit.core.tools.system.termux.TermuxCommandOutputReceiver
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * 终端会话 管理一个独立的终端会话，包括命令历史、工作目录等
 *
 * 关于Termux路径说明：
 * 1. Termux环境中的根目录不同于Android系统根目录
 * 2. Termux主目录通常是 /data/data/com.termux/files/home
 * 3. 绝对路径 (/) 在Termux中是相对于它自己的文件系统的
 * 4. 访问Android存储通常需要映射，如 /storage/emulated/0 对应 /sdcard
 * 5. 工作目录切换需要确保命令在正确的环境中执行
 */
class TerminalSession(
        val id: String = UUID.randomUUID().toString(),
        var name: String,
        initialDirectory: String? = null,
        val commandHistory: SnapshotStateList<TerminalLine> = mutableStateListOf(),
        var commandHistoryIndex: Int = -1,
        var termuxSessionId: String? = null
) {

    private val TAG = "TerminalSession"

    /** 创建带有命令前缀的日志消息 */
    private fun createLogMessage(command: String, message: String): String {
        val shortCommand = if (command.length > 20) command.substring(0, 20) + "..." else command
        return "[cmd: $shortCommand] $message"
    }

    /** 上一个工作目录 (用于支持 cd - 命令) */
    private var previousWorkingDirectory: String? = null

    /** 保存用户输入的命令历史 */
    private val userInputHistory = mutableListOf<String>()

    /** 默认命令提示符 */
    private val defaultPrompt = "$ "

    /** 当前用户 (如果使用su命令切换了用户) 此属性应当只影响当前会话，不影响其他会话 */
    var currentUser: String? = null
        internal set // 使属性可以从包内部修改（用于同步远程状态）

    /** 工作目录 */
    var workingDirectory: String = initialDirectory ?: "/data/data/com.termux/files/home"
        private set

    /**
     * 执行命令 通过Termux运行命令并处理特殊命令 返回Flow以实时获取输出
     * @param context 上下文
     * @param command 要执行的命令
     * @param scope 协程作用域
     * @param onCompletion 命令完成回调，提供退出码和成功状态
     * @return 输出流
     */
    fun executeCommand(
            context: Context,
            command: String,
            scope: CoroutineScope,
            onCompletion: (exitCode: Int, success: Boolean) -> Unit
    ): Flow<TerminalLine.Output> {
        // 创建输出流
        val outputFlow = MutableSharedFlow<TerminalLine.Output>(replay = 0)

        // 在协程中执行命令
        scope.launch(Dispatchers.IO) {
            // 特殊命令处理
            when {
                command.trim() == "clear" -> {
                    Log.d(TAG, createLogMessage(command, "执行清屏命令"))
                    commandHistory.clear()
                    onCompletion(0, true)
                    return@launch
                }
                command.trim() == "help" -> {
                    Log.d(TAG, createLogMessage(command, "执行帮助命令"))
                    outputFlow.emit(
                            TerminalLine.Output(
                                    "Available commands:\n" +
                                            "  help - Display this help information\n" +
                                            "  clear - Clear the terminal\n" +
                                            "  cd [directory] - Change directory\n" +
                                            "  su [user] - Switch user\n" +
                                            "  pwd - Display current working directory\n" +
                                            "  exit - Close this terminal session"
                            )
                    )
                    onCompletion(0, true)
                    return@launch
                }
                command.trim().startsWith("cd ") -> {
                    Log.d(TAG, createLogMessage(command, "执行CD命令"))
                    // Check if this is a combined command with &&
                    if (command.contains(" && ")) {
                        val parts = command.split(" && ", limit = 2)
                        val cdCommand = parts[0].trim()
                        val remainingCommand = parts[1].trim()

                        // Extract cd argument
                        val cdArg = cdCommand.substring(2).trim()
                        Log.d(
                                TAG,
                                createLogMessage(
                                        command,
                                        "拆分组合命令: cd '$cdArg' && '$remainingCommand'"
                                )
                        )

                        // Handle cd part first
                        handleCdCommand(context, cdArg, outputFlow)

                        // Then execute the remaining command with updated working directory
                        if (remainingCommand.isNotEmpty()) {
                            Log.d(
                                    TAG,
                                    createLogMessage(command, "执行组合命令的第二部分: '$remainingCommand'")
                            )
                            // 获取第二个命令的输出流
                            val remainingCommandFlow =
                                    executeCommand(context, remainingCommand, scope, onCompletion)

                            // 收集第二个命令的输出并转发到当前的outputFlow
                            scope.launch {
                                remainingCommandFlow.collect { line -> outputFlow.emit(line) }
                            }
                            return@launch
                        } else {
                            onCompletion(0, true)
                            return@launch
                        }
                    }

                    // Original cd handling for simple cd commands without &&
                    // 处理cd命令，更新工作目录
                    // 确保即使是单个字符的参数也不会丢失
                    val cdArg = command.trim().substring(2).trim()
                    Log.d(TAG, createLogMessage(command, "提取CD参数: '$cdArg'"))

                    // 处理全角斜杠，将其转换为半角斜杠
                    val normalizedArg = cdArg.replace("／", "/")
                    Log.d(TAG, createLogMessage(command, "标准化CD参数: '$normalizedArg'"))

                    // 检查参数是否为空
                    if (normalizedArg.isEmpty() || normalizedArg == " ") {
                        // cd 后面没有参数或只有空格，切换到主目录
                        Log.d(TAG, createLogMessage(command, "CD无参数，切换到主目录"))
                        handleCdCommand(context, "~", outputFlow)
                    } else {
                        // 去掉可能的前导空格
                        Log.d(TAG, createLogMessage(command, "执行CD到: '$normalizedArg'"))
                        handleCdCommand(context, normalizedArg, outputFlow)
                    }
                    onCompletion(0, true)
                    return@launch
                }
                command.trim() == "cd" -> {
                    // 单独的cd命令，切换到主目录
                    Log.d(TAG, createLogMessage(command, "执行CD命令（无参数），切换到主目录"))
                    handleCdCommand(context, "~", outputFlow)
                    onCompletion(0, true)
                    return@launch
                }
                command.trim() == "pwd" -> {
                    // 显示当前工作目录
                    Log.d(TAG, createLogMessage(command, "执行pwd命令，当前工作目录: $workingDirectory"))
                    outputFlow.emit(TerminalLine.Output(workingDirectory))
                    onCompletion(0, true)
                    return@launch
                }
                command.trim().startsWith("su ") || command.trim() == "su" -> {
                    // 处理su命令，切换用户
                    Log.d(TAG, createLogMessage(command, "执行su命令，切换用户"))
                    handleSuCommand(context, command, scope, outputFlow)
                    // 注意：su命令的完成状态由handleSuCommand内部处理
                    onCompletion?.invoke(0, true)
                    return@launch
                }
                command.trim() == "exit" && currentUser != null -> {
                    // 如果当前是su状态，则退出su而不是终端
                    Log.d(TAG, createLogMessage(command, "执行exit命令，当前有用户 $currentUser，退出su状态"))
                    currentUser = null
                    outputFlow.emit(TerminalLine.Output("Returned to normal user"))
                    onCompletion?.invoke(0, true)
                    return@launch
                }
                else -> {
                    // 将命令添加到用户输入历史
                    if (command.isNotEmpty()) {
                        userInputHistory.add(command)
                    }

                    // 创建命令选项，确保使用最新的工作目录
                    val options = createCommandOptions(command)

                    // 记录工作目录，以便调试
                    Log.d(
                            TAG,
                            createLogMessage(command, "执行常规命令，当前工作目录: ${options.workingDirectory}")
                    )

                    // 直接使用原始命令，不添加前缀
                    // TermuxCommandExecutor会使用options中的workingDirectory
                    val effectiveCommand = command

                    Log.d(TAG, createLogMessage(command, "最终执行命令: '$effectiveCommand'"))

                    // 创建输出接收器，以便实时更新终端
                    val outputReceiver =
                            object : TermuxCommandOutputReceiver {
                                private val stdoutBuffer = StringBuilder()
                                private val stderrBuffer = StringBuilder()
                                private var lastOutputLine = ""
                                private var commandExitCode = -1

                                override fun onStdout(output: String, isComplete: Boolean) {
                                    if (output.isEmpty()) return

                                    // 调试输出
                                    Log.d(
                                            TAG,
                                            createLogMessage(
                                                    command,
                                                    "收到标准输出: ${if (output.length > 100) output.substring(0, 100) + "..." else output}"
                                            )
                                    )

                                    // 将输出按行分割并处理
                                    val lines = output.split("\n")
                                    for (line in lines) {
                                        if (line.isEmpty()) continue

                                        // 过滤掉控制输出
                                        if (line.contains("COMMAND_START") ||
                                                        line.contains("COMMAND_END") ||
                                                        line.contains("COMMAND_EXIT_CODE:") ||
                                                        line.startsWith("STARTED: ")
                                        ) {
                                            Log.d(TAG, createLogMessage(command, "过滤控制输出: $line"))

                                            // 捕获退出码
                                            if (line.contains("COMMAND_EXIT_CODE:")) {
                                                val codeMatch =
                                                        "COMMAND_EXIT_CODE:(\\d+)"
                                                                .toRegex()
                                                                .find(line)
                                                codeMatch
                                                        ?.groupValues
                                                        ?.get(1)
                                                        ?.toIntOrNull()
                                                        ?.let { code ->
                                                            commandExitCode = code
                                                            Log.d(
                                                                    TAG,
                                                                    createLogMessage(
                                                                            command,
                                                                            "捕获命令退出码: $commandExitCode"
                                                                    )
                                                            )
                                                        }
                                            }

                                            continue
                                        }

                                        // 添加到输出缓冲区并显示
                                        scope.launch { outputFlow.emit(TerminalLine.Output(line)) }
                                        stdoutBuffer.append(line).append("\n")
                                        lastOutputLine = line
                                    }
                                }

                                override fun onStderr(error: String, isComplete: Boolean) {
                                    if (error.isEmpty()) return

                                    // 调试输出
                                    Log.d(TAG, createLogMessage(command, "收到错误输出: $error"))

                                    // 检查是否是交互式提示特殊标记
                                    if (error.startsWith("INTERACTIVE_PROMPT:")) {
                                        // 直接转发交互式提示消息，不做处理
                                        scope.launch { outputFlow.emit(TerminalLine.Output(error)) }
                                        return
                                    }

                                    // 将错误输出按行分割并处理
                                    val lines = error.split("\n")
                                    for (line in lines) {
                                        if (line.isEmpty()) continue

                                        // 添加到错误缓冲区并显示
                                        scope.launch { outputFlow.emit(TerminalLine.Output(line)) }
                                        stderrBuffer.append(line).append("\n")
                                        lastOutputLine = line
                                    }
                                }

                                override fun onComplete(result: CommandResult) {
                                    // 调试输出
                                    Log.d(TAG, createLogMessage(command, "命令完成: $result"))
                                    Log.d(
                                            TAG,
                                            createLogMessage(
                                                    command,
                                                    "收到的stdout长度: ${result.stdout.length}, stderr长度: ${result.stderr.length}"
                                            )
                                    )

                                    // 命令完成，检查是否有未显示的输出
                                    scope.launch {
                                        try {
                                            // 修改: 确保完整结果显示
                                            if (result.stdout.isNotEmpty()) {
                                                Log.d(
                                                        TAG,
                                                        createLogMessage(
                                                                command,
                                                                "处理最终stdout: ${result.stdout.length}字节"
                                                        )
                                                )
                                                // 处理最终输出，按行显示确保格式正确
                                                val finalLines =
                                                        result.stdout.split("\n").filter {
                                                            it.isNotEmpty()
                                                        }

                                                for (line in finalLines) {
                                                    // 过滤掉控制输出
                                                    if (line.contains("COMMAND_START") ||
                                                                    line.contains("COMMAND_END") ||
                                                                    line.contains(
                                                                            "COMMAND_EXIT_CODE:"
                                                                    ) ||
                                                                    line.startsWith("STARTED: ")
                                                    ) {
                                                        continue
                                                    }

                                                    // 检查是否已有此行避免重复
                                                    if (!stdoutBuffer.toString().contains(line)) {
                                                        outputFlow.emit(TerminalLine.Output(line))
                                                        // Log.d(TAG, createLogMessage(command,
                                                        // "发送输出行: $line"))
                                                    }
                                                }
                                            }

                                            if (result.stderr.isNotEmpty()) {
                                                Log.d(
                                                        TAG,
                                                        createLogMessage(
                                                                command,
                                                                "处理最终stderr: ${result.stderr.length}字节"
                                                        )
                                                )
                                                // 处理最终错误，按行显示确保格式正确
                                                val finalErrorLines =
                                                        result.stderr.split("\n").filter {
                                                            it.isNotEmpty()
                                                        }

                                                for (line in finalErrorLines) {
                                                    if (!stderrBuffer.toString().contains(line)) {
                                                        outputFlow.emit(TerminalLine.Output(line))
                                                        Log.d(
                                                                TAG,
                                                                createLogMessage(
                                                                        command,
                                                                        "发送错误行: $line"
                                                                )
                                                        )
                                                    }
                                                }
                                            }

                                            // 添加命令完成标记，帮助调试
                                            if (!result.success) {
                                                outputFlow.emit(
                                                        TerminalLine.Output(
                                                                "[命令失败，退出码: ${result.exitCode}]"
                                                        )
                                                )
                                            }

                                            // 调用完成回调
                                            val exitCode =
                                                    if (commandExitCode != -1) commandExitCode
                                                    else result.exitCode
                                            onCompletion?.invoke(exitCode, result.success)
                                        } catch (e: Exception) {
                                            Log.e(
                                                    TAG,
                                                    createLogMessage(
                                                            command,
                                                            "处理命令结果时出错: ${e.message}"
                                                    ),
                                                    e
                                            )
                                            outputFlow.emit(
                                                    TerminalLine.Output("[处理输出时出错: ${e.message}]")
                                            )
                                            onCompletion?.invoke(-1, false)
                                        }
                                    }
                                }

                                override fun onError(error: String, exitCode: Int) {
                                    // 调试输出
                                    Log.e(
                                            TAG,
                                            createLogMessage(
                                                    command,
                                                    "命令出错: $error (code: $exitCode)"
                                            )
                                    )

                                    // 显示错误信息
                                    scope.launch {
                                        outputFlow.emit(
                                                TerminalLine.Output(
                                                        "Error: $error (code: $exitCode)"
                                                )
                                        )
                                        onCompletion?.invoke(exitCode, false)
                                    }
                                }
                            }

                    // 使用TermuxCommandExecutor执行命令并确保结果立即处理
                    try {
                        Log.d(
                                TAG,
                                createLogMessage(
                                        command,
                                        "执行命令: '$effectiveCommand'，工作目录: '${options.workingDirectory}'"
                                )
                        )
                        TermuxCommandExecutor.executeCommandStreaming(
                                context = context,
                                command = effectiveCommand,
                                background = true,
                                outputReceiver = outputReceiver,
                                options = options
                        )
                        Log.d(TAG, createLogMessage(command, "命令已发送，等待结果"))
                        // 注意：结果会通过outputReceiver传递
                    } catch (e: Exception) {
                        // 处理异常情况
                        Log.e(TAG, createLogMessage(command, "执行命令时发生异常: ${e.message}"), e)
                        scope.launch {
                            outputFlow.emit(
                                    TerminalLine.Output("Error executing command: ${e.message}")
                            )
                            onCompletion?.invoke(-1, false)
                        }
                    }
                }
            }
        }

        return outputFlow
    }

    /** 处理CD命令 */
    private suspend fun handleCdCommand(
            context: Context,
            directory: String,
            outputFlow: MutableSharedFlow<TerminalLine.Output>
    ) {
        val cdCommand = "cd $directory"

        // 记录开始状态
        Log.d(
                TAG,
                createLogMessage(cdCommand, "CD命令开始: 当前工作目录=$workingDirectory, 目标目录='$directory'")
        )

        try {
            // 处理特殊路径
            val targetDir =
                    when {
                        directory == "-" -> {
                            // cd - 命令切换到上一个工作目录
                            Log.d(
                                    TAG,
                                    createLogMessage(
                                            cdCommand,
                                            "切换到上一个工作目录: ${previousWorkingDirectory ?: workingDirectory}"
                                    )
                            )
                            previousWorkingDirectory ?: workingDirectory
                        }
                        directory == "~" -> {
                            // cd ~ 命令切换到主目录
                            Log.d(
                                    TAG,
                                    createLogMessage(
                                            cdCommand,
                                            "切换到主目录: /data/data/com.termux/files/home"
                                    )
                            )
                            "/data/data/com.termux/files/home"
                        }
                        directory == "." || directory == "./" -> {
                            // 当前目录，不改变工作目录
                            Log.d(TAG, createLogMessage(cdCommand, "当前目录，保持不变: $workingDirectory"))
                            workingDirectory
                        }
                        directory == ".." || directory == "../" -> {
                            // 上级目录
                            val parts = workingDirectory.split("/").filter { it.isNotEmpty() }
                            if (parts.isEmpty() || workingDirectory == "/") {
                                // 已经在根目录，保持不变
                                Log.d(TAG, createLogMessage(cdCommand, "已在根目录，保持不变: /"))
                                "/"
                            } else {
                                // 移除最后一个部分，返回上级目录
                                val parentDir = "/" + parts.dropLast(1).joinToString("/")
                                Log.d(TAG, createLogMessage(cdCommand, "切换到上级目录: $parentDir"))
                                parentDir
                            }
                        }
                        directory.startsWith("../") -> {
                            // 处理以../开头的相对路径
                            var tempDir = workingDirectory
                            var remainingPath = directory
                            Log.d(TAG, createLogMessage(cdCommand, "处理路径: $directory (以../开头)"))

                            // 处理每个../部分
                            while (remainingPath.startsWith("../")) {
                                // 移除一级目录
                                tempDir =
                                        if (tempDir == "/" || tempDir.isEmpty()) {
                                            "/"
                                        } else {
                                            val parts =
                                                    tempDir.split("/").filter { it.isNotEmpty() }
                                            if (parts.isEmpty()) {
                                                "/"
                                            } else {
                                                "/" + parts.dropLast(1).joinToString("/")
                                            }
                                        }
                                Log.d(TAG, createLogMessage(cdCommand, "处理../ - 临时路径: $tempDir"))
                                // 移除处理过的../
                                remainingPath = remainingPath.substring(3)
                            }

                            // 处理剩余路径
                            val finalPath =
                                    if (remainingPath.isEmpty()) {
                                        tempDir
                                    } else if (tempDir == "/") {
                                        tempDir + remainingPath
                                    } else {
                                        tempDir + "/" + remainingPath
                                    }
                            Log.d(TAG, createLogMessage(cdCommand, "最终路径: $finalPath"))
                            finalPath
                        }
                        directory.startsWith("./") -> {
                            // 处理以./开头的相对路径
                            val pathWithoutPrefix = directory.substring(2)
                            val finalPath =
                                    if (workingDirectory.endsWith("/")) {
                                        workingDirectory + pathWithoutPrefix
                                    } else {
                                        workingDirectory + "/" + pathWithoutPrefix
                                    }
                            Log.d(TAG, createLogMessage(cdCommand, "处理./路径: $finalPath"))
                            finalPath
                        }
                        directory.startsWith("/") -> {
                            // 绝对路径，直接使用
                            Log.d(TAG, createLogMessage(cdCommand, "使用绝对路径: $directory"))
                            directory
                        }
                        else -> {
                            // 相对路径，基于当前工作目录解析
                            val finalPath =
                                    if (workingDirectory.endsWith("/")) {
                                        workingDirectory + directory
                                    } else {
                                        workingDirectory + "/" + directory
                                    }
                            Log.d(TAG, createLogMessage(cdCommand, "使用相对路径: $finalPath"))
                            finalPath
                        }
                    }

            Log.d(TAG, createLogMessage(cdCommand, "CD目标目录: '$targetDir'"))

            // 执行pwd命令，使用目标目录作为工作目录
            // 这将测试目录是否存在并可访问
            val options =
                    TermuxCommandOptions(
                            workingDirectory = targetDir, // 直接设置为目标目录
                            background = true,
                            sessionAction = TermuxCommandOptions.SessionAction.ACTION_NEW_SESSION,
                            user = currentUser
                    )

            // 执行pwd命令来验证目录能否访问
            val pwdResult =
                    TermuxCommandExecutor.executeCommandStreaming(
                            context = context,
                            command = "pwd",
                            background = true,
                            resultCallback = null,
                            options = options
                    )

            if (pwdResult.success) {
                // 目录存在且可访问，更新工作目录
                val newDir = targetDir // 使用pwd的输出作为实际路径，避免符号链接问题

                if (newDir.isNotEmpty()) {
                    // 保存旧目录用于cd -命令
                    previousWorkingDirectory = workingDirectory

                    // 更新工作目录
                    val oldDir = workingDirectory
                    workingDirectory = newDir
                    Log.d(
                            TAG,
                            createLogMessage(cdCommand, "工作目录已更新: '$oldDir' -> '$workingDirectory'")
                    )
                } else {
                    Log.e(TAG, createLogMessage(cdCommand, "CD失败: 无法获取目录路径"))
                    outputFlow.emit(TerminalLine.Output("cd: 无法获取目录路径"))
                }
            } else {
                // 目录不存在或无法访问
                Log.e(TAG, createLogMessage(cdCommand, "CD失败: 目录不存在或无法访问: $directory"))
                outputFlow.emit(TerminalLine.Output("cd: $directory: No such file or directory"))
                Log.e(
                        TAG,
                        createLogMessage(
                                cdCommand,
                                "目录不存在或无法访问: $targetDir, 错误: ${pwdResult.stderr}"
                        )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, createLogMessage(cdCommand, "CD命令异常: ${e.message}"), e)
            outputFlow.emit(TerminalLine.Output("cd: 执行错误: ${e.message}"))
        }
    }

    /** 处理su命令 */
    private suspend fun handleSuCommand(
            context: Context,
            command: String,
            scope: CoroutineScope,
            outputFlow: MutableSharedFlow<TerminalLine.Output>
    ) {
        // 提取用户名，如果没有提供则默认为root
        val parts = command.trim().split("\\s+".toRegex())
        val user = if (parts.size > 1) parts[1] else "root"
        Log.d(TAG, createLogMessage(command, "处理su命令，目标用户: $user"))

        // 执行su命令检查是否成功
        val checkCmd = "su $user -c 'whoami'"
        val options =
                TermuxCommandOptions(
                        workingDirectory = workingDirectory,
                        background = true,
                        sessionAction = TermuxCommandOptions.SessionAction.ACTION_NEW_SESSION
                )

        // 创建输出接收器
        val outputReceiver =
                object : TermuxCommandOutputReceiver {
                    override fun onStdout(output: String, isComplete: Boolean) {
                        // 不需要处理中间输出
                        Log.d(TAG, createLogMessage(command, "su命令stdout: $output"))
                    }

                    override fun onStderr(error: String, isComplete: Boolean) {
                        if (error.isNotEmpty()) {
                            Log.e(TAG, createLogMessage(command, "su命令stderr: $error"))
                            scope.launch { outputFlow.emit(TerminalLine.Output("Error: $error")) }
                        }
                    }

                    override fun onComplete(result: CommandResult) {
                        scope.launch {
                            if (result.success) {
                                // 切换用户成功 - 只修改当前会话的用户状态
                                currentUser = user
                                Log.d(TAG, createLogMessage(command, "已切换到用户: $user"))
                                // 输出一个特殊的状态变更消息，以便UI可以特殊处理
                                outputFlow.emit(TerminalLine.Output("USER_SWITCHED:" + user))
                                outputFlow.emit(TerminalLine.Output("Switched to user: $user"))
                            } else {
                                // 切换失败
                                Log.e(TAG, createLogMessage(command, "切换用户失败: ${result.stderr}"))
                                outputFlow.emit(
                                        TerminalLine.Output(
                                                "Failed to switch user: ${result.stderr}"
                                        )
                                )
                            }
                        }
                    }

                    override fun onError(error: String, exitCode: Int) {
                        Log.e(TAG, createLogMessage(command, "su命令错误: $error (code: $exitCode)"))
                        scope.launch {
                            outputFlow.emit(TerminalLine.Output("Failed to switch user: $error"))
                        }
                    }
                }

        Log.d(TAG, createLogMessage(command, "执行su检查命令: $checkCmd"))
        TermuxCommandExecutor.executeCommandStreaming(
                context = context,
                command = checkCmd,
                background = true,
                outputReceiver = outputReceiver,
                resultCallback = null,
                options = options
        )
    }

    /** 创建命令选项 */
    private fun createCommandOptions(command: String): TermuxCommandOptions {
        // 确定会话操作模式
        val sessionAction =
                if (termuxSessionId != null) {
                    TermuxCommandOptions.SessionAction.ACTION_USE_CURRENT_SESSION
                } else {
                    TermuxCommandOptions.SessionAction.ACTION_NEW_SESSION
                }

        // 确保工作目录有效
        var effectiveWorkingDir = workingDirectory
        if (effectiveWorkingDir.isEmpty()) {
            Log.w(TAG, createLogMessage(command, "警告: 工作目录为空，使用默认目录"))
            effectiveWorkingDir = "/data/data/com.termux/files/home"
        }

        // 记录选项创建信息
        Log.d(
                TAG,
                createLogMessage(
                        command,
                        "创建命令选项 - 工作目录: '$effectiveWorkingDir', 用户: '$currentUser', 命令: '$command'"
                )
        )

        // 创建命令选项
        return TermuxCommandOptions(
                workingDirectory = effectiveWorkingDir,
                background = true,
                sessionAction = sessionAction,
                label = "Session command: $name",
                description = "Running: $command",
                user = currentUser
        )
    }

    /** 获取命令提示符 */
    fun getPrompt(): String {
        // 记录工作目录
        Log.d(TAG, "生成提示符时的工作目录: $workingDirectory")

        // 提取工作目录最后一部分作为当前目录
        val currentDir =
                if (workingDirectory == "/") {
                    "/"
                } else {
                    val parts = workingDirectory.split("/").filter { it.isNotEmpty() }
                    parts.lastOrNull() ?: "/"
                }

        // 创建提示符
        val prompt =
                if (currentUser != null) {
                    if (currentUser == "root") {
                        "[$currentUser@$currentDir]# "
                    } else {
                        "[$currentUser@$currentDir]$ "
                    }
                } else {
                    "[$currentDir]$ "
                }

        Log.d(TAG, "最终提示符: $prompt")
        return prompt
    }

    /** 获取上一条命令历史 */
    fun getPreviousCommand(): String? {
        if (userInputHistory.isEmpty()) return null

        if (commandHistoryIndex == -1) {
            commandHistoryIndex = userInputHistory.size - 1
        } else if (commandHistoryIndex > 0) {
            commandHistoryIndex--
        }

        return if (commandHistoryIndex >= 0 && commandHistoryIndex < userInputHistory.size) {
            userInputHistory[commandHistoryIndex]
        } else {
            null
        }
    }

    /** 获取下一条命令历史 */
    fun getNextCommand(): String? {
        if (userInputHistory.isEmpty() || commandHistoryIndex == -1) return null

        if (commandHistoryIndex < userInputHistory.size - 1) {
            commandHistoryIndex++
            return userInputHistory[commandHistoryIndex]
        } else {
            commandHistoryIndex = userInputHistory.size
            return ""
        }
    }

    /** 重置命令历史索引 */
    fun resetCommandHistoryIndex() {
        commandHistoryIndex = -1
    }

    /** 获取用户输入历史列表 */
    fun getUserInputHistory(): List<String> {
        return userInputHistory.toList()
    }
}

/** 终端行类型 */
sealed class TerminalLine {
    data class Input(val text: String, val prompt: String = "$ ") : TerminalLine()
    data class Output(val text: String) : TerminalLine()
}
