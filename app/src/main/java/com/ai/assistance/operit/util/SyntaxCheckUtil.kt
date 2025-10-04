package com.ai.assistance.operit.util

import android.util.Log
import java.io.File

/**
 * 语法检查工具类
 * 提供JavaScript和HTML的简单语法检查功能
 */
object SyntaxCheckUtil {
    private const val TAG = "SyntaxCheckUtil"

    /**
     * 语法错误信息类
     */
    data class SyntaxError(
        val line: Int,
        val column: Int,
        val message: String,
        val severity: Severity = Severity.ERROR
    ) {
        enum class Severity {
            ERROR,
            WARNING
        }

        override fun toString(): String {
            return "Line $line:$column - ${severity.name}: $message"
        }
    }

    /**
     * 语法检查结果
     */
    data class SyntaxCheckResult(
        val filePath: String,
        val fileType: String,
        val errors: List<SyntaxError>,
        val hasErrors: Boolean = errors.any { it.severity == SyntaxError.Severity.ERROR }
    ) {
        override fun toString(): String {
            if (errors.isEmpty()) {
                return "✓ $filePath: No syntax errors found"
            }

            val sb = StringBuilder()
            sb.appendLine("Syntax check for $filePath ($fileType):")
            sb.appendLine("Found ${errors.size} issue(s):")
            errors.forEach { error ->
                sb.appendLine("  ${error}")
            }
            return sb.toString()
        }
    }

    /**
     * 根据文件路径检查语法
     * @param filePath 文件路径
     * @param content 文件内容
     * @return 语法检查结果，如果不支持该文件类型则返回null
     */
    fun checkSyntax(filePath: String, content: String): SyntaxCheckResult? {
        val file = File(filePath)
        val extension = file.extension.lowercase()

        return when (extension) {
            "js", "mjs", "cjs", "jsx" -> checkJavaScript(filePath, content)
            "html", "htm" -> checkHtml(filePath, content)
            else -> null
        }
    }

    /**
     * 检查JavaScript语法
     * 执行简单的语法检查，包括：
     * - 括号匹配（圆括号、方括号、花括号）
     * - 引号匹配（单引号、双引号、反引号）
     * - 注释完整性
     */
    fun checkJavaScript(filePath: String, content: String): SyntaxCheckResult {
        val errors = mutableListOf<SyntaxError>()
        val lines = content.lines()

        // 检查括号匹配
        checkBracketMatching(lines, errors)

        // 检查引号匹配
        checkQuoteMatching(lines, errors)

        // 检查注释完整性
        checkCommentMatching(lines, errors)

        // 检查常见的JavaScript语法错误
        checkCommonJsErrors(lines, errors)

        return SyntaxCheckResult(filePath, "JavaScript", errors)
    }

    /**
     * 检查HTML语法
     * 执行简单的语法检查，包括：
     * - 标签匹配
     * - 属性引号
     * - 注释完整性
     */
    fun checkHtml(filePath: String, content: String): SyntaxCheckResult {
        val errors = mutableListOf<SyntaxError>()
        val lines = content.lines()

        // 检查标签匹配
        checkHtmlTagMatching(lines, errors)

        // 检查HTML注释
        checkHtmlComments(lines, errors)

        // 检查属性引号
        checkHtmlAttributeQuotes(lines, errors)

        return SyntaxCheckResult(filePath, "HTML", errors)
    }

