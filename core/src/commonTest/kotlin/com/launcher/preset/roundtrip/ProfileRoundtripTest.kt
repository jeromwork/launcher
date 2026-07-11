package com.launcher.preset.roundtrip

import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.model.Profile
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileRoundtripTest {

    @Test
    fun profileWithSnapshot_roundtrip() {
        val pool = mvpPool()
        val preset = simpleLauncherPreset()
        val base = ProfileFactory().create(preset, pool)
        val withSnapshot = base.copy(
            preWizardSnapshot = base.copy(preWizardSnapshot = null),
            snapshotTimestamp = 1720614400000L,
        )
        val encoded = testJson.encodeToString(Profile.serializer(), withSnapshot)
        val decoded = testJson.decodeFromString(Profile.serializer(), encoded)
        assertEquals(withSnapshot, decoded)
    }
}
