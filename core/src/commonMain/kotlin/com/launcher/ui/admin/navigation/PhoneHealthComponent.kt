package com.launcher.ui.admin.navigation

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.launcher.api.health.Health
import com.launcher.api.result.Outcome
import com.launcher.api.sync.DocPath
import com.launcher.api.sync.RemoteSyncBackend
import com.launcher.api.wireformat.WireFormatJson
import com.launcher.ui.health.HealthToPhoneIndicatorAdapter
import com.launcher.ui.health.PhoneHealthIndicator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Decompose component backing
 * [com.launcher.ui.health.PhoneHealthIndicatorScreen] (spec 009
 * FR-017..FR-022a).
 *
 * Subscribes to Firestore `/links/{linkId}/health/current` via
 * [RemoteSyncBackend.observe] (FR-020 listener-only-when-screen-open —
 * scope ties to Decompose lifecycle, so observe stops on destroy).
 *
 * Health DocSnapshot → [Health] (kotlinx.serialization auto) →
 * [HealthToPhoneIndicatorAdapter.map] → list of [PhoneHealthIndicator].
 *
 * FR-021: emits [com.launcher.ui.health.PhoneHealthCriticalEvent] via the
 * adapter's shared flow on transitions into Critical. No subscriber в
 * спеке 9 (plan §11 C-1).
 */
class PhoneHealthComponent(
    componentContext: ComponentContext,
    private val linkId: String,
    private val remoteSyncBackend: RemoteSyncBackend,
    private val indicatorAdapter: HealthToPhoneIndicatorAdapter,
    val onBack: () -> Unit,
) : ComponentContext by componentContext {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = WireFormatJson.json

    private val _indicators = MutableStateFlow<List<PhoneHealthIndicator>>(emptyList())
    val indicators: StateFlow<List<PhoneHealthIndicator>> = _indicators.asStateFlow()

    val criticalEvents = indicatorAdapter.criticalEvents

    init {
        lifecycle.doOnDestroy { scope.cancel() }
        scope.launch { observeRemoteHealth() }
    }

    private suspend fun observeRemoteHealth() {
        remoteSyncBackend.observe(DocPath.LinkHealth(linkId)).collectLatest { outcome ->
            val snapshot = (outcome as? Outcome.Success)?.value ?: return@collectLatest
            val data = snapshot.data
            val health: Health = try {
                json.decodeFromJsonElement(Health.serializer(), data)
            } catch (_: Throwable) {
                return@collectLatest
            }
            _indicators.value = indicatorAdapter.map(health)
        }
    }
}
