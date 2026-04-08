package com.launcher.app

import android.view.View
import com.launcher.api.ReturnContextRecord
import com.launcher.core.actions.ReturnContextStore
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeReturnRestoreFlowTest {

    @Test
    fun fallbackRestoreShowsWarning() {
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        val store = ReturnContextStore(activity)
        store.save(
            ReturnContextRecord(
                initiatingTileRef = "tile_anna",
                homeSurfaceRef = "other_home",
                actionCycleRef = "cycle-restore",
                savedAtEpochMs = System.currentTimeMillis(),
            ),
        )

        val recreated = Robolectric.buildActivity(HomeActivity::class.java).setup().resume().get()
        val warning = recreated.findViewById<View>(R.id.warning_root)
        assertEquals(View.VISIBLE, warning.visibility)
    }
}
