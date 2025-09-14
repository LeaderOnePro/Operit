package com.ai.assistance.operit.util

import android.util.Log
import com.ai.assistance.operit.data.updates.UpdateManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object GithubReleaseUtil {
    private const val TAG = "GithubReleaseUtil"

    data class ReleaseInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String,
        val releasePageUrl: String
    )

    private data class GithubRelease(
        val tag_name: String,
        val name: String,
        val body: String,
        val assets: List<Asset>,
        val html_url: String
    )

    private data class Asset(
        val name: String,
        val browser_download_url: String
    )

    // 可用的GitHub加速镜像站点列表
    private val GITHUB_MIRRORS = mapOf(
        "Ghfast" to "https://ghfast.top/",         // 目前国内可访问的最佳选择
        "GitMirror" to "https://hub.gitmirror.com/",  // 备选源
        "Moeyy" to "https://github.moeyy.xyz/",   // 另一个备选
        "Workers" to "https://github.abskoop.workers.dev/"  // 最后的备选
    )

    suspend fun fetchLatestReleaseInfo(repoOwner: String, repoName: String): ReleaseInfo? = withContext(Dispatchers.IO) {
        val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        try {
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val gson = Gson()
                val jsonResponse = gson.fromJson(response, GithubRelease::class.java)

                val tagName = jsonResponse.tag_name
                val version = tagName.removePrefix("v")

                val apkAsset = jsonResponse.assets.find { it.name.endsWith(".apk") }
                val downloadUrl = apkAsset?.browser_download_url

                ReleaseInfo(version, downloadUrl ?: jsonResponse.html_url, jsonResponse.body, jsonResponse.html_url)
            } else {
                Log.e(TAG, "Failed to get release info for $repoOwner/$repoName. HTTP Code: ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching latest release info for $repoOwner/$repoName", e)
            null
        }
    }

    fun getMirroredUrls(originalUrl: String): Map<String, String> {
        if (!originalUrl.contains("github.com") || !originalUrl.endsWith(".apk")) {
            return emptyMap()
        }

        return GITHUB_MIRRORS.mapValues { entry ->
            "${entry.value}$originalUrl"
        }
    }
} 