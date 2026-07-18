package com.launcher.app.preset.task126

import android.app.Activity
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * TASK-73 US3 / SC-003 demonstration (T073-025): a *new* recipe entry for an
 * already-known [Vendor] — added only to a test-fixture-shaped
 * [VendorRecipeCatalogue] instance, never touching [LauncherRoleProvider]'s
 * Kotlin source — is picked up on the next [com.launcher.preset.port.VendorRecipeSource]
 * read. This is the literal acceptance test for "no APK rebuild required for a
 * new override of an already-known vendor" (FR-005, SC-003).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = PresetTask126TestApplication::class)
class VendorRecipeNoRebuildDemonstrationTest {

    @Test
    fun newXiaomiVariantAddedToCatalogue_isDispatchedWithZeroCodeChange() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vendorDetector = FakeVendorDetector(vendor = Vendor.Xiaomi)
        val vendorRecipes = FakeVendorRecipeSource()
        val profile = Profile(basedOnPreset = "test", presetVersion = 1, layoutKey = "layout.default")

        // Simulates a content-only update to vendor-recipes.json: a second,
        // newer MIUI variant for the same known Vendor.Xiaomi — no Provider
        // code touched between this catalogue update and the dispatch below.
        val newVariantPackage = "com.miui.securitycenter"
        val newVariantClass = "com.miui.permcenter.autostart.AutoStartManagementActivity"
        shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName(newVariantPackage, newVariantClass))
        vendorRecipes.catalogue = VendorRecipeCatalogue(
            entries = mapOf(
                "LauncherRole" to mapOf(
                    "Xiaomi" to VendorOverride(
                        intentPackage = newVariantPackage,
                        intentClassName = newVariantClass,
                        fallbackTextKey = "launcher_role.fallback.xiaomi",
                    ),
                ),
            ),
        )

        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val outcome = LauncherRoleProvider(
            context = context,
            currentActivity = { activity },
            vendorDetector = vendorDetector,
            vendorRecipes = vendorRecipes,
            gmsAvailability = FakeGmsAvailabilityPort(status = GmsStatus.Available),
        ).apply(Component.LauncherRole, profile)

        val started: Intent? = shadowOf(activity).nextStartedActivity
        assertEquals(Outcome.Ok, outcome)
        assertEquals(newVariantPackage, started?.component?.packageName)
        assertEquals(newVariantClass, started?.component?.className)
    }
}
