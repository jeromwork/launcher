package com.launcher.ui.setup

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.launcher.api.setup.Criticality
import com.launcher.api.setup.IntentSpec
import com.launcher.ui.theme.LauncherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class WhatNeedsConfiguringScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun renders_required_section_first() {
        rule.setContent {
            LauncherTheme(preset = null) {
                WhatNeedsConfiguringScreen(
                    requiredItems = listOf(
                        WhatNeedsItem(
                            checkId = "role_home",
                            criticality = Criticality.Required,
                            description = "Сделать главным",
                            resolveIntent = IntentSpec("role.home", "request"),
                        ),
                    ),
                    recommendedItems = listOf(
                        WhatNeedsItem(
                            checkId = "post_notifications",
                            criticality = Criticality.Recommended,
                            description = "Разрешить уведомления",
                            resolveIntent = IntentSpec("permission.post_notifications", "request"),
                        ),
                    ),
                    requiredSectionTitle = "Срочно настроить",
                    recommendedSectionTitle = "Можно настроить позже",
                    configureButtonLabel = "Настроить",
                    onConfigureClick = {},
                )
            }
        }
        rule.onNodeWithText("Срочно настроить").assertExists()
        rule.onNodeWithText("Можно настроить позже").assertExists()
        rule.onNodeWithText("Сделать главным").assertExists()
        rule.onNodeWithText("Разрешить уведомления").assertExists()
        // Two «Настроить» buttons — one for each section.
        assertEquals(2, rule.onAllNodesWithText("Настроить").fetchSemanticsNodes().size)
    }

    @Test
    fun configure_click_passes_intent_spec() {
        var captured: IntentSpec? = null
        rule.setContent {
            LauncherTheme(preset = null) {
                WhatNeedsConfiguringScreen(
                    requiredItems = listOf(
                        WhatNeedsItem(
                            checkId = "role_home",
                            criticality = Criticality.Required,
                            description = "Сделать главным",
                            resolveIntent = IntentSpec("role.home", "request"),
                        ),
                    ),
                    recommendedItems = emptyList(),
                    requiredSectionTitle = "Срочно настроить",
                    recommendedSectionTitle = "Можно настроить позже",
                    configureButtonLabel = "Настроить",
                    onConfigureClick = { captured = it },
                )
            }
        }
        rule.onNodeWithText("Настроить").performClick()
        rule.waitForIdle()
        assertEquals(IntentSpec("role.home", "request"), captured)
    }
}
