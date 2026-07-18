package com.launcher.preset.port

import com.launcher.preset.model.FailReason
import com.launcher.preset.settings.SettingsView
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/**
 * TASK-69 (FR-008) — the single door between Settings UI and the reconcile
 * logic. `SettingsViewModel` depends only on this port; `ReconcileEngine` +
 * `ProfileStore` + `PresetSource` live behind it in the [EngineSettingsGateway]
 * adapter (or a `FakeSettingsGateway` in tests, rule 6). Swapping the engine
 * later touches this adapter only, never the VM/screen.
 */
interface SettingsGateway {
    /** Reactive projection of the active Profile (+ Preset.settingsMap). */
    fun observe(): Flow<SettingsView>

    /** Apply one component change through the same engine the wizard uses. */
    suspend fun apply(poolRef: String, params: JsonObject): ApplyResult
}

sealed interface ApplyResult {
    data object Applied : ApplyResult
    data class Failed(val reason: FailReason) : ApplyResult

    /** OS gave no read-back (FR-013) — caller shows "state unknown", offers re-apply. */
    data object NeedsSystemDialog : ApplyResult
}
