package com.launcher.app.ui.recovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test for [RecoveryPassphraseSetupScreen] (T649, FR-014, US-1 acceptance).
 *
 * **Status**: `[deferred-local-emulator]` — Compose UI test depends on
 * `androidx.compose.ui:ui-test-junit4` and AVD with API ≤ 34 (per memory
 * `reference_compose_ui_test_api_mismatch`). Owner runs on
 * pixel_5_api_34 emulator manually.
 *
 * **Covers** (skeleton — fill in when runtime is available):
 *  - Two password fields visible (new + confirm).
 *  - Empty / < 8-char / mismatched passphrase disables the submit button.
 *  - Valid + matching passphrase enables submit; tap surfaces the value.
 *  - Cancel button surfaces empty CharArray (FR-014 cancellation contract).
 *  - All interactive elements ≥ 56dp (senior-safe tap target).
 */
@RunWith(AndroidJUnit4::class)
class RecoveryPassphraseSetupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun setupScreen_displaysBothPasswordFields() {
        composeRule.setContent {
            RecoveryPassphraseSetupScreen(
                onSubmit = { /* no-op */ },
                onCancel = { /* no-op */ },
            )
        }

        composeRule.onNodeWithContentDescription("Поле для нового пароля")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Подтверждение пароля")
            .assertIsDisplayed()
    }

    // TODO(local-emulator): fill in mismatch / min-length / submit-callback /
    //   cancel-callback / tap-target-size assertions when the AVD is online.
}
