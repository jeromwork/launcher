package com.launcher.core.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.CommunicationActionType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunicationConfigValidatorTest {

    @Test
    fun unsupportedPairIsRejectedAtSetupTime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val validator = CommunicationConfigValidator(context)

        assertFalse(
            validator.isActionSupported(
                contactRef = "contact_oleg",
                actionType = CommunicationActionType.VIDEO,
            ),
        )
        assertTrue(
            validator.isActionSupported(
                contactRef = "contact_anna",
                actionType = CommunicationActionType.CALL,
            ),
        )
    }
}