    /**
     * 检查括号匹配
     */
    private fun checkBracketMatching(lines: List<String>, errors: MutableList<SyntaxError>) {
        val stack = mutableListOf<Pair<Char, Pair<Int, Int>>>() // (bracket, (line, col))
        val bracketPairs = mapOf('(' to ')', '[' to ']', '{' to '}')

        lines.forEachIndexed { lineIndex, line ->
            var inString = false
            var inComment = false
            var stringChar = ' '
            var col = 0

            var i = 0
            while (i < line.length) {
                val char = line[i]
                col = i

                // 检查是否在注释中
                if (!inString && i < line.length - 1 && line[i] == '/' && line[i + 1] == '/') {
                    inComment = true
                    break
                }

                // 检查是否在字符串中
                if ((char == '"' || char == '\'' || char == '`') && (i == 0 || line[i - 1] != '\\')) {
                    if (!inString) {
                        inString = true
                        stringChar = char
                    } else if (char == stringChar) {
                        inString = false
                    }
                }

                // 如果不在字符串或注释中，检查括号
                if (!inString && !inComment) {
                    if (char in bracketPairs.keys) {
                        stack.add(char to (lineIndex + 1 to col + 1))
                    } else if (char in bracketPairs.values) {
                        if (stack.isEmpty()) {
                            errors.add(
                                SyntaxError(
                                    lineIndex + 1,
                                    col + 1,
                                    "Unexpected closing bracket '$char'"
                                )
                            )
                        } else {
                            val (openBracket, _) = stack.last()
                            if (bracketPairs[openBracket] == char) {
                                stack.removeAt(stack.size - 1)
                            } else {
                                errors.add(
                                    SyntaxError(
                                        lineIndex + 1,
                                        col + 1,
                                        "Mismatched bracket: expected '${bracketPairs[openBracket]}', found '$char'"
                                    )
                                )
                            }
                        }
                    }
                }

                i++
            }
        }

        // 检查未闭合的括号
        stack.forEach { (bracket, position) ->
            errors.add(
                SyntaxError(
                    position.first,
                    position.second,
                    "Unclosed bracket '$bracket'"
                )
            )
        }
    }

    /**
     * 检查引号匹配
     */
    private fun checkQuoteMatching(lines: List<String>, errors: MutableList<SyntaxError>) {
        lines.forEachIndexed { lineIndex, line ->
            var i = 0
            var inComment = false

            while (i < line.length) {
                // 跳过注释
                if (i < line.length - 1 && line[i] == '/' && line[i + 1] == '/') {
                    inComment = true
                    break
                }

                // 检查字符串
                if (line[i] in listOf('"', '\'', '`') && (i == 0 || line[i - 1] != '\\')) {
                    val quote = line[i]
                    val startCol = i
                    i++

                    // 查找匹配的引号
                    var found = false
                    while (i < line.length) {
                        if (line[i] == quote && line[i - 1] != '\\') {
                            found = true
                            break
                        }
                        i++
                    }

                    if (!found) {
                        errors.add(
                            SyntaxError(
                                lineIndex + 1,
                                startCol + 1,
                                "Unclosed string literal",
                                SyntaxError.Severity.WARNING
                            )
                        )
                    }
                }

                i++
            }
        }
    }

    /**
     * 检查注释完整性
     */
    private fun checkCommentMatching(lines: List<String>, errors: MutableList<SyntaxError>) {
        val content = lines.joinToString("\n")
        var inMultiLineComment = false
        var commentStartLine = -1
        var commentStartCol = -1

        lines.forEachIndexed { lineIndex, line ->
            var i = 0
            while (i < line.length - 1) {
                if (!inMultiLineComment && line[i] == '/' && line[i + 1] == '*') {
                    inMultiLineComment = true
                    commentStartLine = lineIndex + 1
                    commentStartCol = i + 1
                    i += 2
                    continue
                }

                if (inMultiLineComment && line[i] == '*' && line[i + 1] == '/') {
                    inMultiLineComment = false
                    i += 2
                    continue
                }

                i++
            }
        }

        if (inMultiLineComment) {
            errors.add(
                SyntaxError(
                    commentStartLine,
                    commentStartCol,
                    "Unclosed multi-line comment"
                )
            )
        }
    }

    /**
     * 检查常见的JavaScript错误
     */
    private fun checkCommonJsErrors(lines: List<String>, errors: MutableList<SyntaxError>) {
        lines.forEachIndexed { lineIndex, line ->
            val trimmed = line.trim()

            // 检查多余的分号
            if (trimmed.matches(Regex(".*;\\s*;"))) {
                errors.add(
                    SyntaxError(
                        lineIndex + 1,
                        line.indexOf(";;") + 1,
                        "Double semicolon detected",
                        SyntaxError.Severity.WARNING
                    )
                )
            }

            // 检查 return 后面是否有内容但在下一行
            if (trimmed == "return" && lineIndex < lines.size - 1) {
                val nextLine = lines[lineIndex + 1].trim()
                if (nextLine.isNotEmpty() && !nextLine.startsWith("//") && !nextLine.startsWith("/*")) {
                    errors.add(
                        SyntaxError(
                            lineIndex + 1,
                            line.indexOf("return") + 1,
                            "Return statement should have value on same line",
                            SyntaxError.Severity.WARNING
                        )
                    )
                }
            }
        }
    }

