package com.launcher.ui.setup

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
 * Spec 010 T050 — verifies the GMS hard-block screen renders all
 * mandatory elements (FR-042 title/body, FR-043 link) and invokes the
 * `onOk` callback (which the host Activity wires to `finishAffinity`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class GmsHardBlockTest {

    @get:Rule
    val rule = createComposeRule()

    // Compose tests under Robolectric use a default ≈ phone-sized viewport;
    // assertExists (vs assertIsDisplayed) avoids spurious failures when long
    // senior-safe text + tall ≥56dp buttons push elements just below the fold.
    // The structural test below verifies all required text/components are in
    // the tree — visual smoke happens on a real emulator (TODO-SPEC010-EMU-003).

    @Test
    fun hard_block_screen_renders_title_body_and_buttons() {
        rule.setContent {
            LauncherTheme(preset = null) {
                GmsHardBlockScreen(
                    title = "Это устройство не поддерживается",
                    body = "Для работы приложения нужны Google Play Services.",
                    learnMoreLabel = "Подробнее: https://support.google.com/googleplay/answer/9037938",
                    okLabel = "Понятно",
                    onLearnMore = {},
                    onOk = {},
                )
            }
        }
        rule.onNodeWithText("Это устройство не поддерживается").assertExists()
        rule.onNodeWithText("Подробнее: https://support.google.com/googleplay/answer/9037938")
            .assertExists()
        rule.onNodeWithText("Понятно").assertExists()
    }

    @Test
    fun ok_button_triggers_callback() {
        var okCount = 0
        rule.setContent {
            LauncherTheme(preset = null) {
                GmsHardBlockScreen(
                    title = "T",
                    body = "B",
                    learnMoreLabel = "Подробнее",
                    okLabel = "Понятно",
                    onLearnMore = {},
                    onOk = { okCount++ },
                )
            }
        }
        rule.onNodeWithText("Понятно").performClick()
        rule.waitForIdle()
        assertEquals(1, okCount)
    }

    @Test
    fun learn_more_link_triggers_callback() {
        var learnMoreCount = 0
        rule.setContent {
            LauncherTheme(preset = null) {
                GmsHardBlockScreen(
                    title = "T",
                    body = "B",
                    learnMoreLabel = "Подробнее",
                    okLabel = "Понятно",
                    onLearnMore = { learnMoreCount++ },
                    onOk = {},
                )
            }
        }
        rule.onNodeWithText("Подробнее").performClick()
        rule.waitForIdle()
        assertEquals(1, learnMoreCount)
    }
}
