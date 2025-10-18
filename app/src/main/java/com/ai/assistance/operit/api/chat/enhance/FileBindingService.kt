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
            """//\s*\[START-(REPLACE|INSERT|DELETE):(?:after_line=)?([\d-]+)\]\n?(?:(?://\s*\[CONTEXT\]\s*(.*?)\s*//\s*\[/CONTEXT\]\s*(.*?))|(.*?))\s*//\s*\[END-\1\]""".toRegex(
                RegexOption.DOT_MATCHES_ALL
            )

        /**
         * A dedicated utility function to generate a diff-like view for brand new file content.
         * This is intended for callers who handle file creation separately and just need to format the output,
         * ensuring new files also get line numbers and a diff-like appearance.
         *
         * @param newContent The full content of the newly created file.
         * @return A string formatted as a diff, with each line prefixed by '+' and a line number.
         */
        fun formatNewFileContentAsDiff(newContent: String): String {
            val modifiedLines = if (newContent.isEmpty()) emptyList() else newContent.lines()
            if (modifiedLines.isEmpty()) {
                return "Changes: +0 -0 lines\n\n(File created with empty content)"
            }

            val additions = modifiedLines.size
            val sb = StringBuilder()
            sb.appendLine("Changes: +$additions -0 lines")
            sb.appendLine()

            modifiedLines.forEachIndexed { index, line ->
                val newLineNum = index + 1
                sb.appendLine("+${newLineNum.toString().padEnd(4)}|$line")
            }

            return sb.toString()
        }
    }

    private enum class EditAction {
        REPLACE,
        INSERT,
        DELETE
    }

    private sealed class CorrectionResult {
        data class Success(val correctedPatch: String) : CorrectionResult()
        data class MappingFailed(val reason: String) : CorrectionResult()
        data class SyntaxError(val errorMessage: String) : CorrectionResult()
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
                        val detailedMessage = "Error: Could not apply patch. The sub-agent failed to find the correct line numbers and provided the following reason:\n${correctionResult.reason}"
                        return Pair(originalContent, detailedMessage)
                    }
                    is CorrectionResult.SyntaxError -> {
                        Log.w(TAG, "Sub-agent reported a syntax error in the patch.")
                        val detailedMessage = "Error: Could not apply patch. The sub-agent reported a syntax error in the AI-generated code patch:\n${correctionResult.errorMessage}"
                        return Pair(originalContent, detailedMessage)
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
        val originalLines = if (original.isEmpty()) emptyList() else original.lines()
        val modifiedLines = if (modified.isEmpty()) emptyList() else modified.lines()
        val patch = DiffUtils.diff(originalLines, modifiedLines)

        if (patch.deltas.isEmpty()) {
            return "No changes detected (files are identical)"
        }

        // First, calculate stats
        val sb = StringBuilder()
        var additions = 0
        var deletions = 0
        patch.deltas.forEach { delta ->
            when (delta.type) {
                com.github.difflib.patch.DeltaType.INSERT -> additions += delta.target.lines.size
                com.github.difflib.patch.DeltaType.DELETE -> deletions += delta.source.lines.size
                com.github.difflib.patch.DeltaType.CHANGE -> {
                    additions += delta.target.lines.size
                    deletions += delta.source.lines.size
                }
                else -> {}
            }
        }
        sb.appendLine("Changes: +$additions -$deletions lines")
        sb.appendLine()

        // Generate a standard unified diff to process
        val unifiedDiffLines = UnifiedDiffUtils.generateUnifiedDiff(
            "a/file",
            "b/file",
            originalLines,
            patch,
            3 // Context lines
        )

        val resultLines = mutableListOf<String>()
        var origLineNum = 0
        var newLineNum = 0
        val hunkHeaderRegex = """^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@.*$""".toRegex()

        for (line in unifiedDiffLines) {
            when {
                line.startsWith("---") || line.startsWith("+++") -> resultLines.add(line)
                line.startsWith("@@") -> {
                    resultLines.add(line)
                    hunkHeaderRegex.find(line)?.let {
                        origLineNum = it.groupValues[1].toInt()
                        newLineNum = it.groupValues[3].toInt()
                    }
                }
                line.startsWith("-") -> {
                    resultLines.add("-${origLineNum.toString().padEnd(4)}|${line.substring(1)}")
                    origLineNum++
                }
                line.startsWith("+") -> {
                    resultLines.add("+${newLineNum.toString().padEnd(4)}|${line.substring(1)}")
                    newLineNum++
                }
                line.startsWith(" ") -> {
                    resultLines.add(" ${origLineNum.toString().padEnd(4)}|${line.substring(1)}")
                    origLineNum++
                    newLineNum++
                }
            }
        }

        sb.append(resultLines.joinToString("\n"))
        return sb.toString()
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
                                val actionStr = matchResult.groupValues[1]
                                val lineRangeStr = matchResult.groupValues[2]
                                val context = matchResult.groupValues[3]
                                val content = if (context.isNotBlank()) matchResult.groupValues[4] else matchResult.groupValues[5]

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

            Log.d(TAG, "Starting line-based patch application. Found ${operations.size} operations.")

            val originalLines = originalContent.lines().toMutableList()

            // Operations must be sorted in descending order to apply from the bottom up.
            // This prevents edit operations from shifting the line numbers of subsequent operations.
            val sortedOps = operations.sortedByDescending { it.startLine }

            for ((index, op) in sortedOps.withIndex()) {
                Log.d(TAG, "--- Operation ${index + 1}/${sortedOps.size} ---")
                Log.d(TAG, "Action: ${op.action}, Target Lines: ${op.startLine}-${op.endLine}")
                if (op.content.isNotEmpty()) {
                    Log.d(TAG, "Content to apply:\n---\n${op.content}\n---")
                } else {
                    Log.d(TAG, "Content to apply: [EMPTY]")
                }

                // Convert to 0-based index for list manipulation
                val startIndex = op.startLine - 1
                val endIndex = op.endLine - 1
                val maxLine = originalLines.size

                // Log context around the change
                val contextSize = 3
                val contextStart = (startIndex - contextSize).coerceAtLeast(0)
                val contextEnd = (endIndex + contextSize).coerceAtMost(originalLines.size - 1)
                if (contextStart <= contextEnd) {
                    val contextSnippet = originalLines.slice(contextStart..contextEnd)
                            .mapIndexed { i, line -> "${contextStart + i + 1}| $line" }
                            .joinToString("\n")
                    Log.d(TAG, "Context (lines ${contextStart + 1}-${contextEnd + 1}):\n$contextSnippet")
                }


                // Boundary checks for the operation
                // For INSERT, startLine can be 0 (insert at beginning) to maxLine (insert at end)
                // For REPLACE/DELETE, startLine must be within [1, maxLine], endLine can exceed maxLine (will be clamped during execution)
                when (op.action) {
                    EditAction.INSERT -> {
                        if (op.startLine < 0 || op.startLine > maxLine) {
                            Log.e(TAG, "Invalid INSERT line: ${op.startLine}. File has $maxLine lines.")
                            return Pair(false, originalContent)
                        }
                    }
                    EditAction.REPLACE, EditAction.DELETE -> {
                        if (op.startLine <= 0 || op.startLine > maxLine || op.endLine < op.startLine) {
                            Log.e(TAG, "Invalid ${op.action} range: ${op.startLine}-${op.endLine}. File has $maxLine lines.")
                            return Pair(false, originalContent)
                        }
                    }
                }

                when (op.action) {
                    EditAction.REPLACE -> {
                        // Use the AI-generated content as-is, without adding any extra indentation
                        // The AI is responsible for providing correctly indented code
                        val newContentLines = op.content.lines()

                        val range = endIndex downTo startIndex
                        var removedCount = 0

                        for (i in range) {
                            if (i >= 0 && i < originalLines.size) {
                                originalLines.removeAt(i)
                                removedCount++
                            }
                        }

                        val insertionPoint = startIndex.coerceIn(0, originalLines.size)
                        originalLines.addAll(insertionPoint, newContentLines)
                    }
                    EditAction.INSERT -> {
                        // Use the AI-generated content as-is, without adding any extra indentation
                        // The AI is responsible for providing correctly indented code
                        val newContentLines = op.content.lines()

                        // INSERT means "insert after line N", so the insertion point is op.startLine (not startIndex)
                        val insertionPoint = op.startLine.coerceIn(0, originalLines.size)
                        originalLines.addAll(insertionPoint, newContentLines)
                    }
                    EditAction.DELETE -> {
                        val range = endIndex downTo startIndex
                        var removedCount = 0
                        for (i in range) {
                            if (i >= 0 && i < originalLines.size) {
                                originalLines.removeAt(i)
                                removedCount++
                            }
                        }
                    }
                }
                Log.d(TAG, "--- End Operation ${index + 1}/${sortedOps.size} ---")
            }

            Log.d(TAG, "Patch application finished successfully.")
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

            // Check for different response types from the sub-agent
            return when {
                rawResponse.startsWith("[MAPPING-SYNTAX-ERROR]") -> {
                    Log.w(TAG, "Sub-agent reported a syntax error.")
                    CorrectionResult.SyntaxError(rawResponse)
                }
                rawResponse.startsWith("[MAPPING-FAILED]") -> {
                    Log.w(TAG, "Sub-agent explicitly failed to find mapping.")
                    val reason = rawResponse.substringAfter("[MAPPING-FAILED]").trim()
                    CorrectionResult.MappingFailed(reason)
                }
                rawResponse.startsWith("[MAPPING]") -> {
                    val correctedPatch = applyMappingToPatch(aiPatchCode, rawResponse)
                    if (correctedPatch.isNotBlank()) {
                        Log.d(TAG, "Successfully constructed corrected patch from mapping.")
                        CorrectionResult.Success(correctedPatch)
                    } else {
                        Log.e(TAG, "Failed to apply mapping to patch, though mapping block was present.")
                        CorrectionResult.Error
                    }
                }
                else -> {
                    Log.w(TAG, "Sub-agent response did not contain any known mapping markers.")
                    CorrectionResult.Error
                }
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
        if (!mappingBlock.contains("[MAPPING]")) {
            Log.w(TAG, "Sub-agent output did not contain a valid mapping block.")
            return ""
        }

        val mappingLines = mappingBlock.lines()
            .map { it.trim() }
            .filter { it.contains(" -> ") }

        val lineMappings = mappingLines.mapNotNull { line ->
            val parts = line.split(" -> ")
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
            val (action, lines) = originalSpec.split(':', limit = 2)
            val (correctedAction, correctedLines) = correctedSpec.split(':', limit = 2)

            // Normalize the line spec by removing "after_line=" or "after_line " or "after_line:" prefix
            val normalizedLines = lines.replace(Regex("^after_line[=:\\s]+"), "")
            val normalizedCorrectedLines = correctedLines.replace(Regex("^after_line[=:\\s]+"), "")

            // Build a list of possible original tag formats to find, accommodating different AI outputs
            val possibleTagsToFind = if (action == "INSERT") {
                listOf(
                    "[START-$action:after_line=$normalizedLines]",
                    "[START-$action:after_line $normalizedLines]",
                    "[START-$action:$normalizedLines]"
                )
            } else { // For REPLACE, DELETE, the format is simpler
                listOf(
                    "[START-$action:$normalizedLines]"
                    // We could add more variants here if the AI produces them, e.g. with spaces
                )
            }

            // Build the single, canonical, correct format for the new tag based on its action type
            val correctedTag = if (correctedAction == "INSERT") {
                "[START-$correctedAction:after_line=$normalizedCorrectedLines]"
            } else { // For REPLACE, DELETE
                "[START-$correctedAction:$normalizedCorrectedLines]"
            }

            // Find which tag format exists in the patch and replace it with the canonical one
            var replaced = false
            for (tagToFind in possibleTagsToFind) {
                if (correctedPatch.contains(tagToFind)) {
                    // Using simple string replacement. This assumes tags are unique enough
                    // not to partially match other parts of the code, which is a reasonable
                    // assumption for this structured format.
                    correctedPatch = correctedPatch.replace(tagToFind, correctedTag)
                    replaced = true
                    Log.d(TAG, "Replaced '$tagToFind' with '$correctedTag'")
                    break
                }
            }

            if (!replaced) {
                Log.w(TAG, "Could not find tag for original spec: $originalSpec. Tried: ${possibleTagsToFind.joinToString()}")
            }
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

} 