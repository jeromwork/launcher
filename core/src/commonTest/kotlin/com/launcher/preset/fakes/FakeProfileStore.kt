package com.launcher.preset.fakes

import com.launcher.preset.model.Profile
import com.launcher.preset.port.ProfileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeProfileStore(initial: Profile? = null) : ProfileStore {
    private val state = MutableStateFlow(initial)

    override fun observe(): Flow<Profile?> = state.asStateFlow()
    override suspend fun load(): Profile? = state.value
    override suspend fun save(profile: Profile) {
        state.value = profile
    }

    override suspend fun setPreWizardSnapshot(snapshot: Profile) {
        val curr = state.value ?: return
        val stripped = snapshot.copy(preWizardSnapshot = null)
        state.value = curr.copy(preWizardSnapshot = stripped)
    }

    override suspend fun restoreFromPreWizardSnapshot(): Profile? {
        val snapshot = state.value?.preWizardSnapshot ?: return state.value
        state.value = snapshot
        return snapshot
    }
}
