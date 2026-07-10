package com.launcher.app.preset.task120

import androidx.test.core.app.ApplicationProvider
import com.launcher.app.preset.task120.adapter.BundledPoolSource
import com.launcher.app.preset.task120.adapter.BundledPresetSource
import com.launcher.preset.engine.PresetValidator
import com.launcher.preset.model.CapabilityFlag
import com.launcher.preset.model.Component
import com.launcher.preset.port.CapabilityContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.reflect.KClass

/**
 * T067-equivalent JVM smoke — replaces the emulator gate with Robolectric asset
 * loading. Verifies bundled JSON parses under real Android AssetManager.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Task120TestApplication::class)
class BundledAssetsLoadTest {

    private val emptyContract = object : CapabilityContract {
        override fun requires(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
        override fun provides(componentType: KClass<out Component>) = emptySet<CapabilityFlag>()
    }

    @Test
    fun poolJson_loadsAndParses() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pool = BundledPoolSource(ctx).loadPool()
        assertEquals(1, pool.schemaVersion)
        assertEquals(4, pool.declarations.size)
        assertNotNull(pool.byId("font-tile"))
        assertNotNull(pool.byId("sos-main"))
    }

    @Test
    fun bundledPresets_allValidate() = runTest {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val pool = BundledPoolSource(ctx).loadPool()
        val source = BundledPresetSource(ctx)
        val available = source.listAvailable()
        assertTrue(
            "expected all 3 bundled presets, got $available",
            available.containsAll(listOf("simple-launcher", "launcher", "workspace"))
        )
        val validator = PresetValidator(emptyContract)
        for (id in available) {
            val preset = source.loadPreset(id) ?: error("Missing preset $id")
            val errors = validator.validate(preset, pool)
            assertTrue("Preset $id has errors: $errors", errors.isEmpty())
        }
    }
}
