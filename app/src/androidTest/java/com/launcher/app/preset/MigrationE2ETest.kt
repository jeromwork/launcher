package com.launcher.app.preset

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.adapters.profile.PreferencesProfileStore
import com.launcher.api.profile.ProfileStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Spec T67G coverage (US-3, SC-003, FR-015) — legacy migration.
 *
 * Seeds the pre-TASK-65 `wizard_done` marker into the production
 * PreferencesProfileStore via its test-only seam, then verifies `load()`
 * synthesizes `activePresetRef = PresetRef(simple-launcher, 1)` (idempotent
 * on second call).
 */
@RunWith(AndroidJUnit4::class)
class MigrationE2ETest {

    @Before
    fun ensureKoinReady() {
        if (GlobalContext.getOrNull() == null) {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            app.javaClass.getMethod("onCreate").invoke(app)
        }
    }

    @Test
    fun legacyWizardDoneSynthesizesSimpleLauncherActiveRef() = runBlocking {
        val store: ProfileStore = GlobalContext.get().get()
        require(store is PreferencesProfileStore)

        store.seedLegacyWizardDoneForTest()

        val state = store.load()
        assertNotNull("migration must synthesize activePresetRef", state.activePresetRef)
        assertEquals("com.launcher.preset.simple-launcher", state.activePresetRef!!.uid)
        assertEquals(1, state.activePresetRef!!.version)

        // Idempotent — second load reuses synthesized state.
        val again = store.load()
        assertEquals(state, again)
    }
}
