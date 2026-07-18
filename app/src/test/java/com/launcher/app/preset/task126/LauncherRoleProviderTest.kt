package com.launcher.app.preset.task126

import android.app.Activity
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.launcher.api.setup.GmsStatus
import com.launcher.app.preset.task120.provider.LauncherRoleProvider
import com.launcher.preset.model.Component
import com.launcher.preset.model.Outcome
import com.launcher.preset.model.Profile
import com.launcher.preset.model.Vendor
import com.launcher.preset.model.VendorOverride
import com.launcher.preset.model.VendorRecipeCatalogue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
 * Extended TASK-73 (T073-021, T073-022): vendor-aware dispatch, Huawei
 * GMS-availability branch.
 *
 * Scenarios:
 *  - default HOME app → check() == Ok
 *  - not default → check() == NeedsApply
 *  - apply() fires the role-request Intent exactly once (no vendor override —
 *    pre-TASK-73 regression, must stay unchanged)
 *  - apply() prefers a resolvable vendor-specific intent over the generic path
 *  - apply() falls through to Outcome.Failed(fallbackTextKey) when nothing resolves
 *  - Huawei + GMS unavailable skips the generic RoleManager path (no-GMS branch)
 *  - Huawei + GMS available does not take the no-GMS branch (regression-proof)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class LauncherRoleProviderTest {

    private lateinit var context: Context
    private lateinit var profile: Profile
    private lateinit var vendorDetector: FakeVendorDetector
    private lateinit var vendorRecipes: FakeVendorRecipeSource
    private lateinit var gmsAvailability: FakeGmsAvailabilityPort

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        profile = Profile(
            basedOnPreset = "test",
            presetVersion = 1,
            layoutKey = "layout.default",
        )
        vendorDetector = FakeVendorDetector(vendor = Vendor.GenericAndroid)
        vendorRecipes = FakeVendorRecipeSource()
        gmsAvailability = FakeGmsAvailabilityPort(status = GmsStatus.Available)
    }

    private fun provider(currentActivity: () -> Activity? = { null }) = LauncherRoleProvider(
        context = context,
        currentActivity = currentActivity,
        vendorDetector = vendorDetector,
        vendorRecipes = vendorRecipes,
        gmsAvailability = gmsAvailability,
    )

    @Test
    fun check_returnsOk_whenAppIsDefaultLauncher() = runTest {
        val rm = shadowOf(context.getSystemService(RoleManager::class.java))
        rm.addHeldRole(RoleManager.ROLE_HOME)

        val outcome = provider().check(Component.LauncherRole, profile)

        assertEquals(Outcome.Ok, outcome)
    }

    @Test
    fun check_returnsNeedsApply_whenAppIsNotDefault() = runTest {
        // Explicitly no role holder for ROLE_HOME.
        val outcome = provider().check(Component.LauncherRole, profile)

        assertEquals(Outcome.NeedsApply, outcome)
    }

    @Test
    fun check_returnsWellFormedOutcome_neverThrows_acrossEveryVendor() = runTest {
        // TASK-73 T073-019: no vendor branching added to check() in v1 — it must
        // stay a pure Ok/NeedsApply dispatch regardless of the detected Vendor.
        for (vendor in Vendor.entries) {
            vendorDetector.vendor = vendor
            val outcome = provider().check(Component.LauncherRole, profile)
            assertTrue(
                "check() must return Ok/NeedsApply without throwing for vendor=$vendor, got $outcome",
                outcome is Outcome.Ok || outcome is Outcome.NeedsApply,
            )
        }
    }

    @Test
    fun apply_firesRoleRequestIntent_whenNotDefault_noVendorOverride() = runTest {
        // Pre-TASK-73 regression: GenericAndroid + empty catalogue must behave
        // identically to the original vendor-blind provider.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val outcome = provider(currentActivity = { activity }).apply(Component.LauncherRole, profile)

        val started: Intent? = shadowOf(activity).nextStartedActivity
        assertTrue(
            "expected role-request Intent to be launched once, got $started",
            started != null,
        )
        assertEquals(null, shadowOf(activity).nextStartedActivity)
        assertEquals(Outcome.Ok, outcome)
    }

    @Test
    fun apply_vendorOverridePresent_andResolves_launchesVendorIntent_notGenericPath() = runTest {
        vendorDetector.vendor = Vendor.Xiaomi
        val vendorPackage = "com.android.settings"
        val vendorClass = "com.android.settings.Settings\$ManageDefaultAppsActivitiesActivity"
        shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(vendorPackage, vendorClass))
        vendorRecipes.catalogue = VendorRecipeCatalogue(
            entries = mapOf(
                "LauncherRole" to mapOf(
                    "Xiaomi" to VendorOverride(
                        intentPackage = vendorPackage,
                        intentClassName = vendorClass,
                        fallbackTextKey = "launcher_role.fallback.xiaomi",
                    ),
                ),
            ),
        )
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val outcome = provider(currentActivity = { activity }).apply(Component.LauncherRole, profile)

        val started: Intent? = shadowOf(activity).nextStartedActivity
        assertEquals(Outcome.Ok, outcome)
        assertEquals(vendorPackage, started?.component?.packageName)
        assertEquals(vendorClass, started?.component?.className)
    }

    @Test
    fun apply_missingFallbackTextKey_fallsBackToGenericPerComponentKey() = runTest {
        // Huawei + GMS unavailable => generic RoleManager path is skipped
        // (unreliable on this OEM per OEM Matrix); vendor override present but
        // without an intent that resolves, and without its own fallbackTextKey.
        vendorDetector.vendor = Vendor.Huawei
        gmsAvailability.status = GmsStatus.MissingFatal(reason = "no_gms")
        vendorRecipes.catalogue = VendorRecipeCatalogue(
            entries = mapOf(
                "LauncherRole" to mapOf(
                    "Huawei" to VendorOverride(fallbackTextKey = null),
                ),
            ),
        )

        val outcome = provider().apply(Component.LauncherRole, profile)

        val failed = outcome as? Outcome.Failed
        assertTrue("expected Outcome.Failed, got $outcome", failed != null)
        val reason = failed!!.reason as? com.launcher.preset.model.FailReason.InternalError
        assertTrue("expected FailReason.InternalError, got ${failed.reason}", reason != null)
        assertEquals("launcher_role.fallback.generic", reason!!.messageKey)
    }

    @Test
    fun apply_huaweiWithoutGms_skipsGenericPath_noActivityStarted() = runTest {
        vendorDetector.vendor = Vendor.Huawei
        gmsAvailability.status = GmsStatus.MissingFatal(reason = "no_gms")
        // No vendor override registered at all — nothing should resolve, and the
        // generic RoleManager path must not even be attempted.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val outcome = provider(currentActivity = { activity }).apply(Component.LauncherRole, profile)

        assertNull(
            "generic RoleManager path must be skipped for Huawei without GMS",
            shadowOf(activity).nextStartedActivity,
        )
        assertTrue("expected Outcome.Failed, got $outcome", outcome is Outcome.Failed)
    }

    @Test
    fun apply_huaweiWithGms_doesNotSkipGenericPath_regressionProof() = runTest {
        // Pre-2019 Huawei device: GMS still available. Must take the normal
        // generic RoleManager path, same as any other vendor.
        vendorDetector.vendor = Vendor.Huawei
        gmsAvailability.status = GmsStatus.Available
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        val outcome = provider(currentActivity = { activity }).apply(Component.LauncherRole, profile)

        assertTrue(
            "expected the generic RoleManager path to fire an Intent",
            shadowOf(activity).nextStartedActivity != null,
        )
        assertEquals(Outcome.Ok, outcome)
    }
}
