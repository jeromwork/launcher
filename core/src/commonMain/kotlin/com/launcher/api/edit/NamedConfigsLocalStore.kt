package com.launcher.api.edit

import com.launcher.api.result.Outcome
import kotlinx.coroutines.flow.Flow

/**
 * Port for managing admin's local list of [NamedConfig]s (FR-003). Persists
 * to platform storage (DataStore on Android per T056) или in-memory
 * ([FakeNamedConfigsLocalStore] for tests).
 *
 * F-014.0 scope: **local-only**. F-014.1 introduces parallel
 * `RemoteNamedConfigsStore` port для Firestore backup, composed at use site —
 * no changes to this interface.
 *
 * All write operations are **atomic** at storage boundary:
 *  - [markDefault] flips the [NamedConfig.isDefault] flag и automatically
 *    clears all other configs' flags в a single transaction (FR-003a).
 *  - [create] checks 5-limit + uniqueness + default invariant before persist.
 *  - [removeFromCurrentDevice] removes thisDeviceId from `activeDeviceIds`
 *    and sets `orphanedAt` if the set becomes empty.
 *
 * Per CLAUDE.md rule §6: every port has at least one fake adapter (T051) и
 * one real adapter (T056), DI picks per build flavor (T060).
 *
 * TODO(server-roadmap): F-014.1 будет иметь parallel RemoteNamedConfigsStore;
 *   merge via MergedNamedConfigsRepository at use site.
 */
interface NamedConfigsLocalStore {

    /** Hot stream of current configs. Re-emits after every write. */
    val configs: Flow<List<NamedConfig>>

    /**
     * Append [config] to store atomically.
     *
     * Validates:
     *  - 5-limit (FR-003c) → [StoreError.LimitReached].
     *  - Name uniqueness case-insensitive after NFC normalize → [StoreError.NameAlreadyExists].
     *  - If `config.isDefault == true`, other configs' `isDefault` flags are
     *    cleared in the same transaction (FR-003a).
     *
     * Name validation (NFC, length, allowed chars) is caller's responsibility
     * via [ConfigNameValidator] before passing to this method.
     */
    suspend fun create(config: NamedConfig): Outcome<Unit, StoreError>

    /**
     * Update existing config matched by [configName] via [transform]. Atomic.
     *
     * Returns [StoreError.NotFound] if no config matches.
     */
    suspend fun update(
        configName: String,
        transform: (NamedConfig) -> NamedConfig,
    ): Outcome<Unit, StoreError>

    /**
     * Atomically set `isDefault = true` for [configName] и `false` for all
     * other configs. FR-003a invariant enforced as single transaction.
     */
    suspend fun markDefault(configName: String): Outcome<Unit, StoreError>

    /**
     * Add `thisDeviceId` to [configName]'s `activeDeviceIds`. Clears
     * `orphanedAt` if the config was previously orphan.
     */
    suspend fun applyToCurrentDevice(
        configName: String,
        thisDeviceId: String,
    ): Outcome<Unit, StoreError>

    /**
     * Remove `thisDeviceId` from [configName]'s `activeDeviceIds`. If the
     * set becomes empty, set `orphanedAt` to [nowMillis].
     *
     * Refuses with [StoreError.DefaultMustExist] if removing this device
     * would leave the store без any default config.
     */
    suspend fun removeFromCurrentDevice(
        configName: String,
        thisDeviceId: String,
        nowMillis: Long,
    ): Outcome<Unit, StoreError>
}
