package com.launcher.core.capability

import com.launcher.api.capability.Capability
import com.launcher.api.capability.CapabilityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Real Android implementation of [CapabilityRepository]. Wires:
 *  - [AndroidCapabilityCollector] (system events → fresh snapshots).
 *  - [CapabilitySnapshotProjection] (DataStore persistence для cold-start).
 *
 * Cold-start path: при construction reads DataStore lazily (off the main
 * thread); seeds in-memory `MutableStateFlow`. UI rendering начинается
 * immediately с last-known data (zero-flicker).
 *
 * Hot path: upstream `collector.snapshots` Flow emits — repository updates
 * StateFlow + persists to DataStore on background dispatcher. Errors в
 * persistence не пропагируются consumers (logged via projection's logger).
 *
 * Lifetime: single instance (Koin singleton). `scope` обычно
 * `LauncherApplication`-scoped (via Koin или explicit
 * `ProcessLifecycleOwner.get().lifecycleScope`).
 */
class AndroidCapabilityRepository(
    private val collector: AndroidCapabilityCollector,
    private val projection: CapabilitySnapshotProjection,
    scope: CoroutineScope,
) : CapabilityRepository {

    private val state = MutableStateFlow<List<Capability>>(emptyList())

    init {
        // Seed from DataStore (returns emptyList on first run / corruption).
        scope.launch(Dispatchers.IO) {
            projection.flow.collect { persisted ->
                if (state.value.isEmpty()) state.value = persisted
            }
        }
        // Subscribe to live updates: update memory + persist async.
        scope.launch(Dispatchers.Default) {
            collector.snapshots
                .onEach { snapshot ->
                    state.value = snapshot
                    // Fire-and-forget persist on IO; if it fails, projection.flow
                    // emits the previous value at next cold-start (acceptable).
                    launch(Dispatchers.IO) { projection.write(snapshot) }
                }
                .collect()
        }
    }

    override fun observe(): Flow<List<Capability>> = state.asStateFlow()
    override fun snapshot(): List<Capability> = state.value
}
