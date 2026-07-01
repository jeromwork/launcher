package com.launcher.api.pools

/**
 * Loader port for [Pool]s. Adapters: `HardcodedPoolSource` (primary),
 * `JsonAssetPoolSource` (scaffold, future).
 */
interface PoolSource {

    suspend fun load(poolId: String): Pool?

    suspend fun version(poolId: String): Int?

    suspend fun listEntries(poolId: String): List<PoolEntry>
}
