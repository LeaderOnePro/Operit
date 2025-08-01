package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor

import android.graphics.Color
import android.util.Log
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageFactory
import com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.language.LanguageSupport
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 代码解析器，用于语法高亮
 */
class CodeParser(private val codeText: ColorsText) : Runnable {
    companion object {
        private val parserThreadPool: ExecutorService = Executors.newSingleThreadExecutor()
        private const val TAG = "CodeParser"
        
        // 定义语法高亮颜色
        val KEYWORD_COLOR = LanguageSupport.KEYWORD_COLOR
        val STRING_COLOR = LanguageSupport.STRING_COLOR
        val COMMENT_COLOR = LanguageSupport.COMMENT_COLOR
        val NUMBER_COLOR = LanguageSupport.NUMBER_COLOR
        val FUNCTION_COLOR = LanguageSupport.FUNCTION_COLOR
        val TYPE_COLOR = LanguageSupport.TYPE_COLOR
        val VARIABLE_COLOR = LanguageSupport.VARIABLE_COLOR
        val DEFAULT_COLOR = LanguageSupport.DEFAULT_COLOR
        val OPERATOR_COLOR = LanguageSupport.OPERATOR_COLOR
    }

    private var language = "javascript"
    private var running = false
    private var reparse = false
    
    // 当前使用的语言支持
    private var languageSupport = LanguageFactory.getLanguageSupport(language)
    
    // 代码变化的位置信息
    private var start = 0
    private var before = 0
    private var count = 0
    
    // 上次解析的代码长度
    private var lastCodeLength = 0

    /**
     * 解析代码
     */
    fun parse(start: Int, before: Int, count: Int) {
        if (running) {
            reparse = true
            return
        }
        running = true
        this.start = start
        this.before = before
        this.count = count
        parserThreadPool.execute(this)
    }

    /**
     * 设置语言并重新解析
     */
    fun setLanguage(lang: String) {
        this.language = lang.lowercase()
        languageSupport = LanguageFactory.getLanguageSupport(language)
        
        // 如果没有找到语言支持，使用默认的JavaScript
        if (languageSupport == null) {
            Log.w(TAG, "未找到语言支持: $language，使用默认的JavaScript")
            this.language = "javascript"
            languageSupport = LanguageFactory.getLanguageSupport("javascript")
        }
        
        // 重新解析以应用新的高亮规则
        parse(0, 0, codeText.length())
    }

    override fun run() {
        try {
            // 获取颜色数组
            val codeColors = codeText.getCodeColors()
            val text = codeText.text?.toString() ?: ""
            
            // 检查是否需要重新初始化颜色数组
            val currentLength = text.length
            val colorArrayLength = codeColors.size
            
            // 如果文本长度发生了较大变化，或者是首次解析，则完全重新解析
            if (Math.abs(lastCodeLength - currentLength) > 100 || lastCodeLength == 0) {
                // 完全重新解析
                parseFullText(text, codeColors)
            } else {
                // 增量解析
                handleIncrementalParse(text, codeColors)
            }
            
            lastCodeLength = currentLength
            
            if (running) {
                codeText.postInvalidate()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "解析代码时出错", e)
        }
        
        if (reparse) {
            reparse = false
            parse(start, before, count)
        }
        
        running = false
    }
    
