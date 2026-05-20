package com.launcher.ui.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.launcher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Spec 010 T076 — SetupChecksBadge structural verification.
 * Renders both badges when both counts > 0; hides side when its count == 0;
 * hides everything when both zero (no Composable emitted).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class SetupChecksBadgeTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_both_chips_when_counts_positive() {
        rule.setContent {
            LauncherTheme(preset = null) {
                SetupChecksBadge(
                    requiredCount = 2,
                    recommendedCount = 3,
                    requiredLabel = "критично",
                    recommendedLabel = "рекомендуется",
                    requiredA11yLabel = "2 критичных пункта",
                    recommendedA11yLabel = "3 рекомендуемых пункта",
                )
            }
        }
        rule.onNodeWithText("2").assertExists()
        rule.onNodeWithText("3").assertExists()
        rule.onNodeWithText("критично").assertExists()
        rule.onNodeWithText("рекомендуется").assertExists()
    }

    @Test
    fun hides_required_chip_when_count_zero() {
        rule.setContent {
            LauncherTheme(preset = null) {
                SetupChecksBadge(
                    requiredCount = 0,
                    recommendedCount = 1,
                    requiredLabel = "критично",
                    recommendedLabel = "рекомендуется",
                    requiredA11yLabel = "",
                    recommendedA11yLabel = "1 рекомендуемый пункт",
                )
            }
        }
        rule.onNodeWithText("1").assertExists()
        rule.onNodeWithText("рекомендуется").assertExists()
        // «критично» should NOT render.
        assertEquals(0, rule.onAllNodesWithText("критично").fetchSemanticsNodes().size)
    }

    @Test
    fun hides_everything_when_both_zero() {
        rule.setContent {
            LauncherTheme(preset = null) {
                SetupChecksBadge(
                    requiredCount = 0,
                    recommendedCount = 0,
                    requiredLabel = "критично",
                    recommendedLabel = "рекомендуется",
                    requiredA11yLabel = "",
                    recommendedA11yLabel = "",
                )
            }
        }
        assertEquals(0, rule.onAllNodesWithText("критично").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("рекомендуется").fetchSemanticsNodes().size)
    }
}
