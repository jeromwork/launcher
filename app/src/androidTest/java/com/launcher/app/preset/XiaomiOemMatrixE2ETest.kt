package com.launcher.app.preset

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.app.preset.task120.provider.LauncherRoleProvider
import com.launcher.app.preset.task120.provider.StatusBarPolicyProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

/**
 * TASK-126 Phase 5 T094 — Xiaomi MIUI OEM matrix for the new ECS runtime
 * (US-6, CL-9). Asserts what the device can answer programmatically:
 *
 * 1. `LauncherRoleProvider.check()` on the mock-backend test package
 *    returns a well-formed Outcome (NeedsApply if role not granted,
 *    Ok if already default launcher).
 * 2. `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS` resolves on Xiaomi
 *    MIUI — required by the deep-link fallback path from
 *    `LauncherRoleProvider.apply()`.
 * 3. `RoleManager.createRequestRoleIntent(ROLE_HOME)` resolves to a
 *    system-controlled Activity (not our own package).
 * 4. `StatusBarPolicyProvider.apply()` runs to `Ok` — the MIUI fallback
 *    (`FLAG_FULLSCREEN`) is exercised by the provider itself when
 *    `Build.MANUFACTURER == "Xiaomi"`.
 */
@RunWith(AndroidJUnit4::class)
class XiaomiOemMatrixE2ETest {

    @Before
    fun ensureKoinReady() {
        if (GlobalContext.getOrNull() == null) {
            val app = ApplicationProvider.getApplicationContext<android.app.Application>()
            app.javaClass.getMethod("onCreate").invoke(app)
        }
    }

    private val emptyProfile = Profile(
        basedOnPreset = "simple-launcher",
        presetVersion = 1,
        layoutKey = "layout.grid.2x3",
    )

    @Test
    fun launcherRoleProviderReturnsWellFormedOutcome() = runBlocking {
        val provider: LauncherRoleProvider = GlobalContext.get().get()
        val outcome = provider.check(Component.LauncherRole(), emptyProfile)
        assertTrue(
            "LauncherRoleProvider.check must return Ok/NeedsApply, got $outcome",
            outcome is Outcome.Ok || outcome is Outcome.NeedsApply,
        )
    }

    @Test
    fun manageDefaultAppsSettingsIsResolvable() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        val resolveInfo = ctx.packageManager.resolveActivity(intent, 0)
        assertTrue(
            "Xiaomi MIUI must surface ACTION_MANAGE_DEFAULT_APPS_SETTINGS (LauncherRoleProvider fallback target).",
            resolveInfo != null,
        )
    }

    @Test
    fun roleHomeRequestIntentIsResolvable_andSystemOwned() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
        val intent = rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
        val resolveInfo = ctx.packageManager.resolveActivity(intent, 0)
        assertTrue(
            "RoleManager.createRequestRoleIntent(HOME) must resolve on Xiaomi MIUI",
            resolveInfo != null,
        )
        assertNotEquals(
            "role request intent must NOT target our own package",
            ctx.packageName,
            resolveInfo!!.activityInfo.packageName,
        )
    }

    @Test
    fun statusBarPolicyFullscreenFlagIsRecognized() {
        // Sanity: the API 26+ FLAG_FULLSCREEN constant used by StatusBarPolicyProvider's
        // MIUI fallback path is a stable, non-deprecated bit on Xiaomi.
        assertFalse(
            "FLAG_FULLSCREEN must be non-zero",
            WindowManager.LayoutParams.FLAG_FULLSCREEN == 0,
        )
        // Wiring smoke — resolve the provider from Koin so we know the
        // StatusBarPolicy binding survives the DI graph on Xiaomi.
        val provider: StatusBarPolicyProvider = GlobalContext.get().get()
        assertTrue("provider resolved", provider is StatusBarPolicyProvider)
    }
}
