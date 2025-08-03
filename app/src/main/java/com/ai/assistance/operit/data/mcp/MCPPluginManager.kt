package com.ai.assistance.operit.data.mcp

import android.util.Log
import com.ai.assistance.operit.data.mcp.MCPRepositoryConstants.TAG
import com.ai.assistance.operit.ui.features.packages.screens.mcp.model.MCPServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import android.net.Uri

/** Manages MCP plugin installation, uninstallation, and tracking */
class MCPPluginManager(
        private val cacheManager: MCPCacheManager,
        private val mcpInstaller: MCPInstaller
) {
    /** Set of installed plugin IDs */
    private val _installedPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val installedPluginIds: StateFlow<Set<String>> = _installedPluginIds.asStateFlow()

    init {
        // Load installed plugins on initialization
        scanInstalledPlugins()
    }

    /** Scans the file system for installed plugins and updates the internal state */
    fun scanInstalledPlugins() {
        try {
            val installedIds = mutableSetOf<String>()

            // First try loading saved installation records from file
            val cachedIds = cacheManager.loadInstalledPlugins()
            if (cachedIds.isNotEmpty()) {
                Log.d(TAG, "Loaded installed plugin records from cache: ${cachedIds.size}")
                installedIds.addAll(cachedIds)
            }

            // Then scan file system to verify and update
            val pluginsBaseDir = mcpInstaller.pluginsBaseDir
            if (pluginsBaseDir.exists() && pluginsBaseDir.isDirectory) {
                // Clear previous cached records to ensure we only keep actually existing plugins
                installedIds.clear()

                pluginsBaseDir.listFiles()?.forEach { pluginDir ->
                    if (pluginDir.isDirectory) {
                        // Directory name is the plugin ID
                        val pluginId = pluginDir.name
                        // Check if it's actually installed (has content)
                        if (mcpInstaller.isPluginInstalled(pluginId)) {
                            installedIds.add(pluginId)
                        }
                    }
                }
            }

            // Update in-memory installation state
            _installedPluginIds.value = installedIds

            // Save to file for next startup
            cacheManager.saveInstalledPlugins(installedIds)
            
            // Clear plugin info cache to ensure fresh data
            mcpInstaller.clearPluginInfoCache()

            Log.d(TAG, "Scanned installed plugins: ${installedIds.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan installed plugins", e)
        }
    }

    /**
     * Installs an MCP plugin
     *
     * @param pluginId The ID of the plugin to install
     * @param server The server object containing installation details
     * @param progressCallback Callback to report installation progress
     * @return Result of the installation
     */
    suspend fun installPlugin(
            pluginId: String,
            server: MCPServer,
            progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        try {
            // Convert UI model to data model
            val dataServer =
                    com.ai.assistance.operit.data.mcp.MCPServer(
                            id = server.id,
                            name = server.name,
                            description = server.description,
                            logoUrl = server.logoUrl,
                            stars = server.stars,
                            category = server.category,
                            requiresApiKey = server.requiresApiKey,
                            author = server.author,
                            isVerified = server.isVerified,
                            isInstalled = server.isInstalled,
                            version = server.version,
                            updatedAt = server.updatedAt,
                            longDescription = server.longDescription,
                            repoUrl = server.repoUrl
                    )

            // Perform physical installation
            val result = mcpInstaller.installPlugin(dataServer, progressCallback)

            if (result is InstallResult.Success) {
                // Scan installed plugins after successful installation
                scanInstalledPlugins()
                
                // Clear plugin info cache
                mcpInstaller.clearPluginInfoCache()
                
                Log.d(TAG, "Plugin $pluginId installed successfully")
            } else {
                Log.e(TAG, "Failed to install plugin $pluginId")
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Exception while installing plugin $pluginId", e)
            return InstallResult.Error("Installation error: ${e.message}")
        }
    }

    /**
     * "Installs" a remote MCP server by saving its metadata locally.
     * This allows the app to manage it like a local plugin.
     *
     * @param server The remote server to "install".
     * @return true if the metadata was saved successfully, false otherwise.
     */
    suspend fun installRemotePlugin(server: com.ai.assistance.operit.data.mcp.MCPServer): Boolean {
        try {
            if (server.type != "remote") {
                Log.e(TAG, "installRemotePlugin called with a non-remote server: ${server.id}")
                return false
            }
            
            // "Install" the remote plugin by creating its directory and metadata file
            val result = mcpInstaller.installRemotePluginMetadata(server)

            if (result) {
                // Rescan installed plugins to include the new remote service
                scanInstalledPlugins()
                Log.d(TAG, "Remote plugin ${server.id} registered successfully.")
            } else {
                Log.e(TAG, "Failed to register remote plugin ${server.id}.")
            }
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Exception while registering remote plugin ${server.id}", e)
            return false
        }
    }

    /**
     * Installs an MCP plugin from a local zip file
     *
     * @param pluginId The ID of the plugin to install
     * @param zipUri The URI of the zip file to install
     * @param server The server object containing installation details
     * @param progressCallback Callback to report installation progress
     * @return Result of the installation
     */
    suspend fun installPluginFromZip(
            pluginId: String,
            zipUri: Uri,
            server: MCPServer,
            progressCallback: (InstallProgress) -> Unit = {}
    ): InstallResult {
        try {
            // Convert UI model to data model
            val dataServer =
                    com.ai.assistance.operit.data.mcp.MCPServer(
                            id = server.id,
                            name = server.name,
                            description = server.description,
                            logoUrl = server.logoUrl,
                            stars = server.stars,
                            category = server.category,
                            requiresApiKey = server.requiresApiKey,
                            author = server.author,
                            isVerified = server.isVerified,
                            isInstalled = server.isInstalled,
                            version = server.version,
                            updatedAt = server.updatedAt,
                            longDescription = server.longDescription,
                            repoUrl = server.repoUrl
                    )

            // Perform physical installation from zip
            val result = mcpInstaller.installPluginFromZip(dataServer, zipUri, progressCallback)

            if (result is InstallResult.Success) {
                // Scan installed plugins after successful installation
                scanInstalledPlugins()
                
                // Clear plugin info cache
                mcpInstaller.clearPluginInfoCache()
                
                Log.d(TAG, "Plugin $pluginId installed successfully from zip")
            } else {
                Log.e(TAG, "Failed to install plugin $pluginId from zip")
            }

            return result
        } catch (e: Exception) {
            Log.e(TAG, "Exception while installing plugin $pluginId from zip", e)
            return InstallResult.Error("Installation error: ${e.message}")
        }
    }

    /**
     * Uninstalls an MCP plugin
     *
     * @param pluginId The ID of the plugin to uninstall
     * @return true if uninstallation was successful, false otherwise
     */
    suspend fun uninstallPlugin(pluginId: String): Boolean {
        try {
            // Perform physical uninstallation
            val success = mcpInstaller.uninstallPlugin(pluginId)

            if (success) {
                // Rescan installed plugins after successful uninstallation
                scanInstalledPlugins()
                
                // Clear plugin info cache
                mcpInstaller.clearPluginInfoCache()
                
                Log.d(TAG, "Plugin $pluginId uninstalled successfully")
            } else {
                Log.e(TAG, "Failed to uninstall plugin $pluginId")
            }

            return success
        } catch (e: Exception) {
            Log.e(TAG, "Exception while uninstalling plugin $pluginId", e)
            return false
        }
    }

    /**
     * Updates the installed status of a list of servers
     *
     * @param servers The list of servers to update
     * @return A new list with updated installation status
     */
    fun updateInstalledStatus(servers: List<MCPServer>): List<MCPServer> {
        val installedIds = _installedPluginIds.value
        return servers.map { server ->
            val isInstalled = installedIds.contains(server.id)
            if (server.isInstalled != isInstalled) {
                server.copy(isInstalled = isInstalled)
            } else {
                server
            }
        }
    }

    /**
     * Checks if a plugin is installed
     *
     * @param pluginId The ID of the plugin to check
     * @return true if the plugin is installed, false otherwise
     */
    fun isPluginInstalled(pluginId: String): Boolean {
        return mcpInstaller.isPluginInstalled(pluginId)
    }

    /**
     * Gets the installed path of a plugin
     *
     * @param pluginId The ID of the plugin
     * @return The installed path or null if not installed
     */
    fun getInstalledPluginPath(pluginId: String): String? {
        return mcpInstaller.getInstalledPluginPath(pluginId)
    }

    /**
     * Gets the installed plugin info including original metadata
     *
     * @param pluginId The ID of the plugin
     * @return The installed plugin info or null if not installed
     */
    fun getInstalledPluginInfo(pluginId: String): MCPInstaller.InstalledPluginInfo? {
        return mcpInstaller.getInstalledPluginInfo(pluginId)
    }

    /**
     * Gets the original plugin name from metadata if available
     *
     * @param pluginId The ID of the plugin
     * @return The original name or null if metadata not found
     */
    fun getOriginalPluginName(pluginId: String): String? {
        return mcpInstaller.getInstalledPluginInfo(pluginId)?.getOriginalName()
    }

    /**
     * Gets all installed plugins with original metadata restored where available
     *
     * @param allServers List of all known servers
     * @return List of installed servers with original metadata
     */
    fun getInstalledPlugins(allServers: List<MCPServer>): List<MCPServer> {
        val installedIds = _installedPluginIds.value
        return allServers.filter { installedIds.contains(it.id) }.map { server ->
            // Try to restore original metadata for better display
            val pluginInfo = mcpInstaller.getInstalledPluginInfo(server.id)
            if (pluginInfo?.metadata != null) {
                // If we have metadata, use the original name and description
                server.copy(
                        name = pluginInfo.getOriginalName() ?: server.name,
                        description = pluginInfo.getOriginalDescription() ?: server.description
                )
            } else {
                server
            }
        }
    }

    /**
     * Cleans up orphaned plugins (empty directories)
     *
     * @return Number of cleaned plugins
     */
    suspend fun cleanupOrphanedPlugins(): Int {
        try {
            val pluginsBaseDir = mcpInstaller.pluginsBaseDir
            var removed = 0

            if (pluginsBaseDir.exists() && pluginsBaseDir.isDirectory) {
                pluginsBaseDir.listFiles()?.forEach { pluginDir ->
                    if (pluginDir.isDirectory) {
                        // Check if directory is empty or only contains zero-length files
                        val files = pluginDir.listFiles() ?: emptyArray()
                        if (files.isEmpty() || files.all { it.isFile && it.length() == 0L }) {
                            // Empty directory or only contains zero-length files, can be deleted
                            if (pluginDir.deleteRecursively()) {
                                removed++
                                Log.d(TAG, "Deleted empty plugin directory: ${pluginDir.name}")
                            }
                        }
                    }
                }
            }

            // If any plugins were removed, rescan
            if (removed > 0) {
                scanInstalledPlugins()
                Log.d(TAG, "Cleaned up $removed orphaned plugin records")
            }

            return removed
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clean up orphaned plugins", e)
            return 0
        }
    }
}
