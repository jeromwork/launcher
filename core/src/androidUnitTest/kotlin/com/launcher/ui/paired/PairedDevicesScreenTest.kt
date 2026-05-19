package com.launcher.ui.paired

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
 * Spec 010 T090 — Composable test for [PairedDevicesScreen] (FR-029, FR-033).
 * Covers:
 *  - both sections render when each list is non-empty;
 *  - empty-state renders с «Показать QR» button when both lists empty;
 *  - «Прекратить помощь» tap fires the unlink callback with the right item.
 *
 * Pairs with [PairedDevicesPresenterTest] (commonTest) to cover the full
 * local-first revocation flow.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class PairedDevicesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val sampleHelpsMe = PairedDeviceItem(
        linkId = "link-7",
        displayName = "admin-ui",
        pairedDateLabel = "20.05.26",
        role = PairedDeviceItem.Section.HelpsMe,
    )

    private val sampleIHelp = PairedDeviceItem(
        linkId = "link-12",
        displayName = "babushka",
        pairedDateLabel = "20.05.26",
        role = PairedDeviceItem.Section.IHelp,
    )

    @Test
    fun renders_both_sections_when_populated() {
        rule.setContent {
            LauncherTheme(preset = null) {
                PairedDevicesScreen(
                    helpsMe = listOf(sampleHelpsMe),
                    iHelp = listOf(sampleIHelp),
                    helpsMeSectionTitle = "Кто помогает мне",
                    iHelpSectionTitle = "Кому я помогаю",
                    unlinkButtonLabel = "Прекратить помощь",
                    emptyStateBody = "—",
                    emptyStateActionLabel = "—",
                    onUnlinkClick = {},
                    onShowQrClick = {},
                )
            }
        }

        rule.onNodeWithText("Кто помогает мне").assertIsDisplayed()
        rule.onNodeWithText("Кому я помогаю").assertIsDisplayed()
        rule.onNodeWithText("admin-ui").assertIsDisplayed()
        rule.onNodeWithText("babushka").assertIsDisplayed()
        rule.onNodeWithTag("paired_empty_state").assertDoesNotExist()
    }

    @Test
    fun empty_state_shows_with_show_qr_button_when_both_lists_empty() {
        var showQrClicked = false
        rule.setContent {
            LauncherTheme(preset = null) {
                PairedDevicesScreen(
                    helpsMe = emptyList(),
                    iHelp = emptyList(),
                    helpsMeSectionTitle = "Кто помогает мне",
                    iHelpSectionTitle = "Кому я помогаю",
                    unlinkButtonLabel = "Прекратить помощь",
                    emptyStateBody = "Никто пока тобой не помогает",
                    emptyStateActionLabel = "Показать QR",
                    onUnlinkClick = {},
                    onShowQrClick = { showQrClicked = true },
                )
            }
        }
        rule.onNodeWithTag("paired_empty_state").assertIsDisplayed()
        rule.onNodeWithText("Никто пока тобой не помогает").assertIsDisplayed()
        rule.onNodeWithTag("paired_empty_show_qr").performClick()
        rule.waitForIdle()
        assertEquals(true, showQrClicked)
    }

    @Test
    fun unlink_click_passes_the_item() {
        var unlinkedLinkId: String? = null
        rule.setContent {
            LauncherTheme(preset = null) {
                PairedDevicesScreen(
                    helpsMe = listOf(sampleHelpsMe),
                    iHelp = emptyList(),
                    helpsMeSectionTitle = "Кто помогает мне",
                    iHelpSectionTitle = "Кому я помогаю",
                    unlinkButtonLabel = "Прекратить помощь",
                    emptyStateBody = "—",
                    emptyStateActionLabel = "—",
                    onUnlinkClick = { unlinkedLinkId = it.linkId },
                    onShowQrClick = {},
                )
            }
        }
        rule.onNodeWithTag("paired_row_unlink_link-7").performClick()
        rule.waitForIdle()
        assertEquals("link-7", unlinkedLinkId)
    }
}
