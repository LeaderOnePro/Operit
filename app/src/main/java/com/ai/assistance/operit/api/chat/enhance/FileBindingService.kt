package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.core.config.FunctionalPrompts
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.data.preferences.ApiPreferences
import com.ai.assistance.operit.util.ChatUtils
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

class FileBindingService(context: Context) {

    private val apiPreferences = ApiPreferences.getInstance(context)

    companion object {
        private const val TAG = "FileBindingService"
        private val EDIT_BLOCK_REGEX =
                """//\s*\[START-(REPLACE|INSERT|DELETE):([\d-]+)\]\s*(?://\s*\[CONTEXT\]\s*(.*?)\s*//\s*\[/CONTEXT\]\s*)?(?:\n)?(.*?)(?:\n)?//\s*\[END-\1\]""".toRegex(
                        RegexOption.DOT_MATCHES_ALL
                )
    }

    private enum class EditAction {
        REPLACE,
        INSERT,
        DELETE
    }

    private sealed class CorrectionResult {
        data class Success(val correctedPatch: String) : CorrectionResult()
        object MappingFailed : CorrectionResult()
        object Error : CorrectionResult()
    }

    private data class EditOperation(
            val action: EditAction,
            val startLine: Int,
            val endLine: Int, // For INSERT, this is the same as startLine
            val content: String
    )

