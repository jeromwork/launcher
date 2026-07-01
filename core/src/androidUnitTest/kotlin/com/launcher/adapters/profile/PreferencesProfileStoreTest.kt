package com.launcher.adapters.profile

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.preset.Config as PresetConfig
import com.launcher.api.preset.Criticality
import com.launcher.api.preset.PresetRef
import com.launcher.api.profile.AppliedState
import com.launcher.api.profile.Layout
import com.launcher.api.profile.ProfileData
import com.launcher.api.profile.ProfileStoreState
import com.launcher.api.profile.SettingEntry
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PreferencesProfileStoreTest {

    private fun ctx() = ApplicationProvider.getApplicationContext<Application>()

    private val refA = PresetRef("com.launcher.preset.simple-launcher", 1)
    private val refB = PresetRef("com.launcher.preset.workspace", 2)

    private val cfg = PresetConfig(
        id = "ui.font.large",
        poolId = "ui-customization",
        poolVersion = 1,
        entryId = "ui.font.large",
        title = "t",
        description = "d",
        check = CheckSpec.UIFont(1.3f),
        apply = ApplySpec.InAppOnly,
        criticality = Criticality.Optional,
    )

    @Test
    fun roundtripsActiveRefAndProfilesMap() = runTest {
        val store = PreferencesProfileStore(ctx())
        val original = ProfileStoreState(
            activePresetRef = refA,
            profiles = mapOf(
                refA.toCompositeKey() to ProfileData(
                    layout = Layout.empty(),
                    settings = listOf(SettingEntry(cfg, AppliedState.Applied)),
                ),
                refB.toCompositeKey() to ProfileData(layout = Layout.empty()),
            ),
        )
        store.save(original)
        val loaded = store.load()
        assertEquals(original, loaded)
    }

    @Test
    fun getActiveReturnsRefAndDataPair() = runTest {
        val store = PreferencesProfileStore(ctx())
        val data = ProfileData(layout = Layout.empty())
        store.putProfile(refA, data)
        store.setActive(refA)
        val active = store.getActive()
        assertNotNull(active)
        assertEquals(refA, active!!.first)
        assertEquals(data, active.second)
    }

}
