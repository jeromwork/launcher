package com.launcher.api.pools

import com.launcher.adapters.pools.HardcodedPoolSource
import com.launcher.adapters.pools.JsonAssetPoolSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

/**
 * Spec T66C (FR-027) — verifies parity between the primary
 * [HardcodedPoolSource] and the scaffold [JsonAssetPoolSource] once the
 * scaffold is implemented.
 *
 * Currently @Ignore: JsonAssetPoolSource throws NotImplementedError (scaffold
 * per plan R3). Un-ignore when assets/pools/<id>.pool.json adapter lands.
 *
 * TODO(post-task-65): drop @Ignore once JsonAssetPoolSource ships.
 */
class PoolSourceRoundtripTest {

    @Test
    @Ignore("scaffold — JsonAssetPoolSource throws NotImplementedError; see plan R3 / quickstart")
    fun hardcodedAndJsonSourcesAgreeOnSystemSettingsEntries() = runTest {
        val hardcoded = HardcodedPoolSource()
        val jsonSrc = JsonAssetPoolSource()
        assertEquals(
            hardcoded.listEntries("system-settings"),
            jsonSrc.listEntries("system-settings"),
        )
    }
}
