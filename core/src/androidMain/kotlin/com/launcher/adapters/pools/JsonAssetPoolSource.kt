package com.launcher.adapters.pools

import com.launcher.api.pools.Pool
import com.launcher.api.pools.PoolEntry
import com.launcher.api.pools.PoolSource

/**
 * Scaffold adapter — future implementation reads `assets/pools/<id>.pool.json`.
 *
 * TODO(server-roadmap): when pools migrate to own-server,
 * NetworkPoolSource replaces this; wire format stays the same.
 *
 * TODO(shareability): community-authored pools become possible once this
 * scaffold is real — see TASK-65 plan §R3.
 */
class JsonAssetPoolSource : PoolSource {

    override suspend fun load(poolId: String): Pool? =
        throw NotImplementedError("scaffold — see TASK-65 plan R3")

    override suspend fun version(poolId: String): Int? =
        throw NotImplementedError("scaffold — see TASK-65 plan R3")

    override suspend fun listEntries(poolId: String): List<PoolEntry> =
        throw NotImplementedError("scaffold — see TASK-65 plan R3")
}
