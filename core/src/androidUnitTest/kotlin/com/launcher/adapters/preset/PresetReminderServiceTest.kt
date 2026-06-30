package com.launcher.adapters.preset

import com.launcher.api.preset.Config
import com.launcher.api.preset.Criticality
import com.launcher.api.profile.AppliedState
import com.launcher.api.profile.Layout
import com.launcher.api.profile.ProfileData
import com.launcher.api.profile.SettingEntry
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.ApplySpec
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler
import kotlin.reflect.KClass
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetReminderServiceTest {

    private fun cfg(id: String, criticality: Criticality): Config = Config(
        id = id,
        poolId = "system-settings",
        poolVersion = 1,
        entryId = id,
        title = "$id.title",
        description = "$id.desc",
        check = CheckSpec.AndroidRole(role = "any"),
        apply = ApplySpec.AndroidRoleRequest(role = "any"),
        criticality = criticality,
    )

    private fun handlers(status: SettingStatus, throwInstead: Boolean = false): Map<KClass<out CheckSpec>, CheckHandler> =
        mapOf(
            CheckSpec.AndroidRole::class to object : CheckHandler {
                override suspend fun check(spec: CheckSpec): SettingStatus {
                    if (throwInstead) throw RuntimeException("simulated")
                    return status
                }
            },
        )

    @Test
    fun computeCriticalMissingReturnsOnlyRequiredNotApplied() = runTest {
        val service = PresetReminderService(handlers(SettingStatus.NotApplied))
        val profile = ProfileData(
            layout = Layout.empty(),
            settings = listOf(
                SettingEntry(cfg("a", Criticality.Required)),
                SettingEntry(cfg("b", Criticality.Optional)),
            ),
        )
        val result = service.computeCriticalMissing(profile)
        assertEquals(1, result.size)
        assertEquals("a", result.first().config.id)
    }

    @Test
    fun computeAllMissingIncludesOptional() = runTest {
        val service = PresetReminderService(handlers(SettingStatus.NotApplied))
        val profile = ProfileData(
            layout = Layout.empty(),
            settings = listOf(
                SettingEntry(cfg("a", Criticality.Required)),
                SettingEntry(cfg("b", Criticality.Optional)),
            ),
        )
        assertEquals(2, service.computeAllMissing(profile).size)
    }

    @Test
    fun thrownExceptionDowngradesToIndeterminateNotCounted() = runTest {
        val service = PresetReminderService(handlers(SettingStatus.NotApplied, throwInstead = true))
        val profile = ProfileData(
            layout = Layout.empty(),
            settings = listOf(SettingEntry(cfg("a", Criticality.Required))),
        )
        // Indeterminate != NotApplied → not surfaced as missing.
        assertTrue(service.computeCriticalMissing(profile).isEmpty())
        assertTrue(service.computeAllMissing(profile).isEmpty())
    }

    @Test
    fun appliedEntriesAreNotMissing() = runTest {
        val service = PresetReminderService(handlers(SettingStatus.Applied))
        val profile = ProfileData(
            layout = Layout.empty(),
            settings = listOf(SettingEntry(cfg("a", Criticality.Required), AppliedState.Applied)),
        )
        assertTrue(service.computeCriticalMissing(profile).isEmpty())
    }
}
