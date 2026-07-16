package com.launcher.app.preset.task126

import android.app.Activity
import android.view.WindowManager
import com.launcher.app.preset.task120.provider.StatusBarPolicyProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T042 — StatusBarPolicyProvider Robolectric coverage (FR-005, US-6).
 *
 * - standard path uses WindowInsetsControllerCompat.hide(statusBars)
 *   (verified indirectly — no FLAG_FULLSCREEN gets added).
 * - MIUI path adds WindowManager.LayoutParams.FLAG_FULLSCREEN.
 * - check() is stateless: always returns NeedsApply per plan (T034 semantics).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class StatusBarPolicyProviderTest {

    private lateinit var profile: Profile

    @Before
    fun setUp() {
        profile = Profile(basedOnPreset = "test", presetVersion = 1, layoutKey = "layout.default")
    }

    @Test
    fun check_isAlwaysNeedsApply() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val provider = StatusBarPolicyProvider(
            currentActivity = { activity },
            manufacturer = "Google",
        )
        val outcome = provider.check(Component.StatusBarPolicy(), profile)
        assertEquals(Outcome.NeedsApply, outcome)
    }

    @Test
    fun apply_standardPath_doesNotAddFullscreenFlag() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val provider = StatusBarPolicyProvider(
            currentActivity = { activity },
            manufacturer = "Google",
        )

        val outcome = provider.apply(Component.StatusBarPolicy(), profile)

        assertEquals(Outcome.Ok, outcome)
        val flags = activity.window.attributes.flags
        assertEquals(
            "standard path must not set FLAG_FULLSCREEN",
            0,
            flags and WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
    }

    @Test
    fun apply_miuiPath_setsFullscreenFlag() = runTest {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val provider = StatusBarPolicyProvider(
            currentActivity = { activity },
            manufacturer = "Xiaomi",
        )

        val outcome = provider.apply(Component.StatusBarPolicy(), profile)

        assertEquals(Outcome.Ok, outcome)
        val flags = activity.window.attributes.flags
        assertNotEquals(
            "MIUI path must set FLAG_FULLSCREEN",
            0,
            flags and WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
    }

    @Test
    fun apply_withoutActivity_returnsFailure() = runTest {
        val provider = StatusBarPolicyProvider(
            currentActivity = { null },
            manufacturer = "Google",
        )
        val outcome = provider.apply(Component.StatusBarPolicy(), profile)
        // We only need to assert it's a Failed outcome — exact FailReason is
        // implementation detail; the important behaviour is that no crash occurs
        // when there's no foreground Activity.
        assert(outcome is Outcome.Failed) {
            "expected Outcome.Failed when no Activity, got $outcome"
        }
    }
}
