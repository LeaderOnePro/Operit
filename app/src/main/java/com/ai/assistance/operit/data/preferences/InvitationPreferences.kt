package com.ai.assistance.operit.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

// Create the DataStore instance using the extension delegate
private val Context.invitationDataStore: DataStore<Preferences> by preferencesDataStore(name = "invitation_prefs")

/**
 * Manages invitation-related data stored in DataStore.
 */
class InvitationRepository(private val context: Context) {

    private object PreferencesKeys {
        val INVITATION_COUNT = intPreferencesKey("invitation_count")
        val IS_INVITED = booleanPreferencesKey("is_invited") // Track if the device has been invited
        val LAST_USED_INVITATION_CODE = stringPreferencesKey("last_used_invitation_code") // Store the last successful code
        val SENT_INVITATION_TO_DEVICE_IDS = stringSetPreferencesKey("sent_invitation_to_device_ids") // Store IDs of devices this user has invited
    }

    /**
     * A flow that emits the current invitation count whenever it changes.
     */
    val invitationCountFlow: Flow<Int> = context.invitationDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.INVITATION_COUNT] ?: 0
        }

    /**
     * A flow that emits `true` if the workspace feature is unlocked (invitation count >= 1).
     */
    val isWorkspaceUnlockedFlow: Flow<Boolean> = flowOf(true)

    /**
     * A flow that emits `true` if the floating window feature is unlocked (invitation count >= 2).
     */
    val isFloatingWindowUnlockedFlow: Flow<Boolean> = flowOf(true)

    /**
     * A flow that emits `true` if the device has already been successfully invited.
     */
    val isInvitedFlow: Flow<Boolean> = context.invitationDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.IS_INVITED] ?: false
        }

    /**
     * A flow that emits the last invitation code that was successfully used on this device.
     */
    val lastUsedInvitationCodeFlow: Flow<String?> = context.invitationDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LAST_USED_INVITATION_CODE]
        }

    /**
     * A flow that emits the set of device IDs that this user has successfully invited.
     */
    val sentInvitationToDeviceIdsFlow: Flow<Set<String>> = context.invitationDataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.SENT_INVITATION_TO_DEVICE_IDS] ?: emptySet()
        }

    /**
     * Increments the invitation count by one.
     */
    suspend fun incrementInvitationCount() {
        context.invitationDataStore.edit { settings ->
            val currentCount = settings[PreferencesKeys.INVITATION_COUNT] ?: 0
            settings[PreferencesKeys.INVITATION_COUNT] = currentCount + 1
        }
    }

    /**
     * Marks the device as having been invited.
     */
    suspend fun setDeviceAsInvited() {
        context.invitationDataStore.edit { settings ->
            settings[PreferencesKeys.IS_INVITED] = true
        }
    }

    /**
     * Stores the invitation code that was just used to invite this device.
     */
    suspend fun setLastUsedInvitationCode(code: String) {
        context.invitationDataStore.edit { settings ->
            settings[PreferencesKeys.LAST_USED_INVITATION_CODE] = code
        }
    }

    /**
     * Adds a device ID to the set of successfully invited devices.
     */
    suspend fun addSentInvitation(deviceId: String) {
        context.invitationDataStore.edit { settings ->
            val currentSet = settings[PreferencesKeys.SENT_INVITATION_TO_DEVICE_IDS] ?: emptySet()
            settings[PreferencesKeys.SENT_INVITATION_TO_DEVICE_IDS] = currentSet + deviceId
        }
    }
} 