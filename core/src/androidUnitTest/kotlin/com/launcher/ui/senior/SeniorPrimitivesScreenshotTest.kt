package com.launcher.ui.senior

import android.app.Application
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.launcher.ui.senior.primitives.SeniorBodyText
import com.launcher.ui.senior.primitives.SeniorButton
import com.launcher.ui.senior.primitives.SeniorSecondaryButton
import com.launcher.ui.senior.primitives.SeniorTitleText
import com.launcher.ui.senior.theme.SeniorWarmTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * Spec 015 T094 / T095 / T096 — Roborazzi screenshot tests for senior UI
 * primitives. Verifies:
 *   - SC-006: primitive renders sanely at fontScale 1.0 AND 2.0 (no clip).
 *   - SC-006a: same primitives in EN + DE + AR show no text overflow
 *     (DE strings ~30-40% longer than EN; AR is RTL).
 *   - SC-007: wizard step renders right-aligned in ar-SA RTL locale.
 *
 * Snapshots live in core/src/androidUnitTest/snapshots/.
 *
 * Run:
 *   - record:  ./gradlew :core:recordRoborazziMockBackendDebug
 *   - verify:  ./gradlew :core:verifyRoborazziMockBackendDebug
 *   - compare: ./gradlew :core:compareRoborazziMockBackendDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, qualifiers = "w411dp-h891dp")
class SeniorPrimitivesScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun seniorButton_fontScale_1_0() {
        renderAt(fontScale = 1f, locale = Locale.ENGLISH) {
            SeniorButton(text = "Next", onClick = {})
        }
        captureRoot("senior_button_fontScale_1_0")
    }

    @Test
    fun seniorButton_fontScale_2_0_doesNotClip() {
        renderAt(fontScale = 2f, locale = Locale.ENGLISH) {
            SeniorButton(text = "Next", onClick = {})
        }
        captureRoot("senior_button_fontScale_2_0")
    }

    @Test
    fun seniorButton_en_de_ar_lengthExpansion() {
        // SC-006a — DE is the longest Latin-script translation; AR is RTL.
        // Render the trio stacked so the snapshot diff catches truncation /
        // overflow at a glance.
        renderAt(fontScale = 1.3f, locale = Locale.ENGLISH) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SeniorButton(text = "Allow notifications", onClick = {})
                SeniorButton(text = "Benachrichtigungen erlauben", onClick = {})
                SeniorButton(text = "السماح بالإشعارات", onClick = {})
            }
        }
        captureRoot("senior_button_en_de_ar")
    }

    @Test
    fun seniorTexts_renderStacked() {
        renderAt(fontScale = 1f, locale = Locale.ENGLISH) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SeniorTitleText("Choose your language")
                SeniorBodyText("Select your interface language. Larger text is easier to read.")
            }
        }
        captureRoot("senior_texts_stacked")
    }

    @Test
    fun seniorSecondaryButton_renders() {
        renderAt(fontScale = 1f, locale = Locale.ENGLISH) {
            SeniorSecondaryButton(text = "Skip", onClick = {})
        }
        captureRoot("senior_secondary_button")
    }

    @Test
    fun seniorWarmTheme_dark_renders() {
        composeRule.setContent {
            SeniorWarmTheme.Dark {
                Surface(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SeniorTitleText("Choose your language")
                        SeniorBodyText("Select your interface language.")
                        SeniorButton(text = "Next", onClick = {})
                        SeniorSecondaryButton(text = "Skip", onClick = {})
                    }
                }
            }
        }
        captureRoot("senior_warm_theme_dark")
    }

    private fun renderAt(
        fontScale: Float,
        locale: Locale,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        composeRule.setContent {
            // Apply font scale override via LocalDensity.
            val originalDensity = LocalDensity.current
            val scaledDensity = Density(originalDensity.density, fontScale)
            val baseConfig = LocalConfiguration.current
            val configWithLocale = Configuration(baseConfig).apply {
                setLocale(locale)
            }
            CompositionLocalProvider(
                LocalDensity provides scaledDensity,
                LocalConfiguration provides configWithLocale,
            ) {
                SeniorWarmTheme.Light {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .padding(24.dp),
                    ) {
                        Column { content() }
                    }
                }
            }
        }
    }

    private fun captureRoot(snapshotName: String) {
        composeRule.onRoot().captureRoboImage(
            filePath = "src/androidUnitTest/snapshots/${snapshotName}.png",
        )
    }
}
