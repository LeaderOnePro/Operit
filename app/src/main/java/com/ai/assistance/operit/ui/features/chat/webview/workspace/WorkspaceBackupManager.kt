package com.ai.assistance.operit.ui.features.chat.webview.workspace

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import com.ai.assistance.operit.ui.features.chat.webview.workspace.process.GitIgnoreFilter
import com.ai.assistance.operit.util.FileUtils

@Serializable
data class BackupManifest(
    val timestamp: Long,
    val files: Map<String, String> // relativePath -> hash
)

class WorkspaceBackupManager(private val context: Context) {

    companion object {
        private const val TAG = "WorkspaceBackupManager"
        private const val BACKUP_DIR_NAME = ".backup"
        private const val OBJECTS_DIR_NAME = "objects"

        @Volatile
        private var INSTANCE: WorkspaceBackupManager? = null

        fun getInstance(context: Context): WorkspaceBackupManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorkspaceBackupManager(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    /**
     * Synchronizes the workspace state based on the message timestamp.
     * It either creates a new backup or restores to a previous state.
     */
    fun syncState(workspacePath: String, messageTimestamp: Long) {
        val workspaceDir = File(workspacePath)
        if (!workspaceDir.exists() || !workspaceDir.isDirectory) {
            Log.w(TAG, "Workspace path does not exist or is not a directory: $workspacePath")
            return
        }

        val backupDir = File(workspaceDir, BACKUP_DIR_NAME)
        backupDir.mkdirs()

        val existingBackups = backupDir.listFiles { file ->
            file.isFile && file.name.endsWith(".json")
        }?.mapNotNull {
            it.nameWithoutExtension.toLongOrNull()
        }?.sorted() ?: emptyList()
        Log.d(TAG, "syncState called for timestamp: $messageTimestamp. Existing backups: $existingBackups")

        val newerBackups = existingBackups.filter { it > messageTimestamp }

        if (newerBackups.isNotEmpty()) {
            // Found future states, need to rewind
            val restoreTimestamp = newerBackups.first()
            Log.i(TAG, "Newer backups found. Rewinding workspace to state at $restoreTimestamp")
            Log.d(TAG, "[Rewind] Calculated restoreTimestamp: $restoreTimestamp")
            restoreToState(workspaceDir, backupDir, restoreTimestamp)
            // After restoring, delete all backups newer than the restored one
            val backupsToDelete = newerBackups.filter { it >= restoreTimestamp }
            Log.d(TAG, "[Rewind] Backups to be deleted: $backupsToDelete")
            Log.d(TAG, "Deleting backups from $restoreTimestamp onwards: $backupsToDelete")
            backupsToDelete.forEach { ts ->
                File(backupDir, "$ts.json").delete()
            }
            Log.i(TAG, "Deleted ${backupsToDelete.size} newer backup manifests.")

        } else {
            // Forward scenario: this is a new message in sequence.
            if (existingBackups.contains(messageTimestamp)) {
                 Log.d(TAG, "Backup for timestamp $messageTimestamp already exists. Skipping creation.")
                 return
            }
            Log.i(TAG, "No newer backups found for timestamp $messageTimestamp. Creating a new backup.")
            createNewBackup(workspaceDir, backupDir, messageTimestamp)
        }
    }

    private fun createNewBackup(workspaceDir: File, backupDir: File, newTimestamp: Long) {
        val objectsDir = File(backupDir, OBJECTS_DIR_NAME)
        objectsDir.mkdirs()

        val newManifestFiles = mutableMapOf<String, String>()

        val gitignoreRules = GitIgnoreFilter.loadRules(workspaceDir)
        workspaceDir.walkTopDown()
            .filter { FileUtils.isWorkspaceFile(it, workspaceDir, gitignoreRules) }
            .forEach { file ->
                try {
                    val hash = getFileHash(file)
                    val relativePath = file.relativeTo(workspaceDir).path
                    newManifestFiles[relativePath] = hash

                    val objectFile = File(objectsDir, hash)
                    if (!objectFile.exists()) {
                        file.copyTo(objectFile, overwrite = true)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to process file for backup: ${file.path}", e)
                }
            }

        val manifest = BackupManifest(timestamp = newTimestamp, files = newManifestFiles)
        val manifestFile = File(backupDir, "$newTimestamp.json")
        try {
            manifestFile.writeText(json.encodeToString(manifest))
            Log.d(TAG, "Successfully created backup manifest for timestamp $newTimestamp")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write backup manifest for timestamp $newTimestamp", e)
        }
    }

    private fun restoreToState(workspaceDir: File, backupDir: File, targetTimestamp: Long?) {
        val objectsDir = File(backupDir, OBJECTS_DIR_NAME)
        Log.d(TAG, "Attempting to restore workspace to timestamp: $targetTimestamp")

        val targetManifest = if (targetTimestamp != null) {
            val manifestFile = File(backupDir, "$targetTimestamp.json")
            if (manifestFile.exists()) {
                try {
                    json.decodeFromString<BackupManifest>(manifestFile.readText())
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read target manifest for timestamp $targetTimestamp", e)
                    null
                }
            } else {
                Log.w(TAG, "Target manifest file not found for timestamp $targetTimestamp")
                null
            }
        } else {
            // If no target timestamp, we are restoring to an empty state.
            null
        }

        val manifestFiles = targetManifest?.files ?: emptyMap()
        val manifestRelativePaths = manifestFiles.keys
        Log.d(TAG, "Target manifest for timestamp $targetTimestamp contains ${manifestFiles.size} files.")

        // 1. Delete files from workspace that are not in the target manifest
        // Safety: Only delete text-based files that were previously tracked, preserve untracked binary files
        Log.d(TAG, "Step 1: Deleting tracked files not present in the target manifest...")
        workspaceDir.walkTopDown()
            .onEnter { dir -> dir.name != BACKUP_DIR_NAME }
            .filter { it.isFile }
            .forEach { currentFile ->
                val relativePath = currentFile.relativeTo(workspaceDir).path
                if (relativePath !in manifestRelativePaths) {
                    // Only delete text-based files to prevent accidental loss of untracked binary files
                    if (FileUtils.isTextBasedFile(currentFile)) {
                        Log.i(TAG, "Deleting tracked text file not in manifest: $relativePath")
                        currentFile.delete()
                    } else {
                        Log.d(TAG, "Preserving untracked non-text file: $relativePath")
                    }
                }
            }

        // 2. Restore/update files from manifest
        Log.d(TAG, "Step 2: Restoring and updating files from the target manifest...")
        manifestFiles.forEach { (relativePath, hash) ->
            val targetFile = File(workspaceDir, relativePath)
            val objectFile = File(objectsDir, hash)

            if (!objectFile.exists()) {
                 Log.e(TAG, "Object file not found for hash $hash, cannot restore $relativePath")
                 return@forEach
            }

            val needsCopy = if (!targetFile.exists()) {
                true
            } else {
                try {
                    getFileHash(targetFile) != hash
                } catch (e: Exception) {
                    true // File is unreadable or other issue, treat as needing copy
                }
            }

            if (needsCopy) {
                targetFile.parentFile?.mkdirs()
                Log.i(TAG, "Restoring file: $relativePath")
                objectFile.copyTo(targetFile, overwrite = true)
            }
        }

        // 3. Clean up empty directories
        Log.d(TAG, "Step 3: Cleaning up empty directories...")
        workspaceDir.walk(FileWalkDirection.BOTTOM_UP)
            .onEnter { dir -> dir.name != BACKUP_DIR_NAME }
            .filter { it.isDirectory }
            .forEach { dir ->
                if (dir.listFiles()?.isEmpty() == true && dir != workspaceDir) {
                    Log.i(TAG, "Deleting empty directory: ${dir.relativeTo(workspaceDir).path}")
                    dir.delete()
                }
            }

        Log.i(TAG, "Workspace restored to state of timestamp $targetTimestamp")
    }

    private fun getFileHash(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
} 