    /**
     * Processes file binding using a three-tier approach:
     * 1. Attempts a precise, line-number-based patch if structured edit blocks are found.
     * 2. If no blocks are found, assumes a full file replacement.
     * 3. A special `FORCE_AI_MERGE` flag can override all and jump to a robust AI-driven merge.
     *
     * @param originalContent The original content of the file.
     * @param aiGeneratedCode The AI-generated code, which can be full content or structured edit
     * blocks.
     * @param multiServiceManager The service manager for AI communication.
     * @return A Pair containing the final merged content and a diff string representing the
     * changes.
     */
    suspend fun processFileBinding(
            originalContent: String,
            aiGeneratedCode: String,
            multiServiceManager: MultiServiceManager
    ): Pair<String, String> {
        // Tier 1: Attempt precise, line-number-based patching
        if (aiGeneratedCode.contains("// [START-")) {
            Log.d(TAG, "Structured edit blocks detected. Attempting line-based patch.")
            try {
                // Tier 1: Invoke the sub-agent for correction directly for safety
                Log.d(TAG, "Invoking sub-agent for semantic correction before patching.")
                when (val correctionResult = runSubAgentCorrection(originalContent, aiGeneratedCode, multiServiceManager)) {
                    is CorrectionResult.Success -> {
                        // Second attempt with the corrected patch
                        val (success, patchedContent) = applyLineBasedPatch(originalContent, correctionResult.correctedPatch)
                        if (success) {
                            Log.d(TAG, "Line-based patch succeeded after sub-agent correction.")
                            val diffString = generateDiff(originalContent.replace("\r\n", "\n"), patchedContent)
                            return Pair(patchedContent, diffString)
                        }
                        // If even the corrected patch fails, it's a hard error.
                        Log.w(TAG, "Patch application failed even after sub-agent correction. Reporting as error.")
                        return Pair(originalContent, "Error: The corrected patch could not be applied. The patch logic might be invalid.")
                    }
                    is CorrectionResult.MappingFailed -> {
                        Log.w(TAG, "Sub-agent explicitly failed to find mapping. Reporting as tool error.")
                        return Pair(originalContent, "Error: Could not apply patch. The sub-agent failed to find the correct line numbers, likely because the file has changed. Please read the file again to get the latest context and retry the edit.")
                    }
                    is CorrectionResult.Error -> {
                        // This includes general exceptions and parsing failures.
                        Log.w(TAG, "Sub-agent correction failed. Reporting as tool error.")
                        return Pair(originalContent, "Error: The line number correction sub-agent failed due to an internal error.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during line-based patch. Reporting as tool error.", e)
                return Pair(originalContent, "Error: An unexpected exception occurred during the patching process: ${e.message}")
            }
        }

        // Tier 2: Default to full file replacement if no special instructions are found
        Log.d(TAG, "No structured blocks found. Assuming full file replacement.")
        val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
        val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()
        val diffString = generateDiff(normalizedOriginalContent, normalizedAiGeneratedCode)
        return Pair(normalizedAiGeneratedCode, diffString)
    }

    private fun generateDiff(original: String, modified: String): String {
        return UnifiedDiffUtils.generateUnifiedDiff(
                                        "a/file",
                                        "b/file",
                        original.lines(),
                        DiffUtils.diff(original.lines(), modified.lines()),
                                        3
                                )
                                .joinToString("\n")
    }

    /**
     * Parses the AI-generated code for structured edit blocks and applies them to the original
     * content.
     *
     * Operations are applied in reverse line order to avoid index shifting issues.
     *
     * @return A Pair of (Boolean, String) indicating success and the modified content.
     */
    private fun applyLineBasedPatch(
            originalContent: String,
            aiPatchCode: String
    ): Pair<Boolean, String> {
        try {
            val operations =
                    EDIT_BLOCK_REGEX
                            .findAll(aiPatchCode)
                            .mapNotNull { matchResult ->
                                val (actionStr, lineRangeStr, _, content) = matchResult.destructured
                                val action = EditAction.valueOf(actionStr)
                                val contentClean = content.trimEnd('\n')

                                when (action) {
                                    EditAction.REPLACE,
                                    EditAction.DELETE -> {
                                        val parts = lineRangeStr.split('-')
                                        if (parts.size != 2) return@mapNotNull null
                                        val start = parts[0].toIntOrNull() ?: return@mapNotNull null
                                        val end = parts[1].toIntOrNull() ?: return@mapNotNull null
                                        EditOperation(action, start, end, contentClean)
                                    }
                                    EditAction.INSERT -> {
                                        val afterLine =
                                                lineRangeStr.toIntOrNull() ?: return@mapNotNull null
                                        EditOperation(action, afterLine, afterLine, contentClean)
                                    }
                                }
                            }
                            .toList()

            if (operations.isEmpty()) {
                Log.w(TAG, "Patch code contained START tags but no valid operations were parsed.")
                return Pair(false, originalContent)
            }

            val originalLines = originalContent.lines().toMutableList()
            var lineOffset = 0

            // Operations must be sorted by their start line to process them sequentially
            // and correctly calculate the offset for subsequent operations.
            val sortedOps = operations.sortedBy { it.startLine }

            for (op in sortedOps) {
                // Adjust line numbers based on the cumulative offset from previous operations
                val adjustedStartLine = op.startLine + lineOffset
                val adjustedEndLine = op.endLine + lineOffset

                // Convert to 0-based index for list manipulation
                val startIndex = adjustedStartLine - 1
                val endIndex = adjustedEndLine - 1
                val maxLine = originalLines.size

                // Boundary checks for the adjusted operation
                if (adjustedStartLine <= 0 || adjustedStartLine > maxLine + 1 || adjustedEndLine < adjustedStartLine || (op.action != EditAction.INSERT && adjustedEndLine > maxLine)) {
                    Log.e(
                        TAG,
                        "Invalid adjusted line number in operation: ${op.action} ${op.startLine}->${adjustedStartLine}. File now has $maxLine lines."
                    )
                    // If one operation is invalid, we can't guarantee the rest, so we fail.
                    return Pair(false, originalContent)
                }

                var linesChanged = 0

                when (op.action) {
                    EditAction.REPLACE -> {
                        val newContentLines = op.content.lines()
                        // Ensure endIndex is within bounds before removing
                        val effectiveEndIndex = if (endIndex >= originalLines.size) originalLines.size - 1 else endIndex
                        
                        val range = effectiveEndIndex downTo startIndex
                        for (i in range) {
                            if (i >= 0 && i < originalLines.size) {
                                originalLines.removeAt(i)
                            }
                        }
                        
                        if (op.content.isNotEmpty()) {
                            originalLines.addAll(startIndex, newContentLines)
                        }

                        linesChanged = newContentLines.size - (range.count())
                    }
                    EditAction.INSERT -> {
                        val newContentLines = op.content.lines()
                        if (op.content.isNotEmpty()) {
                             originalLines.addAll(adjustedStartLine, newContentLines)
                        }
                        linesChanged = newContentLines.size
                    }
                    EditAction.DELETE -> {
                        // Ensure endIndex is within bounds before removing
                        val effectiveEndIndex = if (endIndex >= originalLines.size) originalLines.size - 1 else endIndex
                        val range = effectiveEndIndex downTo startIndex
                        
                        for (i in range) {
                             if (i >= 0 && i < originalLines.size) {
                                originalLines.removeAt(i)
                            }
                        }
                        linesChanged = -(range.count())
                    }
                }
                lineOffset += linesChanged
            }

            return Pair(true, originalLines.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply line-based patch due to an exception.", e)
            return Pair(false, originalContent)
        }
    }

    /**
     * Invokes a sub-agent to correct the line numbers of a potentially outdated patch.
     *
     * @param originalContent The current, full content of the file.
     * @param aiPatchCode The original patch code from the main AI.
     * @param multiServiceManager The service manager for AI communication.
     * @return The corrected patch code from the sub-agent, or an empty string if it fails.
     */
    private suspend fun runSubAgentCorrection(
        originalContent: String,
        aiPatchCode: String,
        multiServiceManager: MultiServiceManager
    ): CorrectionResult {
        try {
            val contextualSnippet = generateContextualSnippet(originalContent, aiPatchCode)

            val correctionSystemPrompt = FunctionalPrompts.SUB_AGENT_LINE_CORRECTION_PROMPT
                .replace("{{SOURCE_CODE}}", contextualSnippet)
                .replace("{{PATCH_CODE}}", aiPatchCode)

            // The user prompt for this agent is simple, as all context is in the system prompt.
            val correctionUserPrompt = "Please correct the line numbers for the provided patch."

            // Use a specific, likely cheaper/faster model for this sub-task if available
            val subAgentService = multiServiceManager.getServiceForFunction(FunctionType.FILE_BINDING)
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.FILE_BINDING)

            val contentBuilder = StringBuilder()
            subAgentService.sendMessage(
                correctionUserPrompt,
                listOf(Pair("system", correctionSystemPrompt)),
                            modelParameters
            ).collect { content -> contentBuilder.append(content) }

            val rawResponse = ChatUtils.removeThinkingContent(contentBuilder.toString().trim())
            Log.d(TAG, "Sub-agent raw response: $rawResponse")

            val mappingBlock = extractMappingBlock(rawResponse)
            Log.d(TAG, "Extracted mapping block: $mappingBlock")

            if (mappingBlock.contains("// [MAPPING-FAILED]")) {
                return CorrectionResult.MappingFailed
            }

            if (mappingBlock.isBlank()) {
                Log.w(TAG, "Could not extract a valid mapping block from sub-agent response.")
                return CorrectionResult.Error
            }

            // Parse the mapping and apply it to the original patch code
            val correctedPatch = applyMappingToPatch(aiPatchCode, mappingBlock)
            return if (correctedPatch.isNotBlank()) {
                CorrectionResult.Success(correctedPatch)
            } else {
                CorrectionResult.Error
            }

        } catch (e: Exception) {
            Log.e(TAG, "Sub-agent for line correction failed with an exception.", e)
            return CorrectionResult.Error
        }
    }

    /**
     * Parses a mapping block from the sub-agent and applies the line number changes
     * to the original patch code.
     *
     * @param originalPatch The original patch code from the main AI.
     * @param mappingBlock The mapping block string from the sub-agent.
     * @return The corrected patch code, or an empty string if parsing fails.
     */
    private fun applyMappingToPatch(originalPatch: String, mappingBlock: String): String {
        if (!mappingBlock.contains("// [MAPPING]")) {
            Log.w(TAG, "Sub-agent output did not contain a valid mapping block.")
            return ""
        }

        val mappingLines = mappingBlock.lines()
            .map { it.trim() }
            .filter { it.startsWith("//") && it.contains(" -> ") }

        val lineMappings = mappingLines.mapNotNull { line ->
            val parts = line.removePrefix("// ").split(" -> ")
            if (parts.size == 2) {
                Pair(parts[0].trim(), parts[1].trim())
            } else {
                null
            }
        }.toMap()

        if (lineMappings.isEmpty()) {
            Log.w(TAG, "No valid line mappings found in the sub-agent output.")
            // If mapping fails, we can't proceed with the patch.
            // Returning the original patch might apply it to wrong lines, so we return empty.
            return ""
        }

        var correctedPatch = originalPatch
        lineMappings.forEach { (originalSpec, correctedSpec) ->
            val originalTag = "[START-$originalSpec]"
            val correctedTag = "[START-$correctedSpec]"
            correctedPatch = correctedPatch.replace(originalTag, correctedTag)
        }

        Log.d(TAG, "Successfully constructed corrected patch from mapping.")
        return correctedPatch
    }

    private data class EditRange(val start: Int, val end: Int)

    private fun extractRangesFromPatch(aiPatchCode: String): List<EditRange> {
        return EDIT_BLOCK_REGEX
            .findAll(aiPatchCode)
            .mapNotNull { matchResult ->
                val (actionStr, lineRangeStr, _, _) = matchResult.destructured
                try {
                    val action = EditAction.valueOf(actionStr)
                    when (action) {
                        EditAction.REPLACE,
                        EditAction.DELETE -> {
                            val parts = lineRangeStr.split('-')
                            if (parts.size != 2) return@mapNotNull null
                            val start = parts[0].toIntOrNull() ?: return@mapNotNull null
                            val end = parts[1].toIntOrNull() ?: return@mapNotNull null
                            EditRange(start, end)
                        }
                        EditAction.INSERT -> {
                            val afterLine = lineRangeStr.toIntOrNull() ?: return@mapNotNull null
                            EditRange(afterLine, afterLine)
                        }
                    }
                } catch (e: IllegalArgumentException) {
                    null // Invalid action string
                }
                            }
                            .toList()
    }

    private fun mergeRanges(ranges: List<EditRange>): List<EditRange> {
        if (ranges.isEmpty()) return emptyList()

        val sortedRanges = ranges.sortedBy { it.start }
        val merged = mutableListOf<EditRange>()
        var currentMerge = sortedRanges.first()

        for (i in 1 until sortedRanges.size) {
            val nextRange = sortedRanges[i]
            // Merge if overlapping or adjacent
            if (nextRange.start <= currentMerge.end + 1) {
                currentMerge = EditRange(currentMerge.start, maxOf(currentMerge.end, nextRange.end))
            } else {
                merged.add(currentMerge)
                currentMerge = nextRange
            }
        }
        merged.add(currentMerge)
        return merged
    }

    private fun generateContextualSnippet(originalContent: String, aiPatchCode: String): String {
        val originalLines = originalContent.lines()
        val totalLines = originalLines.size
        val contextSize = 100

        val initialRanges = extractRangesFromPatch(aiPatchCode)

        if (initialRanges.isEmpty()) {
            Log.w(TAG, "Could not extract any valid ranges from the patch. Using full file content as fallback.")
            return originalLines.mapIndexed { i, line -> "${i + 1}| $line" }.joinToString("\n")
        }

        val contextRanges = initialRanges.map {
            EditRange(
                start = (it.start - contextSize).coerceAtLeast(1),
                end = (it.end + contextSize).coerceAtMost(totalLines)
            )
        }

        val mergedRanges = mergeRanges(contextRanges)

        val snippetBuilder = StringBuilder()
        var lastLine = 0

        mergedRanges.forEach { range ->
            if (range.start > lastLine + 1) {
                snippetBuilder.appendLine("// ... [Code omitted for brevity] ...")
            }

            for (i in range.start..range.end) {
                val lineIndex = i - 1
                if (lineIndex in originalLines.indices) {
                    snippetBuilder.appendLine("$i| ${originalLines[lineIndex]}")
                }
            }
            lastLine = range.end
        }

        return snippetBuilder.toString().trim()
    }

    private fun extractMappingBlock(rawResponse: String): String {
        val startIndex = rawResponse.indexOf("// [MAPPING]")
        if (startIndex == -1) {
            return if (rawResponse.contains("// [MAPPING-FAILED]")) "// [MAPPING-FAILED]" else ""
        }
        val endIndex = rawResponse.lastIndexOf("// [/MAPPING]")
        if (endIndex == -1 || endIndex < startIndex) {
            return ""
        }
        return rawResponse.substring(startIndex, endIndex + "// [/MAPPING]".length)
    }
} 