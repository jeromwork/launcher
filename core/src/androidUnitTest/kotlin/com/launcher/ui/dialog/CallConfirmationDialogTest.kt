package com.launcher.ui.dialog

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.launcher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spec 010 T062 — verifies the call confirmation dialog renders all
 * mandatory elements (FR-011 layout, FR-015 invalid number, FR-016 cancel
 * has no side effects).
 *
 * Full TalkBack walkthrough (CHK-accessibility-011 — CANCEL focused FIRST)
 * is an emulator-only check tracked as TODO-SPEC010-EMU-005 in the project
 * backlog; the `traversalIndex = -1f` semantics modifier on CANCEL is
 * verified structurally в [CallConfirmationDialog] source via the Konsist
 * gate in Phase 8.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Wide+tall viewport чтобы senior-safe layout (≥56dp buttons + photo + body)
// fits without scrolling; click handlers don't fire on off-screen nodes.
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class CallConfirmationDialogTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_name_and_number_and_both_buttons() {
        rule.setContent {
            LauncherTheme(preset = null) {
                CallConfirmationDialog(
                    displayName = "Маша",
                    formattedNumber = "+7 916 123-45-67",
                    photoUrl = null,
                    cancelLabel = "Отмена",
                    callLabel = "Позвонить",
                    invalidNumberMessage = "Номер некорректен",
                    onCancel = {},
                    onCall = {},
                )
            }
        }
        rule.onNodeWithText("Маша").assertExists()
        rule.onNodeWithText("+7 916 123-45-67").assertExists()
        rule.onNodeWithText("Отмена").assertExists()
        rule.onNodeWithText("Позвонить").assertExists()
    }

    @Test
    fun cancel_button_triggers_cancel_callback() {
        var cancelCount = 0
        var callCount = 0
        rule.setContent {
            LauncherTheme(preset = null) {
                CallConfirmationDialog(
                    displayName = "Маша",
                    formattedNumber = "+7 916 123-45-67",
                    photoUrl = null,
                    cancelLabel = "Отмена",
                    callLabel = "Позвонить",
                    invalidNumberMessage = "Номер некорректен",
                    onCancel = { cancelCount++ },
                    onCall = { callCount++ },
                )
            }
        }
        rule.onNodeWithText("Отмена").performClick()
        rule.waitForIdle()
        assertEquals(1, cancelCount)
        assertEquals(0, callCount) // FR-016 — cancel has no side effects.
    }

    @Test
    fun call_button_triggers_call_callback() {
        var callCount = 0
        rule.setContent {
            LauncherTheme(preset = null) {
                CallConfirmationDialog(
                    displayName = "Иван",
                    formattedNumber = "+1 415-555-0123",
                    photoUrl = null,
                    cancelLabel = "Отмена",
                    callLabel = "Позвонить",
                    invalidNumberMessage = "Номер некорректен",
                    onCancel = {},
                    onCall = { callCount++ },
                )
            }
        }
        rule.onNodeWithText("Позвонить").performClick()
        rule.waitForIdle()
        assertEquals(1, callCount)
    }

    @Test
    fun invalid_number_disables_call_and_shows_helper_text() {
        var callCount = 0
        rule.setContent {
            LauncherTheme(preset = null) {
                CallConfirmationDialog(
                    displayName = "Маша",
                    formattedNumber = "garbage",
                    photoUrl = null,
                    cancelLabel = "Отмена",
                    callLabel = "Позвонить",
                    invalidNumberMessage = "Номер некорректен",
                    onCancel = {},
                    onCall = { callCount++ },
                    numberIsValid = false,
                )
            }
        }
        rule.onNodeWithText("Номер некорректен").assertExists()
        // Click on a disabled button does not propagate.
        rule.onNodeWithText("Позвонить").performClick()
        rule.waitForIdle()
        assertEquals(0, callCount)
    }

    @Test
    fun photo_null_falls_back_to_initials() {
        rule.setContent {
            LauncherTheme(preset = null) {
                CallConfirmationDialog(
                    displayName = "Анна Петрова",
                    formattedNumber = "+7 916 000-00-00",
                    photoUrl = null,
                    cancelLabel = "Отмена",
                    callLabel = "Позвонить",
                    invalidNumberMessage = "Номер некорректен",
                    onCancel = {},
                    onCall = {},
                )
            }
        }
        // "Анна Петрова" → "АП"
        rule.onNodeWithText("АП").assertExists()
    }
}
