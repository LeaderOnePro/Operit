package com.ai.assistance.operit.api.chat.enhance

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger

/**
 * Manages conversation rounds for the AI assistant.
 *
 * This class is responsible for tracking and managing different rounds of conversation,
 * particularly when tools are being executed and responses are processed.
 */
class ConversationRoundManager {
    companion object {
        private const val TAG = "ConversationRoundManager"
        private const val ROUND_SEPARATOR_FORMAT = "--- Round %d ---\n"
    }

    // Map to store content for each round
    private val roundContents = mutableMapOf<Int, String>()

    // Tracks the current round number
    private val currentResponseRound = AtomicInteger(0)

    // Pattern used to remove round separators from displayed content
    private val roundSeparatorPattern = Regex("--- Round \\d+ ---\n")

    /** Initializes a new conversation, resetting all round tracking. */
    fun initializeNewConversation() {
        currentResponseRound.set(0)
        roundContents.clear()
        Log.d(TAG, "New conversation initialized")
    }

    /**
     * Updates content for the current round.
     *
     * @param content The content to be added or updated
     * @return The accumulated content after update
     */
    fun updateContent(content: String): String {
        // Simply update the content for the current round
        val currentRound = currentResponseRound.get()
        roundContents[currentRound] = content
        // Log.d(TAG, "Updated content for round $currentRound")

        return getDisplayContent()
    }

    /**
     * Starts a new round.
     *
     * @return The current round number after incrementing
     */
    fun startNewRound(): Int {
        val newRound = currentResponseRound.incrementAndGet()
        roundContents[newRound] = "" // Initialize empty content for the new round
        Log.d(TAG, "Starting new round: $newRound")
        return newRound
    }

    /**
     * Appends content to the end of the accumulated content, outside any round structure.
     *
     * @param content The content to append
     * @return The updated display content
     */
    fun appendContent(content: String): String {
        roundContents[currentResponseRound.get()] +=
                "\n" + content.trim() // Use -1 as a special key for appended content
        return getDisplayContent()
    }

    /**
     * Gets the content suitable for display, with round separators removed.
     *
     * @return Clean content without round separators
     */
    fun getDisplayContent(): String {
        val buffer = StringBuilder()

        // Add rounds in order
        val sortedKeys = roundContents.keys.filter { it >= 0 }.sorted()

        sortedKeys.forEachIndexed { index, round ->
            val content = roundContents[round] ?: ""
            if (index > 0) buffer.append("\n")
            buffer.append(content)
        }

        // Append any content that's outside rounds (key -1)
        if (roundContents.containsKey(-1)) {
            buffer.append("\n").append(roundContents[-1])
        }

        return buffer.toString()
    }

    fun getCurrentRoundContent(): String {
        return roundContents[currentResponseRound.get()] ?: ""
    }

    /**
     * Gets the raw accumulated content including all round separators.
     *
     * @return Raw content with round separators
     */
    fun getRawContent(): String {
        val buffer = StringBuilder()

        // Add rounds in order with separators
        val sortedKeys = roundContents.keys.filter { it >= 0 }.sorted()

        sortedKeys.forEachIndexed { index, round ->
            val content = roundContents[round] ?: ""
            if (index > 0) buffer.append("\n")
            buffer.append(String.format(ROUND_SEPARATOR_FORMAT, round))
            buffer.append(content)
        }

        // Append any content that's outside rounds (key -1)
        if (roundContents.containsKey(-1)) {
            buffer.append("\n").append(roundContents[-1])
        }

        return buffer.toString()
    }

    /**
     * Gets the current round number.
     *
     * @return Current round number
     */
    fun getCurrentRound(): Int {
        return currentResponseRound.get()
    }

    /** Clears all content. */
    fun clearContent() {
        roundContents.clear()
        Log.d(TAG, "Content cleared")
    }
}
