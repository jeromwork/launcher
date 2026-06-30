package com.launcher.app.preset

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.adapters.preset.PresetSelectionService
import com.launcher.adapters.preset.PresetSwitchService
import com.launcher.api.preset.PresetRef
import com.launcher.api.profile.Grid
import com.launcher.api.profile.Layout
import com.launcher.api.profile.ProfileData
import com.launcher.api.profile.ProfileStore
import com.launcher.api.profile.Screen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * Spec T67F coverage (US-2, SC-002) via service layer.
 *
 * Verifies: select simple-launcher → seed marker ProfileData → switch to
 * workspace → switch back to simple-launcher → marker layout is restored
 * (FR-014 history preservation).
 */
@RunWith(AndroidJUnit4::class)
class PresetSwitchE2ETest {

    @Before
    fun ensureKoinReady() {
        if (GlobalContext.getOrNull() == null) {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            app.javaClass.getMethod("onCreate").invoke(app)
        }
    }

    @Test
    fun switchBackRestoresMarkerLayout() = runBlocking {
        val koin = GlobalContext.get()
        val selection: PresetSelectionService = koin.get()
        val switch: PresetSwitchService = koin.get()
        val store: ProfileStore = koin.get()

        store.setActive(null)

        // 1. Select simple-launcher.
        selection.beginSetup("simple-launcher")
        val refA = PresetRef("com.launcher.preset.simple-launcher", 1)

        // 2. Replace ProfileData with marker layout we can identify after the
        //    round-trip.
        val markerLayout = Layout(
            screens = listOf(Screen(id = "marker-screen", grid = Grid(7, 7))),
        )
        store.putProfile(refA, ProfileData(layout = markerLayout))

        // 3. Switch to workspace — fresh CopyOnActivate.
        val toB = switch.switchTo("workspace") as PresetSwitchService.SwitchOutcome.Switched
        assertEquals(false, toB.restored)

        // 4. Switch back to simple-launcher — marker layout must come back
        //    from history.
        val back = switch.switchTo("simple-launcher") as PresetSwitchService.SwitchOutcome.Switched
        assertEquals(true, back.restored)
        assertEquals(markerLayout, back.profile.layout)
        assertTrue(store.getActive()!!.first.uid == refA.uid)
    }
}
