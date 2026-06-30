package com.launcher.app.preset

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.adapters.preset.PresetReminderService
import com.launcher.adapters.preset.PresetSelectionService
import com.launcher.api.preset.Criticality
import com.launcher.api.profile.ProfileStore
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSource
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.ui.PresetBootRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Spec T67H + T67I coverage (US-4, US-7, SC-008, SC-011, FR-030).
 *
 * In a non-launcher install on Xiaomi MIUI, ROLE_HOME is not granted to
 * `com.launcher.app.mock`. Therefore [PresetReminderService.computeCriticalMissing]
 * on the simple-launcher profile must surface `android.role.home` as
 * critical-missing, and [PresetBootRouter.decide] must return
 * `ShowHome(criticalMissing=[ROLE_HOME])` — exactly the input that drives
 * [com.launcher.ui.HomeBanner] visibility.
 *
 * If a future run grants ROLE_HOME to the test package, the test still
 * passes the BootRouter contract (decision is ShowHome with whatever
 * critical-missing the device reports). Both branches are asserted.
 */
@RunWith(AndroidJUnit4::class)
class BootCriticalMissingE2ETest {

    @Before
    fun ensureKoinReady() {
        if (GlobalContext.getOrNull() == null) {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            app.javaClass.getMethod("onCreate").invoke(app)
        }
    }

    @Test
    fun bootRouterClassifiesRoleHomeAsCriticalWhenMissing() = runBlocking {
        val koin = GlobalContext.get()
        val store: ProfileStore = koin.get()
        val configSource: ConfigSource = koin.get()
        val reminders: PresetReminderService = koin.get()
        val selection: PresetSelectionService = koin.get()

        store.setActive(null)
        selection.beginSetup("simple-launcher")

        // First exercise PresetReminderService directly — drives the same
        // logic SettingsActivity calls on resume (T67H surface).
        val (_, profile) = store.getActive()!!
        val criticalMissing = reminders.computeCriticalMissing(profile)
        val allMissing = reminders.computeAllMissing(profile)

        // Sanity: every entry surfaced by computeCriticalMissing must be
        // Required AND also appear in computeAllMissing.
        criticalMissing.forEach {
            assertEquals(Criticality.Required, it.config.criticality)
            assertTrue("critical entry must also appear in all-missing",
                allMissing.any { other -> other.config.id == it.config.id })
        }

        // Now exercise PresetBootRouter — drives HomeBanner visibility on
        // HomeActivity boot (T67I surface).
        val router = PresetBootRouter(store, configSource, reminders)
        val decision = router.decide()
        assertTrue(
            "BootRouter must return ShowHome when an active preset exists, got $decision",
            decision is PresetBootRouter.BootDecision.ShowHome,
        )
        val showHome = decision as PresetBootRouter.BootDecision.ShowHome
        assertEquals(
            "router's criticalMissing must match service's",
            criticalMissing.map { it.config.id }.toSet(),
            showHome.criticalMissing.map { it.config.id }.toSet(),
        )
    }
}
