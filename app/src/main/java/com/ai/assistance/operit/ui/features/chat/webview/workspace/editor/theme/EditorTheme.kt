package com.ai.assistance.operit.ui.features.chat.webview.workspace.editor.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 编辑器主题定义
 */
data class EditorTheme(
    // 基础颜色
    val background: Color,
    val textColor: Color,
    val cursorColor: Color,
    val selectionColor: Color,

    // 语法高亮颜色
    val keywordColor: Color,
    val typeColor: Color,
    val stringColor: Color,
    val commentColor: Color,
    val numberColor: Color,
    val attributeColor: Color,
    val selectorColor: Color,
    val processingColor: Color,
    
    // Markdown特定颜色
    val headingColor: Color,
    val quoteColor: Color,
    val listItemColor: Color,
    val codeColor: Color,
    
    // 行号
    val gutterBackground: Color,
    val gutterBorder: Color,
    val lineNumberColor: Color,
    
    // 字体大小
    val fontSize: TextUnit
)

/**
 * 暗色主题
 */
val DarkTheme = EditorTheme(
    background = Color(0xFF1E1E1E),
    textColor = Color(0xFFD4D4D4),
    cursorColor = Color(0xFFAEAFAD),
    selectionColor = Color(0x66569CD6), // VSCode 风格的蓝色选区

    keywordColor = Color(0xFF569CD6),
    typeColor = Color(0xFF4EC9B0),
    stringColor = Color(0xFFCE9178),
    commentColor = Color(0xFF6A9955),
    numberColor = Color(0xFFB5CEA8),
    attributeColor = Color(0xFF9CDCFE),
    selectorColor = Color(0xFFD7BA7D),
    processingColor = Color(0xFF808080),
    
    headingColor = Color(0xFF569CD6),
    quoteColor = Color(0xFF6A9955),
    listItemColor = Color(0xFFD7BA7D),
    codeColor = Color(0xFF569CD6),
    
    gutterBackground = Color(0xFF1E1E1E),
    gutterBorder = Color(0xFF444444),
    lineNumberColor = Color(0xFF858585),
    
    fontSize = 14.sp
)

/**
 * 亮色主题
 */
val LightTheme = EditorTheme(
    background = Color(0xFFFFFFFF),
    textColor = Color(0xFF000000),
    cursorColor = Color(0xFF000000),
    selectionColor = Color(0x660070C1), // VSCode 风格的蓝色选区

    keywordColor = Color(0xFF0000FF),
    typeColor = Color(0xFF267F99),
    stringColor = Color(0xFFA31515),
    commentColor = Color(0xFF008000),
    numberColor = Color(0xFF098658),
    attributeColor = Color(0xFF0070C1),
    selectorColor = Color(0xFF800000),
    processingColor = Color(0xFF808080),
    
    headingColor = Color(0xFF0000FF),
    quoteColor = Color(0xFF008000),
    listItemColor = Color(0xFF800000),
    codeColor = Color(0xFF0000FF),
    
    gutterBackground = Color(0xFFF5F5F5),
    gutterBorder = Color(0xFFE0E0E0),
    lineNumberColor = Color(0xFF999999),
    
    fontSize = 14.sp
)

/**
 * 根据语言获取适合的主题
 */
fun getThemeForLanguage(language: String): EditorTheme {
    // 默认使用暗色主题
    return DarkTheme
} 