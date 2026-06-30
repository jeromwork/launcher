package com.launcher.app.ui.recovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test for [RecoveryFallbackScreen] (T651, FR-016, US-3 acceptance).
 *
 * **Status**: `[deferred-local-emulator]` — same constraints as T649.
 *
 * **Covers** (skeleton — fill in when AVD is online):
 *  - Renders correct headline per FallbackReason (TOO_MANY_ATTEMPTS,
 *    MALFORMED_VAULT, NO_VAULT).
 *  - Destructive "set up as new device" button visible.
 *  - Tapping destructive button shows a confirmation dialog before firing
 *    the onSetupAsNewDevice callback.
 *  - Cancel in dialog does NOT fire the callback.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryFallbackScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun fallbackScreen_tooManyAttemptsTitleVisible() {
        composeRule.setContent {
            RecoveryFallbackScreen(
                reason = RecoveryViewModel.FallbackReason.TOO_MANY_ATTEMPTS,
                onSetupAsNewDevice = { /* no-op */ },
                onRetry = { /* no-op */ },
            )
        }

        composeRule.onNodeWithContentDescription("Слишком много неверных попыток")
            .assertIsDisplayed()
    }

    // TODO(local-emulator): cover MALFORMED_VAULT / NO_VAULT titles + dialog
    //   confirmation flow when AVD is online.
}
