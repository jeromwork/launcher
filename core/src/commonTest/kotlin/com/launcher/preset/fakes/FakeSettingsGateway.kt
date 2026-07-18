package com.launcher.preset.fakes

import com.launcher.preset.port.ApplyResult
import com.launcher.preset.port.SettingsGateway
import com.launcher.preset.settings.AppOperation
import com.launcher.preset.settings.SettingsView
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonObject

/**
 * In-memory [SettingsGateway] for VM/UI tests (rule 6) — no [com.launcher.preset.engine.ReconcileEngine].
 * Records every [apply] call so tests can assert on what the ViewModel dispatched.
 */
class FakeSettingsGateway(
    initial: SettingsView = SettingsView(sections = emptyList(), actions = emptyList()),
    private var nextResult: ApplyResult = ApplyResult.Applied,
) : SettingsGateway {

    private val state = MutableStateFlow(initial)

    data class RecordedApply(val poolRef: String, val params: JsonObject)

    val appliedCalls: MutableList<RecordedApply> = mutableListOf()

    override fun observe(): Flow<SettingsView> = state.asStateFlow()

    override suspend fun apply(poolRef: String, params: JsonObject): ApplyResult {
        appliedCalls += RecordedApply(poolRef, params)
        return nextResult
    }

    fun emit(view: SettingsView) {
        state.value = view
    }

    fun setNextResult(result: ApplyResult) {
        nextResult = result
    }
}
