package com.launcher.ui.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.FlowPreset
import com.launcher.api.PresetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Settings screen state and actions: read/change active preset, reset data,
 * QR placeholder, remote-control toggle placeholder. Real backend wiring for
 * remote control + QR comes in spec 007 (pairing-and-firebase-channel).
 */
class SettingsComponent(
    componentContext: ComponentContext,
    private val presetRepository: PresetRepository,
    val onBack: () -> Unit,
    val onPresetChanged: () -> Unit,
    val onResetData: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch {
            val active = presetRepository.getActivePreset() ?: FlowPreset.WORKSPACE
            _state.value = _state.value.copy(activePreset = active)
        }
    }

    fun openPresetPicker() {
        _state.value = _state.value.copy(presetPickerVisible = true)
    }

    fun closePresetPicker() {
        _state.value = _state.value.copy(presetPickerVisible = false)
    }

    fun selectPreset(preset: FlowPreset) {
        scope.launch {
            presetRepository.setActivePreset(preset)
            _state.value = _state.value.copy(activePreset = preset, presetPickerVisible = false)
            onPresetChanged()
        }
    }

    fun openQrPlaceholder() {
        _state.value = _state.value.copy(qrPlaceholderVisible = true)
    }

    fun closeQrPlaceholder() {
        _state.value = _state.value.copy(qrPlaceholderVisible = false)
    }

    fun toggleRemoteControl() {
        _state.value = _state.value.copy(remoteControlEnabled = !_state.value.remoteControlEnabled)
    }

    fun confirmReset() {
        _state.value = _state.value.copy(resetConfirmVisible = true)
    }

    fun cancelReset() {
        _state.value = _state.value.copy(resetConfirmVisible = false)
    }

    fun executeReset() {
        scope.launch {
            presetRepository.clear()
            _state.value = _state.value.copy(resetConfirmVisible = false)
            onResetData()
        }
    }
}

data class SettingsUiState(
    val activePreset: FlowPreset = FlowPreset.WORKSPACE,
    val presetPickerVisible: Boolean = false,
    val qrPlaceholderVisible: Boolean = false,
    val resetConfirmVisible: Boolean = false,
    val remoteControlEnabled: Boolean = false,
)
