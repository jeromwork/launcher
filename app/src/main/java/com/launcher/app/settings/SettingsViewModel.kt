package com.launcher.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.launcher.preset.port.SettingsGateway
import com.launcher.preset.settings.SettingsView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * TASK-69 (FR-005, FR-007, FR-008) — bridges [SettingsGateway] to the Compose
 * Settings screen. Depends **only** on the port (fitness T069-030): swapping
 * the engine behind [SettingsGateway] never touches this class.
 */
class SettingsViewModel(private val gateway: SettingsGateway) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            gateway.observe().collect { view ->
                _uiState.value = SettingsUiState(view = view, loading = false)
            }
        }
    }

    /** FR-007/FR-011 — in-app edit or a system-dialog row's "Change" tap. */
    fun onChange(poolRef: String, params: JsonObject) {
        viewModelScope.launch {
            gateway.apply(poolRef, params)
        }
    }
}

data class SettingsUiState(
    val view: SettingsView = SettingsView(sections = emptyList(), actions = emptyList()),
    val loading: Boolean = true,
)
