package com.launcher.adapters.preset

import com.launcher.api.preset.Criticality
import com.launcher.api.profile.AppliedState
import com.launcher.api.profile.ProfileData
import com.launcher.api.profile.SettingEntry
import com.launcher.api.wizard.SettingStatus
import com.launcher.api.wizard.data.CheckSpec
import com.launcher.api.wizard.handlers.CheckHandler
import kotlin.reflect.KClass

/**
 * Re-evaluates current applied state of preset configs against the device.
 *
 * Used by:
 *   - [com.launcher.ui.PresetBootRouter] → `computeCriticalMissing` to decide
 *     whether to show [com.launcher.ui.HomeBanner] on boot (FR-030, R4).
 *   - Settings onResume → `computeAllMissing` to render full reminder list
 *     (FR-016, FR-031).
 *
 * Dispatches each [SettingEntry] to its registered [CheckHandler] via the
 * shared DI map. If a handler throws — entry counted as Indeterminate
 * (Article VII §15 graceful), not surfaced as missing.
 */
class PresetReminderService(
    private val checkHandlers: Map<KClass<out CheckSpec>, CheckHandler>,
) {

    suspend fun computeCriticalMissing(profile: ProfileData): List<SettingEntry> =
        recompute(profile).filter {
            it.config.criticality == Criticality.Required &&
                it.state == AppliedState.NotApplied
        }

    suspend fun computeAllMissing(profile: ProfileData): List<SettingEntry> =
        recompute(profile).filter { it.state == AppliedState.NotApplied }

    private suspend fun recompute(profile: ProfileData): List<SettingEntry> =
        profile.settings.map { entry ->
            val handler = checkHandlers[entry.config.check::class]
                ?: return@map entry.copy(state = AppliedState.Indeterminate)
            val status = try {
                handler.check(entry.config.check)
            } catch (_: Throwable) {
                return@map entry.copy(state = AppliedState.Indeterminate)
            }
            entry.copy(state = status.toAppliedState())
        }

    private fun SettingStatus.toAppliedState(): AppliedState = when (this) {
        SettingStatus.Applied -> AppliedState.Applied
        SettingStatus.NotApplied -> AppliedState.NotApplied
        SettingStatus.Indeterminate -> AppliedState.Indeterminate
        SettingStatus.NotSupportedOnPlatform -> AppliedState.Indeterminate
        is SettingStatus.CheckFailed -> AppliedState.Indeterminate
    }
}
