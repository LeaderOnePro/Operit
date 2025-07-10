package com.ai.assistance.operit.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.serialization.serializer

// 定义一个用于UI偏好设置的DataStore实例
val Context.uiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "ui_preferences")

@Suppress("UNCHECKED_CAST")
@Composable
inline fun <reified T> rememberLocal(
    key: String,
    defaultValue: T
): MutableState<T> {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val state = remember(key) { mutableStateOf(defaultValue) }

    LaunchedEffect(key) {
        val preferences = context.uiPreferencesDataStore.data.first()
        state.value = when (defaultValue) {
            is Boolean -> (preferences[booleanPreferencesKey(key)] ?: defaultValue) as T
            is Int -> (preferences[intPreferencesKey(key)] ?: defaultValue) as T
            is Long -> (preferences[longPreferencesKey(key)] ?: defaultValue) as T
            is Float -> (preferences[floatPreferencesKey(key)] ?: defaultValue) as T
            is String -> (preferences[stringPreferencesKey(key)] ?: defaultValue) as T
            else -> {
                val json = preferences[stringPreferencesKey(key)]
                if (json != null) {
                    try {
                        Json.decodeFromString(serializer(), json)
                    } catch (e: Exception) {
                        defaultValue
                    }
                } else {
                    defaultValue
                }
            }
        }
    }

    return remember(state, coroutineScope) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(newValue) {
                    if (state.value != newValue) {
                        state.value = newValue
                        coroutineScope.launch {
                            context.uiPreferencesDataStore.edit { preferences ->
                                when (newValue) {
                                    is Boolean -> preferences[booleanPreferencesKey(key)] = newValue
                                    is Int -> preferences[intPreferencesKey(key)] = newValue
                                    is Long -> preferences[longPreferencesKey(key)] = newValue
                                    is Float -> preferences[floatPreferencesKey(key)] = newValue
                                    is String -> preferences[stringPreferencesKey(key)] = newValue
                                    else -> {
                                        val json = Json.encodeToString(serializer(), newValue)
                                        preferences[stringPreferencesKey(key)] = json
                                    }
                                }
                            }
                        }
                    }
                }

            override fun component1(): T = value
            override fun component2(): (T) -> Unit = { value = it }
        }
    }
} 