package com.launcher.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verifies the spec 005 §7.6 / US-508 debounce: two fast taps on the same
 * [TileCard] fire `onClick` only once. The 500-ms gate is local to each
 * tile instance.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class TileCardTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun fastDoubleTap_firesOnceWithin500ms() {
        var counter = 0
        rule.setContent {
            MaterialTheme {
                TileCard(label = "Test", onClick = { counter++ })
            }
        }
        rule.onNodeWithText("Test").performClick()
        rule.onNodeWithText("Test").performClick()
        rule.waitForIdle()
        assertEquals("expected only the first tap to register; counter=$counter", 1, counter)
    }
}
