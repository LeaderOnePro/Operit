package com.ai.assistance.operit.api.chat.enhance

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.model.FunctionType
import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils

class FileBindingService(context: Context) {

    companion object {
        private const val TAG = "FileBindingService"
        private val EDIT_BLOCK_REGEX =
            """\[START-(REPLACE|DELETE)\]\s*\n(.*?)\[END-\1\]""".toRegex(
                RegexOption.DOT_MATCHES_ALL
            )
    }

    private enum class EditAction {
        REPLACE,
        DELETE
    }

    private data class EditOperation(
            val action: EditAction,
            val oldContent: String,
            val newContent: String
    )

    /**
     * Processes file binding by applying structured edit blocks.
     * This new approach abandons line numbers and sub-agents in favor of fuzzy content matching.
     *
     * 1.  Parses `[START-REPLACE]` or `[START-DELETE]` blocks from the AI-generated code.
     * 2.  For each block, it uses the content of the `[OLD]` section as a search pattern.
     * 3.  It performs a fuzzy match of the `[OLD]` content against the `originalContent` to find the
     *     exact lines to be modified, ignoring whitespace and newlines.
     * 4.  Once the correct range is identified, it applies the `REPLACE` or `DELETE` operation.
     * 5.  If no structured blocks are found, it defaults to a full file replacement.
     *
     * @param originalContent The original content of the file.
     * @param aiGeneratedCode The AI-generated code, containing either edit blocks or full content.
     * @return A Pair containing the final merged content and a diff string.
      */
     suspend fun processFileBinding(
             originalContent: String,
             aiGeneratedCode: String
     ): Pair<String, String> {
         if (aiGeneratedCode.contains("[START-")) {
             Log.d(TAG, "Structured edit blocks detected. Attempting fuzzy patch.")
             try {
                 val (success, resultString) = applyFuzzyPatch(originalContent, aiGeneratedCode)
                if (success) {
                    Log.d(TAG, "Fuzzy patch succeeded.")
                    val diffString = generateDiff(originalContent.replace("\r\n", "\n"), resultString)
                    return Pair(resultString, diffString)
                } else {
                    Log.w(TAG, "Fuzzy patch application failed. Reason: $resultString")
                    return Pair(originalContent, "Error: Could not apply patch. Reason: $resultString")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during fuzzy patch process.", e)
                return Pair(originalContent, "Error: An unexpected exception occurred during the patching process: ${e.message}")
            }
        }

        // Default to full file replacement if no special instructions are found
        Log.d(TAG, "No structured blocks found. Assuming full file replacement.")
        val normalizedOriginalContent = originalContent.replace("\r\n", "\n")
        val normalizedAiGeneratedCode = aiGeneratedCode.replace("\r\n", "\n").trim()
        val diffString = generateDiff(normalizedOriginalContent, normalizedAiGeneratedCode)
        return Pair(normalizedAiGeneratedCode, diffString)
    }

    private fun generateDiff(original: String, modified: String): String {
        return generateUnifiedDiff(original, modified)
    }

    /**
     * Generates a unified diff string with line numbers and change indicators (+, -).
     * This is a public utility that can be used by other services.
     *
     * @param original The original text content.
     * @param modified The modified text content.
     * @return A formatted string representing the unified diff.
     */
    fun generateUnifiedDiff(original: String, modified: String): String {
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
        // sb.appendLine()

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
                line.startsWith("---") || line.startsWith("+++") -> continue // Skip header lines
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
     * Applies a patch based on fuzzy matching of the `[OLD]` content block.
     * This method is the core of the new, more robust patching mechanism.
     *
     * @return A Pair of (Boolean, String) indicating success and the modified content, or failure
     * and a detailed error message.
      */
    private fun applyFuzzyPatch(
        originalContent: String,
        aiPatchCode: String
    ): Pair<Boolean, String> {
        try {
            val operations = parseEditOperations(aiPatchCode)
            if (operations.isEmpty()) {
                return Pair(false, "No valid edit operations found in the patch code.")
            }

            val originalLines = originalContent.lines().toMutableList()
            val enrichedOps = mutableListOf<Triple<EditOperation, Int, Int>>()

            for (op in operations) {
                val (start, end) = findBestMatchRange(originalLines, op.oldContent)
                if (start == -1) {
                    Log.w(TAG, "Could not find a suitable match for OLD block: ${op.oldContent.take(100)}...")
                    return Pair(false, "Could not find a match for an OLD block. The file may have changed too much.")
                }
                enrichedOps.add(Triple(op, start, end))
            }

            // Sort operations by start line in descending order to apply from the bottom up
            enrichedOps.sortByDescending { it.second }

            for ((op, start, end) in enrichedOps) {
                Log.d(TAG, "Applying ${op.action} at lines ${start + 1}-${end + 1}")
                
                // Remove the old lines
                for (i in end downTo start) {
                    originalLines.removeAt(i)
                }

                // If it's a REPLACE, add the new lines
                if (op.action == EditAction.REPLACE) {
                    originalLines.addAll(start, op.newContent.lines())
                }
            }

            return Pair(true, originalLines.joinToString("\n"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply fuzzy patch", e)
            return Pair(false, "Failed to apply fuzzy patch due to an exception: ${e.message}")
        }
    }

    private fun parseEditOperations(patchCode: String): List<EditOperation> {
        val operations = mutableListOf<EditOperation>()
        val lines = patchCode.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("[START-")) {
                val header = line
                val actionStr = header.substringAfter("[START-").substringBefore("]")

                val action = try {
                    EditAction.valueOf(actionStr)
                } catch (e: IllegalArgumentException) {
                    i++
                    continue // Skip invalid action
                }

                var oldContent = ""
                var newContent = ""
                var inBlock: String? = null
                i++ // Move to content

                while (i < lines.size && !lines[i].trim().startsWith("[END-$actionStr]")) {
                    val currentLine = lines[i]
                    val trimmedLine = currentLine.trim()

                    if (trimmedLine.startsWith("[OLD]")) inBlock = "OLD"
                    else if (trimmedLine.startsWith("[NEW]")) inBlock = "NEW"
                    else if (trimmedLine.startsWith("[/OLD]")) inBlock = null
                    else if (trimmedLine.startsWith("[/NEW]")) inBlock = null
                    else {
                        when (inBlock) {
                            "OLD" -> oldContent += currentLine + "\n"
                            "NEW" -> newContent += currentLine + "\n"
                        }
                    }
                    i++
                }

                // Basic validation
                if ((action == EditAction.REPLACE || action == EditAction.DELETE) && oldContent.isBlank()) {
                    i++
                    continue // Skip invalid operation
                }
                if (action == EditAction.REPLACE && newContent.isBlank()) {
                    i++
                    continue // Skip invalid operation
                }

                operations.add(EditOperation(action, oldContent.trim(), newContent.trim()))
            }
            i++
        }
        return operations
    }

    private fun findBestMatchRange(originalLines: List<String>, oldContent: String): Pair<Int, Int> {
        val normalizedOldContent = oldContent.replace(Regex("\\s+"), "")
        if (normalizedOldContent.isEmpty()) return -1 to -1

        val oldContentLines = oldContent.lines().filter { it.isNotBlank() }
        val numOldLines = oldContentLines.size

        var bestMatchScore = 0.0
        var bestMatchRange = -1 to -1

        for (i in 0 until originalLines.size) {
            for (j in i until originalLines.size) {
                val windowLines = originalLines.subList(i, j + 1)
                val normalizedWindow = windowLines.joinToString("").replace(Regex("\\s+"), "")
                
                // Heuristic: if the line counts are drastically different, it's unlikely to be a good match.
                if (kotlin.math.abs(windowLines.size - numOldLines) > 5 && numOldLines > 5) continue

                val score = jaroWinklerDistance(normalizedOldContent, normalizedWindow)

                if (score > bestMatchScore) {
                    bestMatchScore = score
                    bestMatchRange = i to j
                }
            }
        }

        // Confidence threshold
        return if (bestMatchScore > 0.9) bestMatchRange else -1 to -1
    }

    private fun jaroWinklerDistance(s1: String, s2: String): Double {
        val jaroDist = jaroDistance(s1, s2)
        if (jaroDist == 0.0) return 0.0

        var prefixLength = 0
        while (prefixLength < s1.length && prefixLength < s2.length && s1[prefixLength] == s2[prefixLength]) {
            prefixLength++
        }
        prefixLength = minOf(4, prefixLength)

        return jaroDist + (prefixLength * 0.1 * (1.0 - jaroDist))
    }

    private fun jaroDistance(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val len1 = s1.length
        val len2 = s2.length
        val matchDistance = maxOf(len1, len2) / 2 - 1

        val s1Matches = BooleanArray(len1)
        val s2Matches = BooleanArray(len2)
        var matches = 0

        for (i in 0 until len1) {
            val start = maxOf(0, i - matchDistance)
            val end = minOf(i + matchDistance + 1, len2)
            for (j in start until end) {
                if (!s2Matches[j] && s1[i] == s2[j]) {
                    s1Matches[i] = true
                    s2Matches[j] = true
                    matches++
                    break
                }
            }
        }

        if (matches == 0) return 0.0

        var t = 0.0
        var k = 0
        for (i in 0 until len1) {
            if (s1Matches[i]) {
                while (!s2Matches[k]) k++
                if (s1[i] != s2[k]) t++
                k++
            }
        }

        val transpositions = t / 2.0
        return ((matches.toDouble() / len1) + (matches.toDouble() / len2) + ((matches - transpositions) / matches)) / 3.0
    }
}