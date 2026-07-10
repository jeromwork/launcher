package com.launcher.app.preset.task120

import com.launcher.preset.engine.PresetValidator
import com.launcher.preset.engine.ProfileFactory
import com.launcher.preset.model.Preset
import com.launcher.preset.port.PoolSource
import com.launcher.preset.port.PresetSource
import com.launcher.preset.port.ProfileStore

/**
 * T066 — application boot: load pool + bundled preset + activate.
 * Idempotent: if a Profile already exists in ProfileStore, does nothing.
 */
class PresetBootstrap(
    private val poolSource: PoolSource,
    private val presetSource: PresetSource,
    private val validator: PresetValidator,
    private val factory: ProfileFactory,
    private val store: ProfileStore,
    private val defaultPresetId: String = "simple-launcher",
) {

    /**
     * @return outcome describing what boot did — Activated / AlreadyActive /
     *   ValidationFailed / PresetNotFound.
     */
    suspend fun bootstrap(): BootstrapOutcome {
        if (store.load() != null) return BootstrapOutcome.AlreadyActive
        val preset: Preset = presetSource.loadPreset(defaultPresetId)
            ?: return BootstrapOutcome.PresetNotFound(defaultPresetId)
        val pool = poolSource.loadPool()
        val errors = validator.validate(preset, pool)
        if (errors.isNotEmpty()) return BootstrapOutcome.ValidationFailed(errors.map { it.toI18nKey() })
        val profile = factory.create(preset, pool)
        store.save(profile)
        return BootstrapOutcome.Activated(preset.presetId)
    }

    sealed class BootstrapOutcome {
        data class Activated(val presetId: String) : BootstrapOutcome()
        object AlreadyActive : BootstrapOutcome()
        data class PresetNotFound(val presetId: String) : BootstrapOutcome()
        data class ValidationFailed(val i18nKeys: List<String>) : BootstrapOutcome()
    }
}
