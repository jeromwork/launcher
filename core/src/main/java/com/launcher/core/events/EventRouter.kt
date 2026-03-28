package com.launcher.core.events

import com.launcher.api.ProjectEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * Fan-out for project events. Coalesces burst [ProjectEvent.PackageSetChanged] within [packageSetDebounceMs]
 * (contracts/project-events.md — ≤ 200 ms, no polling).
 */
@OptIn(FlowPreview::class)
class EventRouter(
    private val scope: CoroutineScope,
    private val packageSetDebounceMs: Long = 200L,
) {
    private val packageSignals = MutableSharedFlow<ProjectEvent.PackageSetChanged>(
        extraBufferCapacity = 32,
    )
    private val immediateSignals = MutableSharedFlow<ProjectEvent>(
        extraBufferCapacity = 32,
    )

    val events: Flow<ProjectEvent> = merge(
        packageSignals.debounce(packageSetDebounceMs),
        immediateSignals,
    )

    fun emit(event: ProjectEvent) {
        when (event) {
            is ProjectEvent.PackageSetChanged -> scope.launch {
                packageSignals.emit(event)
            }
            else -> scope.launch {
                immediateSignals.emit(event)
            }
        }
    }
}
