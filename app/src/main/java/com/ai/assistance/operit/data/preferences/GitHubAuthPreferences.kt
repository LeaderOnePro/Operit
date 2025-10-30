package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ai.assistance.operit.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

private val Context.githubAuthDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "github_auth_preferences")

@Serializable
data class GitHubUser(
    @SerialName("id") val id: Long,
    @SerialName("login") val login: String,
    @SerialName("name") val name: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String,
    @SerialName("bio") val bio: String? = null,
    @SerialName("public_repos") val publicRepos: Int? = null,
    @SerialName("followers") val followers: Int? = null,
    @SerialName("following") val following: Int? = null
)

/**
 * GitHub认证偏好设置管理器
 * 负责管理GitHub OAuth认证状态、用户信息和访问令牌
 */
class GitHubAuthPreferences(private val context: Context) {

    companion object {
        // GitHub OAuth相关配置
        val GITHUB_CLIENT_ID = BuildConfig.GITHUB_CLIENT_ID
        const val GITHUB_SCOPE = "public_repo,user:email,read:user"
        const val GITHUB_REDIRECT_URI = "operit://github-oauth-callback"
        
        // 认证相关键
        private val IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")
        private val ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val TOKEN_TYPE = stringPreferencesKey("token_type")
        private val TOKEN_EXPIRES_AT = longPreferencesKey("token_expires_at")
        private val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val USER_INFO = stringPreferencesKey("user_info")
        private val LAST_LOGIN_TIME = longPreferencesKey("last_login_time")
        private val PKCE_CODE_VERIFIER = stringPreferencesKey("pkce_code_verifier")
        
        @Volatile
        private var INSTANCE: GitHubAuthPreferences? = null
        
        fun getInstance(context: Context): GitHubAuthPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GitHubAuthPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 登录状态Flow
    val isLoggedInFlow: Flow<Boolean> = context.githubAuthDataStore.data.map { preferences ->
        preferences[IS_LOGGED_IN] ?: false
    }

    // 访问令牌Flow
    val accessTokenFlow: Flow<String?> = context.githubAuthDataStore.data.map { preferences ->
        preferences[ACCESS_TOKEN]
    }

    // 用户信息Flow
    val userInfoFlow: Flow<GitHubUser?> = context.githubAuthDataStore.data.map { preferences ->
        val userInfoJson = preferences[USER_INFO]
        if (userInfoJson != null) {
            try {
                json.decodeFromString<GitHubUser>(userInfoJson)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // 最后登录时间Flow
    val lastLoginTimeFlow: Flow<Long> = context.githubAuthDataStore.data.map { preferences ->
        preferences[LAST_LOGIN_TIME] ?: 0L
    }

    /**
     * 保存认证信息
     */
    suspend fun saveAuthInfo(
        accessToken: String,
        tokenType: String = "bearer",
        expiresIn: Long? = null,
        refreshToken: String? = null,
        userInfo: GitHubUser
    ) {
        context.githubAuthDataStore.edit { preferences ->
            preferences[IS_LOGGED_IN] = true
            preferences[ACCESS_TOKEN] = accessToken
            preferences[TOKEN_TYPE] = tokenType
            preferences[USER_INFO] = json.encodeToString(userInfo)
            preferences[LAST_LOGIN_TIME] = System.currentTimeMillis()
            
            expiresIn?.let {
                preferences[TOKEN_EXPIRES_AT] = System.currentTimeMillis() + (it * 1000)
            }
            
            refreshToken?.let {
                preferences[REFRESH_TOKEN] = it
            }
        }
    }

    /**
     * 更新用户信息
     */
    suspend fun updateUserInfo(userInfo: GitHubUser) {
        context.githubAuthDataStore.edit { preferences ->
            preferences[USER_INFO] = json.encodeToString(userInfo)
        }
    }

    /**
     * 更新访问令牌
     */
    suspend fun updateAccessToken(
        accessToken: String,
        tokenType: String = "bearer",
        expiresIn: Long? = null
    ) {
        context.githubAuthDataStore.edit { preferences ->
            preferences[ACCESS_TOKEN] = accessToken
            preferences[TOKEN_TYPE] = tokenType
            
            expiresIn?.let {
                preferences[TOKEN_EXPIRES_AT] = System.currentTimeMillis() + (it * 1000)
            }
        }
    }

    /**
     * 检查令牌是否已过期
     */
    suspend fun isTokenExpired(): Boolean {
        val preferences = context.githubAuthDataStore.data.first()
        val expiresAt = preferences[TOKEN_EXPIRES_AT] ?: return false
        return System.currentTimeMillis() >= expiresAt
    }

    /**
     * 获取当前访问令牌
     */
    suspend fun getCurrentAccessToken(): String? {
        val preferences = context.githubAuthDataStore.data.first()
        return preferences[ACCESS_TOKEN]
    }

    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUserInfo(): GitHubUser? {
        val preferences = context.githubAuthDataStore.data.first()
        val userInfoJson = preferences[USER_INFO]
        return if (userInfoJson != null) {
            try {
                json.decodeFromString<GitHubUser>(userInfoJson)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    /**
     * 检查是否已登录
     */
    suspend fun isLoggedIn(): Boolean {
        val preferences = context.githubAuthDataStore.data.first()
        return preferences[IS_LOGGED_IN] ?: false
    }

    /**
     * 登出
     */
    suspend fun logout() {
        context.githubAuthDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * 保存Code Verifier
     */
    suspend fun saveCodeVerifier(codeVerifier: String) {
        context.githubAuthDataStore.edit { preferences ->
            preferences[PKCE_CODE_VERIFIER] = codeVerifier
        }
    }

    /**
     * 获取并清除Code Verifier
     */
    suspend fun getAndClearCodeVerifier(): String? {
        val preferences = context.githubAuthDataStore.data.first()
        val codeVerifier = preferences[PKCE_CODE_VERIFIER]
        if (codeVerifier != null) {
            context.githubAuthDataStore.edit {
                it.remove(PKCE_CODE_VERIFIER)
            }
        }
        return codeVerifier
    }

    /**
     * 生成GitHub OAuth授权URL
     */
    suspend fun getAuthorizationUrl(): String {
        val state = generateRandomState()
        val (codeVerifier, codeChallenge) = generatePkceChallenge()
        saveCodeVerifier(codeVerifier)

        return "https://github.com/login/oauth/authorize?" +
                "client_id=$GITHUB_CLIENT_ID&" +
                "redirect_uri=$GITHUB_REDIRECT_URI&" +
                "scope=$GITHUB_SCOPE&" +
                "state=$state&" +
                "code_challenge=$codeChallenge&" +
                "code_challenge_method=S256"
    }

    /**
     * 生成PKCE质询
     */
    private fun generatePkceChallenge(): Pair<String, String> {
        val sr = SecureRandom()
        val code = ByteArray(32)
        sr.nextBytes(code)
        val codeVerifier = Base64.encodeToString(code, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(codeVerifier.toByteArray())
        val codeChallenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        return Pair(codeVerifier, codeChallenge)
    }

    /**
     * 生成随机状态字符串
     */
    private fun generateRandomState(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..32)
            .map { chars.random() }
            .joinToString("")
    }

    /**
     * 获取访问令牌的授权头
     */
    suspend fun getAuthorizationHeader(): String? {
        val token = getCurrentAccessToken()
        return if (token != null) {
            "Bearer $token"
        } else {
            null
        }
    }
} 