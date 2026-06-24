package com.launcher.app.ui.onboarding

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.launcher.app.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * TASK-49 T025 — instrumented Compose UI test for [SignInExplanationScreen].
 * Verifies FR-005, FR-006, FR-008a (tap targets, contentDescriptions).
 */
@RunWith(AndroidJUnit4::class)
class SignInExplanationScreenE2ETest {

    @get:Rule
    val composeRule = createComposeRule()

    private val resources by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.resources
    }

    private fun string(id: Int) = resources.getString(id)

    @Test
    fun rendersTitleAndFourBullets() {
        composeRule.setContent {
            SignInExplanationScreen(onSignInClicked = {}, onCancelClicked = {})
        }

        composeRule
            .onNodeWithContentDescription(string(R.string.task49_signin_explanation_title))
            .assertIsDisplayed()

        listOf(
            R.string.task49_signin_explanation_bullet_1,
            R.string.task49_signin_explanation_bullet_2,
            R.string.task49_signin_explanation_bullet_3,
            R.string.task49_signin_explanation_bullet_4,
        ).forEach { bulletId ->
            composeRule
                .onNodeWithContentDescription(string(bulletId))
                .assertIsDisplayed()
        }
    }

    @Test
    fun bothButtonsVisibleWithSeniorSafeTapTarget() {
        composeRule.setContent {
            SignInExplanationScreen(onSignInClicked = {}, onCancelClicked = {})
        }

        composeRule
            .onNodeWithContentDescription(string(R.string.task49_signin_explanation_button_signin))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(56.dp)

        composeRule
            .onNodeWithContentDescription(string(R.string.task49_signin_explanation_button_cancel))
            .assertIsDisplayed()
            .assertHeightIsAtLeast(56.dp)
    }

    @Test
    fun signInButtonClickInvokesCallback() {
        val signInCount = AtomicInteger()
        composeRule.setContent {
            SignInExplanationScreen(
                onSignInClicked = { signInCount.incrementAndGet() },
                onCancelClicked = {},
            )
        }

        composeRule
            .onNodeWithContentDescription(string(R.string.task49_signin_explanation_button_signin))
            .performClick()

        assert(signInCount.get() == 1) { "expected onSignInClicked once, got ${signInCount.get()}" }
    }

    @Test
    fun cancelButtonClickInvokesCallback() {
        val cancelCount = AtomicInteger()
        composeRule.setContent {
            SignInExplanationScreen(
                onSignInClicked = {},
                onCancelClicked = { cancelCount.incrementAndGet() },
            )
        }

        composeRule
            .onNodeWithContentDescription(string(R.string.task49_signin_explanation_button_cancel))
            .performClick()

        assert(cancelCount.get() == 1) { "expected onCancelClicked once, got ${cancelCount.get()}" }
    }

    @Test
    fun talkBackContentDescriptionsPresentForAllElements() {
        composeRule.setContent {
            SignInExplanationScreen(onSignInClicked = {}, onCancelClicked = {})
        }

        // Title + 4 bullets + 2 buttons = 7 elements with contentDescription set explicitly.
        val expectedDescriptions = listOf(
            R.string.task49_signin_explanation_title,
            R.string.task49_signin_explanation_bullet_1,
            R.string.task49_signin_explanation_bullet_2,
            R.string.task49_signin_explanation_bullet_3,
            R.string.task49_signin_explanation_bullet_4,
            R.string.task49_signin_explanation_button_signin,
            R.string.task49_signin_explanation_button_cancel,
        )
        expectedDescriptions.forEach { id ->
            val nodes = composeRule.onAllNodesWithContentDescription(string(id))
            nodes.assertCountEquals(1)
        }
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals(
    expected: Int,
) {
    val actual = fetchSemanticsNodes().size
    assert(actual == expected) {
        "expected $expected nodes, got $actual"
    }
}
