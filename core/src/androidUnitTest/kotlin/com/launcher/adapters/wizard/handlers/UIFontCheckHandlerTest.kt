package com.launcher.adapters.wizard.handlers

import android.app.Application
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Spec T66D — engine genericity demo. Verifies that a non-permission /
 * non-role CheckSpec variant (CheckSpec.UIFont) flows through the same
 * CheckHandler shape and reports Applied/NotApplied based purely on the
 * Configuration.fontScale value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UIFontCheckHandlerTest {

    @Test
    fun returnsNotAppliedWhenFontScaleBelowMin() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        ctx.resources.configuration.fontScale = 1.0f
        val handler = UIFontCheckHandler(ctx)
        val status = handler.check(CheckSpec.UIFont(minScale = 1.3f))
        assertEquals(SettingStatus.NotApplied, status)
    }

    @Test
    fun returnsAppliedWhenFontScaleAtOrAboveMin() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val cfg = Configuration(ctx.resources.configuration).apply { fontScale = 1.5f }
        ctx.resources.updateConfiguration(cfg, ctx.resources.displayMetrics)
        val handler = UIFontCheckHandler(ctx)
        val status = handler.check(CheckSpec.UIFont(minScale = 1.3f))
        assertEquals(SettingStatus.Applied, status)
    }

    @Test
    fun gracefullyRejectsWrongSpecKind() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<Application>()
        val handler = UIFontCheckHandler(ctx)
        val status = handler.check(CheckSpec.AndroidRole("android.app.role.HOME"))
        assertTrue(status is SettingStatus.NotSupportedOnPlatform)
    }
}
