package com.ai.assistance.operit.data.model

/** 表示不同功能类型，用于指定不同功能使用的AI配置 */
enum class FunctionType {
    CHAT, // 常规对话
    SUMMARY, // 对话总结
    PROBLEM_LIBRARY, // 问题库处理
    FILE_BINDING, // 文件绑定处理
    UI_CONTROLLER, // UI自动化控制
    TRANSLATION // 翻译功能
}
