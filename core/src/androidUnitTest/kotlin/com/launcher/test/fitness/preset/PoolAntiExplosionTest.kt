package com.launcher.test.fitness.preset

import com.launcher.preset.model.Pool
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * TASK-120 fitness #8 — pool anti-explosion. Groups declarations by Component
 * subtype; hard-fails at N ≥ 6 per subtype. Parametrization via paramsOverride
 * covers variation; a new pool entry means we're spelling out an entire
 * catalogue instead of parametrizing.
 */
class PoolAntiExplosionTest {

    private val json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
    }

    @Test
    fun poolJson_hasAtMost5DeclarationsPerComponentSubtype() {
        val poolFile = locatePoolJson()
        Assume.assumeTrue("assets/pool.json not present yet", poolFile != null)
        val pool = json.decodeFromString(Pool.serializer(), poolFile!!.readText())
        val counts = pool.declarations.groupingBy { it.component::class.simpleName ?: "?" }.eachCount()
        val violators = counts.filter { it.value >= 6 }
        assertTrue(
            "Fitness #8: pool has ≥ 6 declarations per Component subtype: $violators. " +
                "Use paramsOverride instead of catalogue entries.",
            violators.isEmpty(),
        )
    }

    private fun locatePoolJson(): File? {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(
            File(cwd, "../app/src/main/assets/preset/pool.json"),
            File(cwd, "app/src/main/assets/preset/pool.json"),
        )
        return candidates.firstOrNull { it.isFile }
    }
}
