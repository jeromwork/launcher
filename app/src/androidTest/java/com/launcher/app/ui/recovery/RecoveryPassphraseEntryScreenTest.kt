package com.launcher.app.ui.recovery

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI test for [RecoveryPassphraseEntryScreen] (T650, FR-015, SC-011).
 *
 * **Status**: `[deferred-local-emulator]` — same constraints as T649.
 *
 * **Covers** (skeleton — fill in when AVD is online):
 *  - Initial render (failedAttempts=0) shows passphrase field, no counter.
 *  - After 1 failed attempt — "Осталось попыток: 2" content description visible.
 *  - After 2 — "Осталось попыток: 1".
 *  - At 3 → onFallback callback fires (auto-nav to Fallback per FR-015).
 *  - Submit forwards CharArray to onSubmit.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryPassphraseEntryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun entryScreen_fieldVisibleOnFreshLaunch() {
        composeRule.setContent {
            RecoveryPassphraseEntryScreen(
                failedAttempts = 0,
                onSubmit = { /* no-op */ },
                onCancel = { /* no-op */ },
                onFallback = { /* no-op */ },
            )
        }

        composeRule.onNodeWithContentDescription("Поле для пароля восстановления")
            .assertIsDisplayed()
    }

    // TODO(local-emulator): fill in attempt-counter visibility transitions and
    //   auto-Fallback trigger when AVD is online.
}