    /**
     * 完全重新解析文本
     */
    private fun parseFullText(text: String, codeColors: IntArray) {
        // 重置所有颜色为默认颜色
        for (i in codeColors.indices) {
            if (i < text.length) {
                codeColors[i] = DEFAULT_COLOR
            }
        }
        
        // 如果没有语言支持，使用简单的默认高亮
        if (languageSupport == null) {
            simpleHighlight(text, codeColors)
            return
        }
        
        var i = 0
        while (i < text.length && running) {
            if (reparse) break
            
            // 处理注释
            val commentStartMarkers = languageSupport?.getCommentStart() ?: listOf("//", "/*")
            var handled = false
            
            for (commentMarker in commentStartMarkers) {
                if (text.startsWith(commentMarker, i)) {
                    if (commentMarker == "//" || commentMarker.length == 1) {
                        // 单行注释
                        val end = text.indexOf('\n', i)
                        val commentEnd = if (end == -1) text.length else end
                        for (j in i until commentEnd) {
                            if (j < codeColors.size) codeColors[j] = COMMENT_COLOR
                        }
                        i = commentEnd
                    } else {
                        // 多行注释
                        val commentEnd = languageSupport?.getMultiLineCommentEnd() ?: "*/"
                        val end = text.indexOf(commentEnd, i + commentMarker.length)
                        val endPos = if (end == -1) text.length else end + commentEnd.length
                        for (j in i until endPos) {
                            if (j < codeColors.size) codeColors[j] = COMMENT_COLOR
                        }
                        i = endPos
                    }
                    handled = true
                    break
                }
            }
            
            if (handled) continue
            
            // 处理字符串
            if (languageSupport?.isStringDelimiter(text[i]) == true) {
                val quote = text[i]
                val escapeChar = languageSupport?.getStringEscapeChar() ?: '\\'
                
                if (i < codeColors.size) codeColors[i] = STRING_COLOR
                i++
                
                while (i < text.length && text[i] != quote) {
                    if (text[i] == escapeChar && i + 1 < text.length) {
                        if (i < codeColors.size) codeColors[i] = STRING_COLOR
                        if (i + 1 < codeColors.size) codeColors[i + 1] = STRING_COLOR
                        i += 2
                    } else {
                        if (i < codeColors.size) codeColors[i] = STRING_COLOR
                        i++
                    }
                }
                
                if (i < text.length) {
                    if (i < codeColors.size) codeColors[i] = STRING_COLOR
                    i++
                }
                
                continue
            }
            
            // 处理数字
            if (Character.isDigit(text[i])) {
                val start = i
                while (i < text.length && (Character.isDigit(text[i]) || text[i] == '.')) {
                    if (i < codeColors.size) codeColors[i] = NUMBER_COLOR
                    i++
                }
                continue
            }
            
            // 处理标识符（关键字、类型、函数等）
            if (Character.isJavaIdentifierStart(text[i])) {
                val start = i
                while (i < text.length && Character.isJavaIdentifierPart(text[i])) {
                    i++
                }
                
                if (i <= text.length) {
                    val word = text.substring(start, i)
                    val color = when {
                        languageSupport?.getKeywords()?.contains(word) == true -> KEYWORD_COLOR
                        languageSupport?.getBuiltInTypes()?.contains(word) == true -> TYPE_COLOR
                        languageSupport?.getBuiltInVariables()?.contains(word) == true -> VARIABLE_COLOR
                        languageSupport?.getBuiltInFunctions()?.contains(word) == true -> FUNCTION_COLOR
                        i < text.length && text[i] == '(' -> FUNCTION_COLOR
                        else -> DEFAULT_COLOR
                    }
                    
                    for (j in start until i) {
                        if (j < codeColors.size) codeColors[j] = color
                    }
                }
                
                continue
            }
            
            // 处理操作符
            if ("+-*/%=&|<>!~^?:;,(){}[]".contains(text[i])) {
                if (i < codeColors.size) codeColors[i] = OPERATOR_COLOR
            } else {
                if (i < codeColors.size) codeColors[i] = DEFAULT_COLOR
            }
            
            i++
        }
    }
    
    /**
     * 处理增量解析
     */
    private fun handleIncrementalParse(text: String, codeColors: IntArray) {
        // 如果是删除操作
        if (count < before) {
            // 删除内容，左移动
            val offset = before - count
            for (i in start until codeColors.size - offset) {
                if (reparse) break
                if (i + offset < codeColors.size) {
                    codeColors[i] = codeColors[i + offset]
                }
            }
            
            // 重新解析受影响的区域
            val contextStart = (start - 100).coerceAtLeast(0)
            val contextEnd = (start + 100).coerceAtMost(text.length)
            parseRegion(text, codeColors, contextStart, contextEnd)
        } 
        // 如果是添加操作
        else if (count > before) {
            // 添加内容，右移动
            val offset = count - before
            for (i in codeColors.size - 1 downTo start + offset) {
                if (reparse) break
                if (i - offset >= 0) {
                    codeColors[i] = codeColors[i - offset]
                }
            }
            
            // 重新解析受影响的区域
            val contextStart = (start - 100).coerceAtLeast(0)
            val contextEnd = (start + count + 100).coerceAtMost(text.length)
            parseRegion(text, codeColors, contextStart, contextEnd)
        }
        // 如果是替换操作（count == before）
        else {
            // 重新解析受影响的区域
            val contextStart = (start - 100).coerceAtLeast(0)
            val contextEnd = (start + count + 100).coerceAtMost(text.length)
            parseRegion(text, codeColors, contextStart, contextEnd)
        }
    }
    
