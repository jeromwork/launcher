package com.launcher.core.health

import com.launcher.api.health.Health
import com.launcher.api.health.HealthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Real Android implementation of [HealthRepository]. Wires:
 *  - [AndroidHealthCollector] (system events → fresh Health snapshots).
 *  - [HealthSnapshotProjection] (DataStore persistence для cold-start).
 *
 * Cold-start path: read DataStore lazily; if exists — seed StateFlow с
 * persisted value, иначе — с [HealthSnapshotProjection.unknownDefault].
 * Collector quickly overwrites with fresh data once subscriptions wire up.
 *
 * Hot path: collector emits → update StateFlow + persist async.
 * `distinctUntilChanged` дедуплицирует identical emissions
 * (e.g. multiple network callbacks producing same Health).
 */
class AndroidHealthRepository(
    private val collector: AndroidHealthCollector,
    private val projection: HealthSnapshotProjection,
    scope: CoroutineScope,
) : HealthRepository {

    private val state = MutableStateFlow(projection.unknownDefault())

    init {
        // Seed from DataStore (one-shot). After first emit ≠ null we let
        // collector take over; persisted value is only the cold-start placeholder.
        scope.launch(Dispatchers.IO) {
            val persisted = projection.flow
            persisted.collect { h ->
                if (h != null && state.value.lastSeen == 0L) {
                    state.value = h
                }
            }
        }
        // Live updates from collector. Persist on each change.
        scope.launch(Dispatchers.Default) {
            collector.snapshots
                .distinctUntilChanged()
                .onEach { snapshot ->
                    state.value = snapshot
                    launch(Dispatchers.IO) { projection.write(snapshot) }
                }
                .collect()
        }
    }

    override fun observe(): Flow<Health> = state.asStateFlow()
    override fun snapshot(): Health = state.value
}
