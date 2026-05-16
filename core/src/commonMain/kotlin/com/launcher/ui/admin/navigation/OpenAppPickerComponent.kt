package com.launcher.ui.admin.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.apps.InstalledApp
import com.launcher.api.apps.InstalledAppsCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Decompose component backing [com.launcher.ui.admin.OpenAppTilePicker]
 * (spec 009 FR-034). One-shot load on init; selection flows back through
 * [onSelected] callback.
 */
class OpenAppPickerComponent(
    componentContext: ComponentContext,
    private val installedAppsCatalog: InstalledAppsCatalog,
    val onBack: () -> Unit,
    val onSelected: (InstalledApp) -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch {
            _apps.value = installedAppsCatalog.listApps()
        }
    }
}
