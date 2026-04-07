package com.launcher.app

import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Robolectric

class HomeCancelFlowTest {

    @Test
    fun cancelHidesConfirmationAndKeepsHomeStable() {
        val activity = Robolectric.buildActivity(HomeActivity::class.java).setup().get()
        activity.findViewById<Button>(R.id.video_button).performClick()
        activity.findViewById<Button>(R.id.cancel_button).performClick()

        val confirmation = activity.findViewById<View>(R.id.confirmation_root)
        assertEquals(View.GONE, confirmation.visibility)
    }
}

