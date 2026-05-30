package com.launcher.api.edit

import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [NamedConfigsLocalStore] adapter для tests (CLAUDE.md §6 mock-first
 * development). Stores configs в [MutableStateFlow]; writes serialized via
 * mutex для atomic ops (FR-003a invariant, FR-003c limit, name uniqueness).
 *
 * Optional [initial] seed for tests that need pre-populated state.
 *
 * Lives в commonTest so KMM tests на любом target могут разделять fixture.
 */
class FakeNamedConfigsLocalStore(
    initial: List<NamedConfig> = emptyList(),
) : NamedConfigsLocalStore {

    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val configs: Flow<List<NamedConfig>> = state.asStateFlow()

    /** Snapshot accessor for assertions without flow collection. */
    fun snapshot(): List<NamedConfig> = state.value

    override suspend fun create(config: NamedConfig): Outcome<Unit, StoreError> = mutex.withLock {
        val current = state.value
        if (current.size >= NamedConfig.MAX_CONFIGS_PER_ADMIN) {
            return Outcome.Failure(StoreError.LimitReached)
        }
        val normalized = config.configName.lowercase()
        if (current.any { it.configName.lowercase() == normalized }) {
            return Outcome.Failure(StoreError.NameAlreadyExists(config.configName))
        }
        // FR-003a invariant: if new config claims default, clear others atomically.
        val cleared = if (config.isDefault) {
            current.map { if (it.isDefault) it.copy(isDefault = false) else it }
        } else {
            current
        }
        state.value = cleared + config
        Outcome.Success(Unit)
    }

    override suspend fun update(
        configName: String,
        transform: (NamedConfig) -> NamedConfig,
    ): Outcome<Unit, StoreError> = mutex.withLock {
        val current = state.value
        val index = current.indexOfFirst { it.configName == configName }
        if (index < 0) return Outcome.Failure(StoreError.NotFound)
        val transformed = transform(current[index])
        // Guard: if transformed clears the only default — refuse.
        if (current[index].isDefault && !transformed.isDefault) {
            val anyOtherDefault = current.withIndex()
                .any { (i, c) -> i != index && c.isDefault }
            if (!anyOtherDefault) {
                return Outcome.Failure(StoreError.DefaultMustExist)
            }
        }
        // If transformed makes config default, clear others (FR-003a invariant).
        val updatedList = current.toMutableList()
        updatedList[index] = transformed
        if (transformed.isDefault && !current[index].isDefault) {
            for (i in updatedList.indices) {
                if (i != index && updatedList[i].isDefault) {
                    updatedList[i] = updatedList[i].copy(isDefault = false)
                }
            }
        }
        state.value = updatedList.toList()
        Outcome.Success(Unit)
    }

    override suspend fun markDefault(configName: String): Outcome<Unit, StoreError> = mutex.withLock {
        val current = state.value
        if (current.none { it.configName == configName }) {
            return Outcome.Failure(StoreError.NotFound)
        }
        state.value = current.map { c ->
            c.copy(isDefault = c.configName == configName)
        }
        Outcome.Success(Unit)
    }

    override suspend fun applyToCurrentDevice(
        configName: String,
        thisDeviceId: String,
    ): Outcome<Unit, StoreError> = mutex.withLock {
        val current = state.value
        val index = current.indexOfFirst { it.configName == configName }
        if (index < 0) return Outcome.Failure(StoreError.NotFound)
        val updatedList = current.toMutableList()
        updatedList[index] = current[index].copy(
            activeDeviceIds = current[index].activeDeviceIds + thisDeviceId,
            orphanedAt = null, // restored from orphan, if it was orphan
        )
        state.value = updatedList.toList()
        Outcome.Success(Unit)
    }

    override suspend fun removeFromCurrentDevice(
        configName: String,
        thisDeviceId: String,
        nowMillis: Long,
    ): Outcome<Unit, StoreError> = mutex.withLock {
        val current = state.value
        val index = current.indexOfFirst { it.configName == configName }
        if (index < 0) return Outcome.Failure(StoreError.NotFound)
        val target = current[index]
        val newActiveSet = target.activeDeviceIds - thisDeviceId
        val willBecomeOrphan = newActiveSet.isEmpty() && target.activeDeviceIds.isNotEmpty()
        // Refuse if this would leave the store без any default config.
        if (target.isDefault && willBecomeOrphan) {
            val anyOtherDefault = current.withIndex()
                .any { (i, c) -> i != index && c.isDefault && c.isActive }
            if (!anyOtherDefault) {
                return Outcome.Failure(StoreError.DefaultMustExist)
            }
        }
        val updatedList = current.toMutableList()
        updatedList[index] = target.copy(
            activeDeviceIds = newActiveSet,
            orphanedAt = if (willBecomeOrphan) nowMillis else target.orphanedAt,
        )
        state.value = updatedList.toList()
        Outcome.Success(Unit)
    }
}
