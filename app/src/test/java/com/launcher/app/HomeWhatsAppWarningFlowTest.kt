package com.launcher.app

import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Robolectric

class HomeWhatsAppWarningFlowTest {

    @Test
    fun unavailableLaunchShowsWarning() {
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        activity.findViewById<Button>(R.id.call_button).performClick()
        activity.findViewById<Button>(R.id.confirm_button).performClick()

        val warning = activity.findViewById<View>(R.id.warning_root)
        assertEquals(View.VISIBLE, warning.visibility)
    }
}

