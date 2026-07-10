package com.launcher.preset.port

import com.launcher.preset.model.Profile
import kotlinx.coroutines.flow.Flow

interface ProfileStore {
    fun observe(): Flow<Profile?>
    suspend fun load(): Profile?
    suspend fun save(profile: Profile)
    suspend fun setPreWizardSnapshot(snapshot: Profile)
    suspend fun restoreFromPreWizardSnapshot(): Profile?
}
