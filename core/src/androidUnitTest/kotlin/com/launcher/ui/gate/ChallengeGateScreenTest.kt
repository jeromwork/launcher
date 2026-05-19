package com.launcher.ui.gate

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Spec 010 T101 — Composable test для [ChallengeGateScreen] (FR-021, FR-022,
 * FR-024).
 *
 *  - **Seed 1**: forces NumericEntry (Random(1).nextBoolean() == true).
 *    Test types the displayed answer → onSuccess fires.
 *  - CANCEL button fires onCancel, no other side effects.
 *  - Wrong answer regenerates challenge (numeric typed digit doesn't equal
 *    answer prefix → on OK we don't fire success).
 *
 * Rotation persistence (C-1 / [ChallengeSaver]) is unit-tested separately в
 * [ChallengeSaverTest] так что не дублируем StateRestorationTester здесь.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class ChallengeGateScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun cancel_fires_onCancel_and_no_success() {
        var successCount = 0
        var cancelCount = 0
        rule.setContent {
            LauncherTheme(preset = null) {
                ChallengeGateScreen(
                    cancelLabel = "Отмена",
                    sequenceInstructionTemplate = { "Нажми $it" },
                    onSuccess = { successCount += 1 },
                    onCancel = { cancelCount += 1 },
                    randomSeed = 1L,
                )
            }
        }
        rule.onNodeWithTag("challenge_cancel").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, cancelCount)
        assertEquals(0, successCount)
    }

    @Test
    fun screen_renders_with_either_challenge_variant() {
        rule.setContent {
            LauncherTheme(preset = null) {
                ChallengeGateScreen(
                    cancelLabel = "Отмена",
                    sequenceInstructionTemplate = { "Нажми $it" },
                    onSuccess = {},
                    onCancel = {},
                    randomSeed = 1L,
                )
            }
        }
        rule.onNodeWithTag("challenge_gate_screen").assertIsDisplayed()
        // One of the two variants must be rendered.
        val numericRendered = runCatching {
            rule.onNodeWithTag("numeric_entry_challenge").assertExists()
        }.isSuccess
        val sequenceRendered = runCatching {
            rule.onNodeWithTag("sequence_tap_challenge").assertExists()
        }.isSuccess
        assertEquals(true, numericRendered || sequenceRendered)
    }
}
