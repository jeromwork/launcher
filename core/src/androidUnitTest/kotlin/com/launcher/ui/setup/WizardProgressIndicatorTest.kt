package com.launcher.ui.setup

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.launcher.ui.theme.LauncherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spec 010 T049 — verifies «Шаг N из M» renders correctly for both
 * M=3 (API < 13) и M=4 (API 13+) wizard configurations.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class WizardProgressIndicatorTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_step_2_of_4_label() {
        rule.setContent {
            LauncherTheme(preset = null) {
                WizardProgressIndicator(
                    currentStep = 2,
                    totalSteps = 4,
                    progressLabelTemplate = "Шаг %1\$d из %2\$d",
                )
            }
        }
        rule.onNodeWithText("Шаг 2 из 4").assertIsDisplayed()
    }

    @Test
    fun renders_step_2_of_3_when_post_notifications_skipped() {
        rule.setContent {
            LauncherTheme(preset = null) {
                WizardProgressIndicator(
                    currentStep = 2,
                    totalSteps = 3,
                    progressLabelTemplate = "Шаг %1\$d из %2\$d",
                )
            }
        }
        rule.onNodeWithText("Шаг 2 из 3").assertIsDisplayed()
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejects_current_step_beyond_total() {
        rule.setContent {
            LauncherTheme(preset = null) {
                WizardProgressIndicator(
                    currentStep = 5,
                    totalSteps = 4,
                    progressLabelTemplate = "Шаг %1\$d из %2\$d",
                )
            }
        }
    }
}
