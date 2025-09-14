package com.ai.assistance.operit.core.tools.system

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.ai.assistance.operit.util.GithubReleaseUtil
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

object OperitTerminalManager {
    const val PACKAGE_NAME = "com.ai.assistance.operit.terminal"
    private const val REPO_OWNER = "AAswordman"
    private const val REPO_NAME = "OperitTerminal"
    private const val TAG = "OperitTerminalManager"

    data class ReleaseInfo(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String
    )

    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getInstalledVersion(context: Context): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(PACKAGE_NAME, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    suspend fun fetchLatestReleaseInfo(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val releaseInfo = GithubReleaseUtil.fetchLatestReleaseInfo(REPO_OWNER, REPO_NAME)
        releaseInfo?.let {
            ReleaseInfo(it.version, it.downloadUrl, it.releaseNotes)
        }
    }
} 