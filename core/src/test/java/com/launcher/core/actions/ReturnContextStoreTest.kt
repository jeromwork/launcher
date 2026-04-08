package com.launcher.core.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.ReturnContextRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReturnContextStoreTest {

    @Test
    fun saveLoadAndClearLifecycle() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ReturnContextStore(context)
        store.clear()
        assertNull(store.load())

        val first = ReturnContextRecord(
            initiatingTileRef = "tile_a",
            homeSurfaceRef = "home_main",
            actionCycleRef = "cycle-1",
            savedAtEpochMs = 100L,
        )
        store.save(first)
        assertEquals("cycle-1", store.load()?.actionCycleRef)

        val replacement = first.copy(actionCycleRef = "cycle-2", savedAtEpochMs = 200L)
        store.save(replacement)
        assertEquals("cycle-2", store.load()?.actionCycleRef)

        store.clear()
        assertNull(store.load())
    }
}