    /**
     * 解析指定区域的代码
     */
    private fun parseRegion(text: String, codeColors: IntArray, start: Int, end: Int) {
        // 找到区域的起始和结束位置
        var regionStart = start
        var regionEnd = end
        
        // 向前找到一个安全的解析起点（行首或空格后）
        while (regionStart > 0) {
            val c = text[regionStart - 1]
            if (c == '\n' || c == ' ' || c == '\t') {
                break
            }
            regionStart--
        }
        
        // 向后找到一个安全的解析终点（行尾或空格前）
        while (regionEnd < text.length) {
            val c = text[regionEnd]
            if (c == '\n' || c == ' ' || c == '\t') {
                break
            }
            regionEnd++
        }
        
        // 提取区域文本并解析
        val regionText = text.substring(regionStart, regionEnd)
        val tempColors = IntArray(regionText.length)
        
        // 使用完全解析方法解析区域
        parseFullRegion(regionText, tempColors)
        
        // 将解析结果复制回主颜色数组
        for (i in tempColors.indices) {
            val pos = regionStart + i
            if (pos < codeColors.size) {
                codeColors[pos] = tempColors[i]
            }
        }
    }
    
    /**
     * 完全解析一个区域的文本
     */
    private fun parseFullRegion(text: String, colors: IntArray) {
        // 如果没有语言支持，使用简单的默认高亮
        if (languageSupport == null) {
            for (i in text.indices) {
                colors[i] = DEFAULT_COLOR
            }
            return
        }
        
        var i = 0
        while (i < text.length) {
            // 处理标识符（关键字、类型、函数等）
            if (Character.isJavaIdentifierStart(text[i])) {
                val start = i
                while (i < text.length && Character.isJavaIdentifierPart(text[i])) {
                    i++
                }
                
                val word = text.substring(start, i)
                val color = when {
                    languageSupport?.getKeywords()?.contains(word) == true -> KEYWORD_COLOR
                    languageSupport?.getBuiltInTypes()?.contains(word) == true -> TYPE_COLOR
                    languageSupport?.getBuiltInVariables()?.contains(word) == true -> VARIABLE_COLOR
                    languageSupport?.getBuiltInFunctions()?.contains(word) == true -> FUNCTION_COLOR
                    i < text.length && text[i] == '(' -> FUNCTION_COLOR
                    else -> DEFAULT_COLOR
                }
                
                for (j in start until i) {
                    colors[j] = color
                }
                
                continue
            }
            
            // 处理数字
            if (Character.isDigit(text[i])) {
                val start = i
                while (i < text.length && (Character.isDigit(text[i]) || text[i] == '.')) {
                    colors[i] = NUMBER_COLOR
                    i++
                }
                continue
            }
            
            // 处理操作符
            if ("+-*/%=&|<>!~^?:;,(){}[]".contains(text[i])) {
                colors[i] = OPERATOR_COLOR
            } else {
                colors[i] = DEFAULT_COLOR
            }
            
            i++
        }
    }
    
    /**
     * 简单的高亮处理，当没有语言支持时使用
     */
    private fun simpleHighlight(text: String, codeColors: IntArray) {
        var i = 0
        while (i < text.length && running) {
            if (reparse) break
            
            when {
                // 注释
                text.startsWith("//", i) -> {
                    val end = text.indexOf('\n', i)
                    val commentEnd = if (end == -1) text.length else end
                    for (j in i until commentEnd) {
                        if (j < codeColors.size) codeColors[j] = COMMENT_COLOR
                    }
                    i = commentEnd
                }
                // 多行注释
                text.startsWith("/*", i) -> {
                    val end = text.indexOf("*/", i + 2)
                    val commentEnd = if (end == -1) text.length else end + 2
                    for (j in i until commentEnd) {
                        if (j < codeColors.size) codeColors[j] = COMMENT_COLOR
                    }
                    i = commentEnd
                }
                // 字符串
                text[i] == '"' || text[i] == '\'' -> {
                    val quote = text[i]
                    if (i < codeColors.size) codeColors[i] = STRING_COLOR
                    i++
                    while (i < text.length && text[i] != quote) {
                        if (text[i] == '\\' && i + 1 < text.length) {
                            if (i < codeColors.size) codeColors[i] = STRING_COLOR
                            if (i + 1 < codeColors.size) codeColors[i + 1] = STRING_COLOR
                            i += 2
                        } else {
                            if (i < codeColors.size) codeColors[i] = STRING_COLOR
                            i++
                        }
                    }
                    if (i < text.length) {
                        if (i < codeColors.size) codeColors[i] = STRING_COLOR
                        i++
                    }
                }
                // 数字
                Character.isDigit(text[i]) -> {
                    val start = i
                    while (i < text.length && (Character.isDigit(text[i]) || text[i] == '.')) {
                        if (i < codeColors.size) codeColors[i] = NUMBER_COLOR
                        i++
                    }
                }
                else -> {
                    if (i < codeColors.size) codeColors[i] = DEFAULT_COLOR
                    i++
                }
            }
        }
    }
} 