package com.launcher.app.preset.task126

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.launcher.app.preset.task120.provider.LauncherRoleProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * T039 — LauncherRoleProvider Robolectric coverage (FR-002, US-2).
 *
 * Scenarios:
 *  - default HOME app → check() == Ok
 *  - not default → check() == NeedsApply
 *  - apply() fires the role-request Intent exactly once
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class LauncherRoleProviderTest {

    private lateinit var context: Context
    private lateinit var profile: Profile

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        profile = Profile(
            basedOnPreset = "test",
            presetVersion = 1,
            layoutKey = "layout.default",
        )
    }

    @Test
    fun check_returnsOk_whenAppIsDefaultLauncher() = runTest {
        val rm = shadowOf(context.getSystemService(RoleManager::class.java))
        rm.addHeldRole(RoleManager.ROLE_HOME)

        val provider = LauncherRoleProvider(context = context)
        val outcome = provider.check(Component.LauncherRole(), profile)

        assertEquals(Outcome.Ok, outcome)
    }

    @Test
    fun check_returnsNeedsApply_whenAppIsNotDefault() = runTest {
        // Explicitly no role holder for ROLE_HOME.
        val provider = LauncherRoleProvider(context = context)
        val outcome = provider.check(Component.LauncherRole(), profile)

        assertEquals(Outcome.NeedsApply, outcome)
    }

    @Test
    fun apply_firesRoleRequestIntent_whenNotDefault() = runTest {
        // Provide a foreground Activity so we exercise the standard branch.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val provider = LauncherRoleProvider(
            context = context,
            currentActivity = { activity },
        )

        val outcome = provider.apply(Component.LauncherRole(), profile)

        val started: Intent? = shadowOf(activity).nextStartedActivity
        assertTrue(
            "expected role-request Intent to be launched once, got $started",
            started != null,
        )
        // Assert exactly one intent fired.
        assertEquals(null, shadowOf(activity).nextStartedActivity)
        assertEquals(Outcome.Ok, outcome)
    }
}
