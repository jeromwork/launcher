package com.launcher.app.preset

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.launcher.api.profile.ProfileStore
import kotlinx.coroutines.flow.first
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
 * Seeds the pre-TASK-65 `wizard_done` marker into the same DataStore the
 * production PreferencesProfileStore reads, then verifies the store
 * synthesizes `activePresetRef = PresetRef(simple-launcher, 1)` on first
 * `load()` call (idempotent).
 *
 * The DataStore name MUST match the production adapter
 * (`"profile_store"` per [com.launcher.adapters.profile.PreferencesProfileStore]).
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

    private val Context.profileDataStore: DataStore<Preferences> by preferencesDataStore(
        name = "profile_store",
    )

    @Test
    fun legacyWizardDoneSynthesizesSimpleLauncherActiveRef() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val store: ProfileStore = GlobalContext.get().get()

        // Reset to clean state, then seed the legacy marker.
        store.setActive(null)
        // setActive saves a state that has activePresetRef=null but profiles
        // may be non-empty — for migration to fire we need to clear store
        // entirely. Cheapest: write a fresh DataStore with only the legacy
        // marker, no profile.store.json blob.
        ctx.profileDataStore.edit { prefs ->
            prefs.clear()
            prefs[stringPreferencesKey("wizard_done")] = "true"
        }

        val state = store.load()
        assertNotNull("migration must synthesize activePresetRef", state.activePresetRef)
        assertEquals(
            "com.launcher.preset.simple-launcher",
            state.activePresetRef!!.uid,
        )
        assertEquals(1, state.activePresetRef!!.version)

        // Idempotent — second load must reuse the synthesized state.
        val again = store.load()
        assertEquals(state, again)
    }
}
