package com.launcher.preset.roundtrip

import com.launcher.preset.model.Pool
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * SC-008 backward-compat scaffolding.
 * MVP has only schemaVersion=1 → this is a placeholder that unfreezes when v2 arrives.
 */
class BackwardCompatTest {

    @Test
    @Ignore
    fun poolV1_readableByV2Loader() {
        val v1Json = mvpPool().let { testJson.encodeToString(Pool.serializer(), it) }
        val decoded = testJson.decodeFromString(Pool.serializer(), v1Json)
        assertEquals(1, decoded.schemaVersion)
    }
}
