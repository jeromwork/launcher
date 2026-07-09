package com.launcher.api.wizard

import kotlinx.coroutines.flow.Flow

interface UserPreferencesStore {
    suspend fun save(prefs: UserPreferences)
    fun observe(): Flow<UserPreferences>
    suspend fun current(): UserPreferences
    suspend fun markWizardCompleted(presetId: String)
    suspend fun isWizardCompleted(presetId: String): Boolean
}
