package com.launcher.adapters.wizard

import androidx.test.core.app.ApplicationProvider
import com.launcher.api.wizard.ConfigKind
import com.launcher.api.wizard.ConfigSourceResult
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.data.ConfigDocument
import com.launcher.api.wizard.data.WireCriticality
import com.launcher.api.wizard.data.WireStepType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * TASK-7 Phase 4 — content validation for production assets:
 *
 *  - `simple-launcher.json` manifest loads cleanly and exposes the 4
 *    expected steps in the right order (T040 / T045).
 *  - Every SystemSetting `refId` in the manifest resolves to an entry
 *    in the migrated v2 `android-pool.json`.
 *  - Pool v2 entries all carry both `check` and `apply` blocks (T041).
 *  - `PoolSchemaV2RoundtripTest` semantics replicated on the real
 *    asset, not just a synthetic fixture (T044).
 */
@RunWith(RobolectricTestRunner::class)
class SimpleLauncherManifestTest {

    private val configSource = BundledConfigSource(ApplicationProvider.getApplicationContext())

    @Test
    fun manifest_loads_with_fourExplicitSteps() = runTest {
        val result = configSource.load(
            ConfigKind.WizardManifest,
            "wizard-manifest.simple-launcher",
        )
        assertTrue(
            "expected Success, got $result",
            result is ConfigSourceResult.Success,
        )
        val doc = (result as ConfigSourceResult.Success).document as ConfigDocument.Manifest
        assertEquals(false, doc.body.autoOrder)
        val steps = doc.body.steps
        assertNotNull(steps)
        steps!!
        assertEquals(4, steps.size)
        // Order — Сценарий 1.
        assertEquals(WireStepType.SystemSetting, steps[0].stepType)
        assertEquals("android.role.home", steps[0].refId)
        assertEquals(false, steps[0].canSkip)
        assertEquals(WireCriticality.Required, steps[0].criticality)

        assertEquals(WireStepType.UIChoice, steps[1].stepType)
        assertEquals("tileSet", steps[1].refId)
        assertEquals(WireCriticality.Required, steps[1].criticality)

        assertEquals(WireStepType.SystemSetting, steps[2].stepType)
        assertEquals("android.permission.POST_NOTIFICATIONS", steps[2].refId)
        assertEquals(true, steps[2].canSkip) // per-profile override
        assertEquals(WireCriticality.Required, steps[2].criticality)

        assertEquals(WireStepType.Custom, steps[3].stepType)
        assertEquals("pair-admin", steps[3].refId)
        assertEquals(true, steps[3].canSkip)
        assertEquals(WireCriticality.Optional, steps[3].criticality)
    }

    @Test
    fun pool_v2_loads_with_checkAndApplyOnAllEntries() = runTest {
        val result = configSource.load(
            ConfigKind.SystemSettingsPool,
            "system-settings.android-pool",
        )
        assertTrue(
            "expected Success, got $result",
            result is ConfigSourceResult.Success,
        )
        val doc = (result as ConfigSourceResult.Success).document as ConfigDocument.SystemSettingsPoolDoc
        assertEquals(2, doc.header.schemaVersion)
        assertEquals(6, doc.body.settings.size)
        for (entry in doc.body.settings) {
            assertNotNull("entry ${entry.id} missing check", entry.check)
            assertNotNull("entry ${entry.id} missing apply", entry.apply)
        }
    }

    @Test
    fun manifest_systemSettingRefIds_resolveInPool() = runTest {
        val manifestRes = configSource.load(
            ConfigKind.WizardManifest,
            "wizard-manifest.simple-launcher",
        ) as ConfigSourceResult.Success
        val poolRes = configSource.load(
            ConfigKind.SystemSettingsPool,
            "system-settings.android-pool",
        ) as ConfigSourceResult.Success

        val poolIds = (poolRes.document as ConfigDocument.SystemSettingsPoolDoc)
            .body.settings.map { it.id }.toSet()
        val manifestSysSettingRefs = (manifestRes.document as ConfigDocument.Manifest)
            .body.steps.orEmpty()
            .filter { it.stepType == WireStepType.SystemSetting }
            .map { it.refId }
        for (ref in manifestSysSettingRefs) {
            assertTrue("manifest references unknown pool id: $ref", ref in poolIds)
        }
    }

    @Test
    fun pool_roleHome_entryShapeMatchesContract() = runTest {
        val result = configSource.load(
            ConfigKind.SystemSettingsPool,
            "system-settings.android-pool",
        ) as ConfigSourceResult.Success
        val doc = result.document as ConfigDocument.SystemSettingsPoolDoc
        val roleHome = doc.body.settings.first { it.id == "android.role.home" }
        assertTrue(roleHome.check is CheckSpec.AndroidPackageHome)
        val apply = roleHome.apply
        assertTrue(apply is ApplySpec.AndroidRoleRequest)
        assertEquals("HOME", (apply as ApplySpec.AndroidRoleRequest).role)
        // T042 — retry message wired in.
        assertEquals("system_setting_role_home_retry_message", roleHome.extendedInstructionKey)
    }
}
