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
    }

    /**
     * Processes file binding using a two-step approach to balance token cost and reliability.
     * 1. Attempts a custom, low-token "loose text patch" that is whitespace-insensitive.
     * 2. If that fails, falls back to a robust but more token-intensive full-content merge.
     *
     * @param originalContent The original content of the file.
     * @param aiGeneratedCode The AI-generated code with placeholders, representing the desired
     * changes.
     * @param multiServiceManager The service manager for AI communication.
     * @return A Pair containing the final merged content and a diff string representing the
     * changes.
     */
    suspend fun processFileBinding(
            originalContent: String,
            aiGeneratedCode: String,
            multiServiceManager: MultiServiceManager
    ): Pair<String, String> {
        // Check for the AI merge override flag
        if (aiGeneratedCode.trim().startsWith("// @FORCE_AI_MERGE")) {
            Log.d(TAG, "Attempt FORCE: AI-driven merge was forced by the agent.")
            val codeToProcess = aiGeneratedCode.lines().drop(1).joinToString("\n")
            // Directly jump to the robust full-content merge
            return runFullContentMerge(originalContent, codeToProcess, multiServiceManager)
        }
        
        // Optimization: If the AI-generated code doesn't contain placeholders like
        // "... existing code ...", it's likely a full file replacement. This avoids a
        // costly and unnecessary patch generation call to the AI.
        if (!aiGeneratedCode.contains("... existing code ...")) {
            Log.d(TAG, "Attempt 1: Full file replacement detected. Skipping patch generation.")
            val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
            val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()

            // Generate a diff for the UI
            val diffString =
                    UnifiedDiffUtils.generateUnifiedDiff(
                                    "a/file",
                                    "b/file",
                                    normalizedOriginalContent.lines(),
                                    DiffUtils.diff(
                                            normalizedOriginalContent.lines(),
                                            normalizedAiGeneratedCode.lines()
                                    ),
                                    3
                            )
                            .joinToString("\n")

            return Pair(normalizedAiGeneratedCode, diffString)
        }

        val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
        val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()

        // --- Attempt 2: Direct Diff-like Patch (fast, no AI call) ---
        Log.d(TAG, "Attempt 2: Trying Direct Diff-like Patch...")
        try {
            val (success, patchedContent) =
                    applyDiffLikePatch(normalizedOriginalContent, normalizedAiGeneratedCode)
            if (success) {
                Log.d(TAG, "Attempt 2: Direct Diff-like Patch succeeded.")

                val finalDiff =
                        UnifiedDiffUtils.generateUnifiedDiff(
                                        "a/file",
                                        "b/file",
                                        normalizedOriginalContent.lines(),
                                        DiffUtils.diff(normalizedOriginalContent.lines(), patchedContent.lines()),
                                        3
                                )
                                .joinToString("\n")

                // No token usage to report here as no AI was called for patching.
                return Pair(patchedContent, finalDiff)
            } else {
                Log.d(TAG, "Attempt 2: Direct Diff-like Patch failed or not applicable.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Attempt 2: Error during Direct Diff-like Patch. Falling back...", e)
        }

        // --- Attempt 3: Custom Loose Text Patch (via Patcher AI) ---
        Log.d(TAG, "Attempt 3: Trying Custom Loose Text Patch via Patcher AI...")
        try {
            val systemPrompt = FunctionalPrompts.FILE_BINDING_PATCH_PROMPT.trimIndent()

            val userPrompt =
"""
**Original File Content:**
```
$normalizedOriginalContent
```
**AI's Edit Request:**
```
$normalizedAiGeneratedCode
```
Now, generate ONLY the patch in the custom format based on all the rules.
""".trimIndent()
            val modelParameters =
                    multiServiceManager.getModelParametersForFunction(FunctionType.FILE_BINDING)
            val fileBindingService =
                    multiServiceManager.getServiceForFunction(FunctionType.FILE_BINDING)

            val contentBuilder = StringBuilder()
            fileBindingService.sendMessage(
                            userPrompt,
                            listOf(Pair("system", systemPrompt)),
                            modelParameters
                    )
                    .collect { content -> contentBuilder.append(content) }

            val patchResponse = ChatUtils.removeThinkingContent(contentBuilder.toString())

            val (success, patchedContent) =
                    applyLooseTextPatch(normalizedOriginalContent, patchResponse)

            if (success) {
                Log.d(TAG, "Attempt 3: Custom Loose Text Patch succeeded.")
                apiPreferences.updateTokensForProviderModel(
                        fileBindingService.providerModel,
                        fileBindingService.inputTokenCount,
                        fileBindingService.outputTokenCount,
                        fileBindingService.cachedInputTokenCount
                )

                val finalDiff =
                        UnifiedDiffUtils.generateUnifiedDiff(
                                        "a/file",
                                        "b/file",
                                        normalizedOriginalContent.lines(),
                                        DiffUtils.diff(
                                                normalizedOriginalContent.lines(),
                                                patchedContent.lines()
                                        ),
                                        3
                                )
                                .joinToString("\n")

                return Pair(patchedContent, finalDiff)
            } else {
                Log.w(
                        TAG,
                        "Attempt 3: Custom Loose Text Patch failed. Falling back to robust full merge."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Attempt 3: Error during Custom Loose Text Patch. Falling back...", e)
        }

        // --- Attempt 4: Robust Full-Content Merge (Fallback) ---
        Log.d(TAG, "Attempt 4 (Fallback): Trying robust full-content merge...")
        return runFullContentMerge(originalContent, aiGeneratedCode, multiServiceManager)
    }

    /**
     * Runs a full-content merge by sending the original and AI-generated code to an AI model.
     * This is the most robust but also most token-intensive method.
     *
     * @param originalContent The original content of the file.
     * @param aiGeneratedCode The AI-generated code with placeholders, representing the desired changes.
     * @param multiServiceManager The service manager for AI communication.
     * @return A Pair containing the final merged content and a diff string.
     */
    private suspend fun runFullContentMerge(
        originalContent: String,
        aiGeneratedCode: String,
        multiServiceManager: MultiServiceManager
    ): Pair<String, String> {
        try {
            val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
            val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()

            val mergeSystemPrompt = FunctionalPrompts.FILE_BINDING_MERGE_PROMPT.trimIndent()

            val mergeUserPrompt =
                """
**Original File Content:**
```
$normalizedOriginalContent
```
**AI-Generated Code (with placeholders):**
```
$normalizedAiGeneratedCode
```
Now, generate ONLY the complete and final merged file content.
""".trimIndent()

            val modelParameters =
                multiServiceManager.getModelParametersForFunction(FunctionType.FILE_BINDING)
            val fileBindingService =
                multiServiceManager.getServiceForFunction(FunctionType.FILE_BINDING)

            val contentBuilder = StringBuilder()
            fileBindingService.sendMessage(
                mergeUserPrompt,
                listOf(Pair("system", mergeSystemPrompt)),
                modelParameters
            )
                .collect { content -> contentBuilder.append(content) }

            val mergedContentFromAI =
                ChatUtils.removeThinkingContent(contentBuilder.toString().trim())

            if (mergedContentFromAI.isBlank()) {
                Log.w(TAG, "Full merge returned empty content. Returning original.")
                return Pair(originalContent, "")
            }

            val diffString =
                UnifiedDiffUtils.generateUnifiedDiff(
                    "a/file",
                    "b/file",
                    normalizedOriginalContent.lines(),
                    DiffUtils.diff(
                        normalizedOriginalContent.lines(),
                        mergedContentFromAI.lines()
                    ),
                    3
                )
                    .joinToString("\n")

            Log.d(TAG, "Robust full-content merge successful.")
            apiPreferences.updateTokensForProviderModel(
                fileBindingService.providerModel,
                fileBindingService.inputTokenCount,
                fileBindingService.outputTokenCount,
                fileBindingService.cachedInputTokenCount
            )
            return Pair(mergedContentFromAI, diffString)
        } catch (e: Exception) {
            Log.e(TAG, "Error during robust full-merge.", e)
            return Pair(originalContent, "Error during robust file binding: ${e.message}")
        }
    }

    /**
     * Normalizes a block of text for a "loose" comparison. This makes the comparison insensitive to
     * leading/trailing whitespace on each line, and also to the amount of internal whitespace
     * between non-whitespace characters. It preserves the number of lines (including blank ones) to
     * ensure an unambiguous replacement.
     */
    private fun normalizeBlock(block: String): String {
        return block.lines().joinToString("\n") { it.trim().replace("\\s+".toRegex(), " ") }
    }

    /**
     * Applies a "diff-like" patch (using +/- prefixes) directly to the original content without
     * needing a secondary AI call. It parses the diff format into search/replace blocks and uses
     * loose matching to apply it.
     *
     * @return A Pair of (Boolean, String) indicating success and the modified content.
     */
    private fun applyDiffLikePatch(
            originalContent: String,
            aiGeneratedPatch: String
    ): Pair<Boolean, String> {
        val patchLines = aiGeneratedPatch.lines()

        // Heuristic: If it doesn't contain +/- lines, or if it contains placeholders,
        // it's not a simple diff that this function can handle.
        val isDiffLike = patchLines.any { it.trim().startsWith("+") || it.trim().startsWith("-") }
        val hasPlaceholders = patchLines.any { it.contains("... existing code ...") }

        if (!isDiffLike || hasPlaceholders) {
            return Pair(false, originalContent)
        }

        // The search block is the patch without '+' lines, and with '-' markers removed.
        val searchBlock =
                patchLines
                        .filter { line -> !line.trim().startsWith("+") }
                        .joinToString("\n") { line ->
                            if (line.trim().startsWith("-")) {
                                val minusIndex = line.indexOf('-')
                                line.substring(minusIndex + 1)
                            } else {
                                line
                            }
                        }

        // The replace block is the patch without '-' lines, and with '+' markers removed.
        val replaceBlock =
                patchLines
                        .filter { line -> !line.trim().startsWith("-") }
                        .joinToString("\n") { line ->
                            if (line.trim().startsWith("+")) {
                                val plusIndex = line.indexOf('+')
                                line.substring(plusIndex + 1)
                            } else {
                                line
                            }
                        }

        // If the context (non +/- lines) is empty and there's nothing to search for,
        // we can't reliably place the patch.
        val contextLines =
                patchLines.filter { !it.trim().startsWith("+") && !it.trim().startsWith("-") }
        if (contextLines.all { it.isBlank() } && searchBlock.isBlank()) {
            Log.w(TAG, "Diff-like patch failed: pure insertion without any context.")
            return Pair(false, originalContent)
        }

        return findAndReplaceBlock(originalContent, searchBlock, replaceBlock)
    }

    /**
     * Applies a series of search-and-replace operations from a custom patch format using a "loose"
     * matching algorithm that ignores leading/trailing whitespace on each line.
     *
     * @return A Pair of (Boolean, String) indicating success and the modified content.
     */
    private fun applyLooseTextPatch(
            originalContent: String,
            patchText: String
    ): Pair<Boolean, String> {
        var modifiedContent = originalContent
        try {
            val patchRegex =
                    """(?s)<<<<<<< SEARCH\n(.*?)\n=======\n(.*?)\n>>>>>>> REPLACE""".toRegex()
            val operations =
                    patchRegex
                            .findAll(patchText)
                            .map {
                                // groupValues[1] is the SEARCH block, groupValues[2] is the REPLACE
                                // block
                                it.groupValues[1].trim() to it.groupValues[2].trim()
                            }
                            .toList()

            if (operations.isEmpty()) {
                Log.w(TAG, "Custom patch was empty or did not match expected format.")
                return Pair(false, originalContent)
            }

            for ((searchBlock, replaceBlock) in operations) {
                val (success, newContent) =
                        findAndReplaceBlock(modifiedContent, searchBlock, replaceBlock)
                if (!success) {
                    // Log is already done inside findAndReplaceBlock
                    return Pair(false, originalContent)
                }
                modifiedContent = newContent
            }
            return Pair(true, modifiedContent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply custom loose text patch.", e)
            return Pair(false, originalContent)
        }
    }

    /**
     * Finds a unique, "loose" match for a search block within the content and replaces it. Loose
     * matching is insensitive to whitespace and indentation differences.
     *
     * @param content The original content to modify.
     * @param searchBlock The block of text to find.
     * @param replaceBlock The block of text to replace with.
     * @return A Pair of (Boolean, String) indicating success and the modified content.
     */
    private fun findAndReplaceBlock(
            content: String,
            searchBlock: String,
            replaceBlock: String
    ): Pair<Boolean, String> {
        val normalizedSearch = normalizeBlock(searchBlock)
        if (normalizedSearch.isEmpty()) {
            if (searchBlock.isNotBlank()) {
                Log.w(TAG, "Patch application failed: search block is empty after normalization.")
            }

            return Pair(false, content)
        }

        val originalLines = content.lines()
        val searchLinesCount = searchBlock.lines().size
        if (searchLinesCount == 0) return Pair(false, content)
        var matchIndex = -1

        // Find a unique loose match
        for (i in 0..(originalLines.size - searchLinesCount)) {
            val windowBlock = originalLines.subList(i, i + searchLinesCount).joinToString("\n")
            if (normalizeBlock(windowBlock) == normalizedSearch) {
                if (matchIndex != -1) { // Ambiguous match
                    Log.w(TAG, "Patch failed: ambiguous match for search block:\n$searchBlock")
                    return Pair(false, content)
                }
                matchIndex = i
            }
        }

        if (matchIndex != -1) { // Unique match found
            val modifiedLinesList = originalLines.toMutableList()
            repeat(searchLinesCount) { modifiedLinesList.removeAt(matchIndex) }
            if (replaceBlock.isNotBlank()) {
                modifiedLinesList.addAll(matchIndex, replaceBlock.lines())
            }
            val modifiedContent = modifiedLinesList.joinToString("\n")
            return Pair(true, modifiedContent)
        } else { // No match found
            Log.w(TAG, "Patch failed: could not find a unique match for block:\n$searchBlock")
            return Pair(false, content)
        }
    }
} 