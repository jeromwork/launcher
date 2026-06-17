package com.launcher.api.wizard

import kotlinx.coroutines.flow.Flow

interface UserPreferencesStore {
    suspend fun save(prefs: UserPreferences)
    fun observe(): Flow<UserPreferences>
    suspend fun current(): UserPreferences
    suspend fun markWizardCompleted(appFamilyId: String)
    suspend fun isWizardCompleted(appFamilyId: String): Boolean
}
