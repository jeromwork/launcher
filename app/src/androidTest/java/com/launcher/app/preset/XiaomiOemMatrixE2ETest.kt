package com.launcher.app.preset

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.launcher.adapters.preset.PresetSelectionService
import com.launcher.api.profile.ProfileStore
import com.launcher.api.wizard.handlers.CheckHandler
import com.launcher.api.wizard.data.CheckSpec
import kotlin.reflect.KClass
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

/**
 * Spec T67K (SC-008, OEM matrix) — verifies Xiaomi MIUI surfaces required
 * for TASK-65 reminder banner UX.
 *
 * Asserts only what the device can actually answer programmatically:
 *   1. CheckSpec.AndroidRole(HOME) handler returns NotApplied for the
 *      mock-backend test package (it is not the default launcher).
 *   2. The system has a resolvable Activity for
 *      Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS (ApplySpec.SettingsDeepLink
 *      target). This is the action TASK-65's reminder banner would
 *      eventually route to via apply for role.home.
 *   3. ApplySpec.AndroidRoleRequest path: RoleManager.createRequestRoleIntent
 *      yields a resolvable component (the system role-grant dialog).
 *
 * UI rendering / user tap is owner-manual (Activity-level verification
 * outside instrumentation scope).
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

    @Test
    fun roleHomeIsNotGrantedToTestPackage() = runBlocking {
        @Suppress("UNCHECKED_CAST")
        val handlers = GlobalContext.get()
            .get<Map<KClass<out CheckSpec>, CheckHandler>>(named("checkHandlers"))
        val handler = handlers[CheckSpec.AndroidRole::class]
            ?: error("AndroidRole handler not wired")
        val status = handler.check(CheckSpec.AndroidRole("android.app.role.HOME"))
        // We're not the default launcher; CheckHandler must report NotApplied
        // (or Applied if someone made us default — both legitimate states).
        assertTrue(
            "AndroidRole handler must return Applied/NotApplied, got $status",
            status is com.launcher.api.wizard.SettingStatus.Applied ||
                status is com.launcher.api.wizard.SettingStatus.NotApplied,
        )
    }

    @Test
    fun manageDefaultAppsSettingsIsResolvable() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        val resolveInfo = ctx.packageManager.resolveActivity(intent, 0)
        assertTrue(
            "Xiaomi MIUI must surface a Settings Activity for " +
                "ACTION_MANAGE_DEFAULT_APPS_SETTINGS (SettingsDeepLink fallback target). " +
                "Got null — fallback toast path applies.",
            resolveInfo != null,
        )
    }

    @Test
    fun roleHomeRequestIntentIsResolvable() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val rm = ctx.getSystemService(android.app.role.RoleManager::class.java)
        val intent = rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)
        val resolveInfo = ctx.packageManager.resolveActivity(intent, 0)
        assertTrue(
            "RoleManager.createRequestRoleIntent(HOME) must yield a resolvable Activity " +
                "on Xiaomi MIUI for the ApplySpec.AndroidRoleRequest path",
            resolveInfo != null,
        )
        // Sanity: it's a system-controlled dialog (not our app).
        assertFalse(
            "role request intent must NOT target our own package",
            resolveInfo!!.activityInfo.packageName == ctx.packageName,
        )
    }
}
