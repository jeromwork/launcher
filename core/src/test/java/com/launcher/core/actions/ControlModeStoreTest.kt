package com.launcher.core.actions

import androidx.test.core.app.ApplicationProvider
import com.launcher.api.ControlMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ControlModeStoreTest {

    @Test
    fun persistsAndLoadsMode() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = ControlModeStore(context, defaultMode = ControlMode.STANDARD)

        store.set(ControlMode.STRICT)

        assertEquals(ControlMode.STRICT, store.get())
    }
}
