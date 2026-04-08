package com.launcher.app

import android.view.View
import android.widget.Button
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeWhatsAppLaunchFlowTest {

    @Test
    fun tileTapShowsConfirmation() {
        val controller: ActivityController<HomeActivity> = Robolectric.buildActivity(HomeActivity::class.java)
        val activity = controller.setup().get()

        val callButton = activity.findViewById<Button>(R.id.call_button)
        callButton.performClick()

        val confirmation = activity.findViewById<View>(R.id.confirmation_root)
        assertEquals(View.VISIBLE, confirmation.visibility)
    }
}
