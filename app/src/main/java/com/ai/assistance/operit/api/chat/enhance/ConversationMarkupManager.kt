package com.ai.assistance.operit.api.chat.enhance

import android.util.Log
import com.ai.assistance.operit.data.model.PlanItem
import com.ai.assistance.operit.data.model.ToolResult

/**
 * Manages the markup elements used in conversations with the AI assistant.
 *
 * This class handles the generation of standardized XML-formatted status messages, tool invocation
 * formats, and tool results to be displayed in the conversation.
 */
class ConversationMarkupManager {

    companion object {
        private const val TAG = "ConversationMarkupManager"

        /**
         * Creates a 'complete' status markup element.
         *
         * @return The formatted status element
         */
        fun createCompleteStatus(): String {
            return "<status type=\"complete\"></status>"
        }

        /**
         * Creates a 'wait for user need' status markup element. Similar to complete but doesn't
         * trigger problem analysis.
         *
         * @return The formatted status element
         */
        fun createWaitForUserNeedStatus(): String {
            return "<status type=\"wait_for_user_need\"></status>"
        }

        /**
         * Creates an 'error' status markup element for a tool.
         *
         * @param toolName The name of the tool that produced the error
         * @param errorMessage The error message
         * @return The formatted status element
         */
        fun createToolErrorStatus(toolName: String, errorMessage: String): String {
            return """
            <tool_result name="${toolName}" status="error">
            <content>
            <error>${errorMessage}</error>
            </content>
            </tool_result>
            """.trimIndent()
        }

        /**
         * Creates a 'warning' status markup element.
         *
         * @param warningMessage The warning message to display
         * @return The formatted status element
         */
        fun createWarningStatus(warningMessage: String): String {
            return "<status type=\"warning\">$warningMessage</status>"
        }

        /**
         * Formats a tool result message for sending to the AI.
         *
         * @param result The tool execution result
         * @return The formatted tool result message
         */
        fun formatToolResultForMessage(result: ToolResult): String {
            return if (result.success) {
                """
                <tool_result name="${result.toolName}" status="success">
                <content>
                ${result.result}
                </content>
                </tool_result>
                """.trimIndent()
            } else {
                """
                <tool_result name="${result.toolName}" status="error">
                <content>
                <error>${result.error ?: "Unknown error"}</error>
                </content>
                </tool_result>
            """.trimIndent()
            }
        }

        /**
         * Formats a message indicating multiple tool invocations were found but only one will be
         * processed.
         *
         * @param toolName The name of the tool that will be processed
         * @return The formatted warning message
         */
        fun createMultipleToolsWarning(toolName: String): String {
            return createWarningStatus(
                    "检测到多个工具调用。系统将只执行第一个工具 `$toolName`，忽略其它工具调用。请避免在单个消息中同时调用多个工具。"
            )
        }

        /**
         * Creates a message for when a tool is not available.
         *
         * @param toolName The name of the unavailable tool
         * @return The formatted error message
         */
        fun createToolNotAvailableError(toolName: String): String {
            return createToolErrorStatus(toolName, "The tool `$toolName` is not available.")
        }

        /**
         * Cleans task completion content by removing the completion marker and adding a completion
         * status.
         *
         * @param content The content to clean
         * @return The cleaned content with completion status
         */
        fun createTaskCompletionContent(content: String): String {
            return content.replace("<status type=\"complete\"></status>", "").trim() +
                    "\n" +
                    createCompleteStatus()
        }

        /**
         * Cleans wait for user need content by removing the marker and adding the appropriate
         * status.
         *
         * @param content The content to clean
         * @return The cleaned content with wait_for_user_need status
         */
        fun createWaitForUserNeedContent(content: String): String {
            return content.replace("<status type=\"wait_for_user_need\"></status>", "").trim() +
                    "\n" +
                    createWaitForUserNeedStatus()
        }

        /**
         * Checks if content contains a task completion marker.
         *
         * @param content The content to check
         * @return True if the content contains a task completion marker
         */
        fun containsTaskCompletion(content: String): Boolean {
            return content.contains("<status type=\"complete\"></status>")
        }

        /**
         * Checks if content contains a wait for user need marker.
         *
         * @param content The content to check
         * @return True if the content contains a wait for user need marker
         */
        fun containsWaitForUserNeed(content: String): Boolean {
            return content.contains("<status type=\"wait_for_user_need\"></status>")
        }

        /** 创建通用错误状态 */
        fun createErrorStatus(title: String, message: String): String {
            return "<status type=\"error\"><title>$title</title><message>$message</message></status>"
        }

        /**
         * Creates a plan item markup element.
         *
         * @param description Description of the plan item
         * @return The formatted plan item element
         */
        fun createPlanItem(description: String): String {
            // 委托给PlanItemParser
            return PlanItemParser.createPlanItem(description)
        }

        /**
         * Updates the status of a plan item.
         *
         * @param id The ID of the plan item
         * @param status The new status of the plan item
         * @param message Optional message to include with the status update
         * @return The formatted plan status update element
         */
        fun createPlanStatusUpdate(id: String, status: String, message: String? = null): String {
            // 委托给PlanItemParser
            return PlanItemParser.createPlanStatusUpdate(id, status, message)
        }

        /**
         * Creates a plan task completion marker.
         *
         * @param id The ID of the plan item
         * @param success Whether the task was completed successfully
         * @param message Optional message about the completion
         * @return The formatted plan task completion element
         */
        fun createPlanTaskCompletion(
                id: String,
                success: Boolean,
                message: String? = null
        ): String {
            // 委托给PlanItemParser
            return PlanItemParser.createPlanTaskCompletion(id, success, message)
        }

        /**
         * Extract plan items from a string of text.
         *
         * @param content The text content to extract plan items from
         * @return List of extracted plan items
         */
        fun extractPlanItems(content: String): List<PlanItem> {
            // 委托给专门的PlanItemParser处理
            return PlanItemParser.extractPlanItems(content)
        }

        /**
         * Extract plan items from a string of text, using existing items for updates.
         *
         * @param content The text content to extract plan items from
         * @param existingItems The existing plan items to update
         * @return List of extracted and updated plan items
         */
        fun extractPlanItems(content: String, existingItems: List<PlanItem>): List<PlanItem> {
            // 委托给专门的PlanItemParser处理，传入现有的计划项列表
            Log.d(TAG, "调用PlanItemParser提取计划项，传入 ${existingItems.size} 个现有计划项")
            return PlanItemParser.extractPlanItems(content, existingItems)
        }

        /**
         * Checks if content contains any plan-related elements.
         *
         * @param content The content to check
         * @return True if the content contains plan-related elements
         */
        fun containsPlanElements(content: String): Boolean {
            return PlanItemParser.containsPlanElements(content)
        }
    }
}