    /**
     * 检查HTML标签匹配
     */
    private fun checkHtmlTagMatching(lines: List<String>, errors: MutableList<SyntaxError>) {
        val stack = mutableListOf<Pair<String, Pair<Int, Int>>>() // (tagName, (line, col))
        val selfClosingTags = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input",
            "link", "meta", "param", "source", "track", "wbr"
        )

        val content = lines.joinToString("\n")
        val tagPattern = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)(\\s[^>]*)?>")

        tagPattern.findAll(content).forEach { match ->
            val isClosing = match.groupValues[1] == "/"
            val tagName = match.groupValues[2].lowercase()
            
            // 计算行号和列号
            val position = match.range.first
            var line = 1
            var col = 1
            var currentPos = 0
            
            for ((lineIndex, lineContent) in lines.withIndex()) {
                if (currentPos + lineContent.length >= position) {
                    line = lineIndex + 1
                    col = position - currentPos + 1
                    break
                }
                currentPos += lineContent.length + 1 // +1 for newline
            }

            if (isClosing) {
                // 处理闭合标签
                if (stack.isEmpty()) {
                    errors.add(
                        SyntaxError(
                            line,
                            col,
                            "Unexpected closing tag </$tagName>"
                        )
                    )
                } else {
                    val (openTag, _) = stack.last()
                    if (openTag == tagName) {
                        stack.removeAt(stack.size - 1)
                    } else {
                        errors.add(
                            SyntaxError(
                                line,
                                col,
                                "Mismatched tag: expected </$openTag>, found </$tagName>"
                            )
                        )
                    }
                }
            } else {
                // 处理开始标签
                if (tagName !in selfClosingTags && !match.value.endsWith("/>")) {
                    stack.add(tagName to (line to col))
                }
            }
        }

        // 检查未闭合的标签
        stack.forEach { (tagName, position) ->
            errors.add(
                SyntaxError(
                    position.first,
                    position.second,
                    "Unclosed tag <$tagName>"
                )
            )
        }
    }

    /**
     * 检查HTML注释
     */
    private fun checkHtmlComments(lines: List<String>, errors: MutableList<SyntaxError>) {
        val content = lines.joinToString("\n")
        var inComment = false
        var commentStartLine = -1
        var commentStartCol = -1
        var currentLine = 1
        var currentCol = 1

        var i = 0
        for ((lineIndex, line) in lines.withIndex()) {
            var col = 0
            while (col < line.length - 3) {
                if (!inComment && line.substring(col).startsWith("<!--")) {
                    inComment = true
                    commentStartLine = lineIndex + 1
                    commentStartCol = col + 1
                    col += 4
                    continue
                }

                if (inComment && line.substring(col).startsWith("-->")) {
                    inComment = false
                    col += 3
                    continue
                }

                col++
            }
        }

        if (inComment) {
            errors.add(
                SyntaxError(
                    commentStartLine,
                    commentStartCol,
                    "Unclosed HTML comment"
                )
            )
        }
    }

    /**
     * 检查HTML属性引号
     */
    private fun checkHtmlAttributeQuotes(lines: List<String>, errors: MutableList<SyntaxError>) {
        val attrPattern = Regex("""(\w+)=([^"'\s>][^\s>]*)""")

        lines.forEachIndexed { lineIndex, line ->
            attrPattern.findAll(line).forEach { match ->
                val attrName = match.groupValues[1]
                val attrValue = match.groupValues[2]
                
                errors.add(
                    SyntaxError(
                        lineIndex + 1,
                        match.range.first + 1,
                        "Attribute '$attrName' value should be quoted",
                        SyntaxError.Severity.WARNING
                    )
                )
            }
        }
    }
}

