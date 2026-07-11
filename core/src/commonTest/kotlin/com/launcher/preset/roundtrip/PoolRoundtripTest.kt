package com.launcher.preset.roundtrip

import com.launcher.preset.model.Pool
import kotlin.test.Test
import kotlin.test.assertEquals

class PoolRoundtripTest {

    @Test
    fun poolRoundtrip_bitIdentical() {
        val pool = mvpPool()
        val encoded = testJson.encodeToString(Pool.serializer(), pool)
        val decoded = testJson.decodeFromString(Pool.serializer(), encoded)
        assertEquals(pool, decoded)
        val reencoded = testJson.encodeToString(Pool.serializer(), decoded)
        assertEquals(encoded, reencoded)
    }
}
