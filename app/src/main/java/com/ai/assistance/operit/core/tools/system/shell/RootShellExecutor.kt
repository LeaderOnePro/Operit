package com.ai.assistance.operit.core.tools.system.shell

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.tools.system.AndroidPermissionLevel
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.IOException

/** 基于Root权限的Shell命令执行器 实现ROOT权限级别的命令执行 */
class RootShellExecutor(private val context: Context) : ShellExecutor {
    companion object {
        private const val TAG = "RootShellExecutor"
        private var rootAvailable: Boolean? = null
        
        // 静态初始化，确保Shell配置只被设置一次
        init {
            // 配置 libsu 库的全局设置
            Shell.enableVerboseLogging = true
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
            )
            
            Log.d(TAG, "libsu Shell静态初始化完成")
        }
    }

    // 是否使用exec模式执行命令
    private var useExecMode = false

    init {
        Log.d(TAG, "RootShellExecutor实例初始化")
    }

    /**
     * 设置是否使用exec模式执行命令
     * @param useExec 是否使用exec模式
     */
    fun setUseExecMode(useExec: Boolean) {
        useExecMode = useExec
        Log.d(TAG, "Root命令执行模式设置为: ${if(useExec) "exec模式" else "libsu模式"}")
    }

    override fun getPermissionLevel(): AndroidPermissionLevel = AndroidPermissionLevel.ROOT

    override fun isAvailable(): Boolean {
        try {
            // 如果使用exec模式，检查su命令是否可用
            if (useExecMode) {
                return checkExecSuAvailable()
            }
            
            // 如果已经检查过，直接返回缓存结果，但不每次都输出日志
            if (rootAvailable != null) {
                // 使用更低级别的日志，减少输出量
                Log.v(TAG, "使用缓存的Root检查结果: $rootAvailable")
                return rootAvailable!!
            }

            // 使用 libsu 检查 root 权限
            val hasRoot = Shell.getShell().isRoot
            val previousValue = rootAvailable
            rootAvailable = hasRoot
            
            // 只在首次检查或值发生变化时输出日志
            if (previousValue != hasRoot) {
                Log.d(TAG, "Root访问检查: $hasRoot")
            }
            return hasRoot
        } catch (e: Exception) {
            Log.e(TAG, "检查Root权限时出错", e)
            rootAvailable = false
            return false
        }
    }
    
    /**
     * 检查通过exec方式执行su命令是否可用
     * @return su命令是否可用
     */
    private fun checkExecSuAvailable(): Boolean {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.append(line)
            }
            
            val exitCode = process.waitFor()
            val result = output.toString().trim()
            
            val available = exitCode == 0 && result.contains("uid=0")
            Log.d(TAG, "exec su可用性检查: $available (结果: $result, 退出码: $exitCode)")
            return available
        } catch (e: Exception) {
            Log.e(TAG, "exec su可用性检查失败", e)
            return false
        }
    }

    override fun hasPermission(): ShellExecutor.PermissionStatus {
        try {
            val available = isAvailable()
            return if (available) {
                ShellExecutor.PermissionStatus.granted()
            } else {
                ShellExecutor.PermissionStatus.denied("Root access not available on this device")
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查Root权限状态时出错", e)
            return ShellExecutor.PermissionStatus.denied("Error checking root permission: ${e.message}")
        }
    }

    override fun initialize() {
        try {
            // 如果使用exec模式，检查su命令是否可用
            if (useExecMode) {
                rootAvailable = checkExecSuAvailable()
                Log.d(TAG, "使用exec模式初始化, Root可用: $rootAvailable")
                return
            }
            
            // 初始化 libsu 主 Shell 实例
            Shell.getShell { shell ->
                Log.d(TAG, "Shell初始化完成, root: ${shell.isRoot}")
                rootAvailable = shell.isRoot
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化Shell时出错", e)
            rootAvailable = false
        }
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        try {
            // Root权限无法通过代码请求，只能提示用户
            val hasRoot = isAvailable()
            onResult(hasRoot)

            if (!hasRoot) {
                Log.d(TAG, "无法以编程方式请求Root权限")
            }
        } catch (e: Exception) {
            Log.e(TAG, "请求Root权限时出错", e)
            onResult(false)
        }
    }

    /**
     * 检查并提取run-as包装中的实际命令
     * @param command 可能包含run-as的命令
     * @return 提取后的实际命令
     */
    private fun extractActualCommand(command: String): String {
        // 检查命令是否是run-as格式
        val runAsPattern = """run-as\s+(\S+)\s+sh\s+-c\s+['"](.+)['"]""".toRegex()
        val match = runAsPattern.find(command)
        
        return if (match != null) {
            // 提取内部命令
            val innerCommand = match.groupValues[2]
            // 使用更低级别的日志，减少输出量
            Log.v(TAG, "提取run-as内部命令: $innerCommand")
            innerCommand
        } else {
            // 没有匹配到run-as格式，直接返回原命令
            command
        }
    }
    
    /**
     * 使用exec方式执行Root命令
     * @param command 要执行的命令
     * @return 命令执行结果
     */
    private suspend fun executeCommandWithExec(command: String): ShellExecutor.CommandResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "使用exec执行Root命令: $command")
                
                // 执行su -c命令
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                
                // 读取标准输出
                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stdout = StringBuilder()
                var line: String?
                while (stdoutReader.readLine().also { line = it } != null) {
                    stdout.append(line).append("\n")
                }
                
                // 读取标准错误
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))
                val stderr = StringBuilder()
                while (stderrReader.readLine().also { line = it } != null) {
                    stderr.append(line).append("\n")
                }
                
                // 等待进程完成并获取退出码
                val exitCode = process.waitFor()
                
                val stdoutStr = stdout.toString().trimEnd()
                val stderrStr = stderr.toString().trimEnd()
                
                Log.d(TAG, "exec执行完成，退出码: $exitCode")
                if (stdoutStr.isNotEmpty()) {
                    Log.v(TAG, "标准输出: $stdoutStr")
                }
                if (stderrStr.isNotEmpty()) {
                    Log.v(TAG, "标准错误: $stderrStr")
                }
                
                return@withContext ShellExecutor.CommandResult(
                    exitCode == 0,
                    stdoutStr,
                    stderrStr,
                    exitCode
                )
            } catch (e: Exception) {
                Log.e(TAG, "使用exec执行Root命令时出错", e)
                return@withContext ShellExecutor.CommandResult(
                    false,
                    "",
                    "错误: ${e.message}",
                    -1
                )
            }
        }
    }

    override suspend fun executeCommand(command: String): ShellExecutor.CommandResult =
            withContext(Dispatchers.IO) {
                try {
                    val permStatus = hasPermission()
                    if (!permStatus.granted) {
                        return@withContext ShellExecutor.CommandResult(false, "", permStatus.reason)
                    }
                    
                    // 如果使用exec模式，则使用exec执行命令
                    if (useExecMode) {
                        return@withContext executeCommandWithExec(command)
                    }

                    // 提取实际要执行的命令（如果是run-as包装的）
                    val actualCommand = extractActualCommand(command)
                    Log.d(TAG, "执行Root命令: $actualCommand (原始命令: $command)")

                    // 使用 libsu 执行命令
                    val shellResult = Shell.cmd(actualCommand).exec()
                    
                    // 处理执行结果
                    val stdout = shellResult.out.joinToString("\n")
                    val stderr = shellResult.err.joinToString("\n")
                    val exitCode = shellResult.code
                    
                    return@withContext ShellExecutor.CommandResult(
                            exitCode == 0,
                            stdout,
                            stderr,
                            exitCode
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "执行Root命令时出错", e)
                    return@withContext ShellExecutor.CommandResult(
                            false,
                            "",
                            "错误: ${e.message}",
                            -1
                    )
                }
            }
}
