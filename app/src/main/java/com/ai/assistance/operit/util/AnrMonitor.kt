package com.ai.assistance.operit.util

import android.content.Context
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.StringWriter
import java.io.PrintWriter
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * ANR监控器，用于跟踪和记录可能导致ANR的主线程阻塞
 * 
 * @param context 应用上下文
 * @param coroutineScope 用于启动监控协程的作用域
 * @param tag 日志标签
 */
class AnrMonitor(
    private val context: Context, 
    private val coroutineScope: CoroutineScope,
    private val tag: String = "AnrMonitor"
) {
    companion object {
        // 默认阈值设置
        private const val ANR_THRESHOLD_MS = 1000L     // 5秒，标准ANR阈值
        private const val WARNING_THRESHOLD_MS = 500L // 1秒，警告阈值
        private const val SAMPLING_INTERVAL_MS = 100L  // 100毫秒采样间隔
        private const val MAX_STACK_TRACES = 10        // 最大堆栈跟踪数
        
        // 主线程名称
        private const val MAIN_THREAD_NAME = "main"
        
        // ANR可能的原因
        private val ANR_PATTERNS = arrayOf(
            "synchronized",
            "lock",
            "runblocking",
            "thread.join",
            "wait(",
            "textsegmenter",
            "initialize",
            "problemlibrary",
            "read",
            "write",
            "socket",
            "io",
            "database",
            "exec"
        )
        
        // ANR相关关键包路径
        private val IMPORTANT_PACKAGES = arrayOf(
            "com.ai.assistance.operit.",
            "java.io.",
            "kotlinx.coroutines.",
            "android.os.",
            "java.util.concurrent."
        )
    }
    
    private val running = AtomicBoolean(false)
    private val lastResponseTime = AtomicLong(System.currentTimeMillis())
    private var monitoringJob: Job? = null
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    
    // 后备方案：如果协程有问题，使用ScheduledExecutorService
    private var scheduledExecutor: ScheduledExecutorService? = null
    
    // 记录ANR次数和严重程度
    private val anrCount = AtomicInteger(0)
    private val warningCount = AtomicInteger(0)
    private val maxBlockDuration = AtomicLong(0)
    
    // 堆栈跟踪历史
    private val stackTraces = mutableListOf<Pair<Long, String>>()
    
    // 跟踪调用者信息
    private val callerInfo = ConcurrentHashMap<String, String>()
    
    // 最后一次获取到的主线程引用
    private var mainThread: Thread? = null
    
    /**
     * 开始ANR监控
     */
    fun start() {
        if (running.getAndSet(true)) {
            Log.w(tag, "ANR监控器已经在运行中")
            return
        }
        
        Log.d(tag, "启动ANR监控器")
        lastResponseTime.set(System.currentTimeMillis())
        
        // 尝试获取主线程引用
        try {
            mainThread = getMainThread()
            Log.d(tag, "已获取主线程引用: $mainThread")
        } catch (e: Exception) {
            Log.e(tag, "获取主线程引用失败", e)
        }
        
        try {
            // 尝试启动协程监控
            monitoringJob = coroutineScope.launch(Dispatchers.Default) {
                while (running.get()) {
                    checkMainThreadHealth()
                    delay(SAMPLING_INTERVAL_MS)
                }
            }
        } catch (e: Exception) {
            // 如果协程启动失败，使用线程池作为备选方案
            Log.e(tag, "协程启动失败，使用备选线程池监控", e)
            startUsingExecutor()
        }
    }
    
    /**
     * 使用ScheduledExecutorService开始监控（备选方案）
     */
    private fun startUsingExecutor() {
        if (scheduledExecutor == null || scheduledExecutor?.isShutdown == true) {
            scheduledExecutor = Executors.newSingleThreadScheduledExecutor { r ->
                val t = Thread(r, "AnrMonitor-Watchdog")
                t.priority = Thread.MAX_PRIORITY
                t.isDaemon = true
                t
            }
        }
        
        scheduledExecutor?.scheduleAtFixedRate({
            if (running.get()) {
                checkMainThreadHealth()
            } else {
                scheduledExecutor?.shutdown()
            }
        }, 0, SAMPLING_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }
    
    /**
     * 停止ANR监控
     */
    fun stop() {
        if (!running.getAndSet(false)) {
            return
        }
        
        Log.d(tag, "停止ANR监控器，监控结果：ANR次数=${anrCount.get()}, 警告次数=${warningCount.get()}, 最长阻塞时间=${maxBlockDuration.get()}ms")
        monitoringJob?.cancel()
        scheduledExecutor?.shutdown()
        
        // 如果有记录到ANR，保存报告
        if (anrCount.get() > 0 || warningCount.get() > 0) {
            saveAnrReport()
        }
    }
    
    /**
     * 报告主线程正常响应
     */
    fun reportThreadHealthy() {
        lastResponseTime.set(System.currentTimeMillis())
    }
    
    /**
     * 报告主线程响应缓慢
     */
    fun reportSlowResponse(responseTime: Long) {
        if (responseTime > WARNING_THRESHOLD_MS) {
            warningCount.incrementAndGet()
            if (responseTime > maxBlockDuration.get()) {
                maxBlockDuration.set(responseTime)
            }
            
            if (responseTime > ANR_THRESHOLD_MS) {
                val anrCount = anrCount.incrementAndGet()
                Log.e(tag, "检测到可能的ANR! 响应时间: ${responseTime}ms, 这是第${anrCount}次ANR")
                captureFullThreadDump()
            } else {
                Log.w(tag, "主线程响应缓慢: ${responseTime}ms")
            }
        }
    }
    
    /**
     * 添加调用者信息，帮助跟踪ANR来源
     */
    fun addCallerInfo(key: String, info: String) {
        callerInfo[key] = "[$key] $info (${SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())})"
    }
    
    /**
     * 检查主线程健康状态
     */
    private fun checkMainThreadHealth() {
        mainThreadHandler.post {
            reportThreadHealthy()
        }

        val now = System.currentTimeMillis()
        val lastResponse = lastResponseTime.get()
        val timeSinceLastResponse = now - lastResponse
        
        if (timeSinceLastResponse > WARNING_THRESHOLD_MS) {
            // 主线程可能被阻塞
            val message = "主线程未响应: ${timeSinceLastResponse}ms"
            
            if (timeSinceLastResponse > ANR_THRESHOLD_MS) {
                // 已超过ANR阈值
                Log.e(tag, "$message - 可能发生ANR!")
                anrCount.incrementAndGet()
                
                // 记录堆栈跟踪 - 使用增强的堆栈捕获
                captureFullThreadDump()
                
                if (timeSinceLastResponse > maxBlockDuration.get()) {
                    maxBlockDuration.set(timeSinceLastResponse)
                }
            } else {
                // 超过警告阈值但未到ANR阈值
                Log.w(tag, "$message - 警告")
                warningCount.incrementAndGet()
            }
        }
    }
    
    /**
     * 捕获主线程堆栈
     */
    private fun captureMainThreadStack() {
        mainThreadHandler.post {
            try {
                val stackTrace = Thread.currentThread().stackTrace
                    .drop(3) // 跳过前三个元素（VM相关调用）
                    .joinToString("\n") { "    at $it" }
                    
                val timeStamp = System.currentTimeMillis()
                val trace = Pair(timeStamp, stackTrace)
                
                synchronized(stackTraces) {
                    stackTraces.add(trace)
                    // 限制堆栈历史数量
                    if (stackTraces.size > MAX_STACK_TRACES) {
                        stackTraces.removeAt(0)
                    }
                }
                
                // 分析堆栈
                val analysis = analyzeStackTrace(stackTrace)
                
                Log.e(tag, "主线程堆栈跟踪:\n$stackTrace\n$analysis")
            } catch (e: Exception) {
                Log.e(tag, "捕获堆栈失败", e)
            }
        }
    }
    
    /**
     * 获取主线程实例的引用
     */
    private fun getMainThread(): Thread? {
        try {
            // 尝试方法1：通过Looper的对应线程
            Looper.getMainLooper().thread?.let { return it }
            
            // 尝试方法2：遍历所有线程查找main线程
            val threadGroup = Thread.currentThread().threadGroup ?: return null
            val threadCount = threadGroup.activeCount()
            val threads = arrayOfNulls<Thread>(threadCount)
            threadGroup.enumerate(threads)
            
            return threads.filterNotNull().find { it.name == MAIN_THREAD_NAME }
        } catch (e: Exception) {
            Log.e(tag, "获取主线程失败", e)
            return null
        }
    }
    
    /**
     * 捕获完整的线程转储信息，包括主线程和其他重要线程
     */
    private fun captureFullThreadDump() {
        try {
            val sbDump = StringBuilder()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            
            sbDump.append("=== 线程转储 (${dateFormat.format(Date())}) ===\n")
            
            // 首先获取主线程信息
            val mainThreadStack: String = mainThread?.let {
                try {
                    val stackTraceElements = it.stackTrace
                    val stackStr = stackTraceElements.joinToString("\n") { element -> "    at $element" }
                    "Main Thread '${it.name}' (状态: ${it.state}):\n$stackStr"
                } catch (e: Exception) {
                    "无法获取主线程堆栈: ${e.message}"
                }
            } ?: "无法获取主线程引用"
            
            // 添加主线程信息
            sbDump.append("【主线程】\n$mainThreadStack\n\n")
            
            // 添加主线程分析
            val analysis = analyzeStackTrace(mainThreadStack)
            sbDump.append("【分析】\n$analysis\n\n")
            
            // 获取并添加调用者信息
            if (callerInfo.isNotEmpty()) {
                sbDump.append("【最近调用信息】\n")
                callerInfo.forEach { (_, info) -> sbDump.append("$info\n") }
                sbDump.append("\n")
            }
            
            // 保存线程转储
            val timestamp = System.currentTimeMillis()
            val trace = Pair(timestamp, sbDump.toString())
            
            // 更新堆栈跟踪历史
            synchronized(stackTraces) {
                stackTraces.add(trace)
                if (stackTraces.size > MAX_STACK_TRACES) {
                    stackTraces.removeAt(0)
                }
            }
            
            // 输出到日志
            Log.e(tag, "检测到ANR! 完整线程转储:\n${sbDump}")
            
        } catch (e: Exception) {
            Log.e(tag, "捕获线程转储失败", e)
            // 失败时尝试旧方法
            captureMainThreadStack()
        }
    }
    
    /**
     * 分析堆栈跟踪，尝试找出ANR的可能原因
     */
    private fun analyzeStackTrace(stackTrace: String): String {
        val analysis = StringBuilder()
        
        // 查找关键词
        ANR_PATTERNS.forEach { pattern ->
            if (stackTrace.contains(pattern, ignoreCase = true)) {
                analysis.append("【发现关键字】$pattern\n")
            }
        }
        
        // 查找重要包
        var importantLineFound = false
        for (line in stackTrace.lines()) {
            for (packageName in IMPORTANT_PACKAGES) {
                if (line.contains(packageName)) {
                    if (!importantLineFound) {
                        analysis.append("【重要调用】\n")
                        importantLineFound = true
                    }
                    analysis.append("$line\n")
                    break
                }
            }
        }
        
        // 分析可能的ANR原因
        if (stackTrace.contains("runBlocking", ignoreCase = true)) {
            analysis.append("【可能原因】检测到主线程中使用runBlocking，这会阻塞主线程直到协程完成\n")
        } else if (stackTrace.contains("synchronized", ignoreCase = true)) {
            analysis.append("【可能原因】检测到同步块锁定，可能由于竞争条件导致主线程长时间等待\n")
        } else if (stackTrace.contains("TextSegmenter", ignoreCase = true)) {
            analysis.append("【可能原因】检测到TextSegmenter在主线程上执行，可能导致耗时操作阻塞主线程\n")
        } else if (stackTrace.contains("ProblemLibrary", ignoreCase = true)) {
            analysis.append("【可能原因】检测到ProblemLibrary初始化或操作在主线程上执行\n")
        } else if (stackTrace.contains("load", ignoreCase = true) || stackTrace.contains("initialize", ignoreCase = true)) {
            analysis.append("【可能原因】检测到初始化或加载操作，可能是耗时的资源加载阻塞了主线程\n")
        } else if (stackTrace.contains("io", ignoreCase = true) || stackTrace.contains("read", ignoreCase = true) || stackTrace.contains("write", ignoreCase = true)) {
            analysis.append("【可能原因】检测到I/O操作，在主线程上执行文件或网络操作会导致ANR\n")
        }
        
        return if (analysis.isNotEmpty()) analysis.toString() else "无法确定具体原因，请检查堆栈中的耗时操作"
    }
    
    /**
     * 保存ANR报告到文件
     */
    private fun saveAnrReport() {
        try {
            val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            val timestamp = dateFormat.format(Date())
            val fileName = "anr_report_${timestamp}.txt"
            
            val file = File(context.getExternalFilesDir("anr_reports"), fileName)
            file.parentFile?.mkdirs()
            
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos).use { writer ->
                    writer.write("===== ANR监控报告 =====\n")
                    writer.write("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                    writer.write("ANR次数: ${anrCount.get()}\n")
                    writer.write("警告次数: ${warningCount.get()}\n")
                    writer.write("最长阻塞时间: ${maxBlockDuration.get()}ms\n\n")
                    
                    writer.write("===== 系统信息 =====\n")
                    writer.write("Android版本: ${android.os.Build.VERSION.SDK_INT}\n")
                    writer.write("设备: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n")
                    writer.write("内存情况: \n")
                    val rt = Runtime.getRuntime()
                    writer.write("  最大内存: ${rt.maxMemory() / 1024 / 1024}MB\n")
                    writer.write("  已分配内存: ${rt.totalMemory() / 1024 / 1024}MB\n")
                    writer.write("  空闲内存: ${rt.freeMemory() / 1024 / 1024}MB\n\n")
                    
                    writer.write("===== 堆栈跟踪历史 =====\n")
                    synchronized(stackTraces) {
                        stackTraces.forEach { (time, stack) ->
                            val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(time))
                            writer.write("时间: $timeStr\n")
                            writer.write("$stack\n\n")
                        }
                    }
                    
                    writer.write("===== 调用者信息 =====\n")
                    callerInfo.forEach { (_, info) -> 
                        writer.write("$info\n") 
                    }
                }
            }
            
            Log.i(tag, "ANR报告已保存到: ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(tag, "保存ANR报告失败", e)
        }
    }
} 