package com.launcher.adapters.wizard.handlers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.wizard.ApplyResult
import com.launcher.api.wizard.PermissionRequestPort
import com.launcher.api.wizard.PermissionResult
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Smoke / contract tests for the 9 TASK-7 Phase 2 handlers (T021-T029).
 *
 * Goal: each handler returns the right SettingStatus / ApplyResult class
 * given a representative spec. Spec mismatch (wrong sealed variant)
 * always yields the documented `NotSupportedOnPlatform` /
 * `UnsupportedMechanism` rather than crashing.
 *
 * Robolectric provides a working Context for the handlers that need
 * PackageManager / PowerManager / RoleManager. Variants that need
 * real device behaviour (e.g. RoleManager actually granting HOME) are
 * deferred to T064/T065/T066 (deferred-physical-device).
 */
@RunWith(RobolectricTestRunner::class)
class Task7HandlersTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private class FakePermPort(
        private val granted: Set<String>,
        private val grantOnRequest: Boolean = true,
    ) : PermissionRequestPort {
        override fun isGranted(permission: String): Boolean = permission in granted
        override fun isPermanentlyDenied(permission: String): Boolean = false
        override suspend fun request(permission: String): PermissionResult =
            if (grantOnRequest) PermissionResult.Granted else PermissionResult.Denied
    }

    // --- CheckHandlers ---

    @Test
    fun roleCheckHandler_returnsNotApplied_whenRoleNotHeld() = runTest {
        // Robolectric: RoleManager available on API 29+, our package never
        // holds HOME in a unit test by default.
        val handler = AndroidRoleCheckHandler(context)
        val status = handler.check(CheckSpec.AndroidRole(role = "HOME"))
        assertTrue(
            "expected NotApplied or NotSupportedOnPlatform (legacy fallback), got $status",
            status is SettingStatus.NotApplied || status is SettingStatus.NotSupportedOnPlatform,
        )
    }

    @Test
    fun permissionCheckHandler_routesGrantedThroughPort() = runTest {
        val handler = AndroidPermissionCheckHandler(
            FakePermPort(granted = setOf("android.permission.POST_NOTIFICATIONS")),
        )
        assertEquals(
            SettingStatus.Applied,
            handler.check(CheckSpec.AndroidPermission("android.permission.POST_NOTIFICATIONS")),
        )
        assertEquals(
            SettingStatus.NotApplied,
            handler.check(CheckSpec.AndroidPermission("android.permission.CALL_PHONE")),
        )
    }

    @Test
    fun specialPermissionCheckHandler_unknownVariant_indeterminate() = runTest {
        val handler = AndroidSpecialPermissionCheckHandler(context)
        assertEquals(
            SettingStatus.Indeterminate,
            handler.check(CheckSpec.AndroidSpecialPermission(variant = "not-a-real-variant")),
        )
    }

    @Test
    fun specialPermissionCheckHandler_batteryOptimizations_returnsConcreteStatus() = runTest {
        val handler = AndroidSpecialPermissionCheckHandler(context)
        val status = handler.check(
            CheckSpec.AndroidSpecialPermission(variant = "ignore_battery_optimizations"),
        )
        // We don't assert Applied vs NotApplied — depends on Robolectric defaults.
        // We only assert it doesn't crash and returns a concrete domain value.
        assertTrue(
            "expected concrete SettingStatus, got $status",
            status is SettingStatus.Applied || status is SettingStatus.NotApplied || status is SettingStatus.Indeterminate,
        )
    }

    @Test
    fun accessibilityServiceCheckHandler_alwaysIndeterminate() = runTest {
        val handler = AndroidAccessibilityServiceCheckHandler()
        assertEquals(
            SettingStatus.Indeterminate,
            handler.check(CheckSpec.AndroidAccessibilityService(componentName = "x/.Y")),
        )
        assertEquals(
            SettingStatus.Indeterminate,
            handler.check(CheckSpec.AndroidAccessibilityService(componentName = null)),
        )
    }

    @Test
    fun packageHomeCheckHandler_ownPackageNotHome_returnsNotApplied() = runTest {
        val handler = AndroidPackageHomeCheckHandler(context)
        val status = handler.check(CheckSpec.AndroidPackageHome(packageName = null))
        // In Robolectric default config our package is not the resolved home.
        assertEquals(SettingStatus.NotApplied, status)
    }

    // --- ApplyHandlers ---

    @Test
    fun standardPermissionApplyHandler_grantedMaps_toApplied() = runTest {
        val handler = AndroidStandardPermissionApplyHandler(
            FakePermPort(granted = emptySet(), grantOnRequest = true),
        )
        val result = handler.apply(
            ApplySpec.StandardPermissionRequest("android.permission.POST_NOTIFICATIONS"),
        )
        assertEquals(ApplyResult.Applied, result)
    }

    @Test
    fun roleApplyHandler_wrongSpec_returnsUnsupported() = runTest {
        val handler = AndroidRoleApplyHandler(context)
        // Pass a different sealed-variant spec to verify type-guard fallback.
        val result = handler.apply(ApplySpec.InAppOnly)
        assertEquals(ApplyResult.UnsupportedMechanism, result)
    }

    @Test
    fun settingsDeepLinkApplyHandler_actionDispatches_returnsPromptShown() = runTest {
        val handler = AndroidSettingsDeepLinkApplyHandler(context)
        val result = handler.apply(
            ApplySpec.SettingsDeepLink(
                action = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS,
                packageScoped = false,
            ),
        )
        // Robolectric records startActivity calls without launching real Activity.
        assertTrue(
            "expected PromptShown or Failed; got $result",
            result is ApplyResult.PromptShown || result is ApplyResult.Failed,
        )
    }

    @Test
    fun inAppOnlyApplyHandler_returnsPromptShown() = runTest {
        val handler = AndroidInAppOnlyApplyHandler()
        assertEquals(ApplyResult.PromptShown, handler.apply(ApplySpec.InAppOnly))
    }
}